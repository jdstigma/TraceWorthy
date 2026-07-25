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
| 1 | **Legal disclaimer — first-run agreement** | First-launch popup: "not legal advice; I'm not a lawyer; contact a local attorney." Accept / Decline (decline exits), shown once and remembered. Bundles the non-affiliation text (DISCLAIMER.md). Cheap + protective — do before wider distribution. | 💡 proposed |
| 2 | **Settings screen + flag-threshold** | Introduce a Settings screen (the container future toggles live in) and make the silent-call flag threshold (hardcoded ≤15s) adjustable there. | 💡 proposed |
| 3 | **Surface HOW_TO_GET_CALL_RECORDS in Learn** | Fold `analysis/HOW_TO_GET_CALL_RECORDS.md` (pull carrier CDRs, the iPhone path) into the Learn knowledge base. Quick content add. | 💡 proposed (from initial prompt) |
| 4 | **Richer Learn / start-here flow** | A guided step-by-step "start here" path + FAQ in Learn. Builds on #3. | 💡 proposed |
| 5 | **Editable doc preview** | Preview/edit the filled-in document text before generating the PDF. | 💡 proposed |
| 6 | **Dark-mode polish pass** | Review every screen in dark mode; add a theme override toggle to the Settings screen (#2). | 💡 proposed |
| 7 | **State-aware recording-consent flag** | One-party vs all-party consent field in the per-state data so the recording flow warns based on the user's state (strong "verify" caveat). Precursor to #8. | 💡 proposed |
| 8 | **Call recording (speakerphone) + transcribe** | Speakerphone acoustic capture + "recording in progress" announcement → audio file (evidence) + best-effort transcription to a note. Adds RECORD_AUDIO; needs real-device testing. (Android blocks earpiece/telephony audio for app-store apps.) | 💡 proposed |
| 9 | **Active call monitoring / alerts** | Notify when a new flagged call is detected (background receiver). Scope carefully vs. Play Store call-log policy. | 💡 proposed (from initial prompt) |
| 10 | **Threat-keyword highlighting** | Auto-highlight words like "threat/kill/address" in the notes timeline. Speculative — guard against false signals. | 💡 proposed |
| 11 | **Attorney trademark clearance (TraceWorthy)** | Formal clearance / manual USPTO search before a public Play Store release. Final gate; do last. | 💡 proposed (end — pre-release) |

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
- Non-affiliation statement drafted (DISCLAIMER.md) — to be surfaced via backlog #1

---

## Parked / dropped

_(none yet)_
