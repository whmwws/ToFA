package ToFA.Android.whmwws.watch.ui

import android.content.res.Configuration
import android.os.SystemClock
import android.util.Log
import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.composed
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ToFA.Android.whmwws.watch.data.DisplayNameMode
import ToFA.Android.whmwws.watch.data.TotpAccount
import ToFA.Android.whmwws.watch.otp.TotpEngine
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sqrt

private const val ROTARY_THRESHOLD = 160f
private const val ROTARY_IDLE_RESET_MS = 300L

/** 圆形页面开关（由根界面提供；关闭时使用方形布局，切换时所有页面同步重排并做弹簧过渡）。 */
val LocalWatchSquareUi = compositionLocalOf { false }

/** 圆形表盘判断：仅在真机为圆屏且用户开启“圆形页面”时使用圆形布局。 */
@Composable
fun isRoundScreen(): Boolean {
    val config = LocalConfiguration.current
    val round = (config.screenLayout and Configuration.SCREENLAYOUT_ROUND_MASK) ==
        Configuration.SCREENLAYOUT_ROUND_YES
    if (!round) return false
    return !LocalWatchSquareUi.current
}

/**
 * 圆屏弦长自适应水平内边距：元素离表盘中心越远（越靠上/下边缘），
 * 水平内缩越多，让按钮与文字贴着圆盘弧线排布，不被圆边裁切。
 * 方屏（或强制方形布局）只保留基础边距。滚动时逐元素实时生效。
 */
@Composable
fun Modifier.circularEdgePadding(base: Dp = WatchPagePadding): Modifier = composed {
    if (!isRoundScreen()) return@composed padding(horizontal = base)
    val density = LocalDensity.current
    val config = LocalConfiguration.current
    val radiusPx = with(density) { (minOf(config.screenWidthDp, config.screenHeightDp) / 2f).dp.toPx() }
    val basePx = with(density) { base.toPx() }
    var padPx by remember { mutableFloatStateOf(basePx) }
    onGloballyPositioned { coords ->
        val dy = abs(coords.boundsInWindow().center.y - radiusPx)
        val inset = radiusPx - sqrt((radiusPx * radiusPx - dy * dy).coerceAtLeast(0f).toDouble()).toFloat()
        val next = basePx + inset * 0.92f
        // 同一像素才写状态，避免滚动时逐帧重组
        if (kotlin.math.abs(next - padPx) >= 1f) padPx = next
    }.padding(horizontal = with(density) { padPx.toDp() })
}

/**
 * 圆屏滚动页的静态安全边距：把内容限制在圆盘中段弦长内，
 * 配合 [RoundScrollPageVerticalPadding] 的上下留白，滚到底也不会被弧边裁切。
 */
internal val RoundScrollEdgePadding = 28.dp
internal val RoundScrollPageVerticalPadding = 30.dp

/**
 * 滚动容器内使用的固定水平边距。
 * `circularEdgePadding` 依赖 onGloballyPositioned，滚动时每个元素位置每帧变化，
 * 会写入状态并触发该行重组 + 重新测量（「其他」页拖动卡顿的根因）。
 * 滚动页因此改用常量边距：圆屏取安全值 28dp，方屏保持统一的 16dp，零逐帧状态写入。
 */
@Composable
fun Modifier.scrollableEdgePadding(base: Dp = WatchPagePadding): Modifier =
    padding(horizontal = if (isRoundScreen()) RoundScrollEdgePadding else base)

/**
 * 主视图：圆形表盘上单账号一页，上下滑动/旋转表冠翻页。
 * 每页展示账号名、验证码，以及（设置开启时）与环形倒计时同排的二维码按钮。
 */
