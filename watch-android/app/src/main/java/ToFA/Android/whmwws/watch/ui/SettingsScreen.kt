package ToFA.Android.whmwws.watch.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ToFA.Android.whmwws.watch.data.DisplayNameMode
import kotlin.math.sqrt

/**
 * 「其他」页：圆屏与方屏两套独立布局，顶部标题固定在屏幕最上方、不随内容滚动。
 *
 * 圆屏版：8 个独立胶囊，行高 / 字号与方屏共用同一套规格；
 * 每行宽度在**布局阶段**按它当前在屏幕上的真实 y 取弦长 w(y) = 2·sqrt(r² - y²)，
 * 滚动时读取 scrollState.value 重排（只走布局，不触发重组），因此胶囊宽度实时跟随圆形轮廓。
 *
 * 方屏版保持整宽卡片列表：16dp 左右边距、行高一致、可滚动。
 */
private val RoundRowGap = 3.dp
private val RoundGroupGap = 8.dp
private val SquareHPad = 16.dp

/** 弦长端点相对玻璃边缘内缩，避免胶囊贴边。 */
private val RoundEdgeInset = 4.dp

/** 最小可用宽度：滚到圆屏最边缘时仍保证「高级功能 已激活 >」整行可读（英文/繁中/日语不省略）。 */
private val RoundMinRowWidth = 134.dp

/** 顶部标题栏高度（固定不滚动），同时也是圆屏弦长计算的视口顶端。 */
private val PinnedHeaderHeight = 30.dp

/** 圆屏 11 行的不可变数据模型：开关集中在顶部，跳转类二级项在下。 */
private enum class RoundRowType { EXPORT_QR, COPY_CODE, RECENTS, LAUNCH_SHOW, SHOW_ACCOUNT_NAME, SQUARE_UI, THEME, THEME_MODE, LANGUAGE, PASSWORD, ADVANCED, LICENSES }

private data class RoundRowModel(val type: RoundRowType, val label: String, val enLabel: String)

private val RoundRowModels = listOf(
    RoundRowModel(RoundRowType.EXPORT_QR, "导出二维码", "Export QR"),
    RoundRowModel(RoundRowType.COPY_CODE, "复制验证码", "Copy code"),
    RoundRowModel(RoundRowType.RECENTS, "最近任务隐私", "Recents privacy"),
    RoundRowModel(RoundRowType.LAUNCH_SHOW, "启动直接显示", "Show on launch"),
    RoundRowModel(RoundRowType.SHOW_ACCOUNT_NAME, "显示账户名", "Account name"),
    RoundRowModel(RoundRowType.SQUARE_UI, "圆形页面", "Round pages"),
    RoundRowModel(RoundRowType.THEME, "主题色", "Accent"),
    RoundRowModel(RoundRowType.THEME_MODE, "深浅色", "Theme"),
    RoundRowModel(RoundRowType.LANGUAGE, "语言", "Language"),
    RoundRowModel(RoundRowType.PASSWORD, "密码", "Password"),
    RoundRowModel(RoundRowType.ADVANCED, "高级功能", "Advanced"),
    RoundRowModel(RoundRowType.LICENSES, "开源许可", "Licenses"),
)

/** 组间距：位置跟随数据模型，避免增删行后硬编码索引错位。 */
private val RoundGroupBreakAfter = setOf(
    RoundRowModels.indexOfFirst { it.type == RoundRowType.SQUARE_UI },
    RoundRowModels.indexOfFirst { it.type == RoundRowType.PASSWORD },
)

private fun roundGapAfter(index: Int, gap: Float, groupGap: Float): Float =
    if (index in RoundGroupBreakAfter) groupGap else gap

