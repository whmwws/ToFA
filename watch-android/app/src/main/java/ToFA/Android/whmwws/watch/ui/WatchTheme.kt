package ToFA.Android.whmwws.watch.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt

/** 深浅色模式：浅色 / 深色 / 跟随系统。 */
enum class WatchThemeMode(val id: String, val label: String, val enLabel: String) {
    System("system", "跟随系统", "System"),
    Light("light", "浅色", "Light"),
    Dark("dark", "深色", "Dark");

    /** 点击整行时循环切换。 */
    fun next(): WatchThemeMode = when (this) {
        System -> Light
        Light -> Dark
        Dark -> System
    }

    companion object {
        fun fromId(id: String?): WatchThemeMode =
            entries.firstOrNull { it.id == id } ?: Dark
    }
}

/**
 * 双套完整配色（浅色 / 深色），所有页面只引用这里的语义色，避免局部反色导致对比度失衡。
 * 深浅色模式共用原始主题色，按钮前景色根据主题色亮度自动切换。
 */
object WatchColors {

    private var modeState by mutableStateOf(WatchThemeMode.Dark)
    private var systemDarkState by mutableStateOf(true)
    private var accentState by mutableStateOf(Color(0xFF7BD88F))

    val mode: WatchThemeMode get() = modeState
    val isDark: Boolean
        get() = when (modeState) {
            WatchThemeMode.Dark -> true
            WatchThemeMode.Light -> false
            WatchThemeMode.System -> systemDarkState
        }

    /** 系统当前是否深色（跟随系统时使用）。 */
    fun applyThemeMode(mode: WatchThemeMode, systemDark: Boolean) {
        modeState = mode
        systemDarkState = systemDark
    }

    /** 深浅色模式都保留原始明亮主题色。 */
    val Accent: Color get() = accentState

    /** 色轮、预设与预览框统一显示实际原色。 */
    fun effectiveAccent(color: Color): Color = color

    /** 明亮底色用深色文字，较深的自定义底色用白色文字。 */
    val OnAccent: Color get() = onAccent(accentState)

    fun setAccent(color: Color) {
        accentState = color
    }

    /** 根据原色亮度选择按钮前景色，不再修改主题色本身。 */
    private fun onAccent(color: Color): Color =
        if (relativeLuminance(color) > 0.22f) Color(0xFF06210F) else Color.White

    private fun relativeLuminance(color: Color): Float =
        0.2126f * linear(color.red) + 0.7152f * linear(color.green) + 0.0722f * linear(color.blue)

    private fun linear(value: Float): Float =
        if (value <= 0.04045f) value / 12.92f else ((value + 0.055f) / 1.055f).pow(2.4f)

    val Background: Color get() = if (isDark) Color(0xFF000000) else Color(0xFFF3F5F9)
    /** 最后 5 秒倒计时环的警示色。 */
    val Red: Color get() = if (isDark) Color(0xFFFF5A5F) else Color(0xFFD32F2F)
    /** 错误文字。 */
    val Error: Color get() = if (isDark) Color(0xFFFF8A80) else Color(0xFFB3261E)
    /** 弹层遮罩：两种模式都用暗色遮罩，保证遮罩下内容仍被压暗。 */
    val Scrim: Color get() = if (isDark) Color(0xF4000000) else Color(0xE6000000)
    val Text: Color get() = if (isDark) Color(0xFFE6E8EC) else Color(0xFF11151B)
    val TextDim: Color get() = if (isDark) Color(0xFF8A8F98) else Color(0xFF4B5563)
    val TextFaint: Color get() = if (isDark) Color(0xFF5F6672) else Color(0xFF8792A2)
    val Surface: Color get() = if (isDark) Color(0xFF171A21) else Color(0xFFFFFFFF)
    val SurfaceVariant: Color get() = if (isDark) Color(0xFF22262F) else Color(0xFFE7EBF1)
    val DotOff: Color get() = if (isDark) Color(0xFF3A3F4A) else Color(0xFFC8D0DC)
    val SwitchOffTrack: Color get() = if (isDark) Color(0xFF36343B) else Color(0xFFD9DEE7)
    val SwitchOffOutline: Color get() = if (isDark) Color(0xFF938F99) else Color(0xFF6B7686)
    val SwitchOffThumb: Color get() = if (isDark) Color(0xFF938F99) else Color(0xFF6B7686)

    /** 兼容旧调用点。 */
    val Green: Color get() = Accent
}

/** 全端统一的弹簧曲线：先快后慢、无回跳过冲；翻页 / 界面切换 / 元素入场共用。 */
val WatchSpringSpec = spring<Float>(
    stiffness = Spring.StiffnessMediumLow,
    dampingRatio = Spring.DampingRatioNoBouncy,
)

/** 全端统一的页面左右留白：方形模式固定 16dp；圆形模式作为最小边距，再叠加弦长内缩。 */
val WatchPagePadding = 16.dp

/** 除主页外，所有页面胶囊行的统一行高与统一字号（保证圆屏可读、跨页一致）。 */
val WatchRowHeight = 30.dp
val WatchRowFontSize = 12.sp

