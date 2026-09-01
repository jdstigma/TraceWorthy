package com.traceworthy.app

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.traceworthy.app.ui.SeverityBadge
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun CallLogScreen(
    entries: List<CallEntry>,
    onRefresh: () -> Unit,
    onKnownCallersChanged: () -> Unit = {},
) {
    val context = LocalContext.current
    var editing by remember { mutableStateOf<CallEntry?>(null) }

    Column(Modifier.padding(16.dp).fillMaxSize()) {
        val suspicious = entries.count { it.isSuspicious }
        val known = entries.count { it.isSafeListed }
        Text(
            "${entries.size} calls logged · $suspicious flagged as suspicious" +
                if (known > 0) " · $known from known callers" else "",
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(8.dp))
        Row {
            Button(onClick = onRefresh, shape = RoundedCornerShape(10.dp)) { Text("Refresh") }
            Spacer(Modifier.width(8.dp))
            OutlinedButton(
                onClick = {
                    // Known callers are excluded from the evidence CSV, same as the documents.
                    val where = CsvExporter.export(context, entries.filterNot { it.isSafeListed })
                    Toast.makeText(context, "Saved: $where", Toast.LENGTH_LONG).show()
                },
                shape = RoundedCornerShape(10.dp),
            ) { Text("Export CSV") }
        }
        Text(
            "Tip: tap a call to add a note, tag how serious it was, or mark the number a known caller",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp)
        )
        Spacer(Modifier.height(8.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        LazyColumn(Modifier.fillMaxSize()) {
            items(entries) { entry -> CallRow(entry) { editing = entry } }
        }
    }

    editing?.let { entry ->
        NoteDialog(
            entry = entry,
            onDismiss = { editing = null },
            onSave = { text, severity, markKnown ->
                NotesStore.set(context, entry.id, text)
                NotesStore.setSeverity(context, entry.id, severity)
                val knownChanged = markKnown != entry.isSafeListed
                if (knownChanged) {
                    if (markKnown) SafeNumberStore.add(context, entry.number)
                    else SafeNumberStore.removeByNumber(context, entry.number)
                }
                editing = null
                if (knownChanged) onKnownCallersChanged() else onRefresh()
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun NoteDialog(
    entry: CallEntry,
    onDismiss: () -> Unit,
    onSave: (String, Severity, Boolean) -> Unit,
) {
    var text by remember { mutableStateOf(entry.note ?: "") }
    var severity by remember { mutableStateOf(entry.severity) }
    var known by remember { mutableStateOf(entry.isSafeListed) }
    val who = entry.cachedName?.takeIf { it.isNotBlank() } ?: entry.number
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Note for $who") },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("e.g. silent, hung up after 20s") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                Text("How serious was it?", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                FlowRow {
                    listOf(Severity.Silent, Severity.Spoken, Severity.Threatening).forEach { s ->
                        FilterChip(
                            selected = severity == s,
                            onClick = { severity = if (severity == s) Severity.Unset else s },
                            label = { Text(s.label) },
                            modifier = Modifier.padding(end = 8.dp),
                        )
                    }
                }
                if (!entry.isKnownContact) {
                    Spacer(Modifier.height(12.dp))
                    FilterChip(
                        selected = known,
                        onClick = { known = !known },
                        label = { Text("Known caller (a friend, not harassment)") },
                    )
                    Text(
                        "Excludes every call from this number from the flagged pattern, the " +
                            "analysis, the CSV, and the documents. It still shows here in the log.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(text, severity, known) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun CallRow(entry: CallEntry, onClick: () -> Unit) {
    val fmt = remember { SimpleDateFormat("MMM d, HH:mm:ss", Locale.US) }
    val bg = when {
        entry.isSuspicious -> MaterialTheme.colorScheme.errorContainer
        entry.isSafeListed -> MaterialTheme.colorScheme.surfaceVariant
        else -> MaterialTheme.colorScheme.surface
    }
    Column(
        Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .background(bg)
            .padding(vertical = 8.dp, horizontal = 4.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                entry.cachedName?.takeIf { it.isNotBlank() } ?: entry.number,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                SeverityBadge(entry.severity)
                if (entry.isSuspicious) {
                    Spacer(Modifier.width(6.dp))
                    Text("⚠ flagged", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                } else if (entry.isSafeListed) {
                    Spacer(Modifier.width(6.dp))
                    Text("✓ known caller", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                }
            }
        }
        Text(
            "${fmt.format(Date(entry.timestampMillis))} · ${entry.typeLabel} · ${entry.durationSeconds}s",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        entry.note?.let { note ->
            val danger = MaterialTheme.colorScheme.error
            Text(
                buildAnnotatedString {
                    append("Note: ")
                    append(ThreatHighlight.annotate(note, danger))
                },
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.tertiary,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}
