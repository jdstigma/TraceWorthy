package com.traceworthy.app

/**
 * In-app knowledge base. The source-of-truth guidance that used to live only in
 * the project's .md files now ships inside the app so users can read it offline.
 * USA-focused: the traceback path described here is federal (FCC / TRACED Act),
 * so it holds in every state — only the local police and state AG contacts differ.
 */
data class Article(
    val title: String,
    val summary: String,
    val body: List<Section>,
)

data class Section(val heading: String, val paragraphs: List<String>)

object LearnContent {

    val articles: List<Article> = listOf(
        Article(
            title = "Start here — the whole process",
            summary = "The step-by-step path from a harassing call to a filed complaint.",
            body = listOf(
                Section(
                    "Set up (once)",
                    listOf(
                        "1. Grant call-log access so TraceWorthy can read your calls. (Android only — " +
                            "iPhone can't; see \"Get your carrier call records.\")",
                        "2. Open My info and enter your name, phone, carrier, and state. Every document " +
                            "auto-fills from this, so you only type it once.",
                        "3. In Settings, adjust the flag threshold if you want (default flags calls " +
                            "15 seconds or shorter from unknown numbers).",
                    )
                ),
                Section(
                    "As calls come in",
                    listOf(
                        "4. In Call log, tap a harassing call to add a note and tag how serious it was " +
                            "(Silent / Spoken / Threatening).",
                        "5. In Flagged numbers, group the spoofed numbers that are really one caller " +
                            "under a single name.",
                        "6. Right after a harassing call, open Call trace and dial *57 — it logs the " +
                            "trace time for police.",
                    )
                ),
                Section(
                    "Build and file",
                    listOf(
                        "7. In Documents, generate the Evidence packet (or individual documents) as a PDF.",
                        "8. File your FCC complaint (federal), a police report (this is what lets police " +
                            "subpoena your carrier), and a carrier harassment case. State help lists your " +
                            "state's contacts.",
                        "9. As you get case numbers, save them back into My info so they cross-reference " +
                            "in every document you generate afterward.",
                    )
                ),
            )
        ),
        Article(
            title = "What TraceWorthy can and can't do",
            summary = "Why an app can't unmask a spoofed caller — and what it does instead.",
            body = listOf(
                Section(
                    "The honest limit",
                    listOf(
                        "Your phone only ever sees the number the network hands it. When a caller " +
                            "spoofs their caller ID, that number is fake — so no app on your phone, " +
                            "TraceWorthy included, can reveal who is really calling.",
                        "Only your carrier and law enforcement can trace the true origin, through a " +
                            "process called a traceback (often backed by a subpoena).",
                    )
                ),
                Section(
                    "What the app is for",
                    listOf(
                        "TraceWorthy turns your call log into organized, timestamped, court-ready " +
                            "evidence. It flags the \"silent stranger\" pattern, counts how many " +
                            "different numbers are hitting you, and builds the exact documents the " +
                            "FCC, your carrier, and the police need to start a traceback.",
                        "Think of it as the evidence binder that makes officials act — not the " +
                            "magnifying glass that names the caller.",
                    )
                ),
            )
        ),
        Article(
            title = "How a traceback actually works",
            summary = "The federal path that unmasks a spoofed caller, step by step.",
            body = listOf(
                Section(
                    "The chain",
                    listOf(
                        "1. You document the calls (TraceWorthy does this).",
                        "2. You file an FCC complaint — this feeds the federal record of spoofing campaigns.",
                        "3. You file a police report and open a carrier harassment case.",
                        "4. Police subpoena your carrier. The carrier works backward through each " +
                            "network the call passed through — the \"traceback\" — until they reach " +
                            "the originating provider and the real line.",
                    )
                ),
                Section(
                    "Why your evidence matters",
                    listOf(
                        "Tracebacks are driven by the Industry Traceback Group and enabled by the " +
                            "federal TRACED Act. Officials prioritize cases with clean documentation: " +
                            "dates, counts, the number of distinct spoofed numbers, and a described " +
                            "pattern. That is exactly what TraceWorthy's CSV and PDF provide.",
                    )
                ),
            )
        ),
        Article(
            title = "Keep the subpoena secret",
            summary = "Ask police to pair the carrier subpoena with a non-disclosure order.",
            body = listOf(
                Section(
                    "The problem",
                    listOf(
                        "When police subpoena your carrier for records, the carrier's normal practice " +
                            "is to notify the account holder. That would tip off the person calling you " +
                            "before the records are secured — and spoofing services routinely delete " +
                            "their logs, so a heads-up invites the evidence to vanish.",
                    )
                ),
                Section(
                    "The fix",
                    listOf(
                        "Under 18 U.S.C. section 2705(b), a prosecutor can ask the court for a " +
                            "non-disclosure order that bars the carrier from telling the subscriber, " +
                            "usually for 90 days and renewable. If bank records are also involved, " +
                            "financial institutions have parallel rules.",
                        "Only a prosecutor or court can obtain it — you can't. But you can put the " +
                            "request and its basis on the record from day one.",
                    )
                ),
                Section(
                    "What to do",
                    listOf(
                        "Generate the \"Non-disclosure order request\" from the Documents screen and " +
                            "hand it to your detective with your police report. It lays out the five " +
                            "statutory grounds and flags which ones fit your case (threats, evidence " +
                            "destruction, witness intimidation, jeopardizing the traceback).",
                    )
                ),
            )
        ),
        Article(
            title = "Right after a harassing call",
            summary = "The one thing to do in the moment, plus what not to bother with.",
            body = listOf(
                Section(
                    "Call Trace (*57)",
                    listOf(
                        "On a landline, dialing *57 immediately after a harassing call tells the " +
                            "carrier to log the true originating line for that specific call, in a " +
                            "form police can subpoena. You won't see the result — by design it goes " +
                            "to the carrier and law enforcement. It usually carries a small per-use fee.",
                        "Reality for mobile: *57 is a landline feature and is not reliable on wireless. " +
                            "On a cell phone, the path that unmasks a spoofed caller is the police " +
                            "report → carrier subpoena chain, not *57.",
                    )
                ),
                Section(
                    "In the moment",
                    listOf(
                        "Don't engage or call back. Note the time and what happened (silent? recording? " +
                            "hung up?) — in TraceWorthy, tap the call and add a note. Those notes ride " +
                            "along in your export and strengthen the pattern.",
                    )
                ),
            )
        ),
        Article(
            title = "Reduce the volume: carrier + phone tools",
            summary = "Free blocking tools that cut the flood while you build your case.",
            body = listOf(
                Section(
                    "Carrier spam blocking",
                    listOf(
                        "Most major US carriers offer a free spam-blocking app or setting (for " +
                            "example AT&T ActiveArmor, Verizon Call Filter, T-Mobile Scam Shield). " +
                            "Turn on spam-risk blocking and unknown-caller handling. These won't " +
                            "identify a spoofed number, but they cut the volume and log spam categories.",
                    )
                ),
                Section(
                    "Built-in phone settings",
                    listOf(
                        "Android's Phone app has \"Filter spam calls,\" and you can enable " +
                            "\"Silence unknown callers\" so numbers not in your contacts don't ring " +
                            "through. TraceWorthy keeps logging them in parallel so your evidence stays complete.",
                    )
                ),
            )
        ),
        Article(
            title = "Register and stay protected",
            summary = "Do-Not-Call registration and keeping your evidence clean.",
            body = listOf(
                Section(
                    "Do Not Call registry",
                    listOf(
                        "Register your number free at donotcall.gov. It won't stop illegal spoofers, " +
                            "but being registered strengthens your FCC complaint (you can state you " +
                            "gave no consent and are on the registry).",
                    )
                ),
                Section(
                    "Keep your evidence clean",
                    listOf(
                        "Export regularly so you have dated snapshots. Don't delete the harassing " +
                            "calls from your phone log — they are the evidence. Add notes while the " +
                            "details are fresh.",
                    )
                ),
            )
        ),
        Article(
            title = "FAQ",
            summary = "Quick answers to the most common questions.",
            body = listOf(
                Section(
                    "Why can't the app tell me who's really calling?",
                    listOf(
                        "A spoofed caller ID is a fake number, so your phone (and any app) only ever " +
                            "sees the fake. Only your carrier and law enforcement can unmask the real " +
                            "origin through a traceback/subpoena. TraceWorthy builds the evidence that " +
                            "gets them to act.",
                    )
                ),
                Section(
                    "Is my data private?",
                    listOf(
                        "Yes. Everything stays on your device — no accounts, and nothing is uploaded. " +
                            "Documents you generate go to your Downloads and are only shared if you choose to.",
                    )
                ),
                Section(
                    "Does *57 work on a cell phone?",
                    listOf(
                        "*57 is most reliable on landlines; some wireless carriers don't support it. On " +
                            "mobile, the route that actually unmasks a spoofed caller is the police-report-" +
                            "to-carrier-subpoena chain.",
                    )
                ),
                Section(
                    "Can I record the harassing call?",
                    listOf(
                        "Android blocks apps from recording call audio, and recording laws vary by state " +
                            "(some require every party's consent). Rely on the call log, your notes, and *57.",
                    )
                ),
                Section(
                    "Why USA only?",
                    listOf(
                        "The traceback process TraceWorthy is built around is US-federal (FCC / TRACED " +
                            "Act) and works the same in every state. Laws differ greatly outside the US.",
                    )
                ),
            )
        ),
        Article(
            title = "Get your carrier call records",
            summary = "How to pull records from your carrier — and why iPhone users must.",
            body = listOf(
                Section(
                    "iPhone can't use this app",
                    listOf(
                        "Apple does not allow third-party apps to access the iPhone call log. " +
                            "That is an iOS restriction, not a limit of this app — no app, including " +
                            "TraceWorthy, can read an iPhone's calls. TraceWorthy is Android-only for " +
                            "this reason (Android does allow it, with your permission).",
                        "If you have an iPhone, you build your record a different way: pull your call " +
                            "history from your carrier (below).",
                    )
                ),
                Section(
                    "Carrier records help everyone",
                    listOf(
                        "Even on Android, carrier records are stronger evidence than an on-phone log: " +
                            "they are complete, carrier-verified, and harder to dispute. They are worth " +
                            "getting to supplement what the app captures.",
                    )
                ),
                Section(
                    "AT&T",
                    listOf(
                        "Sign in at att.com (or the myAT&T app) → Bill & usage → Usage. Choose the " +
                            "line, pick Call details, and Download / Export to CSV.",
                    )
                ),
                Section(
                    "Verizon",
                    listOf(
                        "Sign in at verizon.com → My Verizon → Bill → View bill. Open Call & message " +
                            "logs (or Voice usage details) for the line and download the CSV.",
                    )
                ),
                Section(
                    "T-Mobile",
                    listOf(
                        "Sign in at t-mobile.com → Account → Line usage. Select the line → Calls → " +
                            "Download / Export.",
                    )
                ),
                Section(
                    "Any other carrier",
                    listOf(
                        "Log in to your account portal and look for usage / call detail records (CDR), " +
                            "then download the CSV. Prepaid and MVNO accounts usually have this too.",
                        "Set the date range as wide as it allows, and make sure INCOMING calls are " +
                            "included — some portals default to outgoing only.",
                    )
                ),
                Section(
                    "Ask the carrier for the full records",
                    listOf(
                        "On the harassment-case call (and in writing), ask specifically for: \"copies " +
                            "of the incoming call detail records for my line, going back as far as you " +
                            "retain them — the detailed billing statements, not the summary, as CSV or " +
                            "Excel if possible.\" Note the rep's name and the date.",
                        "The account holder has to request it — if the line is on a family or business " +
                            "plan, whoever's name is on the account makes the request (carriers guard " +
                            "this as CPNI).",
                    )
                ),
                Section(
                    "Act fast, and let police preserve it",
                    listOf(
                        "Carriers keep detailed records only about 12–24 months, sometimes less. Older " +
                            "calls are already gone — request now.",
                        "If you file a police report, police can send the carrier a preservation letter " +
                            "to freeze the records and later subpoena more than you can get as a " +
                            "customer. Filing the report protects the evidence.",
                    )
                ),
                Section(
                    "What the records will and won't show",
                    listOf(
                        "They show every call to your line with date, time, duration, and the caller ID " +
                            "as delivered — which for a spoofed call is the fake number. They will not " +
                            "reveal the true origin; only a traceback or subpoena can. That's fine — the " +
                            "evidence is the pattern: the volume, the timing, and the pile of one-off " +
                            "numbers.",
                    )
                ),
            )
        ),
        Article(
            title = "Glossary",
            summary = "Spoofing, traceback, subpoena, and the terms officials will use.",
            body = listOf(
                Section(
                    "Terms",
                    listOf(
                        "Caller ID spoofing — faking the number that shows on your screen so the " +
                            "call looks local or trusted.",
                        "Traceback — the carrier-to-carrier process of following a call back through " +
                            "each network to its true origin.",
                        "Subpoena — a legal order (obtained by police) compelling your carrier to " +
                            "hand over the originating records.",
                        "TRACED Act — the 2019 federal law that strengthened penalties for illegal " +
                            "spoofing and mandated call-authentication (STIR/SHAKEN).",
                        "STIR/SHAKEN — the carrier framework that cryptographically signs caller ID " +
                            "to make spoofing harder to pass through.",
                    )
                ),
            )
        ),
        Article(
            title = "Disclaimer & about",
            summary = "Not legal advice; an independent app.",
            body = listOf(
                Section("Not legal advice", LegalText.notLegalAdvice),
                Section("Independent app", LegalText.nonAffiliation),
            )
        ),
    )
}
