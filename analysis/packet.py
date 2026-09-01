"""
packet.py — build the full TraceWorthy evidence packet (FCC complaint, police
report cover note, carrier call script, incident timeline, evidence summary, and
a bundled PDF with cover + index) on a PC, from any normalized call log.

This is a Python port of the Android app's DocumentGenerator.kt. It reads a list
of stats.CallRow (produced by the iPhone tool, an app CSV, or a carrier CSV) plus
a profile object, and writes PDFs with fpdf2. Charts are drawn with matplotlib
using the same palette as analyze_calls.py / the in-app charts.

Anything blank in the profile renders as [PLACEHOLDER] so the user can see what to
fill in before filing.
"""

from __future__ import annotations

import os
import shutil
import sys
import tempfile
from dataclasses import dataclass, field
from datetime import datetime, timedelta

import matplotlib

matplotlib.use("Agg")
import matplotlib.pyplot as plt  # noqa: E402
import pandas as pd  # noqa: E402
from fpdf import FPDF  # noqa: E402

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from stats import CallRow, CallStats, stats_extras  # noqa: E402

try:
    import analyze_calls
    from analyze_calls import (
        draw_by_hour, draw_calls_over_time, draw_calls_per_day, draw_pie, draw_top_offenders,
    )
except ImportError:  # pragma: no cover - analyze_calls sits next to this file
    analyze_calls = None
    draw_by_hour = draw_calls_over_time = draw_calls_per_day = draw_pie = draw_top_offenders = None

NAVY = (0x0F, 0x1E, 0x33)
RED = (0xB0, 0x00, 0x20)
TEAL = (0x1F, 0xBF, 0xA6)
BLUE = (0x18, 0x5F, 0xA5)
INK = (0x22, 0x22, 0x22)

FLAG_CAP = 15


# --------------------------------------------------------------------------- #
#  Block model (mirrors DocumentGenerator.Block)
# --------------------------------------------------------------------------- #
@dataclass
class Title:
    text: str


@dataclass
class Heading:
    text: str


@dataclass
class Body:
    text: str


@dataclass
class Bullet:
    text: str


@dataclass
class Table:
    headers: list[str]
    rows: list[list[str]]


@dataclass
class Pie:
    flagged: int
    normal: int


@dataclass
class ChartBar:
    label: str
    value: int
    highlight: bool


@dataclass
class BarChart:
    bars: list[ChartBar]


@dataclass
class Scatter:
    """Calls-over-time scatter (date x time of day), last 90 days, dots by top-5 numbers."""
    days: int = 90


@dataclass
class Gap:
    points: float


@dataclass
class PageBreak:
    pass


Block = object  # documentation alias


# --------------------------------------------------------------------------- #
#  Text helpers
# --------------------------------------------------------------------------- #
_SMART = {
    "—": "-", "–": "-", "‘": "'", "’": "'",
    "“": '"', "”": '"', "…": "...", "•": "-",
    "·": "-", "≈": "~", " ": " ",
}


def _latin1(s: str) -> str:
    for k, v in _SMART.items():
        s = s.replace(k, v)
    return s.encode("latin-1", "replace").decode("latin-1")


def _v(value: str, placeholder: str) -> str:
    return value if (value or "").strip() else f"[{placeholder}]"


def _affected_line(profile) -> str:
    """The harassed number. Falls back to the contact number when not set separately."""
    return (getattr(profile, "affected_line", None)
            or getattr(profile, "affected_number", "")
            or getattr(profile, "phone", "") or "").strip()


def _has_distinct_affected(profile) -> bool:
    a = getattr(profile, "affected_number", "") or ""
    return bool(a.strip()) and a.strip() != (getattr(profile, "phone", "") or "").strip()


def _complainant_header(profile) -> list:
    """The identity lines every document opens with: contact identity, then the
    affected line the packet is about (always shown - it's the subject)."""
    return [
        Body(f"Complainant: {_v(getattr(profile, 'full_name', ''), 'YOUR FULL NAME')} - "
             f"{_v(getattr(profile, 'phone', ''), 'YOUR PHONE')}"),
        Body(f"Affected number (receiving the calls): {_v(_affected_line(profile), 'AFFECTED NUMBER')}"),
    ]


def _title_case(s: str) -> str:
    out = []
    for w in s.split(" "):
        if not w:
            out.append(w)
        elif any(c.isalpha() for c in w) and all(c.isupper() for c in w if c.isalpha()):
            out.append(w)  # acronym / all-caps token (FCC, ID, DC)
        else:
            out.append(w[:1].upper() + w[1:])
    return " ".join(out)


def _human(dt: datetime) -> str:
    return f"{dt.strftime('%B')} {dt.day}, {dt.year}"


def _range_fmt(dt: datetime) -> str:
    return f"{dt.strftime('%b')} {dt.day}, {dt.year}"


def _incident_fmt(dt: datetime) -> str:
    return f"{dt.strftime('%a %b')} {dt.day}, {dt.year} - {dt.strftime('%I:%M %p').lstrip('0')}"


def _hour_label(h: int) -> str:
    period = "AM" if h < 12 else "PM"
    hr = 12 if h == 0 else (h - 12 if h > 12 else h)
    return f"{hr} {period}"


def _trunc(s: str, n: int = 28) -> str:
    return s if len(s) <= n else s[: n - 1] + "..."


def _date_range(rows: list[CallRow]) -> tuple[str, str]:
    if not rows:
        return "[FIRST DATE]", "[MOST RECENT DATE]"
    return _range_fmt(min(r.timestamp for r in rows)), _range_fmt(max(r.timestamp for r in rows))


