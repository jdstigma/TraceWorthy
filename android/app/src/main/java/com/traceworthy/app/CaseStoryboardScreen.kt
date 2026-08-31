package com.traceworthy.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.traceworthy.app.ui.CGCard
import com.traceworthy.app.ui.StageCard
import com.traceworthy.app.ui.StatTile

/**
 * A case's guided workflow — the ordered stages, each with a live status derived
 * from the case's state. This is where the app lands.
 */
@Composable
fun CaseStoryboardScreen(
    case: Case,
    myInfo: MyInfo,
    entries: List<CallEntry>,
    granted: Boolean,
    onNavigate: (CaseScreen) -> Unit,
    onCaseChange: (Case) -> Unit,
) {
    val ctx = StageContext(case = case, myInfo = myInfo, granted = granted, entries = entries)
    val stages = case.type.storyboard()
    val isPhone = case.type == CaseType.PhoneHarassment
    val stats = remember(entries) { if (isPhone) CallStats.from(entries) else null }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)
    ) {
        Text(
            case.title,
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            "Work down the list. Each step explains what it does and where to go next.",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))

        if (stats != null) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile(stats.totalCalls.toString(), "calls logged", Modifier.weight(1f))
                StatTile(stats.flaggedCalls.toString(), "flagged", Modifier.weight(1f), highlight = stats.flaggedCalls > 0)
                StatTile(stats.uniqueNumbers.toString(), "numbers", Modifier.weight(1f))
            }
            Spacer(Modifier.height(20.dp))
        }

        stages.forEachIndexed { i, stage ->
            val filingValue = stage.filingKey?.let { case.filing(it) }
            StageCard(
                index = i + 1,
                title = stage.title,
                summary = stage.summary,
                status = stage.status(ctx),
                actionLabel = stage.actionLabel,
                onAction = { onNavigate(stage.destination) },
                filingValue = filingValue,
                onFilingChange = stage.filingKey?.let { key ->
                    { v: String -> onCaseChange(case.withFiling(key, v)) }
                },
                filingLabel = when (stage.filingKey) {
                    "carrier" -> "Carrier case #"
                    "fcc" -> "FCC complaint #"
                    "police" -> "Police case #"
                    else -> "Confirmation #"
                },
            )
            Spacer(Modifier.height(12.dp))
        }

        Spacer(Modifier.height(8.dp))
        CGCard {
            Text(
                "How this works",
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "An app can only see the number the network delivered — which for a spoofed call " +
                    "is fake. It cannot reveal who is really calling. TraceWorthy turns your call log " +
                    "into court-ready evidence so your carrier and the police can trace the real " +
                    "origin. Read the full walkthrough under Learn.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(8.dp))
    }
}
