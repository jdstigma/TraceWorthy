package com.traceworthy.app

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.traceworthy.app.ui.CGCard
import com.traceworthy.app.ui.SectionHeader

/**
 * Per-case settings: the case title plus everything specific to this case's type.
 * For a phone-harassment case that's the affected number, carrier, harassment
 * type, and the FCC / police / carrier confirmation numbers.
 */
@Composable
fun CaseDetailScreen(
    case: Case,
    myInfo: MyInfo,
    onSave: (Case) -> Unit,
) {
    val context = LocalContext.current
    var title by remember(case.id) { mutableStateOf(case.title) }
    var affectedNumber by remember(case.id) { mutableStateOf(case.affectedNumber) }
    var carrier by remember(case.id) { mutableStateOf(case.carrier) }
    var harassmentType by remember(case.id) { mutableStateOf(case.harassmentType) }
    var fccNo by remember(case.id) { mutableStateOf(case.filing("fcc")) }
    var policeNo by remember(case.id) { mutableStateOf(case.filing("police")) }
    var carrierNo by remember(case.id) { mutableStateOf(case.filing("carrier")) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)
    ) {
        Text(
            "Case details",
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            "Everything specific to this case. Your name and contact details are shared — edit " +
                "those under My info.",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))

        CGCard {
            SectionHeader("This case")
            Spacer(Modifier.height(12.dp))
            Field("Case name", title) { title = it }
            Field("Affected number (the line getting the calls)", affectedNumber) { affectedNumber = it }
            Text(
                "Leave blank if it's the same as your contact phone (${myInfo.phone.ifBlank { "set in My info" }}).",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Field("Carrier (e.g. Verizon)", carrier) { carrier = it }
        }
        Spacer(Modifier.height(16.dp))

        CGCard {
            SectionHeader("Type of harassment")
            Spacer(Modifier.height(4.dp))
            Text(
                "This tailors the wording of your documents. Pick \"Aggressive\" if calls involve " +
                    "threats or abuse — that leans the documents on the incident timeline.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Row {
                HarassmentType.entries.forEach { type ->
                    FilterChip(
                        selected = harassmentType == type,
                        onClick = { harassmentType = type },
                        label = { Text(type.shortLabel) },
                        modifier = Modifier.padding(end = 8.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(16.dp))

        CGCard {
            SectionHeader("Case numbers")
            Spacer(Modifier.height(4.dp))
            Text(
                "Fill these in as you file each one — they cross-reference into your documents. " +
                    "The storyboard also lets you enter them step by step.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Field("Carrier case #", carrierNo) { carrierNo = it }
            Field("FCC complaint #", fccNo) { fccNo = it }
            Field("Police case #", policeNo) { policeNo = it }
        }
        Spacer(Modifier.height(20.dp))

        Button(
            onClick = {
                onSave(
                    case.copy(
                        title = title.trim().ifBlank { case.type.displayName },
                        affectedNumber = affectedNumber.trim(),
                        carrier = carrier.trim(),
                        harassmentType = harassmentType,
                    ).withFiling("carrier", carrierNo)
                        .withFiling("fcc", fccNo)
                        .withFiling("police", policeNo)
                )
                Toast.makeText(context, "Saved", Toast.LENGTH_SHORT).show()
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
        ) { Text("Save", fontWeight = FontWeight.SemiBold) }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun Field(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    )
}