# --------------------------------------------------------------------------- #
#  Shared content sections
# --------------------------------------------------------------------------- #
def _stats_section(rows: list[CallRow]) -> list:
    x = stats_extras(rows)
    blocks: list = [
        Heading("Call Statistics By Time Window"),
        Table(
            ["Window", "Calls", "Flagged", "Numbers"],
            [[w.label, str(w.total), str(w.flagged), str(w.unique_numbers)] for w in x.windows],
        ),
        Gap(4),
    ]
    if x.busiest_hour is not None:
        blocks.append(Body(f"Most calls arrive around {_hour_label(x.busiest_hour)} "
                           f"({x.busiest_hour_count} calls in that hour)."))
    if x.overnight_count > 0:
        blocks.append(Body(f"{x.overnight_count} calls arrived overnight, between 10 PM and 6 AM."))
    if x.avg_per_day > 0:
        blocks.append(Body(f"Average of {x.avg_per_day:.1f} calls per day across the reporting period."))
    return blocks


def _flagged_number_section(rows: list[CallRow]) -> list:
    groups = [
        (number, calls)
        for number, calls in _group_by_number(rows).items()
        if any(c.flagged for c in calls)
    ]
    if not groups:
        return []

    def sort_key(item):
        _, calls = item
        return (
            sum(1 for c in calls if c.severity == "Threatening"),
            sum(1 for c in calls if c.note),
            len(calls),
            sum(1 for c in calls if c.flagged),
        )

    groups.sort(key=sort_key, reverse=True)
    shown = groups[:FLAG_CAP]
    remaining = len(groups) - len(shown)

    heading = f"Most Significant Flagged Numbers (Top {FLAG_CAP})" if remaining > 0 else "Flagged Numbers Detail"
    blocks: list = [Heading(heading)]
    for number, calls in shown:
        who = next((c.name for c in calls if c.name), "") or number
        flagged = sum(1 for c in calls if c.flagged)
        threat = sum(1 for c in calls if c.severity == "Threatening")
        spoken = sum(1 for c in calls if c.severity == "Spoken")
        silent = sum(1 for c in calls if c.severity == "Silent")
        notes = sum(1 for c in calls if c.note)
        sev_parts = []
        if threat:
            sev_parts.append(f"{threat} threatening")
        if spoken:
            sev_parts.append(f"{spoken} spoken")
        if silent:
            sev_parts.append(f"{silent} silent")
        sev = f"; tags: {', '.join(sev_parts)}" if sev_parts else ""
        note_str = f"; {notes} noted" if notes else ""
        blocks.append(Bullet(f"{who} - {len(calls)} calls, {flagged} flagged{sev}{note_str}"))

    if remaining > 0:
        tail = groups[FLAG_CAP:]
        tail_calls = sum(len(c) for _, c in tail)
        tail_oneoff = sum(1 for _, c in tail if len(c) == 1)
        blocks.append(Gap(4))
        blocks.append(Body(
            f"Plus {remaining} additional flagged numbers accounting for {tail_calls} calls"
            + (f", {tail_oneoff} of which called exactly once - a hallmark of caller ID spoofing "
               "used to evade blocking" if tail_oneoff else "")
            + ". The complete per-call list is available in the exported CSV."
        ))
    return blocks


def _pattern_sentence(profile, stats: CallStats, rows: list[CallRow] | None = None) -> str:
    n = stats.unique_numbers
    spoof = (f"The use of {n} different numbers is consistent with deliberate caller ID "
             "spoofing to harass and evade blocking.")
    ht = getattr(profile, "harassment_type", None)
    name = getattr(ht, "value", "Silent")
    timeline = _has_incidents(rows) if rows else False
    if name == "Aggressive":
        tl = (" Specific incidents, with dates and times, are documented in the attached incident "
              "timeline." if timeline else "")
        return (f"{stats.flagged_calls} of these calls involve aggressive, abusive, or threatening "
                f"conduct by the caller.{tl} {spoof}")
    if name == "Both":
        tl = (" (documented with dates and times in the attached incident timeline)" if timeline else "")
        return (f"{stats.flagged_calls} match a harassment pattern of silent or very short calls from "
                "numbers not in my contacts, and a number of the calls additionally involve aggressive "
                f"or threatening conduct{tl}. {spoof}")
    if name == "Silent":
        return (f"{stats.flagged_calls} match a consistent harassment pattern: incoming calls from "
                "numbers not in my contacts on which the caller is silent and/or disconnects within "
                f"seconds. {spoof}")
    return (f"{stats.flagged_calls} match a harassment pattern: repeated unwanted incoming calls from "
            f"numbers not in my contacts, many silent or lasting only seconds. {spoof}")


def _group_by_number(rows: list[CallRow]) -> dict[str, list[CallRow]]:
    out: dict[str, list[CallRow]] = {}
    for r in rows:
        out.setdefault(r.number, []).append(r)
    return out


def _has_incidents(rows: list[CallRow]) -> bool:
    """True once at least one call carries a note or a severity tag."""
    return any(r.note or r.severity for r in rows)


def _num_key(number: str) -> str:
    """Comparable key for a phone number: digits only, last 10. Values with too
    few digits to be a real number fall back to their trimmed literal."""
    digits = "".join(ch for ch in str(number) if ch.isdigit())
    return digits[-10:] if len(digits) >= 7 else str(number).strip()


def _split_known(rows: list[CallRow], profile) -> tuple[list[CallRow], list[CallRow]]:
    """Partition rows into (potential harassment, known personal contacts) using
    profile.safe_numbers — friends/relatives calling from unsaved numbers."""
    safe = {_num_key(n) for n in (getattr(profile, "safe_numbers", None) or [])}
    if not safe:
        return list(rows), []
    keep, drop = [], []
    for r in rows:
        (drop if _num_key(r.number) in safe else keep).append(r)
    return keep, drop