@Composable
fun MainScreen(
    accounts: List<TotpAccount>,
    displayNameMode: DisplayNameMode,
    pagerState: PagerState,
    now: Long,
    codesHidden: Boolean,
    onRevealCodes: () -> Unit,
    onOpenMenu: () -> Unit,
    onAdd: () -> Unit,
    qrEnabled: Boolean,
    copyEnabled: Boolean,
    onShowQr: (TotpAccount) -> Unit,
    onCopyCode: (String, Long) -> Unit,
) {
    if (accounts.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize().watchRotaryBounce("home-empty")) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) { detectTapGestures(onLongPress = { onOpenMenu() }) },
            ) {
                EmptyState(onAdd = onAdd)
            }
        }
    } else {
        val scope = rememberCoroutineScope()
        val focusRequester = remember { FocusRequester() }
        val view = LocalView.current
        var rotaryAccum by remember { mutableFloatStateOf(0f) }
        var lastRotaryAt by remember { mutableLongStateOf(0L) }
        LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }

        // 表冠步进翻页；到首/尾页时返回 false，交给外层页面容器做越界位移
        fun handleHomeRotary(delta: Float): Boolean {
            val nowMs = SystemClock.uptimeMillis()
            if (nowMs - lastRotaryAt > ROTARY_IDLE_RESET_MS) rotaryAccum = 0f
            lastRotaryAt = nowMs
            rotaryAccum += delta
            val forward = delta > 0f
            val atBoundary = if (forward) pagerState.currentPage >= accounts.size - 1
            else pagerState.currentPage <= 0
            if (!atBoundary && abs(rotaryAccum) >= ROTARY_THRESHOLD) {
                val dirForward = rotaryAccum > 0
                rotaryAccum = 0f
                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                val next = if (dirForward) {
                    (pagerState.currentPage + 1).coerceAtMost(accounts.size - 1)
                } else {
                    (pagerState.currentPage - 1).coerceAtLeast(0)
                }
                if (next != pagerState.currentPage) {
                    scope.launch { pagerState.animateScrollToPage(next, animationSpec = WatchSpringSpec) }
                }
            }
            return !atBoundary
        }
        LaunchedEffect(Unit) {
            DebugRotary.events.collect { d ->
                Log.d("RotaryCrown", "home inject delta=$d")
                handleHomeRotary(d)
            }
        }

        Box(modifier = Modifier.fillMaxSize().watchRotaryBounce("home")) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .onRotaryScrollEvent { event ->
                        handleHomeRotary(event.verticalScrollPixels)
                    }
                    .focusRequester(focusRequester)
                    .focusable(),
            ) {
            VerticalPager(
                state = pagerState,
                userScrollEnabled = true,
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                AccountPage(
                    account = accounts[page],
                    displayNameMode = displayNameMode,
                    now = now,
                    qrEnabled = qrEnabled,
                    copyEnabled = copyEnabled,
                    onShowQr = { onShowQr(accounts[page]) },
                    onOpenMenu = onOpenMenu,
                    onCopyCode = onCopyCode,
                )
            }
            AccountDots(
                total = accounts.size,
                current = pagerState.currentPage,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = if (isRoundScreen()) 14.dp else 8.dp),
            )
            // 开屏隐私：关闭「启动时显示验证码」后，进入应用先显示可点击的遮挡页
            if (codesHidden) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(WatchColors.Background)
                        .pointerInput(Unit) { detectTapGestures { onRevealCodes() } },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = tr("点按显示验证码", "Tap to show codes"),
                        color = WatchColors.TextDim,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AccountPage(
    account: TotpAccount,
    displayNameMode: DisplayNameMode,
    now: Long,
    qrEnabled: Boolean,
    copyEnabled: Boolean,
    onShowQr: () -> Unit,
    onOpenMenu: () -> Unit,
    onCopyCode: (String, Long) -> Unit,
) {
    val round = isRoundScreen()
    val code = runCatching { TotpEngine.code(account, now) }.getOrDefault("------")
    val period = account.period.coerceAtLeast(1)
    val isHotp = account.type == "HOTP"
    val remain = if (isHotp) period else TotpEngine.secondsLeft(period, now)
    // 最后 5 秒进入警示状态：倒计时与验证码统一变红
    val urgent = !isHotp && remain <= 5

    val codeColor by animateColorAsState(
        targetValue = if (urgent) WatchColors.Red else WatchColors.Green,
        animationSpec = tween(durationMillis = 300),
        label = "codeColor",
    )

    // 手势识别器只创建一次，用最新状态保证复制的是当前验证码
    val latestCode by rememberUpdatedState(code)
    val latestClearAfter by rememberUpdatedState(if (isHotp) 30_000L else remain * 1_000L)
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(account.id, copyEnabled) {
                detectTapGestures(
                    onTap = {
                        if (copyEnabled) {
                            // 剪贴板在验证码更新时清空；HOTP 无更新，30 秒兜底
                            onCopyCode(latestCode, latestClearAfter)
                        }
                    },
                    onLongPress = { onOpenMenu() },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        val diameter = if (maxWidth < maxHeight) maxWidth else maxHeight
        // 圆形屏往内收，方形屏留常规边距，字号按屏径等比缩放
        val horizontalPadding = if (round) diameter * 0.115f else WatchPagePadding
        val codeSize = (diameter.value * 0.11f).coerceIn(18f, 30f).sp
        val titleSize = (diameter.value * 0.062f).coerceIn(11f, 18f).sp
        val ringSize = (diameter.value * 0.17f).coerceIn(34f, 48f).dp
        // 「都显示」：密钥名与账户名分两行，密钥名为主、账户名为次；
        // 其余两档仍保持单行，避免首页高度变化挤压验证码与光环。
        val twoLine = displayNameMode == DisplayNameMode.Both &&
            account.issuer.isNotBlank() && account.account.isNotBlank() &&
            account.issuer != account.account
        val secondarySize = (titleSize.value * 0.8f).coerceAtLeast(9f).sp

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = horizontalPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            if (twoLine) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = account.issuer,
                        color = WatchColors.TextDim,
                        fontSize = titleSize,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().basicMarquee(),
                    )
                    Text(
                        text = account.account,
                        color = WatchColors.TextFaint,
                        fontSize = secondarySize,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().basicMarquee(),
                    )
                }
            } else {
                Text(
                    text = account.displayTitle(displayNameMode),
                    color = WatchColors.TextDim,
                    fontSize = titleSize,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().basicMarquee(),
                )
            }
            Spacer(Modifier.height(if (twoLine) 4.dp else 6.dp))
            // 验证码换码时淡入 + 轻微放大，作为过渡动画
            AnimatedContent(
                targetState = formatCode(code),
                transitionSpec = {
                    (fadeIn(tween(220)) + scaleIn(initialScale = 0.9f, animationSpec = WatchSpringSpec))
                        .togetherWith(fadeOut(tween(160)))
                },
                label = "code",
            ) { text ->
                Text(
                    text = text,
                    color = codeColor,
                    fontSize = codeSize,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.sp,
                    maxLines = 1,
                )
            }
            Spacer(Modifier.height(if (twoLine) 9.dp else 12.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                CountdownRing(
                    remain = remain,
                    period = period,
                    isHotp = isHotp,
                    urgent = urgent,
                    ringSize = ringSize,
                )
                if (qrEnabled) {
                    OutlinedButton(
                        onClick = onShowQr,
                        modifier = Modifier.height(34.dp),
                        contentPadding = ButtonDefaults.ContentPadding,
                    ) { Text(tr("二维码", "QR code"), fontSize = 11.sp) }
                }
            }
        }
    }
}

