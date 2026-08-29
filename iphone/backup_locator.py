"""
backup_locator.py — find iTunes / Apple Devices / Finder local backups on this
computer and pull individual files (the call-history database, the address book)
out of them.

Two kinds of backup:
  * unencrypted — Manifest.db is a plain SQLite index; files sit on disk under a
    two-hex-char subfolder. Handled here with only the standard library.
  * encrypted   — Manifest.db and every file blob are AES-encrypted with a key
    derived from the backup password. Handled via the optional
    `iphone-backup-decrypt` package (imported lazily so the unencrypted path
    never needs it).

Everything runs locally. Nothing is uploaded anywhere.
"""

from __future__ import annotations

import os
import plistlib
import shutil
import sqlite3
from dataclasses import dataclass
from datetime import datetime, timezone

# Known locations inside a backup (domain, relative path).
CALL_HISTORY = ("HomeDomain", "Library/CallHistoryDB/CallHistory.storedata")
ADDRESS_BOOK = ("HomeDomain", "Library/AddressBook/AddressBook.sqlitedb")


class BackupError(Exception):
    """A backup could not be read (missing file, wrong password, missing dependency)."""


@dataclass
class Backup:
    path: str
    udid: str
    device_name: str
    last_backup: datetime | None
    encrypted: bool

    def label(self) -> str:
        when = self.last_backup.strftime("%Y-%m-%d %H:%M") if self.last_backup else "unknown date"
        lock = "  [encrypted]" if self.encrypted else ""
        return f"{self.device_name or self.udid[:8]} - {when}{lock}"


# --------------------------------------------------------------------------- #
#  Discovery
# --------------------------------------------------------------------------- #
def _candidate_roots() -> list[str]:
    roots: list[str] = []
    home = os.path.expanduser("~")
    appdata = os.environ.get("APPDATA")
    # Apple Devices app (Microsoft Store) on Windows.
    roots.append(os.path.join(home, "Apple", "MobileSync", "Backup"))
    # Legacy iTunes on Windows.
    if appdata:
        roots.append(os.path.join(appdata, "Apple Computer", "MobileSync", "Backup"))
        roots.append(os.path.join(appdata, "Apple", "MobileSync", "Backup"))
    # macOS.
    roots.append(os.path.join(home, "Library", "Application Support", "MobileSync", "Backup"))
    seen: set[str] = set()
    out: list[str] = []
    for r in roots:
        if r not in seen and os.path.isdir(r):
            seen.add(r)
            out.append(r)
    return out


def _read_plist(path: str) -> dict:
    try:
        with open(path, "rb") as f:
            return plistlib.load(f)
    except (OSError, plistlib.InvalidFileException, ValueError):
        return {}


def describe_backup(path: str) -> Backup:
    """Build a Backup record for one backup folder (no decryption needed)."""
    udid = os.path.basename(path.rstrip(os.sep))
    info = _read_plist(os.path.join(path, "Info.plist"))
    manifest = _read_plist(os.path.join(path, "Manifest.plist"))

    last = info.get("Last Backup Date")
    if isinstance(last, datetime):
        last_dt = last if last.tzinfo else last.replace(tzinfo=timezone.utc)
    else:
        try:
            last_dt = datetime.fromtimestamp(os.path.getmtime(path), tz=timezone.utc)
        except OSError:
            last_dt = None

    return Backup(
        path=path,
        udid=udid,
        device_name=str(info.get("Device Name") or info.get("Product Name") or ""),
        last_backup=last_dt,
        encrypted=bool(manifest.get("IsEncrypted", False)),
    )


def find_backups() -> list[Backup]:
    """Every local backup on this machine, newest first."""
    backups: list[Backup] = []
    for root in _candidate_roots():
        for name in os.listdir(root):
            folder = os.path.join(root, name)
            if os.path.isfile(os.path.join(folder, "Manifest.plist")):
                backups.append(describe_backup(folder))
    backups.sort(key=lambda b: b.last_backup or datetime.min.replace(tzinfo=timezone.utc), reverse=True)
    return backups


