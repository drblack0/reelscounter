package com.mindware.reels.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.gestures.detectTapGestures

/**
 * Simple bar chart drawn on a Canvas (no charting dependency). [labels] has one entry per bar; blank
 * entries are skipped so dense charts can label every Nth bar. [selected] highlights one bar.
 */
@Composable
fun BarChart(
    values: List<Int>,
    labels: List<String>,
    description: String,
    modifier: Modifier = Modifier,
    selected: Int? = null,
    onBarClick: ((Int) -> Unit)? = null,
) {
    val bar = MaterialTheme.colorScheme.primary
    val dim = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
    val grid = MaterialTheme.colorScheme.outlineVariant
    val max = (values.maxOrNull() ?: 0).coerceAtLeast(1)

    Column(modifier.semantics { contentDescription = description }) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(140.dp)
                .pointerInput(values.size, onBarClick) {
                    if (onBarClick != null && values.isNotEmpty()) {
                        detectTapGestures { p -> onBarClick((p.x / (size.width / values.size)).toInt().coerceIn(0, values.size - 1)) }
                    }
                },
        ) {
            drawLine(grid, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 2f)
            if (values.isEmpty()) return@Canvas
            val slot = size.width / values.size
            val barW = slot * 0.62f
            values.forEachIndexed { i, v ->
                val h = size.height * v / max
                drawRoundRect(
                    color = if (selected == null || selected == i) bar else dim,
                    topLeft = Offset(i * slot + (slot - barW) / 2, size.height - h),
                    size = Size(barW, h.coerceAtLeast(if (v > 0) 3f else 0f)),
                    cornerRadius = CornerRadius(4f, 4f),
                )
            }
        }
        Row(Modifier.fillMaxWidth()) {
            labels.forEach {
                Text(
                    it,
                    Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    fontSize = 10.sp,
                    maxLines = 1,
                    softWrap = false,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
