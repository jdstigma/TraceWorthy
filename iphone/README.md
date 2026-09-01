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
4. Click **Extract call history**. This writes **`iphone_calls.csv`** into
   `Documents\TraceWorthy\` and prints how many calls (and how many flagged) it
   found. If it says *"No calls found"* or *"filtered out"*, click **Inspect
   backup** for a full breakdown, and see Troubleshooting below.

**Command line** equivalent:

```bash
python -m iphone.cli list                                   # confirm the backup is found
python -m iphone.cli csv --backup auto --password "YOUR-BACKUP-PASSWORD"
python -m iphone.cli inspect --backup auto --password "..."  # diagnose "no calls"
```

`csv` takes `--facetime` (include FaceTime) and `--include-app-calls` (include
WhatsApp / other VoIP apps); by default only cellular phone calls are exported.

That's the iPhone log file done. `iphone_calls.csv` is byte-compatible with the
Android app's export, so `../analysis/analyze_calls.py` also reads it directly.

### Step 4 *(optional)* — Add notes to specific calls

**Notes** tab: pick a call, write what happened ("silent 30s", "shouted threats",
"said he knew my address"), choose a severity (Silent / Spoken / Threatening),
**Apply**, then **Save all to CSV**. These become the incident-timeline document.

### Step 5 *(optional)* — Mark the numbers you actually know

**White list** tab: the numbers that called you, most calls first. Tick anyone you
recognize — a friend or relative on a number you never saved to contacts.
Double-click a row (or select it and **Toggle selected**), then **Save white
list**. Their calls are then left out of every figure, chart, list, and the CSV,
and the evidence summary shows an all-incoming vs. potential-harassment
comparison. Use **Add number** for anyone who isn't in the list.

### Step 6 — Fill in your details and build the packet

1. **My info** tab — your name, **contact phone**, the **affected number** (the line
   getting the calls — leave blank if it's the same as your contact phone),
   city/state, carrier, and any FCC / police / carrier case numbers. **Save**
   (stored in `traceworthy_profile.json`). Blank fields show as `[PLACEHOLDER]`.
2. **Generate** tab — **Build full evidence packet**. PDFs land in `iphone_packet/`,
   numbered in filing order: `01` evidence summary → `02` incident timeline →
   `03` carrier script → `04` FCC complaint → `05` police report → `06`
   non-disclosure order request (`00` is the pre-bundled packet). Drag the folder
   into Acrobat's *Combine Files* and they're already in the right sequence.

Command line:

```bash
python -m iphone.cli packet --csv iphone_calls.csv --profile traceworthy_profile.json --out iphone_packet
```

---

## What you get

| File | Purpose |
|---|---|
| `Documents\TraceWorthy\iphone_calls.csv` | The extracted call log (same format as the Android app's export) |
| `TraceWorthy_00_evidence_packet_*.pdf` | All of the below, bundled with a cover + index |
| `TraceWorthy_01_evidence_summary_*.pdf` | One-page stats + charts to attach to any filing |
| `TraceWorthy_02_incident_timeline_*.pdf` | Chronological log (from the notes added in Step 4) |
| `TraceWorthy_03_carrier_script_*.pdf` | Word-for-word script for your carrier's fraud desk |
| `TraceWorthy_04_fcc_complaint_*.pdf` | Text to paste into consumercomplaints.fcc.gov |
| `TraceWorthy_05_police_report_*.pdf` | Cover note for police so they can subpoena the carrier |
| `TraceWorthy_06_non_disclosure_order_*.pdf` | Hand to police: asks that the carrier subpoena be paired with a court order keeping it secret from the subscriber |

## Limits

- **Retention.** `CallHistory.storedata` keeps only ~the last 1,000 calls, and it
  can be empty entirely (device wiped/restored, or Call History synced to iCloud
  and not cached locally). For a months-long campaign — or when the device history
  is gone — the complete record is with your **carrier**. Full walkthrough for
  pulling those records (self-service and by request, what to ask for, retention
  and account-holder rules) is in
  [`../analysis/HOW_TO_GET_CALL_RECORDS.md`](../analysis/HOW_TO_GET_CALL_RECORDS.md).
  Then: `python ..\analysis\packet.py --csv carrier_records.csv --profile traceworthy_profile.json`.
- **Cellular only by default.** FaceTime calls are excluded unless you ask for them.
- **No unmasking.** Like the app, this documents calls; it cannot reveal who is
  really behind a spoofed number. Only a carrier traceback / police subpoena can.

## Troubleshooting

| Message | Fix |
|---|---|
| "Call history is not in this backup" | The backup is unencrypted. Redo Step 2 with **Encrypt local backup** ticked. |
| "Wrong backup password" | Use the password set when encryption was turned on — not the iPhone passcode or Apple ID. |
| "No iPhone backups found" | Make a backup first, then **Refresh**. Or **Browse…** to the folder that contains `Manifest.plist`. |
| "…has no records (table … is empty)" | The device isn't keeping call history locally (wiped/restored, or synced to iCloud). Try toggling **Settings → [name] → iCloud → Call History OFF**, wait a minute, make a **fresh encrypted backup**, and retry. If it's still empty, the history is gone from the phone — get it from your carrier ([`../analysis/HOW_TO_GET_CALL_RECORDS.md`](../analysis/HOW_TO_GET_CALL_RECORDS.md)). |
| "All N records were filtered out (… FaceTime / third-party app)" | The backup only has FaceTime / app calls. Tick *"include FaceTime calls"* and/or *"include third-party app calls"* and extract again. |
| Extract works but the count looks low | iOS keeps only ~the last 1,000 calls. Get the full history from your carrier. |
| Encrypted backup, no password saved anywhere | Apple can't recover it. Turn encryption off and back on in the backup app to set a new one, then back up again. |
