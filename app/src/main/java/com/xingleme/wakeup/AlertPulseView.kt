package com.xingleme.wakeup

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.ContextCompat
import kotlin.math.min

/**
 * 提醒弹窗背后的呼吸波纹，让“被叫醒”的感觉更明确。
 */
class AlertPulseView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val density = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * density
    }
    private var phase = 0f
    private val color: Int = runCatching {
        val typed = context.obtainStyledAttributes(
            intArrayOf(androidx.appcompat.R.attr.colorPrimary)
        )
        val value = typed.getColor(0, ContextCompat.getColor(context, R.color.lime_primary))
        typed.recycle()
        value
    }.getOrDefault(ContextCompat.getColor(context, R.color.lime_primary))

    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 2100L
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            phase = it.animatedValue as Float
            invalidate()
        }
    }

    init {
        // 避免被误认为可点击元素
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (!animator.isStarted) animator.start()
    }

    override fun onDetachedFromWindow() {
        animator.cancel()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val maxRadius = min(width, height) / 2f - 2f * density
        val base = maxRadius * 0.58f
        for (i in 0 until 3) {
            val progress = (phase + i / 3f) % 1f
            val radius = base + (maxRadius - base) * progress
            val alpha = ((1f - progress) * 82f).toInt().coerceIn(0, 255)
            paint.color = Color.argb(
                alpha,
                Color.red(color),
                Color.green(color),
                Color.blue(color)
            )
            canvas.drawCircle(cx, cy, radius, paint)
        }
    }
}