@Composable
fun WatchTheme(content: @Composable () -> Unit) {
    val scheme = if (WatchColors.isDark) {
        darkColorScheme(
            primary = WatchColors.Accent,
            onPrimary = WatchColors.OnAccent,
            background = WatchColors.Background,
            surface = WatchColors.Surface,
            surfaceVariant = WatchColors.SurfaceVariant,
            onBackground = WatchColors.Text,
            onSurface = WatchColors.Text,
            onSurfaceVariant = WatchColors.TextDim,
        )
    } else {
        lightColorScheme(
            primary = WatchColors.Accent,
            onPrimary = WatchColors.OnAccent,
            background = WatchColors.Background,
            surface = WatchColors.Surface,
            surfaceVariant = WatchColors.SurfaceVariant,
            onBackground = WatchColors.Text,
            onSurface = WatchColors.Text,
            onSurfaceVariant = WatchColors.TextDim,
        )
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

/**
 * 手表统一按钮：最小高度 28dp、左右 10dp 内边距、10sp 字号。
 * 各页所有主/次按钮都用这两个封装，保证风格与尺寸一致。
 *
 * 文案在英文 / 繁中 / 日语下会明显变长：这里不再锁死单行，
 * 允许折成两行并由最小高度自然撑高胶囊，避免出现省略号或被裁掉；
 * 字号与内边距保持原值，不靠缩小字号换空间。
 */
@Composable
fun WatchButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
        Button(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier.heightIn(min = 28.dp),
            shape = RoundedCornerShape(percent = 50),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
        ) {
            Text(
                text,
                fontSize = 10.sp,
                lineHeight = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                textAlign = TextAlign.Center,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun WatchOutlinedButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
        OutlinedButton(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier.heightIn(min = 28.dp),
            shape = RoundedCornerShape(percent = 50),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
        ) {
            Text(
                text,
                fontSize = 10.sp,
                lineHeight = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                textAlign = TextAlign.Center,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * 各页统一标题栏：固定在屏幕最顶端、不随内容滚动；
 * 点击区域是整条顶部区域（文字左右到屏幕边缘、上方到屏幕顶端），不再单独放返回按钮。
 */
@Composable
fun WatchPageHeader(
    title: String,
    onBack: (() -> Unit)? = null,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(30.dp)
            .then(if (onBack != null) Modifier.clickable(onClick = onBack) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            title,
            fontSize = 12.sp,
            lineHeight = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = WatchColors.Text,
            // 标题同样可能在英文 / 繁中 / 日语下变长：允许两行，不省略号硬截断
            maxLines = 2,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * 圆屏弦长列：每个子项按“自身中心到内容中心”的垂直距离计算左右内缩，
 * 让列表轮廓随圆盘收窄；方屏直接纵向排列、不内缩（各自保留原有固定边距）。
 *
 * 内缩在布局阶段一次算好，不写 state、不触发重组，滚动时内容整体平移，
 * 因此不会出现「滚动时每帧重新测量整页」的卡顿。
 */
@Composable
fun ChordColumn(
    round: Boolean,
    modifier: Modifier = Modifier,
    basePadding: Dp = 6.dp,
    maxPadding: Dp = 42.dp,
    content: @Composable () -> Unit,
) {
    if (!round) {
        Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
            content()
        }
        return
    }
    val density = LocalDensity.current
    val config = LocalConfiguration.current
    val radiusPx = with(density) {
        (minOf(config.screenWidthDp, config.screenHeightDp) / 2f).dp.toPx()
    }
    val basePx = with(density) { basePadding.toPx() }
    val maxPx = with(density) { maxPadding.toPx() }

    Layout(content = content, modifier = modifier) { measurables, constraints ->
        val width = constraints.maxWidth
        // 用固有高度估算每项位置（不触发 measure），保证每个 Measurable 只被测量一次；
        // 本项目各行高度固定，固有高度与实际高度一致。
        val estimated = measurables.map { it.minIntrinsicHeight(width) }
        val totalEstimate = estimated.sum()
        val centerY = totalEstimate / 2f

        var y = 0
        val placeables = ArrayList<Placeable>(measurables.size)
        measurables.forEachIndexed { index, measurable ->
            val itemCenterY = y + estimated[index] / 2f
            val dy = abs(itemCenterY - centerY)
            val inset = radiusPx - sqrt((radiusPx * radiusPx - dy * dy).coerceAtLeast(0f))
            val pad = (basePx + inset * 0.92f).coerceIn(basePx, maxPx)
            val targetWidth = (width - 2f * pad).toInt().coerceAtLeast(1)
            placeables += measurable.measure(
                constraints.copy(minWidth = targetWidth, maxWidth = targetWidth, minHeight = 0),
            )
            y += estimated[index]
        }

        val totalHeight = placeables.sumOf { it.height }
        layout(width, totalHeight) {
            var top = 0
            placeables.forEach { placeable ->
                placeable.placeRelative((width - placeable.width) / 2, top)
                top += placeable.height
            }
        }
    }
}

/* 表冠滚动由各页面自身节点处理，页面容器只做不抢焦点的越界回弹，见 WatchBounce.kt。 */

