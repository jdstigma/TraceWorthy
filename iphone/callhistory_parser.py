"""
callhistory_parser.py — turn an iPhone's CallHistory.storedata (a SQLite Core
Data store, table ZCALLRECORD) into a flat list of calls and a CSV that matches
the Android app's export exactly, so analysis/analyze_calls.py and
analysis/packet.py treat it identically to an app export.

App CSV header (see android/.../CsvExporter.kt):
    Timestamp,Number,ContactName,Type,DurationSeconds,Suspicious,Severity,Note
"""

from __future__ import annotations

import csv
import os
import re
import shutil
import sqlite3
import tempfile
from dataclasses import dataclass
from datetime import datetime, timedelta, timezone

# Cocoa / Core Data timestamps count seconds from 2001-01-01 UTC, not the Unix epoch.
_COCOA_EPOCH = datetime(2001, 1, 1, tzinfo=timezone.utc)

CSV_HEADER = ["Timestamp", "Number", "ContactName", "Type", "DurationSeconds", "Suspicious", "Severity", "Note"]

# ZCALLTYPE values (matches iLEAPP's decoding).
CALLTYPE_PHONE = 1
CALLTYPE_THIRD_PARTY = 0
CALLTYPE_FACETIME_VIDEO = 8
CALLTYPE_FACETIME_AUDIO = 16
FACETIME_TYPES = {CALLTYPE_FACETIME_VIDEO, CALLTYPE_FACETIME_AUDIO}

DEFAULT_FLAG_THRESHOLD_SECONDS = 15

# iOS keeps only a rolling window of recent calls in CallHistory.storedata. State this
# on every generated document so no one mistakes a partial export for the full history.
IPHONE_SOURCE_NOTE = (
    "This report was generated from an iPhone local backup, which keeps only recent call "
    "history (roughly the last 1,000 calls). Older calls are not retained on the device - "
    "request full records from your phone carrier for the complete history."
)


@dataclass
class Call:
    """One call from the iPhone history. Mirrors the app's CallEntry."""

    timestamp: datetime          # local, naive
    number: str
    contact_name: str
    duration_seconds: int
    call_type: int               # raw ZCALLTYPE
    originated: int              # 0 incoming, 1 outgoing
    answered: bool

    @property
    def incoming(self) -> bool:
        return self.originated == 0

    @property
    def type_label(self) -> str:
        # iOS has no distinct "Rejected"; an unanswered incoming call is a miss.
        if not self.incoming:
            return "Outgoing"
        return "Incoming" if self.answered else "Missed"

    @property
    def is_facetime(self) -> bool:
        return self.call_type in (CALLTYPE_FACETIME_VIDEO, CALLTYPE_FACETIME_AUDIO)

    def suspicious(self, threshold_seconds: int = DEFAULT_FLAG_THRESHOLD_SECONDS) -> bool:
        """Same predicate as CallEntry.isSuspicious: an incoming-like call from a
        number not in your contacts that never connected or was answered but silent."""
        return self.incoming and not self.contact_name and self.duration_seconds <= threshold_seconds


# --------------------------------------------------------------------------- #
#  Number / timestamp normalisation
# --------------------------------------------------------------------------- #
_BYTES_STR_RE = re.compile(r"^b'(.*)'$|^b\"(.*)\"$")


def _clean_number(raw) -> str:
    """ZADDRESS may be bytes, or a string that stringified a bytes object as b'...'."""
    if raw is None:
        return ""
    if isinstance(raw, (bytes, bytearray)):
        return raw.decode("utf-8", "ignore").strip()
    s = str(raw).strip()
    m = _BYTES_STR_RE.match(s)
    if m:
        s = (m.group(1) or m.group(2) or "").strip()
    return s


def _cocoa_to_local(seconds) -> datetime:
    dt_utc = _COCOA_EPOCH + timedelta(seconds=float(seconds or 0))
    return dt_utc.astimezone().replace(tzinfo=None)


def normalize_number(number: str) -> str:
    """Last 10 digits — used to match a call number against a contact number."""
    digits = re.sub(r"\D", "", number or "")
    return digits[-10:] if len(digits) >= 10 else digits


# --------------------------------------------------------------------------- #
#  Contacts (optional — improves 'known contact' detection)
# --------------------------------------------------------------------------- #
def load_contact_index(addressbook_path: str | None) -> dict[str, str]:
    """{normalized number: contact display name} from AddressBook.sqlitedb. Best-effort."""
    if not addressbook_path:
        return {}
    index: dict[str, str] = {}
    try:
        con = sqlite3.connect(f"file:{addressbook_path}?mode=ro", uri=True)
        rows = con.execute(
            """
            SELECT mv.value AS number,
                   TRIM(COALESCE(p.first,'') || ' ' || COALESCE(p.last,'')) AS name,
                   COALESCE(p.Organization,'') AS org
            FROM ABMultiValue mv
            JOIN ABPerson p ON p.ROWID = mv.record_id
            WHERE mv.value IS NOT NULL
            """
        ).fetchall()
        con.close()
    except sqlite3.Error:
        return {}
    for number, name, org in rows:
        key = normalize_number(str(number))
        if not key:
            continue
        display = (name or "").strip() or (org or "").strip() or "Known contact"
        index.setdefault(key, display)
    return index


# --------------------------------------------------------------------------- #
#  Parse
# --------------------------------------------------------------------------- #
def _table_columns(con: sqlite3.Connection, table: str) -> set[str]:
    return {r[1] for r in con.execute(f"PRAGMA table_info({table})")}


