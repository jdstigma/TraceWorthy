package com.traceworthy.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.traceworthy.app.ui.CGCard

@Composable
fun CaseListScreen(
    cases: List<Case>,
    activeId: String?,
    onOpen: (Case) -> Unit,
    onNew: () -> Unit,
    onDelete: (Case) -> Unit,
) {
    var confirmDelete by remember { mutableStateOf<Case?>(null) }

    LazyColumn(Modifier.fillMaxSize().padding(16.dp)) {
        item {
            Text(
                "Cases",
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                "One case per problem you're documenting. Open a case to work it; switch between " +
                    "them from the title bar.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
        }
        items(cases) { case ->
            CGCard(Modifier.clickable { onOpen(case) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(case.type.icon, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                    Spacer(Modifier.height(0.dp))
                    Column(Modifier.weight(1f).padding(start = 10.dp)) {
                        Text(
                            case.title,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            case.type.displayName + if (case.id == activeId) "  ·  active" else "",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (cases.size > 1) {
                        IconButton(onClick = { confirmDelete = case }) {
                            Icon(Icons.Filled.DeleteOutline, contentDescription = "Delete case")
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
        item {
            OutlinedButton(
                onClick = onNew,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(Modifier.height(0.dp))
                Text("  New case", fontWeight = FontWeight.SemiBold)
            }
        }
    }

    confirmDelete?.let { c ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Delete this case?") },
            text = {
                Text(
                    "\"${c.title}\" and its filing numbers will be removed. Your call log, notes, " +
                        "and any PDFs you already saved are not affected."
                )
            },
            confirmButton = {
                TextButton(onClick = { onDelete(c); confirmDelete = null }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Cancel") } },
        )
    }
}

/** Pick a type for a new case. Types at their instance cap are disabled. */
@Composable
fun NewCaseScreen(
    existing: List<Case>,
    onCreate: (CaseType) -> Unit,
) {
    LazyColumn(Modifier.fillMaxSize().padding(16.dp)) {
        item {
            Text(
                "New case",
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                "What are you documenting?",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
        }
        items(CaseType.entries) { type ->
            val atCap = existing.count { it.type == type } >= type.maxInstances
            CGCard(if (atCap) Modifier else Modifier.clickable { onCreate(type) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(type.icon, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                    Column(Modifier.weight(1f).padding(start = 10.dp)) {
                        Text(
                            type.displayName,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            if (atCap) "You already have this case" else type.blurb,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}
