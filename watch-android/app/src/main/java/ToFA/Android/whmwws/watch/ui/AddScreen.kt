package ToFA.Android.whmwws.watch.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.sqrt
import ToFA.Android.whmwws.watch.data.TotpAccount
import ToFA.Android.whmwws.watch.imports.OtpauthUri
import ToFA.Android.whmwws.watch.otp.Base32

/** 输入区圆角：与设置页卡片（14dp）保持同一套圆角语言。 */
private val InputShape = RoundedCornerShape(14.dp)

/** 输入框高度：三字段 + 保存按钮要在一页内放下，统一压到 26dp。 */
private val InputFieldHeight = 26.dp

/** 名称 / 账户名最大长度：与列表单行省略配套，避免保存超长文本。 */
private const val MaxDisplayNameLength = 40

/**
 * 手表端自绘输入框，三种状态共用一套结构：
 * - 标签在框外上方（10sp 半粗），聚焦时转主题色；「（可选）」跟标签同字号字重，不再单独做小标签。
 * - 未聚焦：Surface 底 + 1dp 弱描边（#8A8F98 34% 透明），安静但不空。
 * - 聚焦：底色提亮到 SurfaceVariant + 1.5dp 主题色描边。
 * - 错误：1.5dp #FF8A80 描边，标签同步转红。
 */
@Composable
private fun WatchInputField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.None,
    maxLength: Int = Int.MAX_VALUE,
) {
    var focused by remember { mutableStateOf(false) }
    val labelColor = when {
        isError -> WatchColors.Error
        focused -> WatchColors.Accent
        else -> WatchColors.TextDim
    }
    val borderColor = when {
        isError -> WatchColors.Error
        focused -> WatchColors.Accent
        else -> WatchColors.TextDim.copy(alpha = 0.34f)
    }
    val borderWidth: Dp = if (focused || isError) 1.5.dp else 1.dp

    Column(modifier = modifier) {
        Text(
            text = label,
            color = labelColor,
            fontSize = 9.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.2.sp,
            lineHeight = 11.sp,
            maxLines = 1,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp),
        )
        Spacer(Modifier.height(2.dp))
        // 编辑与绘制统一由平台 EditText 承担，Compose 只负责底色、描边与占位文案，
        // 避免 Compose 侧 composition/selection 与 IME 状态脱节导致删字后不重绘。
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(InputFieldHeight)
                .clip(InputShape)
                .background(if (focused) WatchColors.SurfaceVariant else WatchColors.Surface)
                .border(borderWidth, borderColor, InputShape)
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (value.isEmpty()) {
                Text(
                    text = placeholder,
                    color = WatchColors.TextFaint,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            WatchEditText(
                value = value,
                onValueChange = onValueChange,
                maxLength = maxLength,
                keyboardType = keyboardType,
                capitalization = capitalization,
                onFocusChanged = { focused = it },
            )
        }
    }
}

/**
 * 添加工号：直接填 Base32 密钥，或粘贴完整 otpauth:// 链接（自动解析发行方/账号/参数）。
 * 手表输入不便，所以只保留“名称 + 密钥”两个最小字段。
 * 圆屏按弦长逐项内缩（输入框、按钮都贴合圆盘），键盘弹出时可滚动；方屏保持原方形排布。
 */