def connect(storedata_path: str) -> sqlite3.Connection:
    """Open the call-history DB for reading. If a non-empty -wal sidecar is next to
    it, copy the DB + sidecars somewhere writable and open read-write so SQLite
    replays the WAL — recent calls are usually only in there."""
    wal = storedata_path + "-wal"
    if os.path.isfile(wal) and os.path.getsize(wal) > 0:
        tmp = tempfile.mkdtemp(prefix="tw_wal_")
        base = os.path.join(tmp, "CallHistory.storedata")
        shutil.copyfile(storedata_path, base)
        for suffix in ("-wal", "-shm"):
            src = storedata_path + suffix
            if os.path.isfile(src):
                shutil.copyfile(src, base + suffix)
        con = sqlite3.connect(base)
        con.execute("PRAGMA wal_checkpoint(TRUNCATE)")  # fold the WAL into the main file
        return con
    return sqlite3.connect(f"file:{storedata_path}?mode=ro", uri=True)


class NoCallRecords(ValueError):
    """The call-history table exists but holds no rows (call history likely lives in iCloud)."""


def _call_table(con: sqlite3.Connection) -> str:
    """The table holding call records. ZCALLRECORD on every iOS seen so far, but
    fall back to anything that looks right so a schema change fails soft."""
    names = [r[0] for r in con.execute("SELECT name FROM sqlite_master WHERE type='table'")]
    if "ZCALLRECORD" in names:
        return "ZCALLRECORD"
    for n in names:
        cols = _table_columns(con, n)
        if "ZADDRESS" in cols and "ZDATE" in cols and "ZDURATION" in cols:
            return n
    raise ValueError("No call-records table — this does not look like a CallHistory.storedata file.")


def parse(
    storedata_path: str,
    addressbook_path: str | None = None,
    *,
    include_facetime: bool = False,
    include_app_calls: bool = False,
    flag_threshold_seconds: int = DEFAULT_FLAG_THRESHOLD_SECONDS,
) -> list[Call]:
    con = connect(storedata_path)
    try:
        table = _call_table(con)
        cols = _table_columns(con, table)
        has_type = "ZCALLTYPE" in cols
        has_name = "ZNAME" in cols
        has_answered = "ZANSWERED" in cols
        select = [
            "ZADDRESS", "ZDATE", "ZDURATION",
            "ZCALLTYPE" if has_type else "1 AS ZCALLTYPE",
            "ZORIGINATED" if "ZORIGINATED" in cols else "0 AS ZORIGINATED",
            "ZANSWERED" if has_answered else "1 AS ZANSWERED",
            "ZNAME" if has_name else "NULL AS ZNAME",
        ]
        rows = con.execute(
            f"SELECT {', '.join(select)} FROM '{table}' ORDER BY ZDATE"
        ).fetchall()
    finally:
        con.close()

    if not rows:
        raise NoCallRecords(
            f"The call-history database in this backup has no records (table '{table}' is empty).\n"
            "This usually means Call History is syncing to iCloud rather than being stored on the "
            "device. On the iPhone: Settings > [your name] > iCloud > See All > check whether "
            "\"Call History\" / iCloud Drive is on. Otherwise, request call records from your carrier."
        )

    contacts = load_contact_index(addressbook_path)
    calls: list[Call] = []
    dropped_types: dict[int, int] = {}
    for address, zdate, zdur, ztype, zorig, zans, zname in rows:
        ztype = int(ztype) if ztype is not None else CALLTYPE_PHONE
        keep = True
        if ztype in FACETIME_TYPES:
            keep = include_facetime
        elif ztype == CALLTYPE_THIRD_PARTY:
            keep = include_app_calls
        if not keep:
            dropped_types[ztype] = dropped_types.get(ztype, 0) + 1
            continue
        number = _clean_number(address)
        contact = contacts.get(normalize_number(number), "")
        if not contact and zname:
            contact = str(zname).strip()
        calls.append(
            Call(
                timestamp=_cocoa_to_local(zdate),
                number=number or "Unknown",
                contact_name=contact,
                duration_seconds=int(round(float(zdur or 0))),
                call_type=ztype,
                originated=int(zorig or 0),
                answered=bool(zans),
            )
        )
    if not calls and dropped_types:
        kinds = {8: "FaceTime video", 16: "FaceTime audio", 0: "third-party app"}
        detail = ", ".join(f"{n} {kinds.get(t, f'type {t}')}" for t, n in sorted(dropped_types.items()))
        raise NoCallRecords(
            f"All {sum(dropped_types.values())} records were filtered out ({detail}) and none are "
            "cellular phone calls. Re-run including those types: add --facetime and/or "
            "--include-app-calls (CLI), or the matching checkboxes in the app."
        )
    calls.sort(key=lambda c: c.timestamp)
    return calls


# --------------------------------------------------------------------------- #
#  CSV
# --------------------------------------------------------------------------- #
def write_csv(calls: list[Call], out_path: str, flag_threshold_seconds: int = DEFAULT_FLAG_THRESHOLD_SECONDS) -> str:
    with open(out_path, "w", newline="", encoding="utf-8") as f:
        w = csv.writer(f)
        w.writerow(CSV_HEADER)
        for c in sorted(calls, key=lambda x: x.timestamp, reverse=True):
            w.writerow([
                c.timestamp.strftime("%Y-%m-%d %H:%M:%S"),
                c.number,
                c.contact_name,
                c.type_label,
                c.duration_seconds,
                "YES" if c.suspicious(flag_threshold_seconds) else "",
                "",   # Severity — no per-call tags in an iPhone backup
                "",   # Note
            ])
    return out_path
