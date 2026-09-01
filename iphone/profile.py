"""
profile.py — the "My info" details, stored once and reused by every generated
document. Python mirror of the Android app's UserProfile.kt / ProfileStore.

Stored as plain JSON (traceworthy_profile.json) next to the tool. Nothing here
leaves the machine.
"""

from __future__ import annotations

import json
import os
from dataclasses import asdict, dataclass, field, fields
from enum import Enum

DEFAULT_PATH = "traceworthy_profile.json"


class HarassmentType(str, Enum):
    """What kind of harassment is being documented — changes document wording."""

    UNSPECIFIED = "Unspecified"
    SILENT = "Silent"
    AGGRESSIVE = "Aggressive"
    BOTH = "Both"

    @property
    def includes_aggressive(self) -> bool:
        return self in (HarassmentType.AGGRESSIVE, HarassmentType.BOTH)

    @property
    def includes_silent(self) -> bool:
        return self in (HarassmentType.SILENT, HarassmentType.BOTH)

    @property
    def label(self) -> str:
        return {
            HarassmentType.UNSPECIFIED: "Not sure yet / prefer not to say",
            HarassmentType.SILENT: "Silent / hang-up calls",
            HarassmentType.AGGRESSIVE: "Aggressive / threatening",
            HarassmentType.BOTH: "Both",
        }[self]

    @classmethod
    def parse(cls, value: str | None) -> "HarassmentType":
        for m in cls:
            if m.value == value:
                return m
        return cls.UNSPECIFIED


@dataclass
class Profile:
    """Everything the documents auto-fill from. Blank fields render as [PLACEHOLDER]."""

    full_name: str = ""
    phone: str = ""                 # where police / FCC / the carrier should reach you
    affected_number: str = ""       # the line actually receiving the harassing calls
    email: str = ""
    address_city: str = ""
    state: str = ""            # two-letter USPS code, e.g. "CA"
    carrier: str = ""          # e.g. "Verizon", "AT&T", "T-Mobile"
    harassment_type: HarassmentType = field(default=HarassmentType.UNSPECIFIED)
    fcc_complaint_number: str = ""
    police_case_number: str = ""
    carrier_case_number: str = ""
    # Known callers: friends/relatives who call from a number you never saved to
    # contacts. Their calls are set aside from every evidence figure, chart, list,
    # and the CSV; the evidence summary reports how many were set aside and how
    # many incoming calls the analysis then covers.
    safe_numbers: list[str] = field(default_factory=list)

    @property
    def affected_line(self) -> str:
        """The harassed number — falls back to the contact number when not set separately."""
        return self.affected_number.strip() or self.phone.strip()

    @staticmethod
    def _num_key(number: str) -> str:
        digits = "".join(ch for ch in str(number) if ch.isdigit())
        return digits[-10:] if len(digits) >= 7 else str(number).strip()

    def is_safe(self, number: str) -> bool:
        """True when [number] is one of the known callers (any format)."""
        return self._num_key(number) in {self._num_key(n) for n in self.safe_numbers}

    @property
    def is_ready_for_documents(self) -> bool:
        """True once the minimum needed to fill a document is present."""
        return bool(self.full_name.strip()) and bool(self.phone.strip())

    # -- persistence -------------------------------------------------------

    def to_dict(self) -> dict:
        d = asdict(self)
        d["harassment_type"] = self.harassment_type.value
        return d

    @classmethod
    def from_dict(cls, data: dict) -> "Profile":
        known = {f.name for f in fields(cls)}
        clean = {k: v for k, v in (data or {}).items() if k in known}
        clean["harassment_type"] = HarassmentType.parse(clean.get("harassment_type"))
        clean["safe_numbers"] = [str(n).strip() for n in (clean.get("safe_numbers") or []) if str(n).strip()]
        return cls(**clean)

    @classmethod
    def load(cls, path: str = DEFAULT_PATH) -> "Profile":
        if not os.path.isfile(path):
            return cls()
        try:
            with open(path, "r", encoding="utf-8") as f:
                return cls.from_dict(json.load(f))
        except (json.JSONDecodeError, OSError):
            return cls()

    def save(self, path: str = DEFAULT_PATH) -> str:
        with open(path, "w", encoding="utf-8") as f:
            json.dump(self.to_dict(), f, indent=2)
        return os.path.abspath(path)
