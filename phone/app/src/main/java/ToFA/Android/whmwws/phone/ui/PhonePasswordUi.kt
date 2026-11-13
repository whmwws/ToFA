package ToFA.Android.whmwws.phone.ui

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import ToFA.Android.whmwws.phone.security.PasswordPolicy
import ToFA.Android.whmwws.phone.security.PasswordStore
import kotlinx.coroutines.delay

private enum class PadPage { Numbers, Symbols }

/**
 * 手机密码键盘：
 * 数字页 [1 2 3] [4 5 6] [7 8 9] [0 符号 退格]
 * 符号页 [@ ? !] [< > '] [( ) '] [空格 数字 退格]
 */
@Composable
fun PasswordPad(
    title: String,
    subtitle: String?,
    confirmLabel: String,
    minLength: Int,
    enabled: Boolean,
    onCancel: (() -> Unit)?,
    onConfirm: (String) -> Unit,
) {
    var input by remember { mutableStateOf("") }
    // 系统输入法完整编辑状态：保留 selection/composition，避免删除后 IME 与文本状态脱节。
    var imeValue by remember { mutableStateOf(TextFieldValue("", TextRange(0))) }
    var page by remember { mutableStateOf(PadPage.Numbers) }
    var imeMode by remember { mutableStateOf(false) }
    var showAll by remember { mutableStateOf(false) }
    var revealNonce by remember { mutableIntStateOf(0) }
    var revealing by remember { mutableStateOf(false) }
    val imeFocus = remember { FocusRequester() }

    // 键入过程中文本一致则不同步，保留光标与组合区；仅外部（自绘键盘）改动时重建。
    LaunchedEffect(input) {
        if (imeValue.text != input) imeValue = TextFieldValue(input, TextRange(input.length))
    }
    LaunchedEffect(revealNonce) {
        if (revealNonce > 0) {
            revealing = true
            delay(3000)
            revealing = false
        }
    }
    LaunchedEffect(imeMode) {
        if (imeMode) runCatching { imeFocus.requestFocus() }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 360.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.fillMaxWidth()) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.align(Alignment.Center),
            )
            if (onCancel != null) {
                TextButton(onClick = onCancel, modifier = Modifier.align(Alignment.CenterStart)) {
                    Text(tr("取消", "Cancel"))
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable { showAll = !showAll },
            contentAlignment = Alignment.Center,
        ) {
            if (imeMode) {
                BasicTextField(
                    value = imeValue,
                    onValueChange = { new ->
                        val next = if (new.text.length > MAX_PASSWORD_LENGTH) {
                            new.copy(
                                text = new.text.take(MAX_PASSWORD_LENGTH),
                                selection = TextRange(MAX_PASSWORD_LENGTH),
                                composition = null,
                            )
                        } else {
                            new
                        }
                        imeValue = next
                        input = next.text
                        revealNonce++
                    },
                    singleLine = true,
                    enabled = enabled,
                    textStyle = TextStyle(
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 20.sp,
                        fontFamily = FontFamily.Monospace,
                        textAlign = TextAlign.Center,
                    ),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = { if (input.length >= minLength) onConfirm(input) },
                    ),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp).focusRequester(imeFocus),
                )
            } else if (input.isEmpty()) {
                Text(tr("请输入密码", "Enter password"), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
            } else {
                Text(
                    text = maskedText(input, showAll, revealing),
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 20.sp,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 2.sp,
                    maxLines = 1,
                )
            }
            if (!imeMode) {
                Text(
                    text = if (showAll) tr("隐藏", "Hide") else tr("明文", "Show"),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    maxLines = 1,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .clickable { showAll = !showAll }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                )
            }
        }
        if (subtitle != null) {
            Text(
                text = subtitle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        Spacer(Modifier.height(12.dp))

        val rows = if (page == PadPage.Numbers) {
            listOf(
                listOf("1", "2", "3"),
                listOf("4", "5", "6"),
                listOf("7", "8", "9"),
                listOf("0", "符号", "⌫"),
            )
        } else {
            listOf(
                listOf("@", "?", "!"),
                listOf("<", ">", "'"),
                listOf("(", ")", "\""),
                listOf("空格", "数字", "⌫"),
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            // 左侧长竖条：切换内置键盘 / 系统输入法
            OutlinedButton(
                onClick = { imeMode = !imeMode },
                enabled = enabled,
                modifier = Modifier.width(44.dp).height(240.dp),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(0.dp),
            ) {
                Text(
                    text = "⌨",
                    fontSize = 18.sp,
                    textAlign = TextAlign.Center,
                    color = if (imeMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f),
            ) {
                rows.forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        row.forEach { key ->
                            val action = key == "符号" || key == "数字" || key == "⌫" || key == "空格"
                            OutlinedButton(
                                onClick = {
                                    if (!enabled) return@OutlinedButton
                                    when (key) {
                                        "⌫" -> if (input.isNotEmpty()) input = input.dropLast(1)
                                        "符号" -> page = PadPage.Symbols
                                        "数字" -> page = PadPage.Numbers
                                        "空格" -> if (input.length < MAX_PASSWORD_LENGTH) input += " "
                                        else -> if (input.length < MAX_PASSWORD_LENGTH) input += key
                                    }
                                    revealNonce++
                                },
                                enabled = enabled && !imeMode,
                                modifier = Modifier.weight(1f).height(54.dp),
                                shape = RoundedCornerShape(12.dp),
                            ) {
                                Text(
                                    text = when (key) {
                                        "空格" -> "␣"
                                        "符号" -> tr("符号", "Sym")
                                        "数字" -> tr("数字", "123")
                                        else -> key
                                    },
                                    fontSize = if (action) 13.sp else 20.sp,
                                    fontWeight = if (action) FontWeight.Normal else FontWeight.Medium,
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        Button(
            onClick = { if (enabled && input.length >= minLength) onConfirm(input) },
            enabled = enabled && input.length >= minLength,
            modifier = Modifier.fillMaxWidth().height(50.dp),
        ) { Text(confirmLabel) }
    }
}

/** 设置密码、修改密码、设置紧急密码时使用的弹窗键盘。 */
@Composable
fun PasswordPadDialog(
    title: String,
    subtitle: String?,
    confirmLabel: String = tr("确认", "Confirm"),
    minLength: Int = 4,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.background),
            shape = RoundedCornerShape(20.dp),
        ) {
            Box(Modifier.padding(18.dp)) {
                PasswordPad(
                    title = title,
                    subtitle = subtitle,
                    confirmLabel = confirmLabel,
                    minLength = minLength,
                    enabled = true,
                    onCancel = onDismiss,
                    onConfirm = onConfirm,
                )
            }
        }
    }
}

/** 启动 / 回到前台时的全屏解锁页，只使用应用自带键盘。 */
@Composable
fun PhoneLockScreen(
    store: PasswordStore,
    onUnlocked: () -> Unit,
    onWipe: () -> Unit,
) {
    var message by remember { mutableStateOf<String?>(null) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val remaining = store.lockRemainingMs(now)
    val locked = remaining > 0L

    LaunchedEffect(locked) {
        while (store.lockRemainingMs() > 0L) {
            now = System.currentTimeMillis()
            delay(1000)
        }
        now = System.currentTimeMillis()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        androidx.compose.material3.Icon(
            Icons.Filled.Lock,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            tr("ToFA 已锁定", "ToFA is locked"),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            tr("验证码只在解锁后显示", "Codes are only shown after unlocking"),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(20.dp))

        PasswordPad(
            title = tr("解锁", "Unlock"),
            subtitle = when {
                locked -> tr(
                    "冷静期 ${PasswordPolicy.formatRemaining(remaining)}",
                    "Cooldown ${PasswordPolicy.formatRemaining(remaining)}",
                )
                message != null -> message
                else -> tr("输入应用密码；紧急密码会清空全部密钥", "Enter the app password; the emergency password wipes all keys")
            },
            confirmLabel = tr("解锁", "Unlock"),
            minLength = 4,
            enabled = !locked,
            onCancel = null,
            onConfirm = { input ->
                when (val result = store.verify(input.toCharArray())) {
                    is PasswordStore.UnlockResult.Ok -> onUnlocked()
                    is PasswordStore.UnlockResult.Wipe -> {
                        store.completeWipe()
                        onWipe()
                    }
                    is PasswordStore.UnlockResult.Wrong -> message = result.message
                    is PasswordStore.UnlockResult.Locked ->
                        message = I18n.pick(
                            "冷静期 ${PasswordPolicy.formatRemaining(result.remainingMs)}",
                            "Cooldown ${PasswordPolicy.formatRemaining(result.remainingMs)}",
                        )
                }
            },
        )

        Spacer(Modifier.height(4.dp))
        Text(
            tr(
                "连续错误会按策略进入冷静期；开启「多次错误后删除」时，第 5 次错误将清空密钥。",
                "Repeated failures trigger a cooldown; with wipe-after-failures enabled, the 5th failure wipes all keys.",
            ),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
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
 * 统一的密码输入框：输入后最后一位短暂明文 3 秒，右侧按钮可整段显示 / 隐藏。
 * 长度一一对应，因此使用 Identity 偏移映射。
 */
@Composable
fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
) {
    var showAll by remember { mutableStateOf(false) }
    var revealNonce by remember { mutableIntStateOf(0) }
    var revealing by remember { mutableStateOf(false) }

    LaunchedEffect(revealNonce) {
        if (revealNonce > 0) {
            revealing = true
            delay(3000)
            revealing = false
        }
    }

    val placeholderContent: (@Composable () -> Unit)? = placeholder?.let { hint ->
        { Text(hint) }
    }

    OutlinedTextField(
        value = value,
        onValueChange = {
            onValueChange(it)
            revealNonce++
        },
        label = { Text(label) },
        placeholder = placeholderContent,
        singleLine = true,
        visualTransformation = RevealLastTransformation(showAll, revealing),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        trailingIcon = {
            TextButton(
                onClick = { showAll = !showAll },
                contentPadding = PaddingValues(horizontal = 8.dp),
            ) {
                Text(if (showAll) tr("隐藏", "Hide") else tr("显示", "Show"), fontSize = 12.sp)
            }
        },
        modifier = modifier.fillMaxWidth(),
    )
}

private class RevealLastTransformation(
    private val showAll: Boolean,
    private val revealing: Boolean,
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val visibleChar = { ch: Char -> if (ch == ' ') "␣" else ch.toString() }
        val shown = text.text.mapIndexed { index, ch ->
            when {
                showAll -> visibleChar(ch)
                revealing && index == text.length - 1 -> visibleChar(ch)
                else -> "•"
            }
        }.joinToString("")
        return TransformedText(AnnotatedString(shown), OffsetMapping.Identity)
    }
}

private const val MAX_PASSWORD_LENGTH = 24
