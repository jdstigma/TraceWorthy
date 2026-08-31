package com.traceworthy.app

/** What a stage's status function gets to look at. */
data class StageContext(
    val case: Case,
    val myInfo: MyInfo,
    val granted: Boolean,
    val entries: List<CallEntry>,
)

/**
 * One step in a case's storyboard — the guided sequence the user works through.
 * [status] is derived from the case's state each time the storyboard renders;
 * [Case.stageOverrides] lets the user pin it.
 */
data class Stage(
    val id: String,
    val title: String,
    val summary: String,
    val actionLabel: String,
    val destination: CaseScreen,
    /** Which filing number (if any) this stage records — drives the inline "enter #" field. */
    val filingKey: String? = null,
    val derive: (StageContext) -> StageStatus,
) {
    fun status(ctx: StageContext): StageStatus =
        ctx.case.stageOverrides[id] ?: derive(ctx)
}