def _known_caller_blocks(profile, evidence_rows: list[CallRow],
                         excluded_rows: list[CallRow]) -> list:
    """The "N calls from M known callers were removed" disclosure plus an
    all-incoming vs. potential-harassment comparison. Empty when there are none."""
    if not excluded_rows:
        return []
    known_numbers = len({_num_key(r.number) for r in excluded_rows})
    evidence_numbers = len({_num_key(r.number) for r in evidence_rows})
    all_calls = len(evidence_rows) + len(excluded_rows)
    all_numbers = len({_num_key(r.number) for r in evidence_rows + excluded_rows})
    name = getattr(profile, "full_name", "") or ""
    if not name.strip():
        name = "the complainant"
    s_calls = "" if len(excluded_rows) == 1 else "s"
    s_nums = "" if known_numbers == 1 else "s"
    return [
        Body(f"{len(excluded_rows)} call{s_calls} from {known_numbers} phone number{s_nums} that "
             f"{name} has identified as known personal contacts - people not saved in the phone's "
             "address book - have been removed. Every figure, chart, and list in this document "
             "counts only the remaining calls, i.e. the potentially harassing ones."),
        Heading("All Incoming Calls Vs Potential Harassment"),
        Table(["", "All incoming", "Potential harassment"],
              [["Calls", str(all_calls), str(len(evidence_rows))],
               ["Distinct numbers", str(all_numbers), str(evidence_numbers)]]),
        Body('"All incoming" is every call received in this period. "Potential harassment" is that '
             "total minus the known personal contacts above - the figures used everywhere else in "
             "this document."),
    ]


def _generated_footer() -> "Body":
    return Body(f"Generated by TraceWorthy on {_human(datetime.now())}. Not legal advice - "
               "TraceWorthy is an independent tool, not a law firm.")


def _spoofing_explainer() -> list:
    """Factual, non-editable background for the reader of the packet (police, FCC, carrier)."""
    return [
        Heading("How Caller ID Spoofing Works"),
        Body("Caller ID spoofing makes a phone display a number other than the real one. It is cheap "
             "and trivial - apps and websites let anyone set any number as their outbound caller ID, "
             "and the network does not verify it at the point of the call."),
        Body("A harasser who changes the displayed number on every call - often a new, never-reused "
             "number each time - does so to defeat blocking and stay anonymous. A long list of numbers "
             "that each called only once or twice is the signature of one spoofing caller, not many "
             "callers. The same method is used at scale by robocall operations, frequently "
             "\"neighbor spoofing\" the recipient's own area code."),
        Body("Neither the recipient nor consumer-grade carrier tools can identify the true originating "
             "line. That takes a STIR/SHAKEN traceback through the Industry Traceback Group, or a "
             "subpoena to the carriers in the call path. Spoofing to defraud or cause harm violates "
             "the federal Truth in Caller ID Act (47 U.S.C. section 227(e)); the 2019 TRACED Act "
             "strengthened enforcement and mandated STIR/SHAKEN."),
    ]


# --------------------------------------------------------------------------- #
#  Document builders (ported 1:1 from DocumentGenerator.kt)
# --------------------------------------------------------------------------- #
# Order the reader should work through them: understand the case, then the steps
# in the sequence they should be filed — carrier first (get a case #), then the
# FCC, then police last (their cover note cross-references the other two numbers).
# Individual PDFs are named "TraceWorthy_<n>_<slug>_<stamp>.pdf" so they sort into
# this order in a folder and drop straight into an Acrobat "Combine Files".
PACKET_CONTENTS = ["evidence_summary", "incident_timeline", "carrier_script", "fcc_complaint",
                   "police_report", "non_disclosure_order"]

def _packet_contents(rows: list[CallRow]) -> list[str]:
    """The packet's document list for this call log. The incident timeline is only
    included once at least one call has been annotated with a note or severity -
    an empty timeline is just a page telling the reader to add notes."""
    keys = list(PACKET_CONTENTS)
    if not _has_incidents(rows):
        keys = [k for k in keys if k != "incident_timeline"]
    return keys


DOC_DISPLAY_NAMES = {
    "evidence_packet": "Full evidence packet",
    "fcc_complaint": "FCC complaint",
    "police_report": "Harassment-police report cover note",
    "carrier_script": "Carrier call script",
    "incident_timeline": "Incident timeline",
    "evidence_summary": "Evidence summary",
    "non_disclosure_order": "Non-disclosure order request",
}


def _fcc_complaint(profile, stats, rows, source_note=None) -> list:
    first, last = _date_range(rows)
    name = _v(profile.full_name, "YOUR FULL NAME")
    affected = _v(_affected_line(profile), "AFFECTED NUMBER")
    contact = _v(getattr(profile, "phone", ""), "YOUR CONTACT NUMBER")
    x = stats_extras(rows)
    cheat = [
        Bullet(f"Your phone number (the line that was called): {affected}"),
        Bullet("Phone issue: Unwanted calls"),
        Bullet("Sub-issue: Caller ID Spoofing"),
        Bullet("Did you give consent? No"),
        Bullet(f"Caller's number: Multiple / spoofed - {stats.unique_numbers} different numbers (see description)"),
        Bullet(f"Date(s) of calls: {first} through {last}"),
        Bullet("Method: Phone call"),
    ]
    if _has_distinct_affected(profile):
        cheat.append(Bullet(f"Best number to reach you: {contact}"))
    blocks: list = [
        Title("FCC Complaint - Caller ID Spoofing"),
        Body("File online at consumercomplaints.fcc.gov -> Phone -> Unwanted Calls -> issue type "
             '"Caller ID Spoofing." Use the field notes below, then paste the description into the '
             "complaint's free-text box."),
        Gap(6),
        Heading("Form Field Cheat-Sheet"),
        *cheat,
        Heading("Totals By Period"),
        Table(["Window", "Calls", "Flagged", "Numbers"],
              [[w.label, str(w.total), str(w.flagged), str(w.unique_numbers)] for w in x.windows]),
        Heading("Description (Paste This)"),
        Body(f"I am receiving a sustained campaign of harassing phone calls to my number, {affected}. "
             f"Over the period {first} to {last} I have logged {stats.total_calls} calls from "
             f"{stats.unique_numbers} distinct phone numbers. {_pattern_sentence(profile, stats, rows)}"),
        Body("I did not consent to these calls. I am requesting FCC action against this illegal spoofing "
             "under the Truth in Caller ID Act and the TRACED Act."),
        Body(f"Name: {name}"
             + (f"    Best contact number: {contact}" if _has_distinct_affected(profile) else "")),
        Gap(10),
        _generated_footer(),
    ]
    if source_note:
        blocks.insert(3, Body(source_note))
    return blocks


