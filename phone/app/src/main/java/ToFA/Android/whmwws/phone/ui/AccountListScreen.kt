package ToFA.Android.whmwws.phone.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ToFA.Android.whmwws.phone.data.DisplayNameMode
import ToFA.Android.whmwws.phone.data.TotpAccount
import ToFA.Android.whmwws.phone.otp.TotpEngine
import ToFA.Android.whmwws.phone.security.ClipboardGuard
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 手机端主页：
 * 左侧 ToFA 标志，右侧传递 / 设置；账号卡片支持拖动排序；
 * 开启复制后轻点卡片复制 6 位动态码，开启二维码后卡片右侧出现二维码按钮。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountListScreen(
    accounts: List<TotpAccount>,
    displayNameMode: DisplayNameMode,
    copyEnabled: Boolean,
    qrEnabled: Boolean,
    codesHidden: Boolean,
    onRevealCodes: () -> Unit,
    showPremiumBanner: Boolean,
    onAdd: () -> Unit,
    onSettings: () -> Unit,
    onTransfer: () -> Unit,
    onDelete: (String) -> Unit,
    onMove: (Int, Int) -> Unit,
    onDonate: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var qrAccount by remember { mutableStateOf<TotpAccount?>(null) }
    var draggingId by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val currentAccounts by rememberUpdatedState(accounts)
    val density = LocalDensity.current
    val thresholdPx = with(density) { 52.dp.toPx() }

    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(300)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(start = 4.dp),
                    ) {
                        Icon(
                            Icons.Filled.Security,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "ToFA",
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            letterSpacing = 0.5.sp,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onTransfer) {
                        Icon(Icons.Filled.SwapVert, contentDescription = tr("传递", "Transfer"))
                    }
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = tr("设置", "Settings"))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAdd) {
                Icon(Icons.Filled.Add, contentDescription = tr("添加密钥", "Add key"))
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        if (accounts.isEmpty()) {
            Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                if (showPremiumBanner) {
                    Box(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        PremiumBanner(onDonate = onDonate)
                    }
                }
                Box(
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Filled.Security,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(44.dp),
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(tr("还没有密钥", "No keys yet"), style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            tr(
                                "点右下角 + 手动输入密钥、扫描 otpauth 二维码，\n或从设置 → 传递 迁移",
                                "Tap + at the bottom right to enter a key, scan an otpauth QR code,\nor migrate from Settings → Transfer",
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        } else {
            Box(Modifier.fillMaxSize()) {
            val listState = rememberLazyListState()
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(padding)
                    .scrollProgressBar(listState, MaterialTheme.colorScheme.primary),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (showPremiumBanner) {
                    item(key = "premium-banner") {
                        PremiumBanner(onDonate = onDonate)
                    }
                }
                itemsIndexed(accounts, key = { _, account -> account.id }) { index, account ->
                    val code = runCatching { TotpEngine.code(account, now) }.getOrDefault("------")
                    val period = account.period.coerceAtLeast(1)
                    val remain = if (account.type == "HOTP") period else TotpEngine.secondsLeft(period, now)
                    val urgent = account.type != "HOTP" && remain <= 5
                    val dragging = draggingId == account.id
                    AccountCard(
                        account = account,
                        displayNameMode = displayNameMode,
                        code = code,
                        remain = remain,
                        period = period,
                        urgent = urgent,
                        copyEnabled = copyEnabled,
                        qrEnabled = qrEnabled,
                        dragging = dragging,
                        onCopy = {
                            // TOTP 在验证码刷新时清空；HOTP 不刷新，沿用 30 秒兜底
                            val clearAfterMs = if (account.type == "HOTP") 30_000L else remain * 1_000L
                            if (ClipboardGuard.copy(
                                    context,
                                    I18n.pick("ToFA 验证码", "ToFA code"),
                                    code.filter { ch -> ch.isDigit() },
                                    clearAfterMs,
                                )
                            ) {
                                scope.launch {
                                    snackbar.showSnackbar(
                                        I18n.pick(
                                            "已复制验证码，更新后自动清空剪贴板",
                                            "Code copied — the clipboard clears when it refreshes",
                                        ),
                                    )
                                }
                            }
                        },
                        onShowQr = { qrAccount = account },
                        onDelete = { onDelete(account.id) },
                        modifier = Modifier
                            .animateItem(
                                fadeInSpec = null,
                                fadeOutSpec = null,
                                placementSpec = tween(durationMillis = 220, easing = LinearEasing),
                            )
                            .pointerInput(account.id) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = {
                                    draggingId = account.id
                                    dragOffset = 0f
                                },
                                onDrag = { change, drag ->
                                    change.consume()
                                    dragOffset += drag.y
                                    val currentIndex = currentAccounts.indexOfFirst { it.id == account.id }
                                    if (currentIndex >= 0) {
                                        when {
                                            dragOffset > thresholdPx && currentIndex < currentAccounts.lastIndex -> {
                                                onMove(currentIndex, currentIndex + 1)
                                                dragOffset -= thresholdPx
                                            }
                                            dragOffset < -thresholdPx && currentIndex > 0 -> {
                                                onMove(currentIndex, currentIndex - 1)
                                                dragOffset += thresholdPx
                                            }
                                        }
                                    }
                                },
                                onDragEnd = {
                                    draggingId = null
                                    dragOffset = 0f
                                },
                                onDragCancel = {
                                    draggingId = null
                                    dragOffset = 0f
                                },
                            )
                        },
                    )
                }
                item(key = "footer") {
                    Text(
                        text = if (copyEnabled) {
                            tr("轻点复制 · 长按拖动排序", "Tap to copy · long-press to reorder")
                        } else {
                            tr("长按拖动排序", "Long-press to reorder")
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 10.dp),
                    )
                }
            }
            // 开屏隐私：关闭「启动时显示验证码」后先显示遮挡页，点按才显示
            if (codesHidden) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                        .pointerInput(Unit) { detectTapGestures { onRevealCodes() } },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = tr("点按显示验证码", "Tap to show codes"),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            }
        }
    }

    qrAccount?.let { account ->
        AccountQrDialog(account = account, onDismiss = { qrAccount = null })
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AccountCard(
    account: TotpAccount,
    displayNameMode: DisplayNameMode,
    code: String,
    remain: Int,
    period: Int,
    urgent: Boolean,
    copyEnabled: Boolean,
    qrEnabled: Boolean,
    dragging: Boolean,
    onCopy: () -> Unit,
    onShowQr: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = if (urgent) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = copyEnabled, onClick = onCopy),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (dragging) {
                MaterialTheme.colorScheme.surfaceVariant
            } else {
                MaterialTheme.colorScheme.surface
            },
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 10.dp, top = 10.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 拖动把手：仅作视觉提示，长按拖动由整张卡片统一处理，避免两个手势竞争
            Box(
                modifier = Modifier
                    .width(34.dp)
                    .height(76.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.DragHandle,
                    contentDescription = tr("拖动排序", "Reorder"),
                    tint = if (dragging) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                val unnamed = tr("未命名", "Unnamed")
                val title = when (displayNameMode) {
                    DisplayNameMode.AccountOnly ->
                        account.account.ifBlank { account.issuer.ifBlank { unnamed } }
                    DisplayNameMode.SecretOnly, DisplayNameMode.Both ->
                        account.issuer.ifBlank { account.account.ifBlank { unnamed } }
                }
                val subtitle = when (displayNameMode) {
                    DisplayNameMode.SecretOnly -> ""
                    DisplayNameMode.AccountOnly -> account.issuer
                    DisplayNameMode.Both -> account.account
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).basicMarquee(),
                    )
                    Text(
                        text = if (account.type == "HOTP") "HOTP" else "${remain}s",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (urgent) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (subtitle.isNotBlank() && subtitle != title) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth().basicMarquee(),
                    )
                }
                AnimatedContent(
                    targetState = code,
                    transitionSpec = {
                        fadeIn(tween(220)) togetherWith fadeOut(tween(120))
                    },
                    label = "otpCode",
                ) { currentCode ->
                    Text(
                        text = currentCode.chunked(3).joinToString(" "),
                        fontSize = 30.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 2.sp,
                        color = accent,
                    )
                }
                Spacer(Modifier.height(4.dp))
                // 自绘进度条：匀速缩短、无停止墨点
                CountdownBar(
                    progress = remain.toFloat() / period.toFloat(),
                    color = accent,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
            }

            if (qrEnabled) {
                IconButton(onClick = onShowQr) {
                    Icon(
                        Icons.Filled.QrCode2,
                        contentDescription = tr("生成二维码", "Show QR code"),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = tr("删除", "Delete"),
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

/** 匀速缩短的倒计时条：用 LinearEasing 平滑过渡，去掉 Material 进度条末端的停止墨点。 */
@Composable
private fun CountdownBar(progress: Float, color: Color, trackColor: Color) {
    val animated by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 320, easing = LinearEasing),
        label = "countdown",
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(3.dp)
            .clip(CircleShape)
            .background(trackColor),
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(animated)
                .clip(CircleShape)
                .background(color),
        )
    }
}

@Composable
private fun PremiumBanner(
    onDonate: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Security,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = tr("激活 ToFA 高级功能", "Activate ToFA Premium"),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = tr(
                    "使用多个密钥或高级功能时，可在此完成激活，并连接手表同步激活状态。",
                    "With multiple keys or premium features, activate here and sync the activation to your watch.",
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = onDonate) {
                    Text(tr("去捐赠", "Donate"), fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
