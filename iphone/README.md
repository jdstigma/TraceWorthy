# TraceWorthy — iPhone route

iOS gives apps **zero** access to the call log, so there is no iPhone app. But an
iPhone's call history *is* reachable from a computer through a **local backup**.
This tool reads that backup, pulls the call history out, and builds the same
evidence packet the Android app produces — FCC complaint, police report cover
note, carrier call script, incident timeline, evidence summary, and a bundled PDF.

Everything runs **on your computer**. Nothing is uploaded anywhere.

---

## 1. Make a local backup — it must be ENCRYPTED

iOS only writes call history into **encrypted** backups. An unencrypted backup
will not contain it.

**Windows** (Apple Devices app, or iTunes):
1. Connect the iPhone, open the Apple Devices app (or iTunes) and select the phone.
2. Choose **This computer** / **Local backup**.
3. Tick **Encrypt local backup** and set a password *you will remember*
   (there is no recovery — losing it means re-doing the backup).
4. Click **Back Up Now**.

**Mac** (Finder): select the iPhone in the sidebar → **Back up all of the data on
your iPhone to this Mac** → tick **Encrypt local backup** → **Back Up Now**.

## 2. One-time setup (running from source)

```bash
pip install -r requirements.txt
```

The compiled `TraceWorthy-iPhone.exe` already contains everything.

## 3. Run it

**GUI:** `python iphone_gui.py` (or double-click `TraceWorthy-iPhone.exe`).

- **Backup tab** — pick the backup (auto-detected), enter the backup password,
  click *Extract call history*. Writes `iphone_calls.csv`.
- **My info tab** — your name, number, carrier, case numbers. Saved to
  `traceworthy_profile.json`. Blank fields show as `[PLACEHOLDER]` in the documents.
- **Generate tab** — *Build full evidence packet* → PDFs in `iphone_packet/`.

**Command line:**

```bash
python -m iphone.cli list                       # show local backups
python -m iphone.cli csv --backup auto --password "YOUR-BACKUP-PASSWORD"
python -m iphone.cli packet --csv iphone_calls.csv --profile traceworthy_profile.json --out iphone_packet
```

## What you get

`iphone_calls.csv` is byte-compatible with the Android app's export, so
`../analysis/analyze_calls.py` reads it too. The packet contains:

| File | Purpose |
|---|---|
| `TraceWorthy_evidence_summary_*.pdf` | One-page stats + charts to attach to any filing |
| `TraceWorthy_incident_timeline_*.pdf` | Chronological log (needs Note/Severity columns filled) |
| `TraceWorthy_police_report_*.pdf` | Cover note for police so they can subpoena the carrier |
| `TraceWorthy_fcc_complaint_*.pdf` | Text to paste into consumercomplaints.fcc.gov |
| `TraceWorthy_carrier_script_*.pdf` | Word-for-word script for your carrier's fraud desk |
| `TraceWorthy_evidence_packet_*.pdf` | All of the above, bundled with a cover + index |

## Limits

- **Retention.** `CallHistory.storedata` keeps only ~the last 1,000 calls. For a
  months-long campaign, request full records from your carrier — see
  [`../analysis/HOW_TO_GET_CALL_RECORDS.md`](../analysis/HOW_TO_GET_CALL_RECORDS.md).
  Every generated document says this.
- **Cellular only by default.** FaceTime calls are excluded unless you ask for them.
- **No unmasking.** Like the app, this documents calls; it cannot reveal who is
  really behind a spoofed number. Only a carrier traceback / police subpoena can.
