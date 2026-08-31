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
| 3 | **Call category tags** | Per-call or per-number category tags beyond severity: Spam, Political Campaign, Robocall, Telemarketer, Debt Collector, Scam/Fraud, Survey/Polling, Wrong Number, Personal/Known Harasser, Unknown. | 💡 proposed |
| 4 | **Learn article: unmasking blocked/private calls** | TraceWorthy can't unmask *67/blocked calls itself (requires carrier-level PRI/SS7 access, not app-level). Add a Learn article documenting TrapCall-style conditional-call-forwarding services as a companion tool, and let the revealed number get fed into TraceWorthy's existing note/CSV pipeline. | 💡 proposed |
| 5 | **Reorder Evidence Packet sections by real-world task sequence** | ~~Packet section order is currently EvidenceSummary → IncidentTimeline → PoliceReport → FccComplaint → CarrierScript.~~ **DONE** — new order Summary → Timeline → Carrier → FCC → Police (carrier first for a case #, police last so their cover note cross-references the FCC + carrier numbers). Applied to both the app (`DocumentType.seq` + `PACKET_CONTENTS`) and the PC packet (`analysis/packet.py`); individual PDFs now named `TraceWorthy_NN_<slug>_<stamp>.pdf` so a folder listing / Acrobat "Combine Files" is already in order. | ✅ done |
| 6 | **In-app download link for the desktop companion** | Surface a link/button in the app (Learn or Home) pointing to the PC toolkit (TraceWorthy.exe / traceworthy_launcher.py) release download, so users discover the desktop companion without leaving the app. | 💡 proposed |
| 7 | **Move Harassment Type off the profile, make it call-based** | `HarassmentType` (Silent/Aggressive/Both) is currently a single global field on `UserProfile`/My info. Remove it from My info and make it per-call instead, so each call/note can be tagged with its own harassment type rather than one setting applying to every call. | 💡 proposed |
| 8 | **Incident Timeline: windowed views + slicer + tags-at-top** | Multi-part: (a) beyond user notes/tags, add 14/30/60/120-day windowed views to the Incident Timeline, each shown as its own tab on the timeline screen; (b) within a window, only surface numbers/entries with tagged count > 2; (c) add a slicer/filter (all calls / flagged / legitimate) that applies across the new windowed timeline views; (d) when the Incident Timeline doc is generated, if user tags/notes exist, list them at the top of the document. | 💡 proposed |
| 9 | **"How caller ID spoofing works" explainer in the packet** | The generated packet assumes the reader (police intake officer, FCC reviewer, carrier rep) already understands spoofing. Add a short, plain-language section — one page or a boxed sidebar — covering both levels: (a) **individual / targeted** — one harasser cycling a new fake number per call to defeat blocking and stay anonymous, which is what this complainant is experiencing; (b) **mass / campaign** — robocall & scam operations spoofing thousands of numbers (incl. "neighbor spoofing" that mimics the victim's own area code/prefix), why the sheer count of one-off numbers is itself evidence of automation, and that neither the victim nor the carrier's consumer tools can unmask it — only a STIR/SHAKEN traceback or a subpoena can. Cite the Truth in Caller ID Act + TRACED Act. Implement in `analysis/packet.py` (shared block, likely in the packet cover + evidence summary) and mirror the wording into the Android `DocumentGenerator.kt` / `LearnContent.kt` so app and PC stay in sync. Keep it factual and non-editable. | 💡 proposed |
| 10 | **Non-disclosure / delayed-notice order page in the packet** | When police subpoena the carrier, the carrier normally notifies the account holder — tipping off the spoofer. A prosecutor can ask the court for a non-disclosure order (18 U.S.C. § 2705(b) for electronic-communications records; banks have parallel provisions) barring notice to the subscriber, typically 90 days and renewable. Add a packet page: plain-language explainer + a paragraph the complainant hands their detective requesting that any subpoena to the carrier be paired with a § 2705(b) order, with the statutory grounds (risk of evidence destruction, flight, witness intimidation, or otherwise jeopardizing the investigation). Make clear only a prosecutor/court can issue it. Slots in as a 6th packet doc or an addendum to the police-report cover note (`DocumentGenerator.kt` + `analysis/packet.py` + a Learn article). Small, well-scoped, fits the current product. | 👍 approved — do next |
| 11 | **Generalize to a fraud-evidence platform (case-type packs)** | Expand beyond phone harassment to other fraud/abuse categories the same victim often faces. The engine (`UserProfile`, `Block` model, PDF renderer, `StateContacts`, `LearnContent`) is already case-agnostic; only the call-log reader and the document templates are phone-specific. Plan: (1) finish + release the phone product; (2) refactor to a `CaseType` abstraction — the call log becomes one evidence source among several (manual entry, screenshot/document attach, CSV import), each case type is a "pack" = intake fields + document set + federal/state agency routing + Learn articles; (3) ship **Identity theft** as pack #2 (highest overlap with vishing victims; build templates around FTC IdentityTheft.gov + police + the 3 credit bureaus); (4) add more packs by demand. Candidate packs: credit fraud / stolen bank account # (fold into identity theft), mail fraud (USPIS), SSA/SSI fraud (SSA OIG), forged documents using stolen info (police + target agency), social-media catfish/impersonation (platform report + police), health-record manipulation (HHS OCR + provider privacy officer — victim framing), insurance fraud filed in the victim's name (state insurance-fraud bureau — big per-state content lift). **NCII / non-consensual intimate images: route to specialists (StopNCII.org, Take It Down / NCMEC) + help build the police packet only — do NOT make TraceWorthy the primary tool; those victims are in crisis.** Positioning: tagline shift away from the traceback framing, NOT another rename. **UI direction:** replace the flat 9-item drawer with (a) a **case switcher** — a "tabbed menu that opens up", since one victim often has several concurrent cases (phone + identity + credit); (b) inside each case, a **storyboard / stepper** with ordered stages as individual panels — intake → gather evidence → generate documents → file with agencies → track case numbers — each panel explaining what it accomplishes and the next action, with visible progress. NOT a locked wizard: stages must be revisitable and independently completable (a police case # arrives weeks later), building on the existing Home "action card → On-file ✓" pattern. Keep the drawer only for cross-cutting shared tools: My info, Learn, State help, Settings. | 💡 proposed (large — platform direction) |

---

## Approved / in progress

_(moved here when we pick them up)_

---

## Done

- **Separate "affected number" from the contact number** — My info had one `phone`
  field doing double duty; the harassed line and the contact line are often different
  (e.g. a wiped/retired line vs. a current number). Added `affectedNumber` /
  `affected_number` (blank = same as contact) with an `affectedLine` fallback, wired
  through every document: police "Affected line", FCC "your phone number (the line
  that was called)" + "best number to reach you", carrier context, and the
  summary/timeline/packet headers. App + PC.

- **iPhone route (`iphone/`)** — PC tool: read an encrypted iPhone backup →
  extract `CallHistory.storedata` → app-format CSV → full evidence packet. New
  `analysis/stats.py` + `analysis/packet.py` (Python port of `DocumentGenerator.kt`,
  rendered with fpdf2) also give carrier / Google Voice / app-export users the full
  packet. GUI (Backup / Notes / My info / Generate) + CLI, `TraceWorthy-iPhone.exe`
  via CI. Includes the calls-over-time scatter, the iPhone GUI Notes tab (per-call
  note/severity → incident timeline), and a "full evidence packet" button in the
  main `traceworthy_launcher.py`. — v1.7.0

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
  — v1.6.0
- Editable doc preview: tapping a document now opens a full-screen "Preview & edit" step
  (DocumentPreviewScreen.kt) before the PDF is written. DocumentGenerator refactored —
  `buildEditable()` returns an `EditableDocument` (text blocks editable, tables/charts shown
  as read-only chips); `updateText()` applies edits by block index; `render()`/`writePdf()`
  save to Downloads. Then it shares as before. — v1.6.0
- Dark mode: persisted Appearance toggle (System/Light/Dark) in Settings — `ThemeMode` in
  SettingsStore, state hoisted above `TraceWorthyTheme` in MainActivity so it applies live.
  Polish: theme-aware bar-chart track (was hardcoded `0xFFEEEEEE`), preview dialog top bar
  uses `onPrimary`. — v1.6.0
- Threat-keyword highlighting: `ThreatHighlight` (curated whole-word danger terms, conservative
  to avoid false signals) styles matches in red/semibold wherever notes render (Call log +
  Flagged numbers detail). — v1.6.0
- Doc preview redesign: replaced per-field editing with collapsible sections (grouped by the
  document's own titles/headings). Only body paragraphs + list items are editable; list sections
  get "+ Add" and a "–" per item to curate (e.g. caller numbers). Stable-id model in
  EditableDocument (`sections()`/`updateText(id)`/`removeRow`/`addBulletInSection`). — v1.6.0
- Notes ↔ Analysis: number cards on the Analysis screen now show the call-log notes for that
  number (threat-highlighted) and are tappable to add a note (targets the number's most recent
  call, so it also shows in the call log). Shared `NoteDialog`; AnalysisScreen gained an
  `onNotesChanged` reload callback. — v1.6.0
- Calls-over-time scatter (date × time of day), 90-day window, dots colored by top-5 numbers —
  Analysis screen + PNG export + Evidence Summary/packet PDF. — v1.6.0

---

## Parked / dropped

- 🗑️ **Call recording (speakerphone) + transcribe** (dropped 2026-07-27) — technically infeasible
  for a Play Store app. Android 10 (API 29, our minSdk) removed third-party access to the call
  audio stream; `VOICE_CALL`/`VOICE_COMMUNICATION` sources are system/carrier-only, and Android 11
  closed the accessibility-API loophole. The only thing possible is speakerphone room-capture via
  the mic — degraded, environment-dependent, and Google Play policy prohibits accessibility-based
  call recording anyway. A "record call" button would produce weak audio and give users false
  confidence in bad evidence, which contradicts the app's "documents, doesn't trace" honesty.
  The state-aware **recording-consent card** (built earlier this session) and its `RecordingConsent`
  / `consentFor` helpers were also removed — consent info is half-hearted with no recording action
  behind it.

- 🗑️ **Vet/adopt app name "SpoofProof"** (dropped 2026-07-27) — screened USPTO/Play Store/
  domain/collision. Rejected: (a) already used by other security software — the PortSwigger
  "SpoofProof" Burp Suite extension + UK "#Spoofproof®" service; (b) highly descriptive/generic
  ("spoof-proof" is pervasive in anti-spoofing/biometrics/email), so it's a weak, hard-to-register
  mark; (c) **misdescribes the app** — it implies the app *prevents/stops* spoofing, but TraceWorthy
  documents calls into evidence and cannot unmask or block spoofed calls; (d) would require a second
  full rename after CallGuard→TraceWorthy. Decision: **keep TraceWorthy** (distinctive, collision-free,
  accurate to the traceback angle). No further name changes planned; attorney clearance (#8) runs for
  TraceWorthy.
