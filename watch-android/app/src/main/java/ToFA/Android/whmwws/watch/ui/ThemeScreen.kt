package ToFA.Android.whmwws.watch.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ToFA.Android.whmwws.watch.data.WatchSettings
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * 主题色：选择后立即生效并持久化，用于验证码、倒计时环与按钮。
 * 布局按「一屏装下」压缩：预设双行 → 色轮+预览，全部落在标题栏下方，
 * 圆屏不再需要滚动（省掉滚动回弹与底部安全留白）。
 */
@Composable
fun ThemeScreen(
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var selectedId by remember { mutableStateOf(WatchSettings.accent(context).id) }
    var wheelColor by remember { mutableStateOf(WatchSettings.accent(context).color) }
    val accents = WatchSettings.accents

    fun apply(color: Color, id: String) {
        wheelColor = color
        selectedId = id
        WatchColors.setAccent(color)
        WatchSettings.setAccent(context, id)
    }

    fun applyCustom(color: Color) {
        apply(color, "custom:" + String.format("%08X", color.toArgb()))
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // 顶部标题固定在最上方，不随内容滚动；点顶部区域退出。
        WatchPageHeader(title = tr("主题色", "Accent color"), onBack = onBack)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(top = 2.dp, bottom = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceEvenly,
        ) {
            // 预设色每行 3 个：圆屏顶部弦长只有约 164dp，
            // 一行 6 个会把首尾色块挤出屏幕、裁成非圆形；分行后每个色块都能完整落在圆内。
            accents.chunked(3).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth().scrollableEdgePadding(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    row.forEach { accent ->
                        AccentPreset(
                            accent = accent,
                            selected = selectedId == accent.id,
                            onClick = { apply(accent.color, accent.id) },
                        )
                    }
                }
            }
            // 色轮 + 当前颜色预览框：预览与实际主题色保持一致。
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                WatchColorWheel(
                    color = wheelColor,
                    modifier = Modifier.size(76.dp),
                    onChange = { hue, saturation ->
                        // 亮度沿用当前颜色，色轮只改色相与饱和度
                        applyCustom(hsvColor(hue, saturation, hsvOf(wheelColor)[2]))
                    },
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(WatchColors.effectiveAccent(wheelColor))
                            .border(
                                width = 1.dp,
                                color = WatchColors.TextDim.copy(alpha = 0.45f),
                                shape = RoundedCornerShape(10.dp),
                            ),
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = tr("预览", "Preview"),
                        color = WatchColors.TextDim,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }
        }
    }
}

/** 预设色名称的英文对照：id 与 WatchSettings.accents 一致。 */
private fun accentEnLabel(id: String): String = when (id) {
    "green" -> "Green"
    "blue" -> "Blue"
    "purple" -> "Violet"
    "orange" -> "Orange"
    "cyan" -> "Cyan"
    "pink" -> "Pink"
    else -> "Custom"
}

/** 单个预设色块：显示实际生效的原始主题色。 */
@Composable
private fun AccentPreset(
    accent: WatchSettings.Accent,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 1.dp, horizontal = 2.dp),
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(WatchColors.effectiveAccent(accent.color))
                .border(
                    width = if (selected) 2.dp else 0.dp,
                    color = if (selected) WatchColors.Text else Color.Transparent,
                    shape = CircleShape,
                ),
        )
        Text(
            text = tr(accent.label, accentEnLabel(accent.id)),
            color = if (selected) WatchColors.Text else WatchColors.TextDim,
            fontSize = 8.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            softWrap = false,
        )
    }
}

/**
 * HSV 色盘：角度=色相，半径=饱和度；中心为白，边缘为纯色。
 * 只负责色相/饱和度，亮度沿用当前颜色。
 */
@Composable
private fun WatchColorWheel(
    color: Color,
    modifier: Modifier = Modifier,
    onChange: (hue: Float, saturation: Float) -> Unit,
) {
    val hsv = hsvOf(color)
    Canvas(
        modifier = modifier
            .clip(CircleShape)
            .pointerInput(Unit) {
                detectTapGestures { offset -> pickColor(offset, size.width, size.height, onChange) }
            }
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    change.consume()
                    pickColor(change.position, size.width, size.height, onChange)
                }
            },
    ) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = size.minDimension / 2f
        drawCircle(brush = Brush.sweepGradient(ColorWheelHues))
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color.White, Color.Transparent),
                center = center,
                radius = radius,
            ),
        )
        val angle = Math.toRadians(hsv[0].toDouble())
        val point = Offset(
            center.x + hsv[1] * radius * kotlin.math.cos(angle).toFloat(),
            center.y + hsv[1] * radius * kotlin.math.sin(angle).toFloat(),
        )
        drawCircle(
            color = Color.White,
            radius = 4.dp.toPx(),
            center = point,
            style = Stroke(width = 2.dp.toPx()),
        )
    }
}

private val ColorWheelHues = listOf(
    Color(0xFFFF0000), Color(0xFFFFFF00), Color(0xFF00FF00),
    Color(0xFF00FFFF), Color(0xFF0000FF), Color(0xFFFF00FF), Color(0xFFFF0000),
)

private fun pickColor(
    offset: Offset,
    width: Int,
    height: Int,
    onChange: (hue: Float, saturation: Float) -> Unit,
) {
    val cx = width / 2f
    val cy = height / 2f
    val radius = minOf(width, height) / 2f
    if (radius <= 0f) return
    val dx = offset.x - cx
    val dy = offset.y - cy
    val saturation = (sqrt(dx * dx + dy * dy) / radius).coerceIn(0f, 1f)
    val hue = ((Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())) + 360.0) % 360.0).toFloat()
    onChange(hue, saturation)
}

private fun hsvOf(color: Color): FloatArray =
    FloatArray(3).also { android.graphics.Color.colorToHSV(color.toArgb(), it) }

private fun hsvColor(hue: Float, saturation: Float, value: Float): Color =
    Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, value)))
