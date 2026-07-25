package com.traceworthy.app

import android.content.Context

/**
 * Remembers whether the user accepted the first-run legal agreement. The key is
 * versioned so a future material change to the terms can prompt again.
 */
object AgreementStore {

    private const val PREFS = "agreement"
    private const val KEY = "accepted_v1"

    fun isAccepted(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY, false)

    fun setAccepted(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY, true).apply()
    }
}