def _police_report(profile, stats, rows, source_note=None) -> list:
    first, last = _date_range(rows)
    ht = getattr(profile, "harassment_type", None)
    aggressive = getattr(ht, "includes_aggressive", False)
    blocks: list = [
        Title("Harassment - Police Report Cover Note"),
        Body("Bring this to the police (in person is best) along with your TraceWorthy evidence summary "
             "and CSV. It states the facts plainly and cross-references your other filings so the file "
             "is self-contained."),
        Gap(6),
        Heading("Complainant"),
        Bullet(f"Date: {_human(datetime.now())}"),
        Bullet(f"Name: {_v(profile.full_name, 'YOUR FULL NAME')}"),
        Bullet(f"Contact: {_v(profile.phone, 'YOUR PHONE')} - {_v(profile.email, 'YOUR EMAIL')}"),
        Bullet(f"Affected line (receiving the calls): {_v(_affected_line(profile), 'AFFECTED NUMBER')}"),
        Bullet(f"Carrier: {_v(profile.carrier, 'YOUR CARRIER')}"),
        Bullet(f"Location: {_v(profile.address_city, 'CITY')}, {_v(profile.state, 'ST')}"),
        Heading("Nature Of Complaint"),
        Body("Ongoing telephone harassment involving aggressive, abusive, or threatening calls, with "
             "caller ID spoofing used to evade blocking." if aggressive
             else "Ongoing telephone harassment via spoofed caller ID."),
        Heading("Summary Of Evidence"),
        Body(f"Over the period {first} to {last} I have logged {stats.total_calls} calls from "
             f"{stats.unique_numbers} distinct phone numbers. {_pattern_sentence(profile, stats, rows)}"),
        Body("The full call statistics - the flagged-vs-normal breakdown, the time-window totals, the "
             "charts, and the per-number list - are in the attached TraceWorthy evidence summary, with "
             "every call itemized in the accompanying CSV."),
    ]
    if aggressive and _has_incidents(rows):
        blocks.append(Body("Specific threatening/abusive incidents are itemized in the attached "
                           "TraceWorthy incident timeline, compiled from notes taken at the time of each call."))
    blocks += [
        Heading("Cross-References"),
        Bullet(f"FCC complaint number: {_v(profile.fcc_complaint_number, 'FCC COMPLAINT #')}"),
        Bullet(f"Carrier harassment case number: {_v(profile.carrier_case_number, 'CARRIER CASE #')}"),
        Heading("Request"),
        Body("I am requesting a police report be filed so that a subpoena can be issued to my carrier "
             "for the true originating records of these calls (a traceback). The attached CSV and "
             "evidence summary document every call."),
        Body("Please also see the attached non-disclosure order request - I am asking that any subpoena "
             "to the carrier be kept confidential from the subscriber so the caller is not tipped off."),
        Gap(10),
        _generated_footer(),
    ]
    if source_note:
        blocks.insert(3, Body(source_note))
    return blocks


def _non_disclosure_order(profile, stats, rows, source_note=None) -> list:
    first, last = _date_range(rows)
    aggressive = getattr(getattr(profile, "harassment_type", None), "includes_aggressive", False)
    timeline = aggressive and _has_incidents(rows)
    life = ("  - APPLIES: the caller has made threatening/abusive statements"
            + (", documented with dates and times in the attached incident timeline." if timeline
               else ".")) if aggressive else "."
    witness = "  - APPLIES: I am the complainant and a witness, and the caller has already engaged in " \
              "intimidating conduct." if aggressive else "."
    return [
        Title("Request for a Non-Disclosure Order"),
        Body("For the investigating officer / prosecutor. This accompanies my police report and "
             "TraceWorthy evidence packet."),
        Gap(6),
        Heading("Why This Matters"),
        Body("When a subpoena or court order is served on a phone carrier for subscriber and call "
             "records, the carrier's normal practice is to notify the account holder. In this case that "
             "would tip off the person placing these calls before the records can be secured - they use "
             "caller ID spoofing specifically to stay anonymous and evade blocking, and services that "
             "enable spoofing routinely purge their logs."),
        Body("Under 18 U.S.C. section 2705(b), the government may apply for a court order directing the "
             "provider not to notify the subscriber for a set period (commonly 90 days, renewable). "
             "Financial institutions are subject to parallel non-disclosure provisions if bank records "
             "are also sought."),
        Heading("What I Am Asking"),
        Body(f"That any subpoena, section 2703(d) order, or search warrant issued to my carrier for "
             f"records relating to the calls to my number, {_v(_affected_line(profile), 'AFFECTED NUMBER')}, "
             f"over the period {first} to {last} be accompanied by a section 2705(b) non-disclosure order "
             "barring the carrier from notifying the subscriber(s) whose records are produced."),
        Heading("Statutory Grounds (section 2705(b))"),
        Body("A court may issue the order on a finding that notification would result in one or more of "
             "the following. The grounds most applicable here are noted."),
        Bullet(f"Endangering the life or physical safety of an individual{life}"),
        Bullet("Flight from prosecution."),
        Bullet("Destruction of or tampering with evidence  - APPLIES: spoofing-service and "
               "intermediate-carrier call logs are short-lived and are routinely deleted; advance "
               "notice invites their destruction."),
        Bullet(f"Intimidation of potential witnesses{witness}"),
        Bullet("Otherwise seriously jeopardizing an investigation or unduly delaying a trial  - "
               "APPLIES: identifying the true originating line depends on a traceback through multiple "
               "carriers that cannot succeed if the subject is alerted."),
        Heading("Cross-References"),
        Bullet(f"Police case number: {_v(profile.police_case_number, 'POLICE CASE #')}"),
        Bullet(f"FCC complaint number: {_v(profile.fcc_complaint_number, 'FCC COMPLAINT #')}"),
        Bullet(f"Carrier harassment case number: {_v(profile.carrier_case_number, 'CARRIER CASE #')}"),
        Gap(6),
        Body("I understand that only a prosecutor or court can obtain this order and that the decision "
             "rests with them. This document is provided so the request and its basis are on the record "
             "from the outset."),
        Body(f"Name: {_v(getattr(profile, 'full_name', ''), 'YOUR FULL NAME')}    "
             f"Contact: {_v(getattr(profile, 'phone', ''), 'YOUR PHONE')}"),
        Gap(8),
        _generated_footer(),
    ]


