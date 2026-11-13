package ToFA.Android.whmwws.watch.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.FlowPreview
import kotlin.math.PI
import kotlinx.coroutines.flow.debounce

/**
 * 手表端滑动进度条：只在滚动/拖动/表冠滚动时出现，静止即隐藏。
 * 圆屏沿右边缘绘制圆弧轨道和滑块；方屏保持常规右侧竖条。
 */
fun Modifier.watchScrollProgress(scroll: ScrollState): Modifier = composed {
    val round = isRoundScreen()
    // 表冠滚动走的是直接 scrollBy，isScrollInProgress 不会变 true，
    // 所以额外监听取值变化，静止约 350ms 后自动隐藏。
    val moving = rememberMoving { scroll.value }
    drawWithContent {
        drawContent()
        if (!moving && !scroll.isScrollInProgress) return@drawWithContent
        val max = scroll.maxValue
        if (max <= 0) return@drawWithContent
        val ratio = size.height / (size.height + max)
        drawSideThumb(round, (scroll.value.toFloat() / max).coerceIn(0f, 1f), ratio)
    }
}

fun Modifier.watchScrollProgress(state: LazyListState): Modifier = composed {
    val round = isRoundScreen()
    val moving = rememberMoving {
        state.firstVisibleItemIndex * 100_000 + state.firstVisibleItemScrollOffset
    }
    drawWithContent {
        drawContent()
        if (!moving && !state.isScrollInProgress) return@drawWithContent
        val info = state.layoutInfo
        val total = info.totalItemsCount
        if (total <= 0) return@drawWithContent
        val visible = info.visibleItemsInfo
        if (visible.isEmpty()) return@drawWithContent
        val fraction = ((visible.last().index + 1).toFloat() / total).coerceIn(0f, 1f)
        drawSideThumb(round, fraction, visible.size.toFloat() / total)
    }
}

/**
 * 取值一变化就置为“移动中”，停手 350ms 后复位。
 * 取值在协程里通过 snapshotFlow 采样，不在组合期读取，避免逐帧重组。
 */
@OptIn(FlowPreview::class)
@Composable
private fun rememberMoving(read: () -> Int): Boolean {
    var moving by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        snapshotFlow(read).collect { moving = true }
    }
    LaunchedEffect(Unit) {
        snapshotFlow(read).debounce(350).collect { moving = false }
    }
    return moving
}

/** 圆屏沿右边缘画圆弧，方屏保持常规右侧竖条。 */
private fun DrawScope.drawSideThumb(round: Boolean, fraction: Float, ratio: Float) {
    if (round) {
        drawArcThumb(fraction, ratio)
        return
    }
    val barWidth = 3.dp.toPx()
    val x = size.width - 4.dp.toPx()
    val trackTop = 4.dp.toPx()
    val trackLen = size.height - 8.dp.toPx()
    if (trackLen <= 0f) return
    val thumbLen = (trackLen * ratio.coerceIn(0.08f, 1f)).coerceIn(26.dp.toPx(), trackLen)
    val corner = CornerRadius(barWidth / 2f)

    drawRoundRect(
        color = WatchColors.Accent.copy(alpha = 0.14f),
        topLeft = Offset(x, trackTop),
        size = Size(barWidth, trackLen),
        cornerRadius = corner,
    )
    val top = trackTop + (trackLen - thumbLen) * fraction
    drawRoundRect(
        color = WatchColors.Accent,
        topLeft = Offset(x, top),
        size = Size(barWidth, thumbLen),
        cornerRadius = corner,
    )
}

/** 以屏幕内切圆为路径，圆弧两端留边，滑块沿同一圆弧移动。 */
private fun DrawScope.drawArcThumb(fraction: Float, ratio: Float) {
    val barWidth = 3.dp.toPx()
    val inset = 1.dp.toPx() + barWidth / 2f
    val arcSize = Size(size.width - inset * 2f, size.height - inset * 2f)
    if (arcSize.width <= 0f || arcSize.height <= 0f) return
    val topLeft = Offset(inset, inset)
    val trackStart = -40f
    val trackSweep = 80f
    val radius = (size.minDimension - inset * 2f) / 2f
    val minSweep = (18.dp.toPx() / radius) * (180f / PI.toFloat())
    val thumbSweep = (trackSweep * ratio.coerceIn(0.08f, 1f))
        .coerceIn(minSweep.coerceAtMost(trackSweep), trackSweep)
    val stroke = Stroke(width = barWidth, cap = StrokeCap.Round)

    drawArc(
        color = WatchColors.Accent.copy(alpha = 0.14f),
        startAngle = trackStart,
        sweepAngle = trackSweep,
        useCenter = false,
        topLeft = topLeft,
        size = arcSize,
        style = stroke,
    )
    drawArc(
        color = WatchColors.Accent,
        startAngle = trackStart + (trackSweep - thumbSweep) * fraction,
        sweepAngle = thumbSweep,
        useCenter = false,
        topLeft = topLeft,
        size = arcSize,
        style = stroke,
    )
}
