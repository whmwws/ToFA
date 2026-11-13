package ToFA.Android.whmwws.phone.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp

/**
 * 页面滑动进度条：右侧竖条，只在内容移动时出现，停止滚动/拖动即隐藏。
 *
 * 只读取滚动状态并直接绘制，不产生重组；挂在 verticalScroll 之前，
 * 绘制范围即视口，滚动内容不会把进度条带走。
 */
fun Modifier.scrollProgressBar(scroll: ScrollState, color: Color): Modifier =
    drawWithContent {
        drawContent()
        // 静止时不画任何东西；isScrollInProgress 同时覆盖拖动与惯性滑动
        if (!scroll.isScrollInProgress) return@drawWithContent
        val max = scroll.maxValue
        if (max <= 0) return@drawWithContent
        val ratio = size.height / (size.height + max)
        drawRightThumb(color, (scroll.value.toFloat() / max).coerceIn(0f, 1f), ratio)
    }

/** LazyColumn 版本：按「已露出条目 / 总条目」估算进度。 */
fun Modifier.scrollProgressBar(state: LazyListState, color: Color): Modifier =
    drawWithContent {
        drawContent()
        if (!state.isScrollInProgress) return@drawWithContent
        val info = state.layoutInfo
        val total = info.totalItemsCount
        if (total <= 0) return@drawWithContent
        val visible = info.visibleItemsInfo
        if (visible.isEmpty()) return@drawWithContent
        val fraction = ((visible.last().index + 1).toFloat() / total).coerceIn(0f, 1f)
        val ratio = visible.size.toFloat() / total
        drawRightThumb(color, fraction, ratio)
    }

/** 右侧贴边竖条：轨道淡、滑块实，长度按可视比例收缩。 */
private fun DrawScope.drawRightThumb(color: Color, fraction: Float, ratio: Float) {
    val barWidth = 3.dp.toPx()
    val pad = 6.dp.toPx()
    val minLen = 36.dp.toPx()
    val trackLen = size.height - pad * 2
    if (trackLen <= 0f) return
    val thumbLen = (trackLen * ratio.coerceIn(0.05f, 1f)).coerceIn(minLen, trackLen)
    val x = size.width - barWidth - 2.dp.toPx()
    val corner = CornerRadius(barWidth / 2f)

    drawRoundRect(
        color = color.copy(alpha = 0.14f),
        topLeft = Offset(x, pad),
        size = Size(barWidth, trackLen),
        cornerRadius = corner,
    )
    val top = pad + (trackLen - thumbLen) * fraction
    drawRoundRect(
        color = color,
        topLeft = Offset(x, top),
        size = Size(barWidth, thumbLen),
        cornerRadius = corner,
    )
}