/** 「其他」设置：开关选项集中在上，跳转类二级项集中在下。 */
@Composable
fun SettingsScreen(
    qrEnabled: Boolean,
    onToggleQr: (Boolean) -> Unit,
    copyEnabled: Boolean,
    onToggleCopy: (Boolean) -> Unit,
    activated: Boolean,
    squareUi: Boolean,
    onToggleSquareUi: (Boolean) -> Unit,
    themeMode: WatchThemeMode,
    onThemeModeChange: (WatchThemeMode) -> Unit,
    recentsPrivacy: Boolean,
    onToggleRecentsPrivacy: (Boolean) -> Unit,
    showCodesOnLaunch: Boolean,
    onToggleShowCodesOnLaunch: (Boolean) -> Unit,
    displayNameMode: DisplayNameMode,
    onDisplayNameModeChange: (DisplayNameMode) -> Unit,
    onDonate: () -> Unit,
    onTheme: () -> Unit,
    onPassword: () -> Unit,
    onLicenses: () -> Unit,
    onBack: () -> Unit,
) {
    val round = isRoundScreen()
    val scroll = rememberScrollState()
    Column(modifier = Modifier.fillMaxSize()) {
        // 顶部标题固定在最上方：整条（含左右到屏幕边缘）都可点返回。
        WatchPageHeader(title = tr("其他", "More"), onBack = onBack)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .watchRotaryBounce("settings"),
        ) {
            if (round) {
                RoundSettings(
                    scroll = scroll,
                    qrEnabled = qrEnabled,
                    onToggleQr = onToggleQr,
                    copyEnabled = copyEnabled,
                    onToggleCopy = onToggleCopy,
                    squareUi = squareUi,
                    onToggleSquareUi = onToggleSquareUi,
                    themeMode = themeMode,
                    onThemeModeChange = onThemeModeChange,
                    recentsPrivacy = recentsPrivacy,
                    onToggleRecentsPrivacy = onToggleRecentsPrivacy,
                    showCodesOnLaunch = showCodesOnLaunch,
                    onToggleShowCodesOnLaunch = onToggleShowCodesOnLaunch,
                    displayNameMode = displayNameMode,
                    onDisplayNameModeChange = onDisplayNameModeChange,
                    activated = activated,
                    onDonate = onDonate,
                    onTheme = onTheme,
                    onPassword = onPassword,
                    onLicenses = onLicenses,
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .watchScrollProgress(scroll)
                        .verticalScroll(scroll)
                        .watchRotaryScroll(scroll, "settings"),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    SquareSettings(
                        qrEnabled = qrEnabled,
                        onToggleQr = onToggleQr,
                        copyEnabled = copyEnabled,
                        onToggleCopy = onToggleCopy,
                        squareUi = squareUi,
                        onToggleSquareUi = onToggleSquareUi,
                        themeMode = themeMode,
                        onThemeModeChange = onThemeModeChange,
                        recentsPrivacy = recentsPrivacy,
                        onToggleRecentsPrivacy = onToggleRecentsPrivacy,
                        showCodesOnLaunch = showCodesOnLaunch,
                        onToggleShowCodesOnLaunch = onToggleShowCodesOnLaunch,
                        displayNameMode = displayNameMode,
                        onDisplayNameModeChange = onDisplayNameModeChange,
                        activated = activated,
                        onDonate = onDonate,
                        onTheme = onTheme,
                        onPassword = onPassword,
                        onLicenses = onLicenses,
                    )
                }
            }
        }
    }
}

