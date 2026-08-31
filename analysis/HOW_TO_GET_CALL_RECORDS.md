# How to get your call records from your carrier

**iPhone gives apps no call-log access**, and even on Android the on-device history
is only ~the last 500–1,000 calls. For a campaign that spans months — or when the
device history has been wiped — the complete record lives with your **carrier**.
Carrier records are also better evidence: complete, carrier-verified, timestamped,
and hard to dispute. Feed the CSV to `packet.py` (full evidence packet) or
`analyze_calls.py` (charts + one-page summary).

> **What carrier records will and won't show.** They give you every call to your
> line with its date, time, duration, and the caller ID **as it was delivered** —
> which for a spoofed call is the *fake* number. They will **not** reveal the true
> origin of a spoofed call; only a carrier traceback or a law-enforcement subpoena
> can. That's fine — the packet is built on the *pattern* (volume, timing, and the
> tell-tale pile of one-off numbers), and the records show that clearly.

---

## Route 1 — Download it yourself (do this first, today)

Every major carrier shows call history in the online account. The **website**
usually exports better than the app.

1. Sign in to the carrier's site for the **affected line**.
2. Find **Usage → Call history / Call detail records**.
3. Set the date range **as wide as it allows** (often 3–18 months).
4. **Download as CSV or Excel** if offered (PDF is hard to parse). No download
   button? Screenshot every page in order.
5. Make sure **incoming** calls are included — some portals default to outgoing
   only; there's usually a direction filter.

### AT&T
att.com (or myAT&T app) → **Bill & usage → Usage** → pick the line →
**Call details** → **Download / Export** to CSV.
Typical columns: `Date`, `Time`, `Number`, `Direction`, `Min.` →
`python analyze_calls.py --duration-unit minutes`

### Verizon
verizon.com → **My Verizon → Usage** (or **Bill → View bill → Call & message
logs / Voice usage details**) for the line → **Download** CSV.
Columns often: `Date`, `Time`, `Number`, `Origination/Destination`, `Minutes`.

### T-Mobile
t-mobile.com → **Account → Line usage / Usage** → select the line → **Calls** →
**Download / Export**.
Columns often: `Date/Time`, `Number`, `Direction`, `Minutes`.

### Prepaid / MVNO / other
Log in to the account portal, find **usage / call detail records (CDR)**, download
CSV. Any file with a number column, a date/time, and ideally duration + direction
will work.

## Route 2 — Ask the carrier for the full records

Use the **carrier call script** from the packet to open a documented harassment
case and get a case number — and on that same call (and in writing) add:

> "I need copies of the **incoming call detail records** for my line, [affected
> number], going back as far as you retain them — the **detailed billing
> statements**, not the summary. Please send them as CSV or Excel if possible."

Keep the chat transcript / email, and note the rep's name and the date.

## Things to know

- **Retention is limited** — often 12–24 months, sometimes less. Older calls are
  already gone. Request now.
- **The account holder must request it.** If the affected line is on a family or
  business plan, whoever's name is on the account makes the request — carriers
  guard this as CPNI (Customer Proprietary Network Information).
- **If you file a police report, do it soon.** Police can send the carrier a
  *preservation letter* to freeze the records and later subpoena more than a
  customer can get. Filing the report protects the evidence.

---

## Turn the records into the packet

```powershell
# Full evidence packet (FCC / police / carrier / timeline / summary)
python analysis\packet.py --csv "C:\path\to\carrier_records.csv" --profile traceworthy_profile.json --out packet

# Or just charts + a one-page summary
python analysis\analyze_calls.py --csv "C:\path\to\carrier_records.csv"
```

Both **auto-detect** the column layout. If the guess is wrong they print the
columns they found; re-run with overrides, e.g.:

```powershell
python analysis\analyze_calls.py --csv records.csv `
  --number-col "Phone Number" --datetime-col "Date/Time" `
  --duration-col "Min." --duration-unit minutes
```

(PowerShell continues lines with a backtick `` ` ``; Windows CMD uses `^`; or put
it all on one line.)

### What "Flagged" means for carrier data
The app knew which calls were silent. Carrier records don't, so the tool flags
**incoming calls that lasted ≤ 15 seconds** — a strong proxy for silent calls and
immediate hang-ups. Weigh that with your own judgment when you present it.

### Outputs
`analyze_calls.py` → five PNG charts + `TraceWorthy_summary.pdf`.
`packet.py` → the six evidence PDFs (numbered `00`–`05` in filing order) built from
authoritative, carrier-verified call data.
