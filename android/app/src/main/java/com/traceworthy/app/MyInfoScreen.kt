package com.traceworthy.app

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
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

@Composable
fun MyInfoScreen(
    info: MyInfo,
    onSave: (MyInfo) -> Unit,
) {
    val context = LocalContext.current
    var fullName by remember { mutableStateOf(info.fullName) }
    var phone by remember { mutableStateOf(info.phone) }
    var email by remember { mutableStateOf(info.email) }
    var city by remember { mutableStateOf(info.addressCity) }
    var state by remember { mutableStateOf(info.state) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)
    ) {
        Text(
            "My info",
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            "Your identity — used in every case's documents. Case-specific details (the " +
                "affected number, carrier, and case numbers) live on each case. Stored only on " +
                "this phone.",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))

        CGCard {
            SectionHeader("About you")
            Spacer(Modifier.height(12.dp))
            Field("Full name", fullName) { fullName = it }
            Field("Contact phone (where officials reach you)", phone) { phone = it }
            Field("Email", email) { email = it }
            Field("City", city) { city = it }
            Field("State (2-letter, e.g. CA)", state) { state = it }
        }
        Spacer(Modifier.height(20.dp))

        Button(
            onClick = {
                onSave(
                    MyInfo(
                        fullName = fullName.trim(),
                        phone = phone.trim(),
                        email = email.trim(),
                        addressCity = city.trim(),
                        state = state.trim().uppercase(),
                    )
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
