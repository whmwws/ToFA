package ToFA.Android.whmwws.watch.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ToFA.Android.whmwws.watch.security.PasswordPolicy
import ToFA.Android.whmwws.watch.security.PasswordStore
import kotlinx.coroutines.delay

private enum class PadPage { Numbers, Symbols }

/**
 * 手表密码键盘。
 *
 * 数字页：[1 2 3] [4 5 6] [7 8 9] [0 符号 退格]
 * 符号页：[@ ? !] [< > '] [( ) "] [空格 数字 退格]
 * 左侧长竖条按钮可切到系统输入法；输入时最近一个字符短暂明文显示，点密码框可整段显示。
 */
@Composable
fun PasswordPadScreen(
    title: String,
    subtitle: String? = null,
    confirmLabel: String = tr("确认", "Confirm"),
    minLength: Int = 4,
    enabled: Boolean = true,
    showCancel: Boolean = true,
    resetToken: Int = 0,
    onCancel: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var input by remember { mutableStateOf("") }
    var page by remember { mutableStateOf(PadPage.Numbers) }
    var imeMode by remember { mutableStateOf(false) }
    var showAll by remember { mutableStateOf(false) }
    var revealNonce by remember { mutableIntStateOf(0) }
    var revealing by remember { mutableStateOf(false) }
    var imeFocusSignal by remember { mutableIntStateOf(0) }
    val context = LocalContext.current

    LaunchedEffect(resetToken) {
        input = ""
        page = PadPage.Numbers
        imeMode = false
        showAll = false
        hideWatchKeyboard(context)
    }
    // 最近输入的字符显示 3 秒后自动回到圆点
    LaunchedEffect(revealNonce) {
        if (revealNonce > 0) {
            revealing = true
            delay(3000)
            revealing = false
        }
    }
    LaunchedEffect(imeMode) {
        if (imeMode) {
            imeFocusSignal++
        } else {
            hideWatchKeyboard(context)
        }
    }

    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
    Box(modifier = Modifier.fillMaxSize().watchRotaryBounce("password-pad")) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            // 表冠只取焦，交给外层回弹；不再用步进切换数字/符号页，避免误触。
            .watchRotaryFocus("password-pad")
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        WatchPageHeader(
            title = title,
            onBack = if (showCancel) onCancel else null,
        )

        // 密码框：圆点 / 最近字符 / 整段明文；右侧按钮可切换明文
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .circularEdgePadding()
                .height(24.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(WatchColors.Surface)
                .clickable { showAll = !showAll },
            contentAlignment = Alignment.Center,
        ) {
            if (imeMode) {
                WatchEditText(
                    value = input,
                    onValueChange = {
                        input = it
                        revealNonce++
                    },
                    textSizeSp = 11f,
                    textColor = WatchColors.Accent,
                    centered = true,
                    monospace = true,
                    maxLength = MAX_PASSWORD_LENGTH,
                    hint = tr("请输入密码", "Enter password"),
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Done,
                    enabled = enabled,
                    requestFocusSignal = imeFocusSignal,
                    onDone = {
                        // 系统键盘收起后回到内置键盘，避免停在一个不可输入的空框里。
                        imeMode = false
                        if (input.length >= minLength) onConfirm(input)
                    },
                    modifier = Modifier.fillMaxWidth().height(24.dp),
                )
            } else if (input.isEmpty()) {
                Text(tr("请输入密码", "Enter password"), color = WatchColors.TextDim, fontSize = 10.sp, fontWeight = FontWeight.Medium)
            } else {
                Text(
                    text = maskedText(input, showAll, revealing),
                    color = WatchColors.Accent,
                    fontSize = 11.sp,
                    letterSpacing = 1.sp,
                    maxLines = 1,
                )
            }
            if (!imeMode) {
                PasswordVisibilityIcon(
                    visible = showAll,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .clickable { showAll = !showAll }
                        .padding(horizontal = 5.dp, vertical = 3.dp)
                        .size(12.dp),
                )
            }
        }
        if (subtitle != null) {
            Text(
                text = subtitle,
                color = WatchColors.TextDim,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                lineHeight = 13.sp,
                maxLines = 2,
                modifier = Modifier.fillMaxWidth().circularEdgePadding(),
            )
        }
        Spacer(Modifier.height(3.dp))

        val keyRows: List<List<String>> = if (page == PadPage.Numbers) {
            listOf(
                listOf("1", "2", "3"),
                listOf("4", "5", "6"),
                listOf("7", "8", "9"),
                listOf("0", "符号", "退格"),
            )
        } else {
            listOf(
                listOf("@", "?", "!"),
                listOf("<", ">", "'"),
                listOf("(", ")", "\""),
                listOf("空格", "数字", "退格"),
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth().circularEdgePadding(),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            // 左侧长竖条：切换内置键盘 / 系统输入法
            OutlinedButton(
                onClick = { imeMode = !imeMode },
                enabled = enabled,
                modifier = Modifier.width(24.dp).height(86.dp),
                contentPadding = PaddingValues(0.dp),
                shape = RoundedCornerShape(7.dp),
            ) {
                Text(
                    text = if (imeMode) "⌫⌨" else "⌨",
                    fontSize = 10.sp,
                    color = if (imeMode) WatchColors.Accent else WatchColors.TextDim,
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                keyRows.forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        row.forEach { key ->
                            val isAction = key == "符号" || key == "数字" || key == "退格" || key == "空格"
                            val keyLabel = when (key) {
                                "符号" -> tr("符号", "Sym")
                                "数字" -> tr("数字", "123")
                                "空格" -> tr("空格", "Space")
                                else -> key
                            }
                            OutlinedButton(
                                onClick = {
                                    if (!enabled) return@OutlinedButton
                                    when (key) {
                                        "退格" -> if (input.isNotEmpty()) input = input.dropLast(1)
                                        "符号" -> page = PadPage.Symbols
                                        "数字" -> page = PadPage.Numbers
                                        "空格" -> if (input.length < MAX_PASSWORD_LENGTH) input += " "
                                        else -> if (input.length < MAX_PASSWORD_LENGTH) input += key
                                    }
                                    revealNonce++
                                },
                                enabled = enabled && !imeMode,
                                modifier = Modifier.height(20.dp).weight(1f),
                                contentPadding = PaddingValues(0.dp),
                                shape = RoundedCornerShape(7.dp),
                            ) {
                                if (key == "退格") {
                                    BackspaceIcon(
                                        modifier = Modifier.size(11.dp),
                                        tint = if (enabled && !imeMode) WatchColors.TextDim
                                        else WatchColors.TextDim.copy(alpha = 0.4f),
                                    )
                                } else {
                                    Text(
                                        text = keyLabel,
                                        fontSize = if (isAction) 8.sp else 12.sp,
                                        maxLines = 1,
                                        softWrap = false,
                                        color = if (enabled && !imeMode) WatchColors.Text
                                        else WatchColors.TextDim.copy(alpha = 0.55f),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(3.dp))
        Button(
            onClick = { if (enabled && input.length >= minLength) onConfirm(input) },
            enabled = enabled && input.length >= minLength,
            modifier = Modifier.fillMaxWidth().circularEdgePadding().height(24.dp),
            contentPadding = PaddingValues(0.dp),
        ) {
            Text(confirmLabel, fontSize = 10.sp)
        }
    }
    }
    }
}

/** 圆点显示；showAll 或刚输入时把最后一位显示成明文。 */
private fun maskedText(input: String, showAll: Boolean, revealing: Boolean): String {
    val visibleChar = { ch: Char -> if (ch == ' ') "␣" else ch.toString() }
    if (showAll) return input.map(visibleChar).joinToString("")
    return input.mapIndexed { index, ch ->
        if (revealing && index == input.lastIndex) visibleChar(ch) else "•"
    }.joinToString("")
}

/**
 * 密码设置：启用开关、修改密码、错误后删除开关、紧急密码开关。
 */
@Composable
fun PasswordSettingsScreen(
    store: PasswordStore,
    onBack: () -> Unit,
    onSetPassword: () -> Unit,
    onChangePassword: () -> Unit,
    onSetEmergency: () -> Unit,
) {
    var enabled by remember { mutableStateOf(store.isEnabled()) }
    var wipe by remember { mutableStateOf(store.isWipeAfterErrors()) }
    var emergency by remember { mutableStateOf(store.isEmergencyEnabled()) }
    val scroll = rememberScrollState()

    Column(modifier = Modifier.fillMaxSize()) {
        // 顶部标题固定在屏幕最上方，滚到底也能直接点整条顶部退出。
        WatchPageHeader(title = tr("密码", "Password"), onBack = onBack)
        Box(modifier = Modifier.fillMaxWidth().weight(1f).watchRotaryBounce("password-settings")) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .watchScrollProgress(scroll)
                .verticalScroll(scroll)
                .watchRotaryScroll(scroll, "password-settings")
                .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Spacer(Modifier.height(2.dp))

        SettingSwitchRow(
            label = tr("启用密码", "Enable password"),
            checked = enabled,
            onCheckedChange = { value ->
                if (value) {
                    onSetPassword()
                } else {
                    store.disablePassword()
                    enabled = false
                }
            },
        )

        if (enabled) {
            SmallActionRow(tr("更改密码", "Change password"), ">", onChangePassword)

            SettingSwitchRow(
                label = tr("多次错误后删除", "Wipe after errors"),
                checked = wipe,
                onCheckedChange = {
                    wipe = it
                    store.setWipeAfterErrors(it)
                },
            )

            SettingSwitchRow(
                label = tr("紧急密码", "Emergency password"),
                checked = emergency,
                onCheckedChange = { value ->
                    if (value) {
                        onSetEmergency()
                    } else {
                        store.disableEmergencyPassword()
                        emergency = false
                    }
                },
            )
            if (emergency) {
                SmallActionRow(
                    tr("更改紧急密码", "Change emergency password"),
                    tr("输入即清空密钥", "Wipes secrets"),
                    onSetEmergency,
                )
            }
        }
    }
    }
    }
}

@Composable
fun SettingSwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().circularEdgePadding(16.dp).heightIn(min = 30.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            color = WatchColors.Text,
            fontSize = 11.sp,
            lineHeight = 13.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.scale(0.66f),
        )
    }
}

@Composable
fun SmallActionRow(
    label: String,
    hint: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .circularEdgePadding(16.dp)
            .height(30.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(WatchColors.Surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = WatchColors.Text, fontSize = 11.sp, modifier = Modifier.weight(1f))
        Text(hint, color = WatchColors.TextDim, fontSize = 11.sp, fontWeight = FontWeight.Medium)
    }
}

/** 解锁失败时的错误文案（含冷静期倒计时）。 */
fun unlockErrorMessage(store: PasswordStore, nowMs: Long): String? {
    val remaining = store.lockRemainingMs(nowMs)
    return if (remaining > 0L) {
        WatchI18n.pick(
            "冷静期 ${PasswordPolicy.formatRemaining(remaining)}",
            "Cooldown ${PasswordPolicy.formatRemaining(remaining)}",
        )
    } else {
        null
    }
}

private const val MAX_PASSWORD_LENGTH = 24