def _carrier_script(profile, stats, rows, source_note=None) -> list:
    return [
        Title("Carrier Harassment Case - Call Script"),
        Body("Call your carrier's fraud / harassment department (dial 611 from your phone, or use the "
             "customer-service number on your bill) and ask to open a documented harassment case."),
        Gap(6),
        Heading("Word-For-Word Script"),
        Body(f'"I\'m a {_v(profile.carrier, "CARRIER")} customer and I\'m being harassed by repeated '
             'calls from different numbers that I believe are spoofed. I want to:'),
        Bullet("Open a documented harassment case on my account."),
        Bullet("Get a case / reference number for my records."),
        Bullet("Turn on any free spam-blocking tools you offer."),
        Bullet('Understand how the police can request a traceback of these calls."'),
        Heading("Write Down"),
        Bullet("Case / reference number: ____________________  (save this in My info)"),
        Bullet("Representative name and date: ____________________"),
        Heading("Context To Give Them"),
        Body(f"The affected line on my account is {_v(_affected_line(profile), 'AFFECTED NUMBER')}. "
             f"I have logged {stats.total_calls} calls from {stats.unique_numbers} different numbers, "
             f"{stats.flagged_calls} matching the harassment pattern. I am also filing an FCC complaint "
             "and a police report."),
        Gap(6),
        Body("Note: carrier tools block and document - they cannot reveal a spoofed caller to you "
             "directly. Only a police subpoena unmasks the origin."),
        Gap(6),
        _generated_footer(),
    ]


def _incident_timeline(profile, stats, rows, source_note=None) -> list:
    documented = sorted(
        [r for r in rows if r.note or r.severity], key=lambda r: r.timestamp
    )
    blocks: list = [
        Title("Harassment Incident Timeline"),
        *_complainant_header(profile),
        Body("This is a chronological log of documented incidents, compiled from notes taken at or near "
             "the time of each call. It is intended to show the pattern of contact and any escalation "
             "of the harassment over time."),
        Gap(4),
    ]
    if not documented:
        blocks.append(Body(
            "No incidents have been documented yet. Add a note to a call - describing what happened, "
            'for example "silent for 30 seconds", "shouted threats", or "said he knew my address" - '
            "and tag how serious it was (Silent / Spoken / Threatening). In the TraceWorthy Android "
            "app this is done from the Call log; when working from a CSV, fill the Note and Severity "
            "columns. Documented incidents then appear here in order."
        ))
        return blocks

    threatening = sum(1 for r in documented if r.severity == "Threatening")
    spoken = sum(1 for r in documented if r.severity == "Spoken")
    silent = sum(1 for r in documented if r.severity == "Silent")
    blocks.append(Heading(f"Documented Incidents ({len(documented)})"))
    if threatening + spoken + silent > 0:
        blocks.append(Body(f"Severity tags across these incidents: {threatening} threatening, "
                           f"{spoken} spoken, {silent} silent."))
        bars = [ChartBar(l, v, l == "Threatening") for l, v in
                (("Threatening", threatening), ("Spoken", spoken), ("Silent", silent)) if v > 0]
        if bars:
            blocks.append(BarChart(bars))
    for r in documented:
        who = r.name or r.number
        tags = [t for t in ([r.severity] if r.severity else []) + (["flagged"] if r.flagged else [])]
        tag_str = f"  [{', '.join(tags)}]" if tags else ""
        blocks.append(Bullet(f"{_incident_fmt(r.timestamp)} - {who}{tag_str}"))
        if r.note:
            blocks.append(Body(f'     "{r.note}"'))
        blocks.append(Gap(3))
    blocks.append(Gap(8))
    blocks.append(Body(f"Earliest documented incident: {_incident_fmt(documented[0].timestamp)}. "
                       f"Most recent: {_incident_fmt(documented[-1].timestamp)}."))
    blocks.append(_generated_footer())
    return blocks


def _evidence_summary(profile, stats, rows, source_note=None, excluded_known=None) -> list:
    first, last = _date_range(rows)
    blocks: list = [
        Title("TraceWorthy - Evidence Summary"),
        *_complainant_header(profile),
        Body(f"Reporting period: {first} to {last}"),
        Body(f"Generated: {_human(datetime.now())}"),
    ]
    if source_note:
        blocks.append(Body(source_note))
    blocks += _known_caller_blocks(profile, rows, excluded_known or [])
    blocks += _stats_section(rows)
    blocks += [
        Heading("Totals"),
        Bullet(f"Calls logged: {stats.total_calls}"),
        Bullet(f"Flagged (harassment pattern): {stats.flagged_calls}"),
        Bullet(f"Distinct numbers: {stats.unique_numbers}"),
        Bullet(f"Incoming: {stats.incoming} - Missed: {stats.missed} - Rejected: {stats.rejected}"),
        Heading("Flagged Vs Normal"),
        Pie(stats.flagged_calls, max(stats.total_calls - stats.flagged_calls, 0)),
        Heading("Top Numbers By Call Count"),
        BarChart([ChartBar(_trunc(n.name or n.number), n.total_count, n.flagged_count > 0)
                  for n in stats.per_number[:8]]),
        Heading("Calls Over Time - Last 90 Days (Date x Time Of Day)"),
        Scatter(),
        Body("Each dot is a call - the horizontal position is the date and the vertical position "
             "is the time of day. Dots are colored by the top-5 most-called numbers; other numbers "
             "are gray. This shows when calls arrive, including overnight clustering or bursts on "
             "particular dates."),
    ]
    blocks += _flagged_number_section(rows)
    blocks.append(Gap(6))
    blocks.append(Body("This summary is generated from the call log. A full per-call CSV accompanies it."))
    blocks.append(_generated_footer())
    return blocks


