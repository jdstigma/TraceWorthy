package com.traceworthy.app

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CreditCardOff
import androidx.compose.material.icons.filled.PhoneDisabled
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * A kind of fraud/abuse the app can document. Each type is a self-contained
 * "pack": which screens it shows, the guided storyboard, and the document set.
 *
 * Adding a pack = a new entry here, a `<Type>Docs` builder object, its
 * `DocumentType` entries, and (optionally) type-specific screens.
 */
enum class CaseType(
    val displayName: String,
    val icon: ImageVector,
    val blurb: String,
    /** How many cases of this type a user may have. Phone harassment is bound to
     *  the one device call log, so exactly one. */
    val maxInstances: Int,
) {
    PhoneHarassment(
        displayName = "Phone harassment",
        icon = Icons.Filled.PhoneDisabled,
        blurb = "Repeated spoofed or silent calls. Builds the evidence for a carrier traceback.",
        maxInstances = 1,
    ),
    IdentityTheft(
        displayName = "Identity theft",
        icon = Icons.Filled.CreditCardOff,
        blurb = "Someone opened accounts or made charges in your name. Builds the FTC report, police packet, and dispute letters.",
        maxInstances = 3,
    );

    /** Case-scoped drawer entries, in order. */
    val drawerScreens: List<CaseScreen>
        get() = when (this) {
            PhoneHarassment -> listOf(
                CaseScreen.Storyboard, CaseScreen.CallLog, CaseScreen.Analysis,
                CaseScreen.FlaggedNumbers, CaseScreen.WhiteList, CaseScreen.CallTrace,
                CaseScreen.Documents, CaseScreen.CaseDetail,
            )
            IdentityTheft -> listOf(
                CaseScreen.Storyboard, CaseScreen.FraudItems, CaseScreen.Documents,
                CaseScreen.CaseDetail,
            )
        }

    val documentTypes: List<DocumentType>
        get() = DocumentType.forCaseType(this)

    /** Filing / reference numbers this type tracks, in order. */
    fun filingKeys(): List<String> = when (this) {
        PhoneHarassment -> listOf("carrier", "fcc", "police")
        IdentityTheft -> listOf("ftc", "police")
    }

    fun filingLabel(key: String): String = when (key) {
        "carrier" -> "Carrier case #"
        "fcc" -> "FCC complaint #"
        "police" -> "Police case #"
        "ftc" -> "FTC Identity Theft Report #"
        else -> "Reference #"
    }

    /** The guided storyboard for a case of this type. */
    fun storyboard(): List<Stage> = when (this) {
        PhoneHarassment -> phoneHarassmentStages
        IdentityTheft -> identityTheftStages
    }
}

// --------------------------------------------------------------------------- //
//  Phone harassment
// --------------------------------------------------------------------------- //
private val phoneHarassmentStages: List<Stage> = listOf(
    Stage(
        id = "setup",
        title = "Set up the case",
        summary = "Enter the number that's getting the calls, your carrier, and the kind of harassment. Confirm your contact details in My info.",
        actionLabel = "Open case details",
        destination = CaseScreen.CaseDetail,
        derive = { c ->
            when {
                c.case.affectedLine(c.myInfo).isBlank() -> StageStatus.NotStarted
                c.case.affectedNumber.isNotBlank() && c.myInfo.isReadyForDocuments -> StageStatus.Done
                else -> StageStatus.InProgress
            }
        },
    ),
    Stage(
        id = "gather",
        title = "Gather the calls",
        summary = "Give TraceWorthy call-log access so it can read the harassing calls into your evidence record.",
        actionLabel = "Open the call log",
        destination = CaseScreen.CallLog,
        derive = { c ->
            when {
                c.granted && c.entries.isNotEmpty() -> StageStatus.Done
                c.granted -> StageStatus.InProgress
                else -> StageStatus.NotStarted
            }
        },
    ),
    Stage(
        id = "tag",
        title = "Tag the harassing calls",
        summary = "Open a call, add a note about what happened, and mark how serious it was. These build the incident timeline.",
        actionLabel = "Tag calls",
        destination = CaseScreen.CallLog,
        derive = { c ->
            val tagged = c.entries.count { !it.note.isNullOrBlank() || it.severity != Severity.Unset }
            when {
                tagged >= 3 -> StageStatus.Done
                tagged > 0 -> StageStatus.InProgress
                else -> StageStatus.NotStarted
            }
        },
    ),
    Stage(
        id = "review",
        title = "Review the pattern",
        summary = "Look at the charts and the flagged-number breakdown — this is what officials will want to see.",
        actionLabel = "Open analysis",
        destination = CaseScreen.Analysis,
        derive = { c -> if (c.entries.isNotEmpty()) StageStatus.InProgress else StageStatus.NotStarted },
    ),
    Stage(
        id = "carrier",
        title = "Open a carrier harassment case",
        summary = "Call your carrier's fraud line with the script, open a documented case, and write the reference number here.",
        actionLabel = "Build the carrier script",
        destination = CaseScreen.Documents,
        filingKey = "carrier",
        derive = { c -> if (c.case.filing("carrier").isNotBlank()) StageStatus.Done else StageStatus.NotStarted },
    ),
    Stage(
        id = "fcc",
        title = "File the FCC complaint",
        summary = "File at consumercomplaints.fcc.gov using the generated complaint, then save the confirmation number.",
        actionLabel = "Build the FCC complaint",
        destination = CaseScreen.Documents,
        filingKey = "fcc",
        derive = { c -> if (c.case.filing("fcc").isNotBlank()) StageStatus.Done else StageStatus.NotStarted },
    ),
    Stage(
        id = "police",
        title = "File the police report",
        summary = "Take the police report cover note and the non-disclosure order request to your local police, in person. Save the case number.",
        actionLabel = "Build the police report",
        destination = CaseScreen.Documents,
        filingKey = "police",
        derive = { c -> if (c.case.filing("police").isNotBlank()) StageStatus.Done else StageStatus.NotStarted },
    ),
    Stage(
        id = "packet",
        title = "Hand over the evidence packet",
        summary = "Generate the full packet and give it, plus the CSV of every call, to police and the FCC.",
        actionLabel = "Build the packet",
        destination = CaseScreen.Documents,
        derive = { c ->
            val filed = listOf("carrier", "fcc", "police").count { c.case.filing(it).isNotBlank() }
            if (filed == 3) StageStatus.InProgress else StageStatus.NotStarted
        },
    ),
)

