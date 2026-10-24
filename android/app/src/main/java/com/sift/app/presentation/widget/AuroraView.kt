package com.sift.app.presentation.widget

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.annotation.ColorInt
import androidx.core.content.ContextCompat
import com.sift.app.R
import com.sift.app.presentation.common.Motion
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Soft ambient glows that drift slowly behind hero areas. Purely decorative:
 * hidden from accessibility, static when animations are off, and paused while
 * the view is detached or hidden.
 */
class AuroraView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    /** A glow centred at a fraction of the view size that orbits by a fraction of its radius. */
    private class Blob(
        @ColorInt val color: Int,
        val centerX: Float,
        val centerY: Float,
        val radiusFraction: Float,
        val orbitFraction: Float,
        val phase: Float,
        val speed: Float
    ) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        var radius = 0f
    }

    private val blobs = listOf(
        Blob(color(R.color.sift_glow_green), 0.85f, 0.08f, 0.75f, 0.10f, 0f, 1f),
        Blob(color(R.color.sift_glow_blue), 0.10f, 0.30f, 0.70f, 0.12f, PI.toFloat() * 0.6f, -0.8f),
        Blob(color(R.color.sift_glow_cyan), 0.55f, 0.65f, 0.55f, 0.14f, PI.toFloat() * 1.3f, 0.6f)
    )

    private var angle = 0f
    private var parallax = 0f
    private var animator: ValueAnimator? = null

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    /** Shifts the glows by a fraction of [scrollY] so they trail behind scrolling content. */
    fun setScrollOffset(scrollY: Int) {
        parallax = -scrollY * PARALLAX_FACTOR
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val base = maxOf(w, h).toFloat()
        blobs.forEach { blob ->
            blob.radius = (base * blob.radiusFraction).coerceAtLeast(1f)
            blob.paint.shader = RadialGradient(
                0f, 0f, blob.radius,
                intArrayOf(blob.color, blob.color and TRANSPARENT_MASK),
                null, Shader.TileMode.CLAMP
            )
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        blobs.forEach { blob ->
            val orbit = blob.radius * blob.orbitFraction
            val theta = angle * blob.speed + blob.phase
            val x = width * blob.centerX + cos(theta) * orbit
            val y = height * blob.centerY + sin(theta) * orbit + parallax
            canvas.save()
            canvas.translate(x, y)
            canvas.drawCircle(0f, 0f, blob.radius, blob.paint)
            canvas.restore()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        start()
    }

    override fun onDetachedFromWindow() {
        stop()
        super.onDetachedFromWindow()
    }

    override fun onVisibilityAggregated(isVisible: Boolean) {
        super.onVisibilityAggregated(isVisible)
        if (isVisible) start() else stop()
    }

    private fun start() {
        if (animator != null || !isAttachedToWindow || !Motion.enabled(context)) return
        animator = ValueAnimator.ofFloat(0f, FULL_TURN).apply {
            duration = resources.getInteger(R.integer.aurora_cycle).toLong()
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                angle = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    private fun stop() {
        animator?.cancel()
        animator = null
    }

    @ColorInt
    private fun color(id: Int) = ContextCompat.getColor(context, id)

    private companion object {
        const val FULL_TURN = (2 * PI).toFloat()
        const val PARALLAX_FACTOR = 0.35f
        const val TRANSPARENT_MASK = 0x00FFFFFF
    }
}