@Composable
private fun RoundSettings(
    scroll: ScrollState,
    qrEnabled: Boolean,
    onToggleQr: (Boolean) -> Unit,
    copyEnabled: Boolean,
    onToggleCopy: (Boolean) -> Unit,
    squareUi: Boolean,
    onToggleSquareUi: (Boolean) -> Unit,
    themeMode: WatchThemeMode,
    onThemeModeChange: (WatchThemeMode) -> Unit,
    recentsPrivacy: Boolean,
    onToggleRecentsPrivacy: (Boolean) -> Unit,
    showCodesOnLaunch: Boolean,
    onToggleShowCodesOnLaunch: (Boolean) -> Unit,
    displayNameMode: DisplayNameMode,
    onDisplayNameModeChange: (DisplayNameMode) -> Unit,
    activated: Boolean,
    onDonate: () -> Unit,
    onTheme: () -> Unit,
    onPassword: () -> Unit,
    onLicenses: () -> Unit,
) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .watchScrollProgress(scroll)
            .verticalScroll(scroll)
            .watchRotaryScroll(scroll, "settings"),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        RoundChordColumn(
            scroll = scroll,
            viewportTop = PinnedHeaderHeight,
            modifier = Modifier.fillMaxWidth(),
        ) {
            RoundRowModels.forEach { row ->
                key(row.type) {
                    when (row.type) {
                        RoundRowType.THEME -> RoundRow(tr(row.label, row.enLabel), onClick = onTheme) { Chevron() }
                        RoundRowType.THEME_MODE -> RoundRow(
                            tr(row.label, row.enLabel),
                            onClick = { onThemeModeChange(themeMode.next()) },
                        ) {
                            Text(
                                text = tr(themeMode.label, themeMode.enLabel),
                                color = WatchColors.TextDim,
                                fontSize = WatchRowFontSize,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                            )
                        }
                        RoundRowType.LANGUAGE -> RoundRow(
                            tr(row.label, row.enLabel),
                            onClick = { WatchI18n.cycle(context) },
                        ) {
                            Text(
                                text = WatchI18n.label(),
                                color = WatchColors.TextDim,
                                fontSize = WatchRowFontSize,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                            )
                        }
                        RoundRowType.PASSWORD -> RoundRow(tr(row.label, row.enLabel), onClick = onPassword) { Chevron() }
                        RoundRowType.ADVANCED -> RoundAdvancedRow(activated, onDonate)
                        RoundRowType.EXPORT_QR -> RoundRow(tr(row.label, row.enLabel)) { WatchSwitch(qrEnabled, onToggleQr) }
                        RoundRowType.COPY_CODE -> RoundRow(tr(row.label, row.enLabel)) { WatchSwitch(copyEnabled, onToggleCopy) }
                        RoundRowType.RECENTS -> RoundRow(tr(row.label, row.enLabel)) { WatchSwitch(recentsPrivacy, onToggleRecentsPrivacy) }
                        RoundRowType.LAUNCH_SHOW -> RoundRow(tr(row.label, row.enLabel)) { WatchSwitch(showCodesOnLaunch, onToggleShowCodesOnLaunch) }
                        RoundRowType.SHOW_ACCOUNT_NAME -> RoundRow(
                            tr(row.label, row.enLabel),
                            onClick = { onDisplayNameModeChange(displayNameMode.next()) },
                        ) {
                            Text(
                                text = tr(displayNameMode.label, displayNameMode.enLabel),
                                color = WatchColors.TextDim,
                                fontSize = WatchRowFontSize,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                            )
                        }
                        RoundRowType.SQUARE_UI -> RoundRow(tr(row.label, row.enLabel)) {
                            WatchSwitch(!squareUi) { enabled -> onToggleSquareUi(!enabled) }
                        }
                        RoundRowType.LICENSES -> RoundCenteredNavRow(tr(row.label, row.enLabel), onClick = onLicenses)
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}

/**
 * 圆屏胶囊列：每行宽度在布局阶段按该行**当前**屏幕 y 取弦长。
 *
 * - `scrollState.value` 在 MeasurePolicy 里读取：滚动时只请求重新布局，不产生重组；
 * - y 使用“视口顶端 + 行中心 - 已滚动距离”，所以拖动/滚动过程中宽度持续跟随圆形轮廓；
 * - 行高固定、组间距由布局插入，全部水平居中。
 */
@Composable
private fun RoundChordColumn(
    scroll: ScrollState,
    viewportTop: Dp,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val config = LocalConfiguration.current
    val shortSidePx = with(density) { minOf(config.screenWidthDp, config.screenHeightDp).dp.toPx() }
    val viewportTopPx = with(density) { viewportTop.toPx() }
    val edgeInsetPx = with(density) { RoundEdgeInset.toPx() }
    val minWidthPx = with(density) { RoundMinRowWidth.toPx() }

    Layout(content = content, modifier = modifier) { measurables, constraints ->
        val rowHeightPx = WatchRowHeight.roundToPx()
        val gapPx = RoundRowGap.roundToPx()
        val groupGapPx = RoundGroupGap.roundToPx()
        // 布局阶段读取滚动快照：宽度实时跟随，但不写组合状态。
        val scrolledPx = scroll.value
        val radius = shortSidePx / 2f - edgeInsetPx
        val centerY = shortSidePx / 2f
        val maxWidthPx = (constraints.maxWidth - 2 * edgeInsetPx).toInt()

        fun rowCenterInContent(index: Int): Float {
            var top = 0f
            for (k in 0 until index) {
                top += rowHeightPx + roundGapAfter(k, gapPx.toFloat(), groupGapPx.toFloat())
            }
            return top + rowHeightPx / 2f
        }

        fun chordWidth(index: Int): Int {
            val screenY = viewportTopPx + rowCenterInContent(index) - scrolledPx
            val dy = screenY - centerY
            val half = sqrt((radius * radius - dy * dy).coerceAtLeast(0f))
            return (half * 2f).toInt().coerceIn(minWidthPx.toInt(), maxWidthPx)
        }

        val placeables = measurables.mapIndexed { index, measurable ->
            val widthPx = chordWidth(index)
            measurable.measure(Constraints.fixed(widthPx, rowHeightPx))
        }
        var totalHeight = 0
        placeables.forEachIndexed { index, placeable ->
            totalHeight += placeable.height
            if (index < placeables.lastIndex) totalHeight +=
                roundGapAfter(index, gapPx.toFloat(), groupGapPx.toFloat()).toInt()
        }
        layout(constraints.maxWidth, totalHeight) {
            var y = 0
            placeables.forEachIndexed { index, placeable ->
                placeable.placeRelative((constraints.maxWidth - placeable.width) / 2, y)
                y += placeable.height
                if (index < placeables.lastIndex) y +=
                    roundGapAfter(index, gapPx.toFloat(), groupGapPx.toFloat()).toInt()
            }
        }
    }
}

/**
 * 文字用**固定上界**测量（上界夹到槽位宽度并向下取整到 16px），
 * 行宽每帧变化时只有跨过 16px 台阶才需要重新排版文字，其余时间命中测量缓存；
 * 这是圆屏实时弦长还能保持流畅的关键。
 */
private fun Modifier.fixedLabelSlot(rowHeightPx: Int, maxTextWidthPx: Int): Modifier =
    clipToBounds().layout { measurable, constraints ->
        // 上界必须夹到槽位实际宽度，否则窄胶囊里的文字会被 clipToBounds 直接切掉、
        // 既不折行也不出省略号（英文 / 繁中 / 日语长文案的贴边截断根因）。
        // 向下取整到 16px 台阶：测量宽度永远不超过槽位，同时减少滚动时的重复排版。
        val raw = constraints.maxWidth.coerceAtMost(maxTextWidthPx)
        val slotWidth = (raw / 16) * 16
        val placeable = measurable.measure(Constraints(maxWidth = slotWidth.coerceAtLeast(16)))
        layout(constraints.maxWidth, rowHeightPx) {
            placeable.placeRelative(0, (rowHeightPx - placeable.height) / 2)
        }
    }

/** 同上，但槽位宽度取文字自然宽度（用于「高级功能 已激活 >」这类居中排布）。 */
private fun Modifier.fixedNaturalText(): Modifier =
    layout { measurable, _ ->
        val placeable = measurable.measure(Constraints())
        layout(placeable.width, placeable.height) { placeable.placeRelative(0, 0) }
    }

/** 圆屏胶囊：整行完全圆角、统一 10dp 内边距、开关/文字垂直居中；宽度由父布局按弦长实时测量。 */
@Composable
private fun RoundRow(
    label: String,
    onClick: (() -> Unit)? = null,
    trailing: @Composable RowScope.() -> Unit,
) {
    val density = LocalDensity.current
    val config = LocalConfiguration.current
    val rowHeightPx = with(density) { WatchRowHeight.roundToPx() }
    // 固定上界：足够容纳最长标签，同时让超长英文按真实宽度出省略号
    val maxTextPx = with(density) {
        (minOf(config.screenWidthDp, config.screenHeightDp) * 0.6f).dp.roundToPx()
    }
    Row(
        modifier = Modifier
            .height(WatchRowHeight)
            .clip(RoundedCornerShape(percent = 50))
            .background(WatchColors.Surface)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = WatchColors.Text,
            fontSize = WatchRowFontSize,
            lineHeight = 14.sp,
            fontWeight = FontWeight.Medium,
            // 圆屏最窄处弦长只有 130dp 左右：允许折成两行（行高 14sp、两行 28dp 仍落在 30dp 行高内），
            // 比单行省略号更完整，也不缩小字号。
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).fixedLabelSlot(rowHeightPx, maxTextPx),
        )
        trailing()
    }
}

/** 导航胶囊（如「开源许可」）：与「高级功能」一致，文字与箭头整体居中。 */
@Composable
private fun RoundCenteredNavRow(label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .height(WatchRowHeight)
            .clip(RoundedCornerShape(percent = 50))
            .background(WatchColors.Surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(
            text = label,
            color = WatchColors.Text,
            fontSize = WatchRowFontSize,
            lineHeight = 14.sp,
            fontWeight = FontWeight.Medium,
            // 箭头先测、文字用剩余宽度：长语言下折两行而不是被圆角裁掉
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f, fill = false),
        )
        Spacer(Modifier.width(8.dp))
        Chevron()
    }
}