@Composable
fun AddScreen(
    onBack: () -> Unit,
    onSave: (TotpAccount) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var accountName by remember { mutableStateOf("") }
    var secret by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val scroll = rememberScrollState()
    val round = isRoundScreen()
    val context = LocalContext.current

    // 圆屏左右边距由 ChordColumn 逐项计算；方屏沿用固定 16dp。
    val itemModifier = Modifier
        .fillMaxWidth()
        .then(if (round) Modifier else Modifier.scrollableEdgePadding())

    val content: @Composable () -> Unit = {
        WatchInputField(
            label = tr("名称（可选）", "Name (optional)"),
            value = name,
            onValueChange = { name = it },
            placeholder = tr("未命名", "Unnamed"),
            modifier = itemModifier,
        )
        Spacer(Modifier.height(1.dp))
        WatchInputField(
            label = tr("账户名（可选）", "Account (optional)"),
            value = accountName,
            onValueChange = { accountName = it },
            placeholder = tr("邮箱 / 用户名", "Email / username"),
            modifier = itemModifier,
        )
        Spacer(Modifier.height(1.dp))
        WatchInputField(
            label = tr("密钥 / otpauth 链接", "Secret / otpauth URI"),
            value = secret,
            onValueChange = {
                secret = it
                error = null
            },
            placeholder = "",
            isError = error != null,
            keyboardType = KeyboardType.Ascii,
            capitalization = KeyboardCapitalization.Characters,
            modifier = itemModifier,
        )

        // 固定预留两行错误区：文本有无/换行都不改变内容高度，
        // 避免滚到底时最大滚动值反复变化导致回弹抖动。
        Spacer(Modifier.height(1.dp))
        Box(
            modifier = itemModifier.height(ErrorReservedHeight),
            contentAlignment = Alignment.Center,
        ) {
            error?.let {
                Text(
                    text = it,
                    color = WatchColors.Error,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Medium,
                    lineHeight = 11.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Spacer(Modifier.height(3.dp))
        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
            Button(
                onClick = {
                    val raw = secret.trim()
                    if (raw.isEmpty()) {
                        error = WatchI18n.pick("请填写密钥", "Enter a secret")
                        return@Button
                    }
                    error = null
                    val result = runCatching {
                        if (OtpauthUri.looksLikeOtpauth(raw)) {
                            val parsed = OtpauthUri.parse(raw)
                            parsed.copy(
                                issuer = if (name.isNotBlank()) name.trim() else parsed.issuer,
                                account = if (accountName.isNotBlank()) accountName.trim() else parsed.account,
                            )
                        } else {
                            val normalized = Base32.encode(Base32.decode(raw))
                            require(normalized.isNotBlank()) { WatchI18n.pick("密钥为空", "Secret is empty") }
                            TotpAccount(
                                issuer = name.trim(),
                                account = accountName.trim(),
                                secret = normalized,
                            )
                        }
                    }
                    result.onSuccess {
                        hideWatchKeyboard(context)
                        onSave(it)
                    }
                    result.onFailure { error = it.message ?: WatchI18n.pick("无法解析", "Cannot parse") }
                },
                modifier = itemModifier.height(28.dp),
                shape = RoundedCornerShape(percent = 50),
                colors = ButtonDefaults.buttonColors(
                    containerColor = WatchColors.Accent,
                    contentColor = WatchColors.OnAccent,
                ),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
            ) {
                Text(
                    text = tr("保存", "Save"),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
            }
        }
        Spacer(Modifier.height(2.dp))
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // 顶部标题固定在屏幕最上方，不随内容滚动，键盘弹出时也保持在顶端。
        WatchPageHeader(title = tr("添加账号", "Add account"), onBack = { hideWatchKeyboard(context); onBack() })
        Box(modifier = Modifier.fillMaxWidth().weight(1f).watchRotaryBounce("add")) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .watchScrollProgress(scroll)
                .verticalScroll(scroll)
                .watchRotaryScroll(scroll, "add"),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top,
        ) {
            if (round) {
                RoundInputColumn(
                    scroll = scroll,
                    viewportTop = 30.dp,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Spacer(Modifier.height(1.dp))
                    content()
                    Spacer(Modifier.height(1.dp))
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    content()
                }
            }
        }
        }
    }
}

/**
 * 改名页：从「密钥管理」点按名称进入，可修改密钥名与账户名。
 * 两个字段都走系统输入法；圆屏按弦长、方屏通栏，页面骨架与添加账号页完全一致。
 */
@Composable
fun RenameAccountScreen(
    account: TotpAccount,
    onBack: () -> Unit,
    onSave: (TotpAccount) -> Unit,
) {
    var name by remember(account.id) { mutableStateOf(account.issuer) }
    var accountName by remember(account.id) { mutableStateOf(account.account) }
    val scroll = rememberScrollState()
    val round = isRoundScreen()
    val context = LocalContext.current

    val itemModifier = Modifier
        .fillMaxWidth()
        .then(if (round) Modifier else Modifier.scrollableEdgePadding())

    val content: @Composable () -> Unit = {
        WatchInputField(
            label = tr("名称（可选）", "Name (optional)"),
            value = name,
            onValueChange = { name = it },
            placeholder = tr("未命名", "Unnamed"),
            maxLength = MaxDisplayNameLength,
            modifier = itemModifier,
        )
        Spacer(Modifier.height(1.dp))
        WatchInputField(
            label = tr("账户名（可选）", "Account (optional)"),
            value = accountName,
            onValueChange = { accountName = it },
            placeholder = tr("邮箱 / 用户名", "Email / username"),
            maxLength = MaxDisplayNameLength,
            modifier = itemModifier,
        )

        Spacer(Modifier.height(3.dp))
        Row(
            modifier = itemModifier,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            WatchOutlinedButton(
                text = tr("取消", "Cancel"),
                modifier = Modifier.weight(1f),
                onClick = {
                    hideWatchKeyboard(context)
                    onBack()
                },
            )
            WatchButton(
                text = tr("保存", "Save"),
                modifier = Modifier.weight(1f),
                onClick = {
                    // 名称留空与手动输入页一致：保存空值，列表按同一套「未命名」回退规则显示。
                    hideWatchKeyboard(context)
                    onSave(account.copy(issuer = name.trim(), account = accountName.trim()))
                },
            )
        }
        Spacer(Modifier.height(2.dp))
    }

    Column(modifier = Modifier.fillMaxSize()) {
        WatchPageHeader(title = tr("修改名称", "Rename"), onBack = { hideWatchKeyboard(context); onBack() })
        Box(modifier = Modifier.fillMaxWidth().weight(1f).watchRotaryBounce("rename")) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .watchScrollProgress(scroll)
                    .verticalScroll(scroll)
                    .watchRotaryScroll(scroll, "rename"),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Top,
            ) {
                if (round) {
                    RoundInputColumn(
                        scroll = scroll,
                        viewportTop = 30.dp,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Spacer(Modifier.height(1.dp))
                        content()
                        Spacer(Modifier.height(1.dp))
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        content()
                    }
                }
            }
        }
    }
}

