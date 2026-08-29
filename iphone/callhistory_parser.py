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
import re
import sqlite3
from dataclasses import dataclass
from datetime import datetime, timedelta, timezone

# Cocoa / Core Data timestamps count seconds from 2001-01-01 UTC, not the Unix epoch.
_COCOA_EPOCH = datetime(2001, 1, 1, tzinfo=timezone.utc)

CSV_HEADER = ["Timestamp", "Number", "ContactName", "Type", "DurationSeconds", "Suspicious", "Severity", "Note"]

# ZCALLTYPE values (matches iLEAPP's decoding).
CALLTYPE_PHONE = 1
CALLTYPE_FACETIME_VIDEO = 8
CALLTYPE_FACETIME_AUDIO = 16

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


def parse(
    storedata_path: str,
    addressbook_path: str | None = None,
    *,
    include_facetime: bool = False,
    flag_threshold_seconds: int = DEFAULT_FLAG_THRESHOLD_SECONDS,
) -> list[Call]:
    con = sqlite3.connect(f"file:{storedata_path}?mode=ro", uri=True)
    try:
        cols = _table_columns(con, "ZCALLRECORD")
        if not cols:
            raise ValueError(
                "No ZCALLRECORD table — this does not look like a CallHistory.storedata file."
            )
        has_name = "ZNAME" in cols
        has_answered = "ZANSWERED" in cols
        select = [
            "ZADDRESS", "ZDATE", "ZDURATION", "ZCALLTYPE", "ZORIGINATED",
            "ZANSWERED" if has_answered else "1 AS ZANSWERED",
            "ZNAME" if has_name else "NULL AS ZNAME",
        ]
        rows = con.execute(
            f"SELECT {', '.join(select)} FROM ZCALLRECORD ORDER BY ZDATE"
        ).fetchall()
    finally:
        con.close()

    contacts = load_contact_index(addressbook_path)
    calls: list[Call] = []
    for address, zdate, zdur, ztype, zorig, zans, zname in rows:
        ztype = int(ztype or 0)
        if ztype != CALLTYPE_PHONE and not (include_facetime and ztype in (CALLTYPE_FACETIME_VIDEO, CALLTYPE_FACETIME_AUDIO)):
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
