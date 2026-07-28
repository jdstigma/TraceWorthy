package com.traceworthy.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** A wedge of the pie / a segment for the legend. */
data class Slice(val label: String, val value: Int, val color: Color)

/** One horizontal bar. */
data class Bar(val label: String, val value: Int, val color: Color)

/** Simple pie chart drawn with Compose Canvas — no external library. */
@Composable
fun PieChart(slices: List<Slice>, modifier: Modifier = Modifier) {
    val total = slices.sumOf { it.value }.toFloat()
    Canvas(modifier) {
        var start = -90f
        slices.forEach { s ->
            val sweep = if (total > 0f) 360f * (s.value / total) else 0f
            drawArc(
                color = s.color,
                startAngle = start,
                sweepAngle = sweep,
                useCenter = true,
            )
            start += sweep
        }
    }
}

/** Colored-square legend to sit beside a pie chart. */
@Composable
fun ChartLegend(slices: List<Slice>) {
    Column {
        slices.forEach { s ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(vertical = 2.dp),
            ) {
                Box(Modifier.size(12.dp).background(s.color))
                Spacer(Modifier.width(8.dp))
                Text("${s.label}: ${s.value}", fontSize = 13.sp)
            }
        }
    }
}

/** One call plotted on the time scatter, pre-colored by its number. */
data class ScatterPoint(val timeMillis: Long, val color: Color)

/**
 * Scatter of calls across dates (x) and time of day (y). Each dot is one call, colored by
 * which top-5 number it came from (see [legend]). Reveals overnight clustering and bursts.
 */
@Composable
fun ScatterChart(
    points: List<ScatterPoint>,
    axisColor: Color,
    labelColor: Color,
    modifier: Modifier = Modifier,
) {
    if (points.isEmpty()) {
        Text("No calls in the last 90 days.", color = labelColor)
        return
    }
    val dateFmt = remember { SimpleDateFormat("MMM d", Locale.US) }
    val minT = points.minOf { it.timeMillis }
    val maxT = points.maxOf { it.timeMillis }
    val span = (maxT - minT).coerceAtLeast(1L).toFloat()
    val plotted = remember(points) {
        points.map { p ->
            val cal = Calendar.getInstance().apply { timeInMillis = p.timeMillis }
            val hourFrac = (cal.get(Calendar.HOUR_OF_DAY) + cal.get(Calendar.MINUTE) / 60f) / 24f
            Triple((p.timeMillis - minT).toFloat() / span, hourFrac, p.color)
        }
    }

    Column(modifier) {
        Row(Modifier.fillMaxWidth().height(180.dp)) {
            Column(
                Modifier.width(30.dp).fillMaxHeight(),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                // top = midnight (next day) down to midnight — matches y = 1 - hourFrac
                listOf("12a", "6p", "12p", "6a", "12a").forEach {
                    Text(it, fontSize = 10.sp, color = labelColor)
                }
            }
            Spacer(Modifier.width(6.dp))
            Canvas(Modifier.weight(1f).fillMaxHeight()) {
                val w = size.width
                val h = size.height
                listOf(0f, 0.25f, 0.5f, 0.75f, 1f).forEach { f ->
                    val yy = h * f
                    drawLine(axisColor, Offset(0f, yy), Offset(w, yy), strokeWidth = 1f)
                }
                plotted.forEach { (xf, hourFrac, color) ->
                    drawCircle(
                        color = color,
                        radius = 5f,
                        center = Offset(w * xf, h * (1f - hourFrac)),
                    )
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(
            Modifier.fillMaxWidth().padding(start = 36.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(dateFmt.format(Date(minT)), fontSize = 10.sp, color = labelColor)
            Text(dateFmt.format(Date(maxT)), fontSize = 10.sp, color = labelColor)
        }
    }
}

/** Horizontal bar chart — bar width is proportional to the largest value. */
@Composable
fun BarChart(bars: List<Bar>) {
    val max = (bars.maxOfOrNull { it.value } ?: 0).toFloat()
    Column(Modifier.fillMaxWidth()) {
        bars.forEach { b ->
            Text("${b.label} — ${b.value}", fontSize = 13.sp)
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(18.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(if (max > 0f) b.value / max else 0f)
                        .height(18.dp)
                        .background(b.color)
                )
            }
            Spacer(Modifier.height(6.dp))
        }
    }
}