/** 错误文字固定两行高度（9sp / 11sp 行高 × 2）。 */
private val ErrorReservedHeight = 22.dp

/**
 * 圆屏输入列：每行宽度在布局阶段按它当前在屏幕上的真实 y 取弦长，
 * 滚动时读取 scroll.value 实时重排；另设最小宽度，避免输入框在圆顶/圆底被压得过窄。
 */
@Composable
private fun RoundInputColumn(
    scroll: ScrollState,
    viewportTop: Dp,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val config = LocalConfiguration.current
    val shortSidePx = with(density) {
        minOf(config.screenWidthDp, config.screenHeightDp).dp.toPx()
    }
    val viewportTopPx = with(density) { viewportTop.toPx() }
    val edgeInsetPx = with(density) { 4.dp.toPx() }
    // 底部「保存」按钮正好落在圆屏下缘，最小宽度取 90dp，保证按钮完整落在玻璃弧内。
    val minWidthPx = with(density) { 90.dp.toPx() }
    // 行高保险边：覆盖输入框整列（标签 + 2dp 间距 + 26dp 输入框，约 42dp），保证整行落在圆内。
    val guardHeightPx = with(density) { 42.dp.toPx() }

    Layout(content = content, modifier = modifier) { measurables, constraints ->
        val maxWidthPx = (constraints.maxWidth - 2 * edgeInsetPx).toInt()
        val minWidth = minWidthPx.toInt().coerceAtMost(maxWidthPx)
        val radius = shortSidePx / 2f - edgeInsetPx
        val centerY = shortSidePx / 2f
        // 布局阶段读取滚动快照：输入框宽度随拖动实时跟随圆屏轮廓。
        val scrolledPx = scroll.value

        fun chord(y: Float): Float {
            val dy = y - centerY
            return 2f * sqrt((radius * radius - dy * dy).coerceAtLeast(0f))
        }

        // 顺序测量：先按行顶边（及行底保险边）算弦长，再测量一次，绝不重复测量同一个子项。
        var cursor = 0f
        val placeables = measurables.map { measurable ->
            val topY = viewportTopPx + cursor - scrolledPx
            val width = minOf(chord(topY), chord(topY + guardHeightPx))
                .toInt()
                .coerceIn(minWidth, maxWidthPx)
            val placeable = measurable.measure(Constraints(minWidth = width, maxWidth = width))
            cursor += placeable.height
            placeable
        }
        val totalHeight = placeables.sumOf { it.height }
        layout(constraints.maxWidth, totalHeight) {
            var top = 0
            placeables.forEach { placeable ->
                placeable.placeRelative((constraints.maxWidth - placeable.width) / 2, top)
                top += placeable.height
            }
        }
    }
}
