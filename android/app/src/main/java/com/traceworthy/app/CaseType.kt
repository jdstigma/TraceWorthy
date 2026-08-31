package com.traceworthy.app

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhoneDisabled
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * A kind of fraud/abuse the app can document. Each type is a self-contained
 * "pack": which screens it shows, the guided storyboard, and the document set.
 *
 * Only [PhoneHarassment] exists today. Adding a pack = a new entry here, a
 * `docs/<Type>Docs.kt` builder file, and (optionally) type-specific screens.
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
    );

    /** Case-scoped drawer entries, in order. */
    val drawerScreens: List<CaseScreen>
        get() = when (this) {
            PhoneHarassment -> listOf(
                CaseScreen.Storyboard,
                CaseScreen.CallLog,
                CaseScreen.Analysis,
                CaseScreen.FlaggedNumbers,
                CaseScreen.CallTrace,
                CaseScreen.Documents,
                CaseScreen.CaseDetail,
            )
        }

    val documentTypes: List<DocumentType>
        get() = DocumentType.entries.filter { it.caseType == this }

    /** The guided storyboard for a case of this type. */
    fun storyboard(): List<Stage> = when (this) {
        PhoneHarassment -> phoneHarassmentStages
    }
}

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
            when (filed) {
                3 -> StageStatus.InProgress
                else -> StageStatus.NotStarted
            }
        },
    ),
)
