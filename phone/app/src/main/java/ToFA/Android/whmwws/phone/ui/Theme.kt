package ToFA.Android.whmwws.phone.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** 与手表端保持同一套主题色；深浅色模式都保留原始明亮颜色。 */
data class AccentOption(
    val id: String,
    val label: String,
    val enLabel: String,
    val dark: Color,
)

private val LightSurface = Color(0xFFF7F8FA)

val accentOptions = listOf(
    AccentOption("green", "青绿", "Green", Color(0xFF7BD88F)),
    AccentOption("blue", "天蓝", "Sky Blue", Color(0xFF8AB4F8)),
    AccentOption("purple", "紫罗兰", "Violet", Color(0xFFC792EA)),
    AccentOption("orange", "暖橙", "Orange", Color(0xFFFFB86C)),
    AccentOption("cyan", "冰青", "Cyan", Color(0xFF67E8F9)),
    AccentOption("pink", "樱粉", "Pink", Color(0xFFFF9EC4)),
)

fun accentById(id: String): AccentOption {
    accentOptions.firstOrNull { it.id == id }?.let { return it }
    if (id.startsWith("custom:")) {
        val argb = id.removePrefix("custom:").toLongOrNull(16)?.toInt() ?: return accentOptions.first()
        val color = Color(argb)
        return AccentOption(id, "自定义", "Custom", color)
    }
    return accentOptions.first()
}

/** 明亮主题色使用深色文字；较深的自定义主题色使用白色文字。 */
private fun onAccent(color: Color): Color =
    if (relativeLuminance(color) > 0.22f) Color(0xFF06210F) else Color.White

private fun relativeLuminance(color: Color): Float =
    0.2126f * linear(color.red) + 0.7152f * linear(color.green) + 0.0722f * linear(color.blue)

private fun linear(value: Float): Float =
    if (value <= 0.04045f) value / 12.92f else ((value + 0.055f) / 1.055f).pow(2.4f)

/** HSV 色盘：角度=色相，半径=饱和度；中心为白，边缘为纯色。 */
@Composable
fun AccentColorWheel(
    color: Color,
    onColor: (Color) -> Unit,
    modifier: Modifier = Modifier,
) {
    val hsv = remember(color) {
        FloatArray(3).also { android.graphics.Color.colorToHSV(color.toArgb(), it) }
    }
    Canvas(
        modifier = modifier
            .pointerInput(Unit) {
                detectTapGestures { offset -> pickAccent(offset, size.width, size.height, onColor) }
            }
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    change.consume()
                    pickAccent(change.position, size.width, size.height, onColor)
                }
            },
    ) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = size.minDimension / 2f
        drawCircle(brush = Brush.sweepGradient(accentWheelHues))
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color.White, Color.Transparent),
                center = center,
                radius = radius,
            ),
        )
        val angle = Math.toRadians(hsv[0].toDouble())
        val point = Offset(
            center.x + hsv[1] * radius * cos(angle).toFloat(),
            center.y + hsv[1] * radius * sin(angle).toFloat(),
        )
        drawCircle(
            color = Color.White,
            radius = 5.dp.toPx(),
            center = point,
            style = Stroke(width = 2.dp.toPx()),
        )
    }
}

private val accentWheelHues = listOf(
    Color(0xFFFF0000), Color(0xFFFFFF00), Color(0xFF00FF00),
    Color(0xFF00FFFF), Color(0xFF0000FF), Color(0xFFFF00FF), Color(0xFFFF0000),
)

private fun pickAccent(offset: Offset, width: Int, height: Int, onColor: (Color) -> Unit) {
    val cx = width / 2f
    val cy = height / 2f
    val radius = minOf(width, height) / 2f
    if (radius <= 0f) return
    val dx = offset.x - cx
    val dy = offset.y - cy
    val saturation = (sqrt(dx * dx + dy * dy) / radius).coerceIn(0f, 1f)
    val hue = ((Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())) + 360.0) % 360.0).toFloat()
    onColor(Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, 1f))))
}

/** 深浅色模式的可选项；id 与持久化值一一对应。 */
data class ThemeModeOption(val id: String, val label: String, val enLabel: String)

val themeModeOptions = listOf(
    ThemeModeOption("system", "跟随系统", "Follow system"),
    ThemeModeOption("light", "浅色", "Light"),
    ThemeModeOption("dark", "深色", "Dark"),
)

/** 模式 + 系统当前状态 → 是否使用深色。系统栏等原生部分也复用这个判定。 */
fun resolveDarkTheme(themeModeId: String, systemDark: Boolean): Boolean = when (themeModeId) {
    "light" -> false
    "dark" -> true
    else -> systemDark
}

@Composable
fun TfaTheme(
    accentId: String,
    themeModeId: String = "system",
    content: @Composable () -> Unit,
) {
    val dark = resolveDarkTheme(themeModeId, isSystemInDarkTheme())
    val option = accentById(accentId)
    val scheme = if (dark) {
        darkColorScheme(
            primary = option.dark,
            onPrimary = onAccent(option.dark),
            secondary = Color(0xFF8AB4F8),
            background = Color(0xFF0F1115),
            surface = Color(0xFF171A21),
            surfaceVariant = Color(0xFF22262F),
            onBackground = Color(0xFFE6E8EC),
            onSurface = Color(0xFFE6E8EC),
            onSurfaceVariant = Color(0xFF8A8F98),
        )
    } else {
        lightColorScheme(
            primary = option.dark,
            onPrimary = onAccent(option.dark),
            secondary = Color(0xFF1A5FB4),
            background = LightSurface,
            surface = Color(0xFFFFFFFF),
            surfaceVariant = Color(0xFFE9ECF1),
            onBackground = Color(0xFF1A1C20),
            onSurface = Color(0xFF1A1C20),
            onSurfaceVariant = Color(0xFF5B6472),
        )
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
