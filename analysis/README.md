# TraceWorthy — PC analysis + evidence packet

Turns any call log into report-grade charts **and** the full set of filing
documents — the same ones the Android app generates on-device. Runs entirely on
your PC.

Two tools here:

| Script | Output |
|---|---|
| `analyze_calls.py` | Five PNG charts + a one-page `TraceWorthy_summary.pdf` |
| `packet.py` | FCC complaint, police cover note, carrier script, incident timeline, evidence summary + a bundled packet PDF |
| `stats.py` | Shared statistics engine (imported by `packet.py`) — not run directly |

Both auto-detect the input format: a **TraceWorthy app export**, an **iPhone
export** (`../iphone/`), a **carrier records** export from any carrier (see
`HOW_TO_GET_CALL_RECORDS.md`), or a **Google Voice** CSV from
`../google_voice/gvoice_to_csv.py`.

## One-time setup
```bash
pip install -r requirements.txt
```

## Get the CSV onto your PC
In the app: **Calls tab → Export CSV** (saves to the phone's Downloads). Copy that
`TraceWorthy_evidence_*.csv` to your computer — e.g. drop it in this `analysis`
folder, or leave it in your Windows Downloads (the script checks there too).

## Charts + one-page summary
```bash
python analyze_calls.py
```
- Auto-finds the newest `TraceWorthy_evidence_*.csv` in this folder or Downloads.
- Writes PNGs + `TraceWorthy_summary.pdf` to a `charts/` subfolder.

Options:
```bash
python analyze_calls.py --csv "C:\path\to\TraceWorthy_evidence_2026-07-22.csv"
python analyze_calls.py --days 30          # last 30 days only
python analyze_calls.py --out report_figs  # custom output folder
```

Output:
- `flagged_vs_normal.png` — pie: suspicious vs normal calls
- `top_offenders.png` — bar: numbers with the most calls (flagged ones in red)
- `calls_per_day.png` — timeline: call volume per day, flagged stacked on top
- `calls_by_hour.png` — calls by hour of day (reveals overnight / 3am patterns)
- `calls_over_time.png` — scatter: date × time of day, dots colored by the top-5 numbers
- **`TraceWorthy_summary.pdf`** — one page with the charts + key stats.

## Full evidence packet
```bash
python packet.py --csv <call log CSV> --profile ..\traceworthy_profile.json --out packet
```
`--profile` is optional — blank fields render as `[PLACEHOLDER]`. Writes the five
documents plus `TraceWorthy_evidence_packet_*.pdf` (all bundled, with a cover +
index). The incident timeline is populated from the `Note` / `Severity` columns of
the CSV (the Android app fills these; the `../iphone/` tool has a Notes tab for it).

Colors match the in-app charts, so the phone view and the report look consistent.
