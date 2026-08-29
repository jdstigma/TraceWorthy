# TraceWorthy

An all-in-one **Android app** for people dealing with **unwanted or spoofed phone
calls** — the repeated, silent, always-a-different-number kind. TraceWorthy both
**monitors** your calls (flagging the harassment pattern) and turns them into the
**evidence and documents** you need to get a carrier traceback started: FCC
complaints, police report cover notes, carrier call scripts, and evidence
summaries, all generated on-device as shareable PDFs.

The app is organized into a simple left-drawer menu — **Home** (a checklist of
what to file next), **Call log**, **Analysis** (charts), **Documents** (on-device
PDF generation), **Learn** (an in-app knowledge base explaining how spoofing and
tracebacks work), **State help** (federal + your state's reporting contacts), and
**My info** (enter your details once; every document auto-fills). It's free, with
no accounts and no data leaving the device.

> **Audience:** USA only. The traceback mechanism TraceWorthy is built around is
> federal (FCC / TRACED Act), so it works the same in every state — only your
> local police and state Attorney General contacts differ, and the app lists
> those for you. Laws differ greatly outside the US, which is out of scope.

> **Scope & limits:** TraceWorthy *documents* calls; it cannot reveal who is really
> calling when the number is spoofed. Spoofed caller ID is only unmaskable by
> carriers and law enforcement (via traceback / subpoena). TraceWorthy reads only
> the device owner's own call log; it does not record others' calls, intercept
> anything, or access any other device.

> **Disclaimer:** TraceWorthy is not legal advice and is an independent project
> (unaffiliated with the similarly named "TraceWorthy Consulting"). See [DISCLAIMER.md](DISCLAIMER.md).

> **Note:** A companion PC pipeline (Python) still exists for turning carrier CSVs
> into charts/PDFs, but the app is now the primary, self-contained tool — it
> generates its documents on-device without needing the PC toolkit.

---

## Components

| Component | Folder | What it does |
|-----------|--------|--------------|
| **Android app** | `android/` | The main product. Left-drawer navigation; reads the device call log, flags the silent-stranger pattern, per-call notes, charts, CSV export, on-device PDF document generation, in-app knowledge base, and per-state reporting contacts. Open this folder in Android Studio. |
| **Analysis pipeline** | `analysis/` | `analyze_calls.py` turns any call CSV (app export **or** carrier records, any carrier) into charts + a one-page PDF. `packet.py` builds the full multi-document evidence packet from any CSV — the same documents the app generates on-device. |
| **iPhone route** | `iphone/` | iOS blocks call-log access, so there is no iPhone app. This PC tool reads an **encrypted iPhone backup**, extracts the call history, and builds the full evidence packet. GUI + CLI; ships as `TraceWorthy-iPhone.exe` ([v1.7.0+](https://github.com/jdstigma/TraceWorthy/releases/latest)). Setup: [`iphone/README.md`](iphone/README.md). |
| **Google Voice route** | `google_voice/` | Free screening number; `gvoice_to_csv.py` converts a Takeout export into the CSV the pipeline reads. |
| **Twilio route** *(optional, ~$1/mo)* | `twilio/` | Logs each call's **STIR/SHAKEN attestation**. |
| **Desktop control panel** | `traceworthy_launcher.py` | Tabbed GUI (Run + Help). Build to `.exe` with `build_exe.bat`. |

## Data flow

```
Android app ──────┐
Carrier CSV ──────┼─►  analyze_calls.py  ─►  charts + TraceWorthy_summary.pdf
Google Voice ─────┤
iPhone backup ─┐  └─►  packet.py        ─►  full evidence packet (FCC / police /
   iphone/ ────┘                            carrier / timeline / summary + bundle)
```

## Reference templates (project root)

Generic, fill-in-the-blank source documents: `EVIDENCE_AND_CARRIER_GUIDE.md`,
`ATT_CARRIER_SCRIPT.md`, `FCC_COMPLAINT.md`, `POLICE_REPORT_COVER.md`. The app now
generates filled-in versions of these on-device (Documents screen) and surfaces
the guidance in-app (Learn screen); these Markdown files remain as the underlying
source material and for use with the PC pipeline.

## Quick start

**Android app:** open `android/` in Android Studio and Run ▶ (`SETUP_ANDROID_STUDIO.md`
covers first-time setup). Min SDK 29, package `com.traceworthy.app`.

**Analysis (PC):** `pip install pandas matplotlib`, then double-click
**`TraceWorthy Control Panel.bat`** — or `python analysis/analyze_calls.py --csv <file>`.
Full packet from a CSV: `python analysis/packet.py --csv <file> --profile traceworthy_profile.json`.

**iPhone (PC):** download `TraceWorthy-iPhone.exe` from the
[latest release](https://github.com/jdstigma/TraceWorthy/releases/latest) (or run
`iphone/iphone_gui.py`), make an **encrypted** local backup of the iPhone, then follow
the step-by-step in [`iphone/README.md`](iphone/README.md) to produce `iphone_calls.csv`
and the evidence packet.

**Build artifacts:** `BUILD_DISTRIBUTABLES.md` covers the APK, the `.exe`, and CI;
`SIGNING.md` covers signed release builds.

## Privacy

Real call records (`*.csv`), generated charts, and PDF reports are **git-ignored**
and are never committed. Signing keystores are ignored too.

## License

MIT — see `LICENSE`.