# --------------------------------------------------------------------------- #
#  Extraction
# --------------------------------------------------------------------------- #
def _extract_unencrypted(backup_path: str, domain: str, relative_path: str, out_path: str) -> str:
    manifest_db = os.path.join(backup_path, "Manifest.db")
    if not os.path.isfile(manifest_db):
        raise BackupError(f"No Manifest.db in {backup_path} — is this a complete backup?")
    try:
        con = sqlite3.connect(f"file:{manifest_db}?mode=ro", uri=True)
        row = con.execute(
            "SELECT fileID FROM Files WHERE domain = ? AND relativePath = ?",
            (domain, relative_path),
        ).fetchone()
        con.close()
    except sqlite3.Error as e:
        raise BackupError(f"Could not read Manifest.db: {e}") from e
    if not row:
        if relative_path == CALL_HISTORY[1]:
            raise BackupError(
                "Call history is not in this backup.\n\n"
                "iOS only saves call history in *encrypted* backups. In the Apple Devices "
                "app / iTunes / Finder, tick \"Encrypt local backup\" (set a password you "
                "will remember), run the backup again, then come back and enter that password."
            )
        raise BackupError(
            f"{relative_path} is not in this backup (the backup may be partial)."
        )
    file_id = row[0]
    blob = os.path.join(backup_path, file_id[:2], file_id)
    if not os.path.isfile(blob):
        raise BackupError(f"Backup index points to {blob}, but that file is missing.")
    shutil.copyfile(blob, out_path)
    return out_path


def _extract_encrypted(backup_path: str, relative_path: str, password: str, out_path: str) -> str:
    if not password:
        raise BackupError("This backup is encrypted — a backup password is required.")
    try:
        from iphone_backup_decrypt import EncryptedBackup
    except ImportError as e:
        raise BackupError(
            "Reading an encrypted backup needs the 'iphone-backup-decrypt' package.\n"
            "Install it with:  pip install iphone-backup-decrypt\n"
            "(or make an unencrypted backup instead)."
        ) from e
    try:
        backup = EncryptedBackup(backup_directory=backup_path, passphrase=password)
        backup.extract_file(relative_path=relative_path, output_filename=out_path)
    except ValueError as e:
        # The library raises ValueError("Failed to decrypt keys: incorrect passphrase?")
        if "passphrase" in str(e).lower():
            raise BackupError("Wrong backup password.") from e
        raise BackupError(str(e)) from e
    except FileNotFoundError as e:
        raise BackupError(
            f"{relative_path} is not in this backup (call history may not be synced)."
        ) from e
    except Exception as e:  # noqa: BLE001 — surface anything else cleanly to the GUI
        raise BackupError(f"Could not decrypt the backup: {e}") from e
    if not os.path.isfile(out_path):
        raise BackupError("Decryption reported success but no file was written.")
    return out_path


def extract_file(
    backup: Backup | str,
    domain_and_path: tuple[str, str],
    out_path: str,
    password: str | None = None,
) -> str:
    """Pull one known file out of a backup to `out_path`. Returns `out_path`."""
    b = backup if isinstance(backup, Backup) else describe_backup(backup)
    domain, relative_path = domain_and_path
    os.makedirs(os.path.dirname(os.path.abspath(out_path)) or ".", exist_ok=True)
    if b.encrypted:
        return _extract_encrypted(b.path, relative_path, password or "", out_path)
    return _extract_unencrypted(b.path, domain, relative_path, out_path)


def backup_has_call_history(backup: Backup | str) -> bool | None:
    """True/False for an unencrypted backup; None for an encrypted one (can't check
    the manifest without the password — assume the file is there)."""
    b = backup if isinstance(backup, Backup) else describe_backup(backup)
    if b.encrypted:
        return None
    manifest_db = os.path.join(b.path, "Manifest.db")
    if not os.path.isfile(manifest_db):
        return None
    try:
        con = sqlite3.connect(f"file:{manifest_db}?mode=ro", uri=True)
        hit = con.execute(
            "SELECT 1 FROM Files WHERE domain = ? AND relativePath = ? LIMIT 1", CALL_HISTORY
        ).fetchone()
        con.close()
        return hit is not None
    except sqlite3.Error:
        return None


def extract_call_history(backup: Backup | str, out_path: str, password: str | None = None) -> str:
    return extract_file(backup, CALL_HISTORY, out_path, password)


def extract_address_book(backup: Backup | str, out_path: str, password: str | None = None) -> str | None:
    """Best-effort — the address book improves 'known contact' detection but is optional."""
    try:
        return extract_file(backup, ADDRESS_BOOK, out_path, password)
    except BackupError:
        return None