_BUILDERS = {
    "fcc_complaint": _fcc_complaint,
    "police_report": _police_report,
    "carrier_script": _carrier_script,
    "incident_timeline": _incident_timeline,
    "evidence_summary": _evidence_summary,
    "non_disclosure_order": _non_disclosure_order,
}


def _evidence_packet(profile, stats, rows, source_note=None, excluded_known=None) -> list:
    excluded_known = excluded_known or []
    first, last = _date_range(rows)
    blocks: list = [
        Title("TraceWorthy Evidence Packet"),
        Body(f"Prepared by {_v(getattr(profile, 'full_name', ''), 'YOUR FULL NAME')} - "
             f"{_v(getattr(profile, 'phone', ''), 'YOUR PHONE')}"),
        Body(f"Affected number (receiving the calls): {_v(_affected_line(profile), 'AFFECTED NUMBER')}"),
        Body(f"Reporting period: {first} to {last}"),
        Body(f"Generated: {_human(datetime.now())}"),
        Gap(6),
        Body(f"This packet documents a campaign of harassing phone calls to "
             f"{_v(_affected_line(profile), 'AFFECTED NUMBER')} and is intended to support a carrier "
             f"traceback. It contains {stats.total_calls} logged calls from {stats.unique_numbers} "
             f"distinct numbers, {stats.flagged_calls} matching the harassment pattern. The full "
             "statistics and charts are on the evidence summary that follows."),
    ]
    if source_note:
        blocks.append(Body(source_note))
    if excluded_known:
        known_numbers = len({_num_key(r.number) for r in excluded_known})
        s_c = "" if len(excluded_known) == 1 else "s"
        s_n = "" if known_numbers == 1 else "s"
        blocks.append(Body(
            f"This packet reflects only the potentially harassing calls: {len(excluded_known)} "
            f"call{s_c} from {known_numbers} known personal contact{s_n} (not in the phone's address "
            "book) have been removed. The evidence summary shows the all-incoming vs. "
            "potential-harassment comparison."))
    contents = _packet_contents(rows)
    blocks.append(Heading("Contents"))
    for i, key in enumerate(contents, 1):
        blocks.append(Bullet(f"{i}.  {DOC_DISPLAY_NAMES[key]}"))
    blocks += [
        Heading("How To Use This Packet"),
        Bullet("1. Call your carrier's fraud / harassment line using the carrier call script. Open a "
               "case, get a reference number, and enter it in My info."),
        Bullet("2. File an FCC complaint at consumercomplaints.fcc.gov using the FCC complaint "
               "document. Save the confirmation number in My info."),
        Bullet("3. Take the police report cover note and the non-disclosure order request to your "
               "local police - in person is best. Bring this packet and the CSV of every call."),
        Bullet("4. As each case / complaint number comes in, add it in My info and regenerate - the "
               "documents cross-reference one another."),
    ]
    blocks.append(PageBreak())
    blocks.append(Heading("Background: How Caller ID Spoofing Works"))
    blocks += _spoofing_explainer()[1:]  # drop the duplicate heading
    for key in contents:
        blocks.append(PageBreak())
        if key == "evidence_summary":
            blocks += _evidence_summary(profile, stats, rows, source_note, excluded_known)
        else:
            blocks += _BUILDERS[key](profile, stats, rows, source_note)
    return blocks


# --------------------------------------------------------------------------- #
#  Charts
# --------------------------------------------------------------------------- #
def _rows_to_df(rows: list[CallRow]) -> pd.DataFrame:
    return pd.DataFrame({
        "Number": [r.number for r in rows],
        "Timestamp": pd.to_datetime([r.timestamp for r in rows]),
        "DurationSeconds": [r.duration_seconds for r in rows],
        "Flagged": [r.flagged for r in rows],
    })


def _chart_png(kind: str, rows: list[CallRow], path: str, titled: bool = False) -> str | None:
    if draw_pie is None:
        return None
    df = _rows_to_df(rows)
    fn, figsize = {
        "pie": (draw_pie, (6, 4)),
        "top": (draw_top_offenders, (9, 4.5)),
        "hour": (draw_by_hour, (9, 3.5)),
        "day": (draw_calls_per_day, (10, 3.5)),
        "over": (draw_calls_over_time, (10, 4)),
    }[kind]
    fig, ax = plt.subplots(figsize=figsize, dpi=150)
    try:
        fn(ax, df)
    except Exception:  # noqa: BLE001
        plt.close(fig)
        return None
    if not titled:
        ax.set_title("")  # the packet's section Heading already labels the chart
    fig.tight_layout()
    fig.savefig(path)
    plt.close(fig)
    return path


# --------------------------------------------------------------------------- #
#  PDF renderer (mirrors DocumentGenerator.renderPdf)
# --------------------------------------------------------------------------- #
PAGE_W, PAGE_H = 612, 792   # US Letter, points
MARGIN = 54
CONTENT_W = PAGE_W - MARGIN * 2


def _keep_with_heading(block) -> float:
    """Vertical space the block that follows a Heading needs, so a heading is
    never stranded at the foot of a page away from the chart/table/text it
    labels. Tables and long bar lists are capped — they legitimately span
    pages, but the heading + first rows should still travel together."""
    if block is None:
        return 0.0
    if isinstance(block, Pie):
        return 200.0
    if isinstance(block, Scatter):
        return 230.0
    if isinstance(block, BarChart):
        return min(30.0 * max(len(block.bars), 1), 150.0)
    if isinstance(block, Table):
        return min(24.0 + 15.0 * len(block.rows), 120.0)
    if isinstance(block, Body):
        return 32.0
    if isinstance(block, Bullet):
        return 16.0
    return 0.0


