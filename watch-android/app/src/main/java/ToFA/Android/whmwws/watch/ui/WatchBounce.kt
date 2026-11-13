package ToFA.Android.whmwws.watch.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.os.SystemClock
import android.util.Log
import android.view.HapticFeedbackConstants
import android.view.InputDevice
import android.view.MotionEvent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.focusable
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

private const val TAG = "RotaryCrown"

/** 表冠转过多少像素算一次“步进”（震动 + 单屏页面的切换动作）。 */
internal const val RotaryTickPixels = 160f

private const val RotaryIdleResetMs = 320L

/**
 * 边界回弹统一参数：位移上限、停转延时、归位弹簧全部集中在这里。
 * 表冠越界与触控越界都走同一套参数，保证观感一致，后续只改这一处即可微调。
 */
internal object WatchBounceSpec {
    /** 越界位移上限（dp，按密度换算成 px）。 */
    val MaxStretch = 5.dp

    /** 停转 / 停手后多久开始归位。 */
    const val StopDelayMs = 140L

    /** 归位弹簧：阻尼略低于临界阻尼，保留一点点回弹感但不来回晃。 */
    val Settle = spring<Float>(
        dampingRatio = 0.82f,
        stiffness = Spring.StiffnessMedium,
    )
}

/**
 * 表冠滚动：挂在滚动容器自己身上（与子节点同一布局节点），由它负责焦点与实际滚动。
 * 已经在边界时返回 false（不消费），让事件继续冒泡给页面级的 [watchRotaryBounce]，
 * 由那里做 10dp 越界位移 + 弹簧归位。
 *
 * 注意 modifier 顺序：onRotaryScrollEvent 必须写在 focusRequester/focusable 之前，
 * 否则它是焦点节点内侧的修饰，收不到冒泡上来的表冠事件。
 */
@Composable
fun Modifier.watchRotaryScroll(state: ScrollState, page: String): Modifier = composed {
    val focusRequester = remember { FocusRequester() }
    val view = LocalView.current
    var accum by remember { mutableFloatStateOf(0f) }
    var lastAt by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }
    val handle: (Float) -> Boolean = { delta ->
        val forward = delta > 0f
        val atBoundary = if (forward) state.value >= state.maxValue else state.value <= 0f
        state.dispatchRawDelta(delta)
        val now = SystemClock.uptimeMillis()
        if (now - lastAt > RotaryIdleResetMs) accum = 0f
        lastAt = now
        accum += delta
        if (abs(accum) >= RotaryTickPixels) {
            accum = 0f
            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        }
        !atBoundary
    }
    LaunchedEffect(Unit) {
        DebugRotary.events.collect { d ->
            Log.d(TAG, "$page inject delta=$d")
            handle(d)
        }
    }
    onRotaryScrollEvent { handle(it.verticalScrollPixels) }
        .focusRequester(focusRequester)
        .focusable()
}

/** LazyColumn 版本：LazyListState 自带 canScrollForward/canScrollBackward。 */
@Composable
fun Modifier.watchRotaryScroll(state: LazyListState, page: String): Modifier = composed {
    val focusRequester = remember { FocusRequester() }
    val view = LocalView.current
    var accum by remember { mutableFloatStateOf(0f) }
    var lastAt by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }
    val handle: (Float) -> Boolean = { delta ->
        val forward = delta > 0f
        val atBoundary = if (forward) !state.canScrollForward else !state.canScrollBackward
        state.dispatchRawDelta(delta)
        val now = SystemClock.uptimeMillis()
        if (now - lastAt > RotaryIdleResetMs) accum = 0f
        lastAt = now
        accum += delta
        if (abs(accum) >= RotaryTickPixels) {
            accum = 0f
            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        }
        !atBoundary
    }
    LaunchedEffect(Unit) {
        DebugRotary.events.collect { d ->
            Log.d(TAG, "$page inject delta=$d")
            handle(d)
        }
    }
    onRotaryScrollEvent { handle(it.verticalScrollPixels) }
        .focusRequester(focusRequester)
        .focusable()
}

/**
 * 单屏页面（内容不滚动）的表冠步进：每转 [RotaryTickPixels] 触发一次 [onStep]。
 * 始终返回 false，把越界位移交给页面级 [watchRotaryBounce]，不消费事件。
 */
@Composable
fun Modifier.watchRotaryStep(page: String, onStep: (forward: Boolean) -> Unit): Modifier = composed {
    val focusRequester = remember { FocusRequester() }
    val view = LocalView.current
    var accum by remember { mutableFloatStateOf(0f) }
    var lastAt by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }
    val handle: (Float) -> Boolean = { delta ->
        val now = SystemClock.uptimeMillis()
        if (now - lastAt > RotaryIdleResetMs) accum = 0f
        lastAt = now
        accum += delta
        if (abs(accum) >= RotaryTickPixels) {
            val forward = accum > 0
            accum = 0f
            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            onStep(forward)
        }
        false
    }
    LaunchedEffect(Unit) {
        DebugRotary.events.collect { d ->
            Log.d(TAG, "$page inject delta=$d")
            handle(d)
        }
    }
    onRotaryScrollEvent { handle(it.verticalScrollPixels) }
        .focusRequester(focusRequester)
        .focusable()
}