/** 「高级功能 已激活 >」：标签可收缩省略，状态用短词，避免英文在窄胶囊内重叠。 */
@Composable
private fun RoundAdvancedRow(activated: Boolean, onDonate: () -> Unit) {
    Row(
        modifier = Modifier
            .height(WatchRowHeight)
            .clip(RoundedCornerShape(percent = 50))
            .background(WatchColors.Surface)
            .then(if (activated) Modifier.clickable(onClick = onDonate) else Modifier)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(
            text = tr("高级功能", "Advanced"),
            color = WatchColors.Text,
            fontSize = WatchRowFontSize,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = if (activated) tr("已激活 >", "Active >") else tr("未激活", "Inactive"),
            color = if (activated) WatchColors.Accent else WatchColors.TextDim,
            fontSize = WatchRowFontSize,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
    }
}

@Composable
private fun Chevron() {
    Text(
        text = ">",
        color = WatchColors.TextDim,
        fontSize = WatchRowFontSize,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        modifier = Modifier.fixedNaturalText(),
    )
}

@Composable
private fun SquareSettings(
    qrEnabled: Boolean,
    onToggleQr: (Boolean) -> Unit,
    copyEnabled: Boolean,
    onToggleCopy: (Boolean) -> Unit,
    squareUi: Boolean,
    onToggleSquareUi: (Boolean) -> Unit,
    themeMode: WatchThemeMode,
    onThemeModeChange: (WatchThemeMode) -> Unit,
    recentsPrivacy: Boolean,
    onToggleRecentsPrivacy: (Boolean) -> Unit,
    showCodesOnLaunch: Boolean,
    onToggleShowCodesOnLaunch: (Boolean) -> Unit,
    displayNameMode: DisplayNameMode,
    onDisplayNameModeChange: (DisplayNameMode) -> Unit,
    activated: Boolean,
    onDonate: () -> Unit,
    onTheme: () -> Unit,
    onPassword: () -> Unit,
    onLicenses: () -> Unit,
) {
    val gap = 3.dp
    val context = LocalContext.current
    Spacer(Modifier.height(6.dp))
    SquareRow(tr("导出二维码", "Export QR")) { WatchSwitch(qrEnabled, onToggleQr) }
    Spacer(Modifier.height(gap))
    SquareRow(tr("复制验证码", "Copy code")) { WatchSwitch(copyEnabled, onToggleCopy) }
    Spacer(Modifier.height(gap))
    SquareRow(tr("最近任务隐私", "Recents privacy")) { WatchSwitch(recentsPrivacy, onToggleRecentsPrivacy) }
    Spacer(Modifier.height(gap))
    SquareRow(tr("启动直接显示", "Show on launch")) { WatchSwitch(showCodesOnLaunch, onToggleShowCodesOnLaunch) }
    Spacer(Modifier.height(gap))
    SquareRow(tr("显示账户名", "Show account name"), onClick = { onDisplayNameModeChange(displayNameMode.next()) }) {
        Text(
            text = tr(displayNameMode.label, displayNameMode.enLabel),
            color = WatchColors.TextDim,
            fontSize = WatchRowFontSize,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
        )
    }
    Spacer(Modifier.height(gap))
    SquareRow(tr("圆形页面", "Round pages")) {
        WatchSwitch(!squareUi) { enabled -> onToggleSquareUi(!enabled) }
    }
    Spacer(Modifier.height(8.dp))
    SquareRow(tr("主题色", "Accent color"), onClick = onTheme) { Chevron() }
    Spacer(Modifier.height(gap))
    SquareRow(tr("深浅色模式", "Theme mode"), onClick = { onThemeModeChange(themeMode.next()) }) {
        Text(
            text = tr(themeMode.label, themeMode.enLabel),
            color = WatchColors.TextDim,
            fontSize = WatchRowFontSize,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
        )
    }
    Spacer(Modifier.height(gap))
    SquareRow(tr("语言", "Language"), onClick = { WatchI18n.cycle(context) }) {
        Text(
            text = WatchI18n.label(),
            color = WatchColors.TextDim,
            fontSize = WatchRowFontSize,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
        )
    }
    Spacer(Modifier.height(gap))
    SquareRow(tr("密码", "Password"), onClick = onPassword) { Chevron() }
    Spacer(Modifier.height(8.dp))
    SquareRow(tr("高级功能", "Advanced"), onClick = if (activated) onDonate else null) {
        Text(
            text = if (activated) tr("已激活 >", "Active >") else tr("未激活", "Inactive"),
            color = if (activated) WatchColors.Accent else WatchColors.TextDim,
            fontSize = WatchRowFontSize,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
    Spacer(Modifier.height(8.dp))
    SquareRow(tr("开源许可", "Licenses"), onClick = onLicenses) { Chevron() }
    Spacer(Modifier.height(12.dp))
}

/** 方屏整宽卡片：左右 16dp 固定边距，所有行同宽，不按弦长收窄。 */
@Composable
private fun SquareRow(
    label: String,
    onClick: (() -> Unit)? = null,
    trailing: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = SquareHPad)
            .heightIn(min = WatchRowHeight)
            .clip(RoundedCornerShape(14.dp))
            .background(WatchColors.Surface)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = WatchColors.Text,
            fontSize = WatchRowFontSize,
            lineHeight = 14.sp,
            fontWeight = FontWeight.Medium,
            // 方屏同样允许两行：长语言下单行放不下时折行，而不是省略号截断
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        trailing()
    }
}

/**
 * 轻量开关：滚动/回弹时不引入 Material Switch 的多层动画/手势节点与阴影层，只保留绘制；
 * 静态几何与配色和优化前截图逐像素对齐：
 * 轨道 36×22dp、滑块直径 16dp、左右各 3dp；关：底 #36343B + 1dp #938F99 描边 + #938F99 滑块；
 * 开：主题色底 + #06210F 滑块。
 */
@Composable
private fun WatchSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val progress by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "switch",
    )
    Canvas(
        modifier = Modifier
            .width(36.dp)
            .height(22.dp)
            .clickable { onCheckedChange(!checked) },
    ) {
        val radius = size.height / 2f
        val outlinePx = 1.dp.toPx()
        drawRoundRect(
            color = lerp(WatchColors.SwitchOffTrack, WatchColors.Accent, progress),
            cornerRadius = CornerRadius(radius),
        )
        if (progress < 1f) {
            drawRoundRect(
                color = lerp(WatchColors.SwitchOffOutline, Color.Transparent, progress),
                topLeft = Offset(outlinePx / 2f, outlinePx / 2f),
                size = Size(size.width - outlinePx, size.height - outlinePx),
                cornerRadius = CornerRadius((size.height - outlinePx) / 2f),
                style = Stroke(width = outlinePx),
            )
        }
        val thumbRadius = 8.dp.toPx()
        val edge = 3.dp.toPx()
        val minX = thumbRadius + edge
        val maxX = size.width - thumbRadius - edge
        drawCircle(
            color = lerp(WatchColors.SwitchOffThumb, WatchColors.OnAccent, progress),
            radius = thumbRadius,
            center = Offset(minX + (maxX - minX) * progress, radius),
        )
    }
}
