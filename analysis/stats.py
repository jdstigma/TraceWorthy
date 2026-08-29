"""
stats.py — call-log statistics, a Python port of the Android app's CallStats.kt /
StatsExtras. Pure standard library; works on a list of CallRow (see below), which
both the iPhone tool and any carrier CSV can produce.

One deliberate difference from the app: the 7 / 30 / 90-day windows are anchored
to the most recent call in the data, not to "now". A phone backup can be weeks
old, and "calls in the last 7 days" should mean the 7 days up to the last logged
call, not 7 days up to today (which might be empty).
"""

from __future__ import annotations

from collections import Counter
from dataclasses import dataclass, field
from datetime import datetime, timedelta

INCOMING_LIKE = {"Incoming", "Missed", "Rejected"}


@dataclass
class CallRow:
    """One normalized call. The common shape every stats/document function reads."""

    number: str
    name: str
    timestamp: datetime          # naive, local
    duration_seconds: int
    type_label: str              # Incoming / Outgoing / Missed / Rejected / Blocked / Other
    flagged: bool
    severity: str = ""           # "" / Silent / Spoken / Threatening
    note: str = ""

    @property
    def incoming_like(self) -> bool:
        return self.type_label in INCOMING_LIKE


@dataclass
class NumberStat:
    number: str
    name: str
    total_count: int
    flagged_count: int
    first_seen: datetime
    last_seen: datetime


@dataclass
class WindowStat:
    label: str
    total: int
    flagged: int
    unique_numbers: int


@dataclass
class StatsExtras:
    first_call: datetime | None
    last_call: datetime | None
    busiest_hour: int | None
    busiest_hour_count: int
    overnight_count: int
    avg_per_day: float
    windows: list[WindowStat]


@dataclass
class CallStats:
    total_calls: int
    flagged_calls: int
    unique_numbers: int
    incoming: int
    missed: int
    rejected: int
    per_number: list[NumberStat] = field(default_factory=list)

    @classmethod
    def from_rows(cls, rows: list[CallRow]) -> "CallStats":
        by_number: dict[str, list[CallRow]] = {}
        for r in rows:
            by_number.setdefault(r.number, []).append(r)

        per_number = [
            NumberStat(
                number=number,
                name=next((c.name for c in calls if c.name), ""),
                total_count=len(calls),
                flagged_count=sum(1 for c in calls if c.flagged),
                first_seen=min(c.timestamp for c in calls),
                last_seen=max(c.timestamp for c in calls),
            )
            for number, calls in by_number.items()
        ]
        per_number.sort(key=lambda n: (n.total_count, n.flagged_count), reverse=True)

        return cls(
            total_calls=len(rows),
            flagged_calls=sum(1 for r in rows if r.flagged),
            unique_numbers=len(per_number),
            incoming=sum(1 for r in rows if r.type_label == "Incoming"),
            missed=sum(1 for r in rows if r.type_label == "Missed"),
            rejected=sum(1 for r in rows if r.type_label == "Rejected"),
            per_number=per_number,
        )


def _window(rows: list[CallRow], label: str, days: int | None, anchor: datetime) -> WindowStat:
    if days is None:
        sub = rows
    else:
        cutoff = anchor - timedelta(days=days)
        sub = [r for r in rows if r.timestamp >= cutoff]
    s = CallStats.from_rows(sub)
    return WindowStat(label, s.total_calls, s.flagged_calls, s.unique_numbers)


def stats_extras(rows: list[CallRow]) -> StatsExtras:
    windows_spec = [("Last 7 Days", 7), ("Last 30 Days", 30), ("Last 90 Days", 90), ("All Time", None)]
    if not rows:
        return StatsExtras(None, None, None, 0, 0, 0.0,
                           [WindowStat(lbl, 0, 0, 0) for lbl, _ in windows_spec])

    first = min(r.timestamp for r in rows)
    last = max(r.timestamp for r in rows)
    windows = [_window(rows, lbl, d, last) for lbl, d in windows_spec]

    hours = Counter(r.timestamp.hour for r in rows)
    overnight = sum(1 for r in rows if r.timestamp.hour >= 22 or r.timestamp.hour < 6)
    busiest_hour, busiest_count = hours.most_common(1)[0]

    span_days = max((last - first).days + 1, 1)
    return StatsExtras(
        first_call=first,
        last_call=last,
        busiest_hour=busiest_hour,
        busiest_hour_count=busiest_count,
        overnight_count=overnight,
        avg_per_day=len(rows) / span_days,
        windows=windows,
    )
