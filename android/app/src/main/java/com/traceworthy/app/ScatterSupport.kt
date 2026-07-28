package com.traceworthy.app

/**
 * Shared data for the "calls over time" scatter, used by the on-screen chart, the PNG
 * export, and the PDF. Dots are colored by which of the top-5 most-called numbers they
 * belong to (others gray). Colors are ARGB longs so both Compose and android.graphics
 * can consume them. Red is intentionally avoided so it doesn't read as the flagged signal.
 */
object ScatterColors {

    private const val NINETY_DAYS_MS = 90L * 24 * 60 * 60 * 1000

    /** Distinct hues for ranks 1..5 (blue, teal, orange, purple, coral). */
    val top5: List<Long> = listOf(0xFF185FA5, 0xFF1FBFA6, 0xFFBA7517, 0xFF7A3EA1, 0xFFFF7A59)

    /** Everything outside the top 5. */
    const val other: Long = 0xFF9AA4B2

    /** Only the calls from the last 90 days — the scatter's fixed window. */
    fun last90Days(entries: List<CallEntry>): List<CallEntry> {
        val cutoff = System.currentTimeMillis() - NINETY_DAYS_MS
        return entries.filter { it.timestampMillis >= cutoff }
    }

    /** The ordered top-5 numbers by call count in [entries], as (number, displayName). */
    fun top5Numbers(entries: List<CallEntry>): List<Pair<String, String>> =
        CallStats.from(entries).perNumber.take(5)
            .map { it.number to (it.name?.takeIf { n -> n.isNotBlank() } ?: it.number) }

    /** ARGB color for a call, given the ordered top-5 [numbers]. */
    fun colorFor(number: String, numbers: List<String>): Long {
        val i = numbers.indexOf(number)
        return if (i in 0..4) top5[i] else other
    }
}
