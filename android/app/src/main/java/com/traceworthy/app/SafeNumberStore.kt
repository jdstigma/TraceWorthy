package com.traceworthy.app

import android.content.Context

/**
 * The user's list of "known callers" — numbers that are NOT saved in the phone's
 * contacts but are trusted (a friend or relative the user just never added). A
 * close friend who calls a lot from an unsaved number would otherwise land in
 * the flagged pattern and crowd the "top numbers" charts.
 *
 * Calls from a known caller are dropped from every evidence-facing measure: the
 * flag heuristic ([CallEntry.isSuspicious]), the analysis charts, the flagged-
 * numbers list, the exported CSV, and the generated documents (which note how
 * many were excluded). The calls still appear in the raw Call log, where the
 * user marks and unmarks them.
 *
 * Stored locally in SharedPreferences as the numbers exactly as they were shown
 * when marked (for a readable list in Settings); matching is done on a
 * normalized key (digits only, last 10) so format differences don't matter.
 * Nothing leaves the device.
 */
object SafeNumberStore {

    private const val PREFS = "safe_numbers"
    private const val KEY = "numbers"

    /**
     * Normalize a call-log number to a comparable key: digits only, last 10.
     * A value with too few digits to be a real number ("Unknown / withheld")
     * falls back to its trimmed literal so those don't all collide on "".
     */
    fun key(number: String): String {
        val digits = number.filter { it.isDigit() }
        return if (digits.length >= 7) digits.takeLast(10) else number.trim()
    }

    /** The stored numbers, as originally shown — for display and management. */
    fun all(context: Context): Set<String> =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getStringSet(KEY, emptySet())?.toSet() ?: emptySet()

    /** Normalized keys of every stored number — for matching against the log. */
    fun matchKeys(context: Context): Set<String> =
        all(context).map { key(it) }.toSet()

    fun contains(context: Context, number: String): Boolean =
        key(number) in matchKeys(context)

    fun add(context: Context, number: String) = mutate(context) { it.add(number.trim()) }

    fun addAll(context: Context, numbers: Collection<String>) =
        mutate(context) { set -> numbers.map { it.trim() }.filter { it.isNotEmpty() }.forEach(set::add) }

    /** Remove by the exact stored string (as listed in Settings). */
    fun remove(context: Context, storedNumber: String) = mutate(context) { it.remove(storedNumber) }

    /** Remove whatever stored number matches [number] (any format that shares its key). */
    fun removeByNumber(context: Context, number: String) {
        val k = key(number)
        mutate(context) { set -> set.removeAll { key(it) == k } }
    }

    private fun mutate(context: Context, block: (MutableSet<String>) -> Unit) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        // getStringSet's result must not be mutated in place — copy first.
        val set = (prefs.getStringSet(KEY, emptySet()) ?: emptySet()).toMutableSet()
        block(set)
        prefs.edit().putStringSet(KEY, set).apply()
    }
}
