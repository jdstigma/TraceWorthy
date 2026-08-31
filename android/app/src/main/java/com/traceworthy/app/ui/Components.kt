package com.traceworthy.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.traceworthy.app.Severity
import com.traceworthy.app.StageStatus
import com.traceworthy.app.ui.theme.Teal
import com.traceworthy.app.ui.theme.TealDeep
import com.traceworthy.app.ui.theme.Coral
import com.traceworthy.app.ui.theme.CoralDeep

/** Small colored pill showing a call's severity tag. Renders nothing when Unset. */
@Composable
fun SeverityBadge(severity: Severity, modifier: Modifier = Modifier) {
    if (severity == Severity.Unset) return
    val (bg, fg) = when (severity) {
        Severity.Silent -> Color(0xFFE7EAF0) to Color(0xFF4A5563)
        Severity.Spoken -> Color(0xFFFAEEDA) to Color(0xFF854F0B)
        Severity.Threatening -> Color(0xFFFCEBEB) to Color(0xFFA32D2D)
        Severity.Unset -> return
    }
    Surface(color = bg, shape = RoundedCornerShape(6.dp), modifier = modifier) {
        Text(
            severity.label,
            color = fg,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

/** Bold section heading with a coral accent bar, to break long screens into scannable blocks. */
@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Surface(
            color = MaterialTheme.colorScheme.tertiary,
            shape = RoundedCornerShape(2.dp),
            modifier = Modifier.size(width = 4.dp, height = 18.dp),
        ) {}
        Spacer(Modifier.width(8.dp))
        Text(
            title,
            fontWeight = FontWeight.SemiBold,
            fontSize = 18.sp,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}

/** White, softly-rounded surface card — the default container on every screen. */
@Composable
fun CGCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(16.dp)) { content() }
    }
}

/** A compact metric tile: big number over a small label. */
@Composable
fun StatTile(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    highlight: Boolean = false,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Text(
                value,
                fontSize = 26.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (highlight) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                label,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Small status pill for a storyboard stage. */
@Composable
fun StageStatusChip(status: StageStatus, modifier: Modifier = Modifier) {
    val (label, bg, fg) = when (status) {
        StageStatus.Done -> Triple("Done", Color(0xFFE3F3EE), Color(0xFF1B7A63))
        StageStatus.InProgress -> Triple("In progress", Color(0xFFFAEEDA), Color(0xFF854F0B))
        StageStatus.NotStarted -> Triple("Not started", Color(0xFFECEEF2), Color(0xFF5B6472))
    }
    Surface(color = bg, shape = RoundedCornerShape(6.dp), modifier = modifier) {
        Text(
            label,
            color = fg,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

/**
 * One storyboard step. Numbered, status-chipped, with the action button and an
 * optional inline "confirmation #" field for filing stages.
 */
@Composable
fun StageCard(
    index: Int,
    title: String,
    summary: String,
    status: StageStatus,
    actionLabel: String,
    onAction: () -> Unit,
    filingValue: String? = null,
    onFilingChange: ((String) -> Unit)? = null,
    filingLabel: String = "Confirmation / case #",
) {
    CGCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                shape = CircleShape,
                color = if (status == StageStatus.Done) Teal else MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.size(26.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        if (status == StageStatus.Done) "✓" else index.toString(),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (status == StageStatus.Done) TealDeep else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            Text(
                title,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            StageStatusChip(status)
        }
        Spacer(Modifier.height(6.dp))
        Text(summary, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (filingValue != null && onFilingChange != null) {
            Spacer(Modifier.height(8.dp))
            androidx.compose.material3.OutlinedTextField(
                value = filingValue,
                onValueChange = onFilingChange,
                label = { Text(filingLabel) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Button(
                onClick = onAction,
                colors = ButtonDefaults.buttonColors(containerColor = Teal, contentColor = TealDeep),
                shape = RoundedCornerShape(10.dp),
            ) { Text(actionLabel, fontWeight = FontWeight.SemiBold) }
        }
    }
}

/**
 * A "next step" action card (navy background, teal button) used on Home to guide
 * the user through the documents they need to file. Shows a done state once the
 * matching case number is on file.
 */
@Composable
fun ActionCard(
    header: String,
    title: String,
    subtitle: String,
    buttonText: String,
    icon: ImageVector,
    onClick: () -> Unit,
    done: Boolean = false,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Coral,
                    modifier = Modifier.size(36.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(icon, contentDescription = null, tint = CoralDeep, modifier = Modifier.size(20.dp))
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        header,
                        color = Coral,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        title,
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                subtitle,
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f),
                fontSize = 13.sp,
            )
            Spacer(Modifier.height(14.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (done) {
                    Text("On file ✓", color = Coral, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                } else {
                    Spacer(Modifier.width(1.dp))
                }
                Button(
                    onClick = onClick,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Teal,
                        contentColor = TealDeep,
                    ),
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Text(buttonText, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