def _render_pdf(blocks: list, out_path: str, rows: list[CallRow]) -> str:
    pdf = FPDF(unit="pt", format=(PAGE_W, PAGE_H))
    pdf.set_auto_page_break(True, margin=MARGIN)
    pdf.set_margins(MARGIN, MARGIN, MARGIN)
    pdf.add_page()

    tmpdir = tempfile.mkdtemp(prefix="tw_charts_")

    def ensure(space: float):
        if pdf.get_y() + space > PAGE_H - MARGIN:
            pdf.add_page()

    def text_block(text: str, size: int, bold: bool, color, gap: float, top: float = 0.0):
        pdf.set_font("Helvetica", "B" if bold else "", size)
        pdf.set_text_color(*color)
        if top:
            pdf.set_y(pdf.get_y() + top)
        pdf.set_x(MARGIN)
        pdf.multi_cell(CONTENT_W, gap, _latin1(text), new_x="LMARGIN", new_y="NEXT")

    for i, block in enumerate(blocks):
        if isinstance(block, Title):
            text_block(_title_case(block.text), 18, True, NAVY, 22)
            pdf.set_y(pdf.get_y() + 4)
        elif isinstance(block, Heading):
            nxt = blocks[i + 1] if i + 1 < len(blocks) else None
            ensure(30 + _keep_with_heading(nxt))
            text_block(_title_case(block.text), 12, True, NAVY, 16, top=8)
        elif isinstance(block, Body):
            text_block(block.text, 10.5, False, INK, 14)
        elif isinstance(block, Bullet):
            pdf.set_font("Helvetica", "", 10.5)
            pdf.set_text_color(*INK)
            pdf.set_x(MARGIN)
            pdf.multi_cell(CONTENT_W, 14, _latin1(f"-  {block.text}"), new_x="LMARGIN", new_y="NEXT")
        elif isinstance(block, Gap):
            # A gap that would run off the page just goes to the next one.
            new_y = pdf.get_y() + block.points
            if new_y > PAGE_H - MARGIN:
                pdf.add_page()
            else:
                pdf.set_y(new_y)
        elif isinstance(block, PageBreak):
            # Only break if there's content on the page — never emit a blank one.
            if pdf.get_y() > MARGIN + 2:
                pdf.add_page()
        elif isinstance(block, Table):
            _draw_table(pdf, block, ensure)
        elif isinstance(block, Pie):
            _embed_chart(pdf, _chart_png("pie", rows, os.path.join(tmpdir, "pie.png")), ensure, 190)
        elif isinstance(block, BarChart):
            _draw_barchart(pdf, block, ensure)
        elif isinstance(block, Scatter):
            _embed_chart(pdf, _chart_png("over", rows, os.path.join(tmpdir, "over.png")), ensure, 220)

    pdf.output(out_path)
    shutil.rmtree(tmpdir, ignore_errors=True)
    return out_path


def _draw_table(pdf: FPDF, block: Table, ensure):
    cols = len(block.headers)
    if not cols:
        return
    col_w = CONTENT_W / cols
    ensure(24 + 15 * len(block.rows))
    pdf.set_font("Helvetica", "B", 10)
    pdf.set_text_color(*NAVY)
    y = pdf.get_y()
    for i, h in enumerate(block.headers):
        pdf.set_xy(MARGIN + i * col_w, y)
        pdf.cell(col_w, 14, _latin1(h))
    y += 16
    pdf.set_draw_color(0xCC, 0xCC, 0xCC)
    pdf.line(MARGIN, y, MARGIN + CONTENT_W, y)
    y += 6
    pdf.set_font("Helvetica", "", 10)
    pdf.set_text_color(*(0x22, 0x22, 0x22))
    for row in block.rows:
        for i, c in enumerate(row):
            pdf.set_xy(MARGIN + i * col_w, y)
            pdf.cell(col_w, 13, _latin1(str(c)))
        y += 15
    pdf.set_y(y + 4)


def _draw_barchart(pdf: FPDF, block: BarChart, ensure):
    if not block.bars:
        return
    max_v = max((b.value for b in block.bars), default=1) or 1
    for b in block.bars:
        ensure(30)
        pdf.set_font("Helvetica", "", 10)
        pdf.set_text_color(*(0x22, 0x22, 0x22))
        pdf.set_x(MARGIN)
        pdf.cell(CONTENT_W, 13, _latin1(f"{b.label} - {b.value}"), new_x="LMARGIN", new_y="NEXT")
        y = pdf.get_y()
        pdf.set_fill_color(0xEC, 0xEF, 0xF3)
        pdf.rect(MARGIN, y, CONTENT_W, 7, style="F")
        pdf.set_fill_color(*(RED if b.highlight else BLUE))
        pdf.rect(MARGIN, y, CONTENT_W * b.value / max_v, 7, style="F")
        pdf.set_y(y + 13)


def _embed_chart(pdf: FPDF, path: str | None, ensure, height: float):
    if not path or not os.path.isfile(path):
        return
    ensure(height + 10)
    pdf.image(path, x=MARGIN, w=CONTENT_W)
    pdf.set_y(pdf.get_y() + 6)


# --------------------------------------------------------------------------- #
#  Public entry point
# --------------------------------------------------------------------------- #
def rows_from_csv(csv_path: str, include_outgoing: bool = False) -> list[CallRow]:
    """Load a TraceWorthy app export or any carrier CSV into CallRow objects.
    Inbound-only by default, matching the Android app — outgoing calls are noise
    in a harassment packet."""
    rows = _rows_from_csv(csv_path)
    if not include_outgoing:
        rows = [r for r in rows if r.type_label.lower() != "outgoing"]
    return rows


