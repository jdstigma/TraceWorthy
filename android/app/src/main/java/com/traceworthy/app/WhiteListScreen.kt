package com.traceworthy.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.traceworthy.app.ui.CGCard
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** One row on the white list: a single unsaved number, or a named caller group. */
private data class WhiteListRow(
    val key: String,             // branch name, or the number itself
    val label: String,           // what we show
    val isBranch: Boolean,
    val numbers: List<String>,   // member numbers (size 1 unless a branch)
    val total: Int,
    val patternHits: Int,        // calls that fit the harassment pattern (ignores the list)
    val lastSeen: Long,
)

private fun whiteListRows(entries: List<CallEntry>, branches: Map<String, String>): List<WhiteListRow> =
    entries
        .filter { it.cachedName.isNullOrBlank() }        // contacts are already excluded from flagging
        .groupBy { branches[it.number] ?: it.number }
        .map { (key, calls) ->
            val numbers = calls.map { it.number }.distinct()
            val isBranch = numbers.size > 1 || branches[numbers.firstOrNull().orEmpty()] == key
            WhiteListRow(
                key = key,
                label = key,
                isBranch = isBranch,
                numbers = numbers,
                total = calls.size,
                patternHits = calls.count { it.matchesHarassmentPattern },
                lastSeen = calls.maxOf { it.timestampMillis },
            )
        }
        .filter { it.total >= 2 }                        // a friend calls more than once
        .sortedByDescending { it.total }

@Composable
fun WhiteListScreen(
    entries: List<CallEntry>,
    onKnownCallersChanged: () -> Unit = {},
) {
    val context = LocalContext.current
    var version by remember { mutableStateOf(0) }
    val branches = remember(entries) { BranchStore.all(context) }
    val safeKeys = remember(version, entries) { SafeNumberStore.matchKeys(context) }
    val rows = remember(entries, branches) { whiteListRows(entries, branches) }
    val singleCallHidden = remember(entries, branches) {
        entries.filter { it.cachedName.isNullOrBlank() }
            .groupBy { branches[it.number] ?: it.number }
            .count { it.value.size == 1 }
    }

    fun isWhitelisted(row: WhiteListRow) = row.numbers.all { SafeNumberStore.key(it) in safeKeys }
    val marked = rows.count { isWhitelisted(it) }

    LazyColumn(Modifier.fillMaxSize().padding(16.dp)) {
        item {
            Text(
                "White list",
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                "Numbers that aren't in your contacts, most calls first. Flip the switch for anyone " +
                    "you actually know — a friend or relative who just never got saved. Their calls " +
                    "are then left out of the flagged pattern, the analysis, the CSV, and every " +
                    "document.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            if (rows.isNotEmpty()) {
                Text(
                    "$marked of ${rows.size} marked as known callers.",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            }
        }

        if (rows.isEmpty()) {
            item {
                CGCard {
                    Text(
                        "Nothing to review yet",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Once a number that isn't in your contacts has called you more than once, " +
                            "it shows up here so you can mark it a known caller if you recognize it.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            items(rows, key = { it.key }) { row ->
                WhiteListItem(
                    row = row,
                    whitelisted = isWhitelisted(row),
                    onToggle = { on ->
                        if (on) SafeNumberStore.addAll(context, row.numbers)
                        else row.numbers.forEach { SafeNumberStore.removeByNumber(context, it) }
                        version++
                        onKnownCallersChanged()
                    },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            }
            if (singleCallHidden > 0) {
                item {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "$singleCallHidden number${if (singleCallHidden == 1) "" else "s"} that called " +
                            "only once ${if (singleCallHidden == 1) "is" else "are"} hidden — a single " +
                            "call is the signature of a spoofed harasser, not a friend.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun WhiteListItem(
    row: WhiteListRow,
    whitelisted: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    val fmt = remember { SimpleDateFormat("MMM d, yyyy", Locale.US) }
    Row(
        Modifier.fillMaxWidth().padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (row.isBranch) {
                    Icon(
                        Icons.Filled.Folder,
                        contentDescription = "Caller group",
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    row.label,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    (if (row.isBranch) "${row.numbers.size} numbers · " else "") +
                        "${row.total} calls · last ${fmt.format(Date(row.lastSeen))}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (row.patternHits > 0 && !whitelisted) {
                Text(
                    "${row.patternHits} match the harassment pattern",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Switch(checked = whitelisted, onCheckedChange = onToggle)
            Text(
                if (whitelisted) "known" else "flag",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