/**
 * 单屏页面「只取焦、不切换、不震动」：只提供一个焦点节点，
 * 让表冠事件能从页面内容冒泡到外层 [watchRotaryBounce] 做越界位移；
 * 页面内的二维码/文字切换等仍只由点击控制。
 */
@Composable
fun Modifier.watchRotaryFocus(page: String): Modifier = composed {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }
    onRotaryScrollEvent { false }
        .focusRequester(focusRequester)
        .focusable()
}

/**
 * 页面级回弹外壳（不抢焦点、不改焦点树、不消费子级已处理的事件）：
 * - 只有子级明确没消费的表冠事件（已到边界）才会冒泡到这里，做越界位移 + 弹簧归位；
 * - 触控越界用 nestedScroll 的剩余位移；两者共用 [WatchBounceSpec] 的参数，观感一致。
 */
@Composable
fun Modifier.watchRotaryBounce(page: String): Modifier = composed {
    val density = LocalDensity.current
    val maxPx = with(density) { WatchBounceSpec.MaxStretch.toPx() }
    val scope = rememberCoroutineScope()
    // 用 Animatable 而不是 animateFloatAsState：目标值只在触摸/表冠事件里改，
    // 位移在 graphicsLayer 的绘制阶段读取，动画期间不写 Composition state、不触发重组。
    val offset = remember { Animatable(0f) }
    val releaseJob = remember { arrayOfNulls<Job>(1) }

    fun scheduleRelease() {
        releaseJob[0]?.cancel()
        releaseJob[0] = scope.launch {
            delay(WatchBounceSpec.StopDelayMs)
            offset.animateTo(0f, WatchBounceSpec.Settle)
        }
    }

    fun push(delta: Float) {
        if (delta == 0f) return
        val next = (offset.value + delta).coerceIn(-maxPx, maxPx)
        scope.launch { offset.snapTo(next) }
        scheduleRelease()
    }

    val connection = remember(maxPx) {
        object : NestedScrollConnection {
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && available.y != 0f) {
                    push(available.y)
                }
                return Offset.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                push(-offset.value)
                return Velocity.Zero
            }
        }
    }

    // debug 专用：单独验证“回弹层”，不影响真实输入链路
    LaunchedEffect(Unit) {
        DebugRotary.bounceEvents.collect { d ->
            Log.d(TAG, "$page inject bounce delta=$d")
            push(-d)
        }
    }

    onRotaryScrollEvent { event ->
        push(-event.verticalScrollPixels)
        true
    }
        .nestedScroll(connection)
        .graphicsLayer { translationY = offset.value }
}

/** 仅 debug 生效：把合成表冠位移喂进与真实表冠相同的处理函数，用于真机取证。 */
object DebugRotary {
    const val ACTION = "ToFA.DEBUG.ROTARY"
    const val BOUNCE_ACTION = "ToFA.DEBUG.BOUNCE"
    /** 走真实输入分发链路（SOURCE_ROTARY_ENCODER + ACTION_SCROLL），用于验证焦点/修饰符顺序。 */
    const val HW_ACTION = "ToFA.DEBUG.ROTARY_HW"
    val events = kotlinx.coroutines.flow.MutableSharedFlow<Float>(extraBufferCapacity = 256)
    val bounceEvents = kotlinx.coroutines.flow.MutableSharedFlow<Float>(extraBufferCapacity = 256)
}

private fun rotaryMotionEvent(deltaPx: Float, timeMs: Long): MotionEvent {
    val props = MotionEvent.PointerProperties().apply {
        id = 0
        toolType = MotionEvent.TOOL_TYPE_UNKNOWN
    }
    val coords = MotionEvent.PointerCoords().apply {
        x = 233f
        y = 233f
        setAxisValue(MotionEvent.AXIS_VSCROLL, deltaPx)
        setAxisValue(MotionEvent.AXIS_SCROLL, deltaPx)
    }
    return MotionEvent.obtain(
        0L,
        timeMs,
        MotionEvent.ACTION_SCROLL,
        1,
        arrayOf(props),
        arrayOf(coords),
        0,
        0,
        1f,
        1f,
        0,
        0,
        InputDevice.SOURCE_ROTARY_ENCODER,
        0,
    )
}

/** debug 包内注册合成表冠广播入口（release 包自动跳过）。 */
@Composable
fun DebugRotaryReceiver() {
    val context = LocalContext.current
    val view = LocalView.current
    val debuggable = remember(context) {
        (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    }
    if (!debuggable) return
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                val delta = intent?.getFloatExtra("delta", 0f) ?: 0f
                Log.d(TAG, "inject delta=$delta action=${intent?.action}")
                when (intent?.action) {
                    DebugRotary.BOUNCE_ACTION -> DebugRotary.bounceEvents.tryEmit(delta)
                    DebugRotary.HW_ACTION -> {
                        val event = rotaryMotionEvent(delta, SystemClock.uptimeMillis())
                        val handled = view.dispatchGenericMotionEvent(event)
                        event.recycle()
                        Log.d(TAG, "hw dispatch delta=$delta handled=$handled view=${view.javaClass.simpleName}")
                    }
                    else -> DebugRotary.events.tryEmit(delta)
                }
            }
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(DebugRotary.ACTION).apply {
                addAction(DebugRotary.BOUNCE_ACTION)
                addAction(DebugRotary.HW_ACTION)
            },
            ContextCompat.RECEIVER_EXPORTED,
        )
        onDispose { runCatching { context.unregisterReceiver(receiver) } }
    }
}
