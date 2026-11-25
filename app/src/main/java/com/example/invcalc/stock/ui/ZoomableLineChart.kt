package com.example.invcalc.stock.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** 圖表目前顯示的範圍。多張圖共用同一個實例即可同步縮放與平移。 */
class ChartViewState(val n: Int, initialVisible: Int) {
    private val initial = min(n, initialVisible)
    var start by mutableFloatStateOf((n - initial).toFloat())
    var count by mutableFloatStateOf(initial.toFloat())

    fun reset() {
        count = initial.toFloat()
        start = (n - initial).toFloat()
    }
}

@Composable
fun rememberChartViewState(n: Int, initialVisible: Int = 150): ChartViewState =
    remember(n, initialVisible) { ChartViewState(n, initialVisible) }

class ChartSeries(
    val name: String,
    val color: Color,
    val values: List<Double>, // 可含 NaN（該點不繪製）
    val strokeDp: Float = 1.6f
)

/**
 * 可手勢縮放的折線圖：雙指縮放（X 軸）、放大後單指左右拖曳平移、雙擊還原。
 * 只有在雙指或放大後的水平拖曳時才消耗手勢，其餘交給外層捲動／分頁滑動。
 */
@Composable
fun ZoomableLineChart(
    labels: List<String>,
    series: List<ChartSeries>,
    modifier: Modifier = Modifier,
    band: Pair<List<Double>, List<Double>>? = null,
    bandName: String = "",
    bandColor: Color = Color(0xFF90A4AE),
    chartHeight: Dp = 260.dp,
    initialVisible: Int = 150,
    state: ChartViewState? = null,
    showHint: Boolean = true
) {
    if (labels.size < 2) {
        Text("資料不足，無法繪圖", style = MaterialTheme.typography.bodyMedium, modifier = modifier)
    } else {
        ChartBody(labels, series, modifier, band, bandName, bandColor, chartHeight, initialVisible, state, showHint)
    }
}

