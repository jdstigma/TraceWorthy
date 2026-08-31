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
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.traceworthy.app.ui.CGCard
import com.traceworthy.app.ui.SectionHeader

@Composable
fun FraudItemsScreen(
    case: Case,
    onSave: (Case) -> Unit,
) {
    var editing by remember { mutableStateOf<FraudItem?>(null) }
    var confirmDelete by remember { mutableStateOf<FraudItem?>(null) }

    LazyColumn(Modifier.fillMaxSize().padding(16.dp)) {
        item {
            Text("Fraudulent accounts", fontSize = 22.sp, fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground)
            Spacer(Modifier.height(2.dp))
            Text(
                "Every account, charge, or misuse opened in your name. Each one becomes a line in your " +
                    "evidence summary and its own dispute letter.",
                fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
        }
        items(case.fraudItems) { item ->
            CGCard(Modifier.clickable { editing = item }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "${item.kind.label} — ${item.institution.ifBlank { "unknown institution" }}",
                            fontWeight = FontWeight.SemiBold, fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            listOfNotNull(
                                item.accountRef.ifBlank { null }?.let { "ref $it" },
                                item.amount.ifBlank { null },
                                item.discoveredDate.ifBlank { null },
                                item.status.label,
                            ).joinToString(" · "),
                            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { confirmDelete = item }) {
                        Icon(Icons.Filled.DeleteOutline, contentDescription = "Delete")
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
        item {
            OutlinedButton(
                onClick = { editing = FraudItem() },
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Text("  Add a fraudulent account", fontWeight = FontWeight.SemiBold)
            }
        }
    }

    editing?.let { item ->
        FraudItemDialog(
            item = item,
            onDismiss = { editing = null },
            onSave = { updated ->
                val list = case.fraudItems.toMutableList()
                val i = list.indexOfFirst { it.id == updated.id }
                if (i >= 0) list[i] = updated else list.add(updated)
                onSave(case.withFraudItems(list))
                editing = null
            },
        )
    }

    confirmDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Remove this item?") },
            text = { Text("${item.kind.label} — ${item.institution.ifBlank { "unknown" }} will be removed from this case.") },
            confirmButton = {
                TextButton(onClick = {
                    onSave(case.withFraudItems(case.fraudItems.filterNot { it.id == item.id }))
                    confirmDelete = null
                }) { Text("Remove") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun FraudItemDialog(
    item: FraudItem,
    onDismiss: () -> Unit,
    onSave: (FraudItem) -> Unit,
) {
    var kind by remember { mutableStateOf(item.kind) }
    var institution by remember { mutableStateOf(item.institution) }
    var accountRef by remember { mutableStateOf(item.accountRef) }
    var amount by remember { mutableStateOf(item.amount) }
    var discovered by remember { mutableStateOf(item.discoveredDate) }
    var status by remember { mutableStateOf(item.status) }
    var note by remember { mutableStateOf(item.note) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (item.institution.isBlank() && item.accountRef.isBlank()) "Add account" else "Edit account") },
        text = {
            Column {
                SectionHeader("Kind")
                Spacer(Modifier.height(6.dp))
                Row(Modifier.horizontalScroll(rememberScrollState())) {
                    FraudKind.entries.forEach { k ->
                        FilterChip(
                            selected = kind == k,
                            onClick = { kind = k },
                            label = { Text(k.label) },
                            modifier = Modifier.padding(end = 6.dp),
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                DlgField("Institution (Chase, the hospital, IRS…)", institution) { institution = it }
                DlgField("Account ref / last 4", accountRef) { accountRef = it }
                DlgField("Amount (e.g. \$1,240 or unknown)", amount) { amount = it }
                DlgField("Discovered (date)", discovered) { discovered = it }
                Spacer(Modifier.height(8.dp))
                SectionHeader("Status")
                Spacer(Modifier.height(6.dp))
                Row(Modifier.horizontalScroll(rememberScrollState())) {
                    FraudStatus.entries.forEach { s ->
                        FilterChip(
                            selected = status == s,
                            onClick = { status = s },
                            label = { Text(s.label) },
                            modifier = Modifier.padding(end = 6.dp),
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                DlgField("Note (optional)", note) { note = it }
            }
        },
        confirmButton = {
            Button(onClick = {
                onSave(item.copy(
                    kind = kind, institution = institution.trim(), accountRef = accountRef.trim(),
                    amount = amount.trim(), discoveredDate = discovered.trim(), status = status,
                    note = note.trim(),
                ))
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun DlgField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
    )
}
