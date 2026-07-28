package com.traceworthy.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.traceworthy.app.ui.CGCard

/**
 * Full-screen preview/editor shown before a document is turned into a PDF. Content is
 * grouped into collapsible sections that mirror the document's own headings. Only body
 * paragraphs and list items are editable; list sections (e.g. caller numbers) get "+ Add"
 * and a "–" per item so the list can be curated. Titles, stats and charts stay auto.
 */
@Composable
fun DocumentPreviewDialog(
    doc: EditableDocument,
    generateLabel: String,
    onDismiss: () -> Unit,
    onGenerated: (DocumentGenerator.Result) -> Unit,
) {
    val context = LocalContext.current
    var sections by remember(doc) { mutableStateOf(doc.sections()) }
    val edits = remember(doc) { mutableStateMapOf<Long, String>() }
    val expandedMap = remember(doc) { mutableStateMapOf<Long, Boolean>() }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize()) {
                Surface(color = MaterialTheme.colorScheme.primary) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Filled.Close, contentDescription = "Close preview", tint = MaterialTheme.colorScheme.onPrimary)
                        }
                        Text(
                            "Preview & edit",
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }

                LazyColumn(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                ) {
                    item {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "Tap a section to expand it. Edit the wording or curate lists (add or " +
                                "remove items). Statistics and charts are drawn from your call data " +
                                "and stay as-is.",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(12.dp))
                    }
                    items(sections, key = { it.id }) { section ->
                        SectionCard(
                            section = section,
                            edits = edits,
                            expanded = expandedMap[section.id] == true,
                            onToggle = { expandedMap[section.id] = (expandedMap[section.id] != true) },
                            onAdd = {
                                doc.addBulletInSection(section.id)
                                sections = doc.sections()
                                expandedMap[section.id] = true
                            },
                            onRemove = { id ->
                                doc.removeRow(id)
                                edits.remove(id)
                                sections = doc.sections()
                            },
                        )
                        Spacer(Modifier.height(10.dp))
                    }
                    item { Spacer(Modifier.height(8.dp)) }
                }

                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 3.dp,
                    shadowElevation = 8.dp,
                ) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        Button(
                            onClick = {
                                edits.forEach { (id, t) -> doc.updateText(id, t) }
                                onGenerated(doc.render(context))
                            },
                            shape = RoundedCornerShape(10.dp),
                        ) {
                            Icon(Icons.Filled.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(generateLabel)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionCard(
    section: EditSection,
    edits: SnapshotStateMap<Long, String>,
    expanded: Boolean,
    onToggle: () -> Unit,
    onAdd: () -> Unit,
    onRemove: (Long) -> Unit,
) {
    CGCard {
        Row(
            Modifier.fillMaxWidth().clickable { onToggle() },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                contentDescription = if (expanded) "Collapse" else "Expand",
                tint = MaterialTheme.colorScheme.tertiary,
            )
            Spacer(Modifier.width(6.dp))
            Text(
                section.title,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            val editableCount = section.rows.count { it.editable }
            if (!expanded && editableCount > 0) {
                Text(
                    "$editableCount editable",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
        }

        if (expanded) {
            Spacer(Modifier.height(8.dp))
            section.rows.forEach { row ->
                RowEditor(row, edits, onRemove)
                Spacer(Modifier.height(8.dp))
            }
            if (section.canAddBullet) {
                TextButton(
                    onClick = onAdd,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.tertiary),
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Add item")
                }
            }
        }
    }
}

@Composable
private fun RowEditor(
    row: EditRow,
    edits: SnapshotStateMap<Long, String>,
    onRemove: (Long) -> Unit,
) {
    // Structural rows (tables/charts) — read-only info chip.
    if (row.kind == PreviewKind.Structural) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(8.dp),
        ) {
            Row(
                Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Filled.BarChart,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(row.label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        return
    }

    // Editable — the narrative "Notes" field, or a user-added list item.
    if (row.editable) {
        val value = edits[row.id] ?: row.text
        Column {
            Text(
                row.label.uppercase(),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.tertiary,
            )
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = value,
                    onValueChange = { edits[row.id] = it },
                    modifier = Modifier.weight(1f),
                    singleLine = false,
                    textStyle = LocalTextStyle.current.copy(fontSize = 14.sp),
                )
                if (row.removable) {
                    IconButton(onClick = { onRemove(row.id) }) {
                        Icon(Icons.Filled.RemoveCircleOutline, contentDescription = "Remove item", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
        return
    }

    // Read-only body/list item — shown for context; lists still get a "–" to filter.
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (row.kind == PreviewKind.Bullet) {
            Text("•  ", fontSize = 14.sp, color = MaterialTheme.colorScheme.tertiary)
        }
        Text(
            row.text,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        if (row.removable) {
            IconButton(onClick = { onRemove(row.id) }) {
                Icon(Icons.Filled.RemoveCircleOutline, contentDescription = "Remove item", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}
