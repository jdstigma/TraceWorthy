# TraceWorthy — Ideas Backlog

A running list of feature/enhancement ideas to review one by one. Status legend:
`💡 proposed` · `👍 approved` · `🔨 in progress` · `✅ done` · `❄️ parked` · `🗑️ dropped`

When we pick one up, move it to the relevant section and update status.

---

## Under review (bounce 1-by-1)

Priority-ordered by best procedure (reordered 2026-07-24). Rationale: protect first
(disclaimer), then stand up the Settings container that later toggles slot into, then
content/UX polish that makes the app feel finished, then the heavier features that need
real-device testing, and attorney clearance as the final pre-Play gate. Completed ideas
are in the Done section below.

| # | Idea | Notes | Status |
|---|------|-------|--------|
| 1 | **Active call monitoring / alerts** | Notify when a new flagged call is detected (background receiver). **Play Store call-log-policy risk**; needs device testing. | ❄️ parked — policy + device-testing gate |
| 2 | **Attorney trademark clearance** | Formal clearance / manual USPTO search for **TraceWorthy** before a public Play Store release. Final gate; do last. **External — a real-world legal action, not a code task.** | 💡 proposed (end — pre-release) |

---

## Approved / in progress

_(moved here when we pick them up)_

---

## Done

- Left-drawer navigation + brand theme (v1.1.0)
- On-device PDF document generation (v1.1.0)
- In-app Learn knowledge base (v1.1.0)
- Per-state + federal reporting contacts (v1.1.0)
- 7/30/90-day windowed stats + extra metrics in documents _(in progress)_
- Silent vs aggressive harassment routes + Incident Timeline doc _(in progress)_
- Title Case document headings _(in progress)_
- Call Trace (*57) & recording instructions page _(in progress)_
- Idea #1: Note severity tagging (Silent / Spoken / Threatening) — badges + timeline + CSV
- Idea #2: Full evidence packet (all docs → one PDF with cover + index)
- Flagged Numbers screen (per-number notes + stats, flagged only) + flagged-number detail in docs
- Fix: PDF title glyph overlap (subpixel/linear text, ligatures off)
- Vector charts in PDFs: pie (flagged vs normal) + top-numbers bar in every
  evidence doc; severity bar in the timeline (all except the *57 how-to page)
- Idea #1 (reprioritized): Flagged Numbers detail view (tap → per-number stats,
  charts, notes, back button) + polished tappable list cards
- *57: one-tap "Call *57" button + trace log (new Call trace screen, CALL_PHONE);
  replaced the old *57 PDF document. Recording/transcription is the next step (#11).
- Documents de-duplicated: differentiated stats per doc (summary = full; FCC =
  period table; police = pie + pointer; carrier = none; packet cover = index only)
- Docs: flagged-numbers detail capped at top 15 by significance + long-tail summary line
- Idea #2: Caller-name branches — multi-select Group flow, branch cards + merged
  detail (member numbers, Ungroup), documents print a branch as one identity
- Inbound-only: outbound calls excluded from all totals/lists/docs
- Rename CallGuard → TraceWorthy (app package/display, GitHub repo, PC toolkit, CI,
  docs, moved off OneDrive to C:\Dev\TraceWorthy) + new app icon (coral receiver in a
  white locator reticle w/ red center dot on navy)
- Non-affiliation statement drafted (DISCLAIMER.md)
- #1 First-run legal agreement (not legal advice + non-affiliation; Agree/Decline) — v1.5.0
- #2 Settings screen + adjustable flag threshold — v1.5.0
- #3 "Get your carrier call records" Learn article (Apple blocks iPhone call-log access) — v1.5.0
- #4 "Start here" flow + FAQ in Learn — v1.5.0
- Theme to match the icon: coral `#FF7A59` tertiary accent (brand tiles + header/section
  icons + status/emphasis text); teal stays for buttons/selected-nav, red stays for flagged
  _(in progress — pending on-device eyeball + release)_
- Editable doc preview: tapping a document now opens a full-screen "Preview & edit" step
  (DocumentPreviewScreen.kt) before the PDF is written. DocumentGenerator refactored —
  `buildEditable()` returns an `EditableDocument` (text blocks editable, tables/charts shown
  as read-only chips); `updateText()` applies edits by block index; `render()`/`writePdf()`
  save to Downloads. Then it shares as before. _(in progress — pending on-device eyeball + release)_
- Dark mode: persisted Appearance toggle (System/Light/Dark) in Settings — `ThemeMode` in
  SettingsStore, state hoisted above `TraceWorthyTheme` in MainActivity so it applies live.
  Polish: theme-aware bar-chart track (was hardcoded `0xFFEEEEEE`), preview dialog top bar
  uses `onPrimary`. _(in progress — pending on-device eyeball + release)_
- State-aware recording consent: `RecordingConsent` enum + `Contacts.allPartyConsentStates`
  / `consentFor(usps)` in StateContacts.kt; a state-aware consent card on State help (flips
  one-party vs all-party from the selected state) with a strong not-legal-advice caveat.
  Data helper is reusable by a future recording flow. _(in progress — pending release)_
- Threat-keyword highlighting: `ThreatHighlight` (curated whole-word danger terms, conservative
  to avoid false signals) styles matches in red/semibold wherever notes render (Call log +
  Flagged numbers detail). _(in progress — pending release)_

---

## Parked / dropped

- 🗑️ **Call recording (speakerphone) + transcribe** (dropped 2026-07-27) — technically infeasible
  for a Play Store app. Android 10 (API 29, our minSdk) removed third-party access to the call
  audio stream; `VOICE_CALL`/`VOICE_COMMUNICATION` sources are system/carrier-only, and Android 11
  closed the accessibility-API loophole. The only thing possible is speakerphone room-capture via
  the mic — degraded, environment-dependent, and Google Play policy prohibits accessibility-based
  call recording anyway. A "record call" button would produce weak audio and give users false
  confidence in bad evidence, which contradicts the app's "documents, doesn't trace" honesty.
  (The state-aware **recording-consent card** stays — it's useful general legal-awareness info for
  users who record by other means; it no longer gates an in-app recorder.)

- 🗑️ **Vet/adopt app name "SpoofProof"** (dropped 2026-07-27) — screened USPTO/Play Store/
  domain/collision. Rejected: (a) already used by other security software — the PortSwigger
  "SpoofProof" Burp Suite extension + UK "#Spoofproof®" service; (b) highly descriptive/generic
  ("spoof-proof" is pervasive in anti-spoofing/biometrics/email), so it's a weak, hard-to-register
  mark; (c) **misdescribes the app** — it implies the app *prevents/stops* spoofing, but TraceWorthy
  documents calls into evidence and cannot unmask or block spoofed calls; (d) would require a second
  full rename after CallGuard→TraceWorthy. Decision: **keep TraceWorthy** (distinctive, collision-free,
  accurate to the traceback angle). No further name changes planned; attorney clearance (#8) runs for
  TraceWorthy.
