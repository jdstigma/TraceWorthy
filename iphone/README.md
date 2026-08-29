# TraceWorthy — iPhone route

iOS gives apps **zero** access to the call log, so there is no iPhone app. But an
iPhone's call history *is* reachable from a computer through a **local backup**.
This tool reads that backup, pulls the call history out into `iphone_calls.csv`
(the "iPhone log file"), and builds the same evidence packet the Android app
produces — FCC complaint, police report cover note, carrier call script, incident
timeline, evidence summary, and a bundled PDF.

Everything runs **on your computer**. Nothing is uploaded anywhere.

---

## Step by step — from the iPhone to `iphone_calls.csv`

### Step 1 — Get the tool

Download **`TraceWorthy-iPhone.exe`** from the
[latest release](https://github.com/jdstigma/TraceWorthy/releases/latest)
(Windows, self-contained — no Python needed).

Or run from source:

```bash
pip install -r requirements.txt
python iphone_gui.py
```

### Step 2 — Make an **encrypted** local backup of the iPhone

iOS only writes call history into **encrypted** backups. An unencrypted backup
will **not** contain it — the tool will tell you if it's missing.

**Windows** (Apple Devices app, or iTunes):

1. Connect the iPhone with a cable. Open the **Apple Devices** app (or iTunes) and
   click the phone.
2. Under *Backups*, choose **Back up to this computer** / **This computer**.
3. Tick **Encrypt local backup**. Set a password *you will remember* — there is no
   recovery, and losing it means starting over. Write it down.
4. Click **Back Up Now**. Wait for it to finish (first encrypted backup can take a
   while — it re-encrypts everything).

**Mac** (Finder): select the iPhone in the sidebar → **Back up all of the data on
your iPhone to this Mac** → tick **Encrypt local backup** → set a password →
**Back Up Now**.

> Already have an *unencrypted* backup? Turning on encryption and backing up again
> replaces it. That's expected.

### Step 3 — Extract the call history

**GUI** — open `TraceWorthy-iPhone.exe`, **Backup** tab:

1. The most recent backup is picked automatically (use **Refresh** / **Browse…**
   otherwise). Backups marked `[encrypted]` are the ones that will work.
2. Type the **backup password** from Step 2.
3. Leave *"include FaceTime calls"* unticked unless you need them.
4. Click **Extract call history**. This writes **`iphone_calls.csv`** next to the
   program and prints how many calls (and how many flagged) it found.

**Command line** equivalent:

```bash
python -m iphone.cli list                                   # confirm the backup is found
python -m iphone.cli csv --backup auto --password "YOUR-BACKUP-PASSWORD"
```

That's the iPhone log file done. `iphone_calls.csv` is byte-compatible with the
Android app's export, so `../analysis/analyze_calls.py` also reads it directly.

### Step 4 *(optional)* — Add notes to specific calls

**Notes** tab: pick a call, write what happened ("silent 30s", "shouted threats",
"said he knew my address"), choose a severity (Silent / Spoken / Threatening),
**Apply**, then **Save all to CSV**. These become the incident-timeline document.

### Step 5 — Fill in your details and build the packet

1. **My info** tab — your name, cell number, city/state, carrier, and any FCC /
   police / carrier case numbers. **Save** (stored in `traceworthy_profile.json`).
   Anything left blank shows as `[PLACEHOLDER]` in the documents.
2. **Generate** tab — **Build full evidence packet**. PDFs land in `iphone_packet/`.

Command line:

```bash
python -m iphone.cli packet --csv iphone_calls.csv --profile traceworthy_profile.json --out iphone_packet
```

---

## What you get

| File | Purpose |
|---|---|
| `iphone_calls.csv` | The extracted call log (same format as the Android app's export) |
| `TraceWorthy_evidence_summary_*.pdf` | One-page stats + charts to attach to any filing |
| `TraceWorthy_incident_timeline_*.pdf` | Chronological log (from the notes added in Step 4) |
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

## Troubleshooting

| Message | Fix |
|---|---|
| "Call history is not in this backup" | The backup is unencrypted. Redo Step 2 with **Encrypt local backup** ticked. |
| "Wrong backup password" | Use the password set when encryption was turned on — not the iPhone passcode or Apple ID. |
| "No iPhone backups found" | Make a backup first, then **Refresh**. Or **Browse…** to the folder that contains `Manifest.plist`. |
| Encrypted backup, no password saved anywhere | Apple can't recover it. Turn encryption off and back on in the backup app to set a new one, then back up again. |
