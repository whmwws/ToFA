package ToFA.Android.whmwws.watch.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.annotation.ColorInt

/**
 * 冷启动加载动画（纯 View 实现）。
 *
 * 用普通 View 而不是 Compose，是为了让加载环在 Activity 第一帧就出现：
 * Compose 首次初始化在老设备上要 1 秒以上，纯 View 的首帧只要几十毫秒。
 * 只画一圈主题色脉冲弧，不读盘、不做任何初始化工作。
 */
class WatchSplashView(
    context: Context,
    @ColorInt backgroundColor: Int,
    @ColorInt accentColor: Int,
) : View(context) {

    private val backgroundPaint = Paint().apply {
        isAntiAlias = true
        color = backgroundColor
        style = Paint.Style.FILL
    }

    private val trackPaint = Paint().apply {
        isAntiAlias = true
        color = accentColor
        alpha = 41 // 约 16%
        style = Paint.Style.STROKE
    }

    private val arcPaint = Paint().apply {
        isAntiAlias = true
        color = accentColor
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    private var progress = 0f

    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 900
        repeatCount = ValueAnimator.INFINITE
        repeatMode = ValueAnimator.REVERSE
        interpolator = AccelerateDecelerateInterpolator()
        addUpdateListener {
            progress = it.animatedValue as Float
            invalidate()
        }
    }

    init {
        isClickable = true
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        animator.start()
    }

    override fun onDetachedFromWindow() {
        animator.cancel()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), backgroundPaint)

        val stroke = 2.5f * resources.displayMetrics.density
        val cx = width / 2f
        val cy = height / 2f
        val radius = 22f * resources.displayMetrics.density - stroke / 2f

        trackPaint.strokeWidth = stroke
        canvas.drawCircle(cx, cy, radius, trackPaint)

        arcPaint.strokeWidth = stroke
        arcPaint.alpha = (140 + 115 * progress).toInt() // 55% → 100%
        canvas.drawArc(
            cx - radius,
            cy - radius,
            cx + radius,
            cy + radius,
            -90f,
            90f + 150f * progress,
            false,
            arcPaint,
        )
    }
}