/** 环形倒计时：进度随剩余秒数平滑收缩，最后 5 秒整体变红 */
@Composable
private fun CountdownRing(
    remain: Int,
    period: Int,
    isHotp: Boolean,
    urgent: Boolean,
    ringSize: androidx.compose.ui.unit.Dp,
) {
    val targetRatio = if (isHotp) 1f else remain.toFloat() / period
    val ratio by animateFloatAsState(
        targetValue = targetRatio,
        animationSpec = tween(durationMillis = 1000, easing = LinearEasing),
        label = "ringSweep",
    )
    val tint by animateColorAsState(
        targetValue = if (urgent) WatchColors.Red else WatchColors.Green,
        animationSpec = tween(durationMillis = 300),
        label = "ringColor",
    )
    val numberColor by animateColorAsState(
        targetValue = if (urgent) WatchColors.Red else WatchColors.Text,
        animationSpec = tween(durationMillis = 300),
        label = "ringNumber",
    )

    Box(modifier = Modifier.size(ringSize), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = (ringSize.value * 0.075f).dp.toPx()
            val inset = stroke / 2
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(
                color = WatchColors.DotOff,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            drawArc(
                color = tint,
                startAngle = -90f,
                sweepAngle = 360f * ratio.coerceIn(0f, 1f),
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }
        Text(
            text = if (isHotp) "H" else remain.toString(),
            color = numberColor,
            fontSize = (ringSize.value * 0.275f).sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun AccountDots(total: Int, current: Int, modifier: Modifier = Modifier) {
    if (total <= 8) {
        Column(
            modifier = modifier,
            verticalArrangement = Arrangement.spacedBy(5.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            repeat(total) { index ->
                Box(
                    modifier = Modifier
                        .size(5.dp)
                        .clip(CircleShape)
                        .background(if (index == current) WatchColors.Green else WatchColors.DotOff),
                )
            }
        }
    } else {
        Text(
            text = "${current + 1}/$total",
            modifier = modifier,
            color = WatchColors.TextFaint,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun EmptyState(onAdd: () -> Unit) {
    val round = isRoundScreen()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = if (round) 40.dp else WatchPagePadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(tr("暂无密钥", "No keys yet"), color = WatchColors.TextDim, fontSize = 13.sp)
        Spacer(Modifier.height(6.dp))
        Text(
            tr(
                "长按表盘打开菜单\n支持传递 / 唯一码",
                "Long-press the dial for menu\nTransfer / Unique code",
            ),
            color = WatchColors.TextDim,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            lineHeight = 14.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = onAdd,
            modifier = Modifier.height(34.dp),
            contentPadding = ButtonDefaults.ContentPadding,
        ) {
            Text(tr("添加账号", "Add account"), fontSize = 11.sp)
        }
    }
}

private fun formatCode(code: String): String = when (code.length) {
    6 -> code.substring(0, 3) + " " + code.substring(3)
    8 -> code.substring(0, 4) + " " + code.substring(4)
    else -> code
}
