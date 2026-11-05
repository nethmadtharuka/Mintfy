package com.mintfy.app.ui.screens.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mintfy.app.ui.theme.BorderSubtle
import com.mintfy.app.ui.theme.BrandPrimary
import com.mintfy.app.ui.theme.ChartGreen50
import com.mintfy.app.ui.theme.MintfyTheme
import kotlin.math.roundToInt

/**
 * Two-segment donut: committed against discretionary spending (FR-F2).
 * Discretionary is the bright brand green (the part Kavindu can change), committed the darker Figma
 * chart green. [committedShare] is 0 to 1, or null when nothing has been spent (an empty grey ring).
 * Screen readers hear the two percentages instead of a picture.
 */
@Composable
fun DonutChart(
    committedShare: Float?,
    modifier: Modifier = Modifier,
    diameter: Dp = 120.dp,
    thickness: Dp = 18.dp,
    committedColor: Color = ChartGreen50,
    discretionaryColor: Color = BrandPrimary,
    emptyColor: Color = BorderSubtle,
) {
    val committedPercent = committedShare?.let { (it * 100).roundToInt() }
    val description = if (committedPercent == null) {
        "No spending yet"
    } else {
        "Committed $committedPercent percent, discretionary ${100 - committedPercent} percent"
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.size(diameter).clearAndSetSemantics { contentDescription = description },
    ) {
        Canvas(Modifier.size(diameter)) {
            val strokePx = thickness.toPx()
            val inset = strokePx / 2
            val arcSize = Size(size.width - strokePx, size.height - strokePx)
            val stroke = Stroke(width = strokePx)
            val topLeft = Offset(inset, inset)

            if (committedShare == null) {
                drawArc(emptyColor, 0f, 360f, useCenter = false, topLeft = topLeft, size = arcSize, style = stroke)
            } else {
                val committedSweep = 360f * committedShare.coerceIn(0f, 1f)
                // Start at twelve o'clock and go clockwise: committed first, then discretionary.
                drawArc(committedColor, -90f, committedSweep, false, topLeft, arcSize, style = stroke)
                drawArc(discretionaryColor, -90f + committedSweep, 360f - committedSweep, false, topLeft, arcSize, style = stroke)
            }
        }
        if (committedPercent != null) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${100 - committedPercent}%", style = MaterialTheme.typography.titleLarge)
                Text(
                    "discretionary",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Preview
@Composable
fun DonutChartPreview() {
    MintfyTheme {
        DonutChart(committedShare = 52_000f / 125_000f)
    }
}