def _rows_from_csv(csv_path: str) -> list[CallRow]:
    raw = pd.read_csv(csv_path, dtype=str, keep_default_na=True)
    lower = {str(c).strip().lower(): c for c in raw.columns}

    if {"timestamp", "number", "suspicious"}.issubset(lower):
        ts = pd.to_datetime(raw[lower["timestamp"]], errors="coerce")
        rows: list[CallRow] = []
        for i, t in enumerate(ts):
            if pd.isna(t):
                continue
            g = lambda k, d="": str(raw[lower[k]].iloc[i]).strip() if k in lower and not pd.isna(raw[lower[k]].iloc[i]) else d
            dur = g("durationseconds", "0")
            rows.append(CallRow(
                number=g("number") or "Unknown",
                name=g("contactname"),
                timestamp=t.to_pydatetime(),
                duration_seconds=int(float(dur)) if dur.replace(".", "", 1).isdigit() else 0,
                type_label=g("type", "Incoming") or "Incoming",
                flagged=g("suspicious").upper() == "YES",
                severity=g("severity"),
                note=g("note"),
            ))
        return rows

    if analyze_calls is None:
        raise RuntimeError("Carrier CSV support needs analyze_calls.py alongside packet.py.")
    df = analyze_calls.load_carrier(raw, None, {"duration_unit": "auto"})
    out: list[CallRow] = []
    for _, r in df.iterrows():
        out.append(CallRow(
            number=str(r["Number"]).strip() or "Unknown",
            name="",
            timestamp=pd.Timestamp(r["Timestamp"]).to_pydatetime(),
            duration_seconds=int(r["DurationSeconds"]),
            type_label="Incoming" if bool(r.get("Incoming", True)) else "Outgoing",
            flagged=bool(r["Flagged"]),
        ))
    return out


def generate_all(rows: list[CallRow], profile, out_dir: str, source_note: str | None = None) -> dict[str, str]:
    """Write every document + the bundled packet + the standalone chart PNGs.
    Returns {name: path}."""
    os.makedirs(out_dir, exist_ok=True)
    # Known callers (friends on unsaved numbers, from profile.safe_numbers) are
    # removed from every figure, chart, and list; the evidence summary reports how
    # many and shows an all-incoming vs. potential-harassment comparison.
    rows, excluded_known = _split_known(rows, profile)
    stats = CallStats.from_rows(rows)
    stamp = datetime.now().strftime("%Y-%m-%d_%H%M%S")
    written: dict[str, str] = {}

    # The bundled packet sorts first (00); the individual docs are numbered in the
    # order the reader should work through them, so a folder listing / an Acrobat
    # "Combine Files" already yields the right sequence.
    packet_blocks = _evidence_packet(profile, stats, rows, source_note, excluded_known)
    packet_path = os.path.join(out_dir, f"TraceWorthy_00_evidence_packet_{stamp}.pdf")
    _render_pdf(packet_blocks, packet_path, rows)
    written["evidence_packet"] = packet_path

    for i, key in enumerate(_packet_contents(rows), 1):
        if key == "evidence_summary":
            blocks = _evidence_summary(profile, stats, rows, source_note, excluded_known)
        else:
            blocks = _BUILDERS[key](profile, stats, rows, source_note)
        path = os.path.join(out_dir, f"TraceWorthy_{i:02d}_{key}_{stamp}.pdf")
        _render_pdf(blocks, path, rows)
        written[key] = path

    for kind, name in (("pie", "flagged_vs_normal"), ("top", "top_offenders"),
                       ("hour", "calls_by_hour"), ("day", "calls_per_day"),
                       ("over", "calls_over_time")):
        p = _chart_png(kind, rows, os.path.join(out_dir, f"{name}.png"), titled=True)
        if p:
            written[f"chart_{name}"] = p

    return written


class _JsonProfile:
    """Minimal duck-typed profile read straight from traceworthy_profile.json, so
    analysis/packet.py works for carrier users without the iphone/ package."""

    _FIELDS = ("full_name", "phone", "affected_number", "email", "address_city", "state",
               "carrier", "fcc_complaint_number", "police_case_number", "carrier_case_number")

    class _HT:
        def __init__(self, value):
            self.value = value or "Silent"
            self.includes_aggressive = self.value in ("Aggressive", "Both")
            self.includes_silent = self.value in ("Silent", "Both")

    def __init__(self, data: dict | None = None):
        data = data or {}
        for f in self._FIELDS:
            setattr(self, f, str(data.get(f, "") or ""))
        self.harassment_type = self._HT(data.get("harassment_type"))
        self.safe_numbers = [str(n).strip() for n in (data.get("safe_numbers") or []) if str(n).strip()]

    def is_safe(self, number: str) -> bool:
        keys = {_num_key(n) for n in self.safe_numbers}
        return _num_key(number) in keys

    @property
    def affected_line(self) -> str:
        return self.affected_number.strip() or self.phone.strip()

    @classmethod
    def load(cls, path: str | None):
        if path and os.path.isfile(path):
            import json
            try:
                with open(path, encoding="utf-8") as f:
                    return cls(json.load(f))
            except (OSError, ValueError):
                pass
        return cls()


def main():
    import argparse

    for stream in (sys.stdout, sys.stderr):
        try:
            stream.reconfigure(encoding="utf-8", errors="replace")
        except (AttributeError, ValueError):
            pass

    p = argparse.ArgumentParser(description="Build the TraceWorthy evidence packet from a call CSV.")
    p.add_argument("--csv", required=True, help="App export or carrier records CSV")
    p.add_argument("--profile", help="traceworthy_profile.json (blank fields show as [PLACEHOLDER])")
    p.add_argument("--out", default="packet", help="Output folder (default: packet)")
    p.add_argument("--note", help="Optional source caveat line added to the summary + packet cover")
    args = p.parse_args()

    rows = rows_from_csv(args.csv)
    if not rows:
        sys.exit("No usable calls found in the CSV.")
    profile = _JsonProfile.load(args.profile)
    written = generate_all(rows, profile, args.out, args.note)
    print(f"{len(rows)} calls -> {os.path.abspath(args.out)}")
    for name, path in written.items():
        print(f"  {name}: {os.path.basename(path)}")


if __name__ == "__main__":
    main()