// --------------------------------------------------------------------------- //
//  Identity theft
// --------------------------------------------------------------------------- //
private val identityTheftStages: List<Stage> = listOf(
    Stage(
        id = "ftc",
        title = "Report to the FTC",
        summary = "Go to IdentityTheft.gov, answer the questions, and get your Identity Theft Report. Save its number here — police and creditors accept it in place of a police report in many cases.",
        actionLabel = "Build the FTC companion",
        destination = CaseScreen.Documents,
        filingKey = "ftc",
        derive = { c -> if (c.case.filing("ftc").isNotBlank()) StageStatus.Done else StageStatus.NotStarted },
    ),
    Stage(
        id = "items",
        title = "List the fraudulent accounts",
        summary = "Add every account, charge, or misuse you've found — the institution, roughly how much, and when you spotted it. This drives every letter.",
        actionLabel = "Add fraudulent accounts",
        destination = CaseScreen.FraudItems,
        derive = { c ->
            when {
                c.case.fraudItems.isEmpty() -> StageStatus.NotStarted
                else -> StageStatus.InProgress
            }
        },
    ),
    Stage(
        id = "protect",
        title = "Place a fraud alert or credit freeze",
        summary = "Contact one credit bureau for a free one-year fraud alert (they tell the other two), or freeze your credit at all three to block new accounts entirely. Use the credit-bureau letters.",
        actionLabel = "Build the bureau letters",
        destination = CaseScreen.Documents,
        derive = { c ->
            when (c.case.td("creditProtection")) {
                "freeze", "alert" -> StageStatus.Done
                else -> StageStatus.NotStarted
            }
        },
    ),
    Stage(
        id = "police",
        title = "File a police report",
        summary = "Bring the police report cover note, your FTC report, and your ID + proof of address to your local police. Save the case number.",
        actionLabel = "Build the police report",
        destination = CaseScreen.Documents,
        filingKey = "police",
        derive = { c -> if (c.case.filing("police").isNotBlank()) StageStatus.Done else StageStatus.NotStarted },
    ),
    Stage(
        id = "dispute",
        title = "Dispute each fraudulent account",
        summary = "Send the dispute letter to each creditor and bank, with your FTC report attached. Mark each account Disputed as you go.",
        actionLabel = "Build the dispute letters",
        destination = CaseScreen.Documents,
        derive = { c ->
            val items = c.case.fraudItems
            when {
                items.isEmpty() -> StageStatus.NotStarted
                items.all { it.status == FraudStatus.Disputed || it.status == FraudStatus.Resolved } -> StageStatus.Done
                items.any { it.status == FraudStatus.Disputed || it.status == FraudStatus.Resolved } -> StageStatus.InProgress
                else -> StageStatus.NotStarted
            }
        },
    ),
    Stage(
        id = "packet",
        title = "Keep everything together",
        summary = "Generate the full packet and keep it with your FTC report and correspondence. Follow up with any creditor that doesn't respond within 30 days.",
        actionLabel = "Build the packet",
        destination = CaseScreen.Documents,
        derive = { c ->
            if (c.case.filing("ftc").isNotBlank() && c.case.fraudItems.isNotEmpty()) StageStatus.InProgress
            else StageStatus.NotStarted
        },
    ),
)