@Composable
private fun ChartBody(
    labels: List<String>,
    series: List<ChartSeries>,
    modifier: Modifier,
    band: Pair<List<Double>, List<Double>>?,
    bandName: String,
    bandColor: Color,
    chartHeight: Dp,
    initialVisible: Int,
    state: ChartViewState?,
    showHint: Boolean
) {
    val n = labels.size
    val own = remember(n, initialVisible) { ChartViewState(n, initialVisible) }
    val vs = if (state != null && state.n == n) state else own
    val textColor = MaterialTheme.colorScheme.onSurfaceVariant
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val paint = remember { android.graphics.Paint().apply { isAntiAlias = true } }

    Column(modifier) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(chartHeight)
                .pointerInput(n) {
                    detectTapGestures(onDoubleTap = { vs.reset() })
                }
                .pointerInput(n) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        do {
                            val event = awaitPointerEvent()
                            val multi = event.changes.count { it.pressed } >= 2
                            val zoomed = vs.count < n - 0.5f
                            val zoom = event.calculateZoom()
                            val pan = event.calculatePan()
                            val w = size.width.toFloat().coerceAtLeast(1f)
                            if (multi || (zoomed && abs(pan.x) > abs(pan.y))) {
                                if (multi && zoom != 1f) {
                                    val newCount = (vs.count / zoom).coerceIn(min(10, n).toFloat(), n.toFloat())
                                    val cx = (event.calculateCentroid().x / w).coerceIn(0f, 1f)
                                    vs.start += (vs.count - newCount) * cx
                                    vs.count = newCount
                                }
                                vs.start -= pan.x / w * vs.count
                                vs.start = vs.start.coerceIn(0f, max(0f, n - vs.count))
                                event.changes.forEach { if (it.positionChanged()) it.consume() }
                            }
                        } while (event.changes.any { it.pressed })
                    }
                }
        ) {
            val leftPad = 54.dp.toPx()
            val rightPad = 8.dp.toPx()
            val topPad = 8.dp.toPx()
            val bottomPad = 22.dp.toPx()
            val plotW = size.width - leftPad - rightPad
            val plotH = size.height - topPad - bottomPad
            val s = vs.start
            val c = vs.count
            val i0 = floor(s).toInt().coerceIn(0, n - 1)
            val i1 = ceil(s + c - 1f).toInt().coerceIn(i0, n - 1)

            var lo = Double.POSITIVE_INFINITY
            var hi = Double.NEGATIVE_INFINITY
            fun scan(vals: List<Double>) {
                for (i in i0..i1) {
                    val v = vals[i]
                    if (v.isFinite()) {
                        if (v < lo) lo = v
                        if (v > hi) hi = v
                    }
                }
            }
            series.forEach { scan(it.values) }
            band?.let { scan(it.first); scan(it.second) }
            if (!lo.isFinite() || !hi.isFinite()) return@Canvas
            if (hi - lo < 1e-9) { lo -= 1.0; hi += 1.0 }
            val padV = (hi - lo) * 0.06
            lo -= padV
            hi += padV

            fun xOf(i: Int) = leftPad + (i - s) / max(c - 1f, 1f) * plotW
            fun yOf(v: Double) = (topPad + (hi - v) / (hi - lo) * plotH).toFloat()

            paint.textSize = 10.dp.toPx()
            paint.color = textColor.toArgb()
            val nc = drawContext.canvas.nativeCanvas

            // 水平格線與 Y 軸標籤
            for (k in 0..4) {
                val v = lo + (hi - lo) * k / 4
                val y = yOf(v)
                drawLine(gridColor, Offset(leftPad, y), Offset(leftPad + plotW, y), strokeWidth = 1f)
                paint.textAlign = android.graphics.Paint.Align.RIGHT
                nc.drawText(fmtAxis(v), leftPad - 4.dp.toPx(), y + 3.5.dp.toPx(), paint)
            }
            // X 軸標籤
            paint.textAlign = android.graphics.Paint.Align.CENTER
            for (k in 0..3) {
                val idx = (s + (c - 1f) * k / 3).roundToInt().coerceIn(0, n - 1)
                val x = (leftPad + plotW * k / 3).coerceIn(leftPad + 24.dp.toPx(), leftPad + plotW - 24.dp.toPx())
                nc.drawText(labels[idx], x, size.height - 6.dp.toPx(), paint)
            }
            // 零軸
            if (lo < 0 && hi > 0) {
                drawLine(textColor.copy(alpha = 0.6f), Offset(leftPad, yOf(0.0)), Offset(leftPad + plotW, yOf(0.0)), strokeWidth = 1.5f)
            }

            val step = max(1, (i1 - i0 + 1) / max(plotW.toInt(), 1))
            clipRect(leftPad, topPad, leftPad + plotW, topPad + plotH) {
                // 區間帶
                band?.let { (up, low) ->
                    val idxs = ArrayList<Int>()
                    var i = i0
                    while (true) {
                        if (up[i].isFinite() && low[i].isFinite()) idxs.add(i)
                        if (i >= i1) break
                        i = min(i + step, i1)
                    }
                    if (idxs.size >= 2) {
                        val p = Path()
                        idxs.forEachIndexed { k, id ->
                            if (k == 0) p.moveTo(xOf(id), yOf(up[id])) else p.lineTo(xOf(id), yOf(up[id]))
                        }
                        for (k in idxs.indices.reversed()) p.lineTo(xOf(idxs[k]), yOf(low[idxs[k]]))
                        p.close()
                        drawPath(p, bandColor.copy(alpha = 0.25f))
                    }
                }
                // 折線
                series.forEach { sr ->
                    val path = Path()
                    var pen = false
                    var i = i0
                    while (true) {
                        val v = sr.values[i]
                        if (v.isFinite()) {
                            if (!pen) { path.moveTo(xOf(i), yOf(v)); pen = true } else path.lineTo(xOf(i), yOf(v))
                        } else pen = false
                        if (i >= i1) break
                        i = min(i + step, i1)
                    }
                    drawPath(
                        path, sr.color,
                        style = Stroke(width = sr.strokeDp.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                    )
                }
            }
        }
        // 圖例
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            series.forEach {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Spacer(Modifier.size(width = 14.dp, height = 3.dp).background(it.color))
                    Spacer(Modifier.width(4.dp))
                    Text(it.name, style = MaterialTheme.typography.labelSmall)
                }
            }
            if (band != null && bandName.isNotBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Spacer(Modifier.size(width = 14.dp, height = 8.dp).background(bandColor.copy(alpha = 0.4f)))
                    Spacer(Modifier.width(4.dp))
                    Text(bandName, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        if (showHint) {
            Text(
                "雙指縮放、放大後左右拖曳、雙擊還原",
                style = MaterialTheme.typography.labelSmall,
                color = textColor
            )
        }
    }
}

private fun fmtAxis(v: Double): String = when {
    abs(v) >= 1000 -> String.format(Locale.US, "%.0f", v)
    abs(v) >= 10 -> String.format(Locale.US, "%.1f", v)
    else -> String.format(Locale.US, "%.2f", v)
}
