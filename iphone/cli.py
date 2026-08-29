"""
cli.py — headless entry point for the iPhone call-log tools.

    python -m iphone.cli list
    python -m iphone.cli extract  --backup auto [--password PW] --out call_history.storedata
    python -m iphone.cli csv      --backup auto [--password PW] [--facetime] --out iphone_calls.csv
    python -m iphone.cli csv      --storedata call_history.storedata [--addressbook ab.sqlitedb] --out iphone_calls.csv
    python -m iphone.cli packet   --csv iphone_calls.csv [--profile traceworthy_profile.json] --out out/

Everything runs locally; nothing is uploaded.
"""

from __future__ import annotations

import argparse
import os
import sys
import tempfile

_HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, _HERE)
sys.path.insert(0, os.path.join(_HERE, os.pardir, "analysis"))

from backup_locator import (  # noqa: E402
    BackupError, describe_backup, extract_address_book, extract_call_history, find_backups,
)
import callhistory_parser as chp  # noqa: E402
from callhistory_parser import IPHONE_SOURCE_NOTE  # noqa: E402  (re-exported for callers)


# --------------------------------------------------------------------------- #
def _resolve_backup(spec: str):
    if spec and spec != "auto":
        if not os.path.isdir(spec):
            sys.exit(f"Not a backup folder: {spec}")
        return describe_backup(spec)
    backups = find_backups()
    if not backups:
        sys.exit("No iPhone backups found. Make one with the Apple Devices app / iTunes / Finder first.")
    print(f"Using most recent backup: {backups[0].label()}")
    return backups[0]


def _extract_dbs(backup, password, workdir):
    storedata = os.path.join(workdir, "call_history.storedata")
    extract_call_history(backup, storedata, password)
    ab = os.path.join(workdir, "address_book.sqlitedb")
    ab = extract_address_book(backup, ab, password)
    return storedata, ab


# --------------------------------------------------------------------------- #
def cmd_list(_args):
    backups = find_backups()
    if not backups:
        print("No local iPhone backups found.")
        return
    for b in backups:
        print(f"  {b.label()}")
        print(f"      {b.path}")


def cmd_extract(args):
    backup = _resolve_backup(args.backup)
    try:
        extract_call_history(backup, args.out, args.password)
    except BackupError as e:
        sys.exit(str(e))
    print(f"Extracted call history -> {os.path.abspath(args.out)}")


def cmd_inspect(args):
    """Dump what's actually inside the backup's call-history DB — no phone numbers,
    just table names, row counts, and column-value distributions. For diagnosing
    'No calls found'."""
    import sqlite3

    if args.storedata:
        storedata = args.storedata
    else:
        backup = _resolve_backup(args.backup)
        try:
            storedata, _ = _extract_dbs(backup, args.password, tempfile.mkdtemp(prefix="tw_iphone_"))
        except BackupError as e:
            sys.exit(str(e))

    print(f"File: {storedata}  ({os.path.getsize(storedata):,} bytes)")
    con = sqlite3.connect(f"file:{storedata}?mode=ro", uri=True)
    tables = [r[0] for r in con.execute("SELECT name FROM sqlite_master WHERE type='table' ORDER BY name")]
    print(f"Tables: {tables}")

    for t in tables:
        if not (t.upper().startswith("ZCALL") or "CALL" in t.upper()):
            continue
        n = con.execute(f"SELECT COUNT(*) FROM '{t}'").fetchone()[0]
        cols = [r[1] for r in con.execute(f"PRAGMA table_info('{t}')")]
        print(f"\n[{t}]  rows={n}")
        print(f"  columns: {cols}")
        if n == 0:
            continue
        for col in ("ZCALLTYPE", "ZORIGINATED", "ZANSWERED", "ZSERVICE_PROVIDER", "ZCALL_CATEGORY"):
            if col in cols:
                dist = con.execute(
                    f"SELECT {col}, COUNT(*) FROM '{t}' GROUP BY {col} ORDER BY 2 DESC"
                ).fetchall()
                print(f"  {col}: {dist}")
        if "ZDATE" in cols:
            lo, hi = con.execute(f"SELECT MIN(ZDATE), MAX(ZDATE) FROM '{t}'").fetchone()
            print(f"  ZDATE range: {chp._cocoa_to_local(lo)}  ..  {chp._cocoa_to_local(hi)}")
        if "ZADDRESS" in cols:
            samples = con.execute(f"SELECT ZADDRESS, typeof(ZADDRESS) FROM '{t}' LIMIT 3").fetchall()
            masked = [(f"...{chp._clean_number(a)[-4:]}", ty) for a, ty in samples]
            print(f"  ZADDRESS samples (masked): {masked}")
    con.close()


