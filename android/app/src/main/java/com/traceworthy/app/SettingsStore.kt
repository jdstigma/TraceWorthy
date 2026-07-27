package com.traceworthy.app

import android.content.Context

/**
 * App preferences, stored locally in SharedPreferences.
 *
 * [flagThresholdSeconds] is the "silent/short" cutoff: an incoming call from a
 * number not in your contacts lasting no longer than this is flagged as the
 * silent-stranger harassment pattern.
 */
/** How the app decides light vs. dark: follow the phone, or force one. */
enum class ThemeMode(val label: String) {
    System("System"),
    Light("Light"),
    Dark("Dark"),
}

object SettingsStore {

    private const val PREFS = "settings"
    private const val KEY_FLAG = "flag_threshold_seconds"
    private const val KEY_THEME = "theme_mode"

    const val DEFAULT_FLAG_SECONDS = 15
    const val MIN_FLAG_SECONDS = 5
    const val MAX_FLAG_SECONDS = 60

    fun flagThresholdSeconds(context: Context): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt(KEY_FLAG, DEFAULT_FLAG_SECONDS)

    fun setFlagThresholdSeconds(context: Context, seconds: Int) {
        val clamped = seconds.coerceIn(MIN_FLAG_SECONDS, MAX_FLAG_SECONDS)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putInt(KEY_FLAG, clamped).apply()
    }

    fun themeMode(context: Context): ThemeMode {
        val name = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_THEME, ThemeMode.System.name)
        return runCatching { ThemeMode.valueOf(name!!) }.getOrDefault(ThemeMode.System)
    }

    fun setThemeMode(context: Context, mode: ThemeMode) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_THEME, mode.name).apply()
    }
}
