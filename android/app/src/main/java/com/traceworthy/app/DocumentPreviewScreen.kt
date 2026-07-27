package com.traceworthy.app

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
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * Full-screen preview/editor shown before a document is turned into a PDF. Text blocks
 * become editable fields; tables and charts appear as read-only chips (they are drawn
 * from the call data at render time). Tapping Generate applies the edits and writes the PDF.
 */
@Composable
fun DocumentPreviewDialog(
    doc: EditableDocument,
    generateLabel: String,
    onDismiss: () -> Unit,
    onGenerated: (DocumentGenerator.Result) -> Unit,
) {
    val context = LocalContext.current
    val items = remember(doc) { doc.items() }
    val edits = remember(doc) { mutableStateMapOf<Int, String>() }

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
                            "Review the document and edit any text before it becomes a PDF. " +
                                "Tables and charts (shown as chips) are drawn from your call data and " +
                                "aren't edited here.",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(14.dp))
                    }
                    items(items, key = { it.index }) { item ->
                        PreviewRow(item, edits)
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
                                edits.forEach { (i, t) -> doc.updateText(i, t) }
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
private fun PreviewRow(item: PreviewItem, edits: SnapshotStateMap<Int, String>) {
    if (!item.editable) {
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
                Text(
                    item.label,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        return
    }

    val value = edits[item.index] ?: item.text
    val emphasized = item.kind == PreviewKind.Title || item.kind == PreviewKind.Heading
    Column {
        Text(
            item.label.uppercase(),
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.tertiary,
        )
        Spacer(Modifier.height(2.dp))
        OutlinedTextField(
            value = value,
            onValueChange = { edits[item.index] = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = item.kind == PreviewKind.Title,
            textStyle = LocalTextStyle.current.copy(
                fontSize = when (item.kind) {
                    PreviewKind.Title -> 18.sp
                    PreviewKind.Heading -> 15.sp
                    else -> 14.sp
                },
                fontWeight = if (emphasized) FontWeight.SemiBold else FontWeight.Normal,
            ),
        )
    }
}