def cmd_csv(args):
    if args.storedata:
        storedata, ab = args.storedata, args.addressbook
    else:
        backup = _resolve_backup(args.backup)
        try:
            storedata, ab = _extract_dbs(backup, args.password, tempfile.mkdtemp(prefix="tw_iphone_"))
        except BackupError as e:
            sys.exit(str(e))

    try:
        calls = chp.parse(storedata, ab, include_facetime=args.facetime,
                          include_app_calls=args.include_app_calls)
    except chp.NoCallRecords as e:
        sys.exit(str(e))
    chp.write_csv(calls, args.out)
    flagged = sum(1 for c in calls if c.suspicious())
    print(f"{len(calls)} calls ({flagged} flagged) -> {os.path.abspath(args.out)}")
    print(IPHONE_SOURCE_NOTE)


def cmd_packet(args):
    import packet

    rows = packet.rows_from_csv(args.csv)
    if not rows:
        sys.exit("No usable calls in the CSV.")
    profile = packet._JsonProfile.load(args.profile)
    written = packet.generate_all(rows, profile, args.out, IPHONE_SOURCE_NOTE)
    print(f"{len(rows)} calls -> {os.path.abspath(args.out)}")
    for name, path in written.items():
        print(f"  {name}: {os.path.basename(path)}")


# --------------------------------------------------------------------------- #
def build_parser() -> argparse.ArgumentParser:
    p = argparse.ArgumentParser(prog="iphone.cli", description=__doc__,
                                formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = p.add_subparsers(dest="cmd", required=True)

    sub.add_parser("list", help="Show local iPhone backups").set_defaults(func=cmd_list)

    e = sub.add_parser("extract", help="Pull CallHistory.storedata out of a backup")
    e.add_argument("--backup", default="auto", help="Backup folder, or 'auto' for the newest")
    e.add_argument("--password", help="Backup password (encrypted backups only)")
    e.add_argument("--out", default="call_history.storedata")
    e.set_defaults(func=cmd_extract)

    c = sub.add_parser("csv", help="Backup or .storedata -> app-format CSV")
    c.add_argument("--backup", default="auto")
    c.add_argument("--password")
    c.add_argument("--storedata", help="Parse this file directly instead of a backup")
    c.add_argument("--addressbook", help="AddressBook.sqlitedb for better contact matching")
    c.add_argument("--facetime", action="store_true", help="Include FaceTime calls (default: cellular only)")
    c.add_argument("--include-app-calls", action="store_true",
                   help="Include third-party VoIP app calls (WhatsApp, etc.)")
    c.add_argument("--out", default="iphone_calls.csv")
    c.set_defaults(func=cmd_csv)

    k = sub.add_parser("packet", help="CSV -> full evidence packet PDFs")
    k.add_argument("--csv", required=True)
    k.add_argument("--profile", help="traceworthy_profile.json")
    k.add_argument("--out", default="out")
    k.set_defaults(func=cmd_packet)

    n = sub.add_parser("inspect", help="Diagnose 'No calls found' — dump DB structure, no phone numbers")
    n.add_argument("--backup", default="auto")
    n.add_argument("--password")
    n.add_argument("--storedata", help="Inspect this file directly instead of a backup")
    n.set_defaults(func=cmd_inspect)
    return p


def main(argv=None):
    for stream in (sys.stdout, sys.stderr):
        try:
            stream.reconfigure(encoding="utf-8", errors="replace")
        except (AttributeError, ValueError):
            pass
    args = build_parser().parse_args(argv)
    args.func(args)


if __name__ == "__main__":
    main()
