package com.traceworthy.app

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle

/**
 * Highlights genuine threat / safety words inside a free-text note so they stand out
 * where notes are shown. Deliberately conservative — a short, curated list of clear
 * danger terms, whole-word and case-insensitive — to guard against crying wolf on
 * ordinary notes. This is a visual aid only; it makes no legal judgement.
 */
object ThreatHighlight {

    // Curated danger terms. "address" / "doxx" flag doxxing; the rest are violence or
    // death. Kept intentionally small so ordinary notes don't light up.
    private val TERMS = listOf(
        "kill", "killed", "kills",
        "shoot", "shot", "gun", "guns", "weapon", "weapons",
        "stab", "stabbed", "knife",
        "rape", "raped",
        "beat", "beaten", "hurt", "harm",
        "threat", "threats", "threaten", "threatened", "threatening",
        "die", "dead", "death",
        "bomb", "burn",
        "address", "doxx", "doxxed",
    )

    private val pattern = Regex(
        "\\b(" + TERMS.joinToString("|") { Regex.escape(it) } + ")\\b",
        RegexOption.IGNORE_CASE,
    )

    /** True if the note contains any danger term. */
    fun hasMatch(text: String?): Boolean = text != null && pattern.containsMatchIn(text)

    /** [text] with every danger term styled in [highlight] (semibold); rest left as-is. */
    fun annotate(text: String, highlight: Color): AnnotatedString = buildAnnotatedString {
        var last = 0
        pattern.findAll(text).forEach { m ->
            append(text.substring(last, m.range.first))
            withStyle(SpanStyle(color = highlight, fontWeight = FontWeight.SemiBold)) {
                append(text.substring(m.range.first, m.range.last + 1))
            }
            last = m.range.last + 1
        }
        append(text.substring(last))
    }
}
