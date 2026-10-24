package com.sift.app.presentation.widget

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.core.content.ContextCompat
import com.sift.app.R
import com.sift.app.presentation.common.Motion

/**
 * A soft band of light that sweeps across a pill-shaped button every few seconds.
 * Decorative only: it never takes touches and stays hidden when animations are off.
 */
class ShineView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val bandWidth = resources.getDimension(R.dimen.shine_width)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val clip = Path()
    private val bounds = RectF()
    private var progress = -1f
    private var animator: ValueAnimator? = null

    init {
        isClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        val light = ContextCompat.getColor(context, R.color.sift_shine)
        val clear = light and TRANSPARENT_MASK
        paint.shader = LinearGradient(0f, 0f, bandWidth, 0f, intArrayOf(clear, light, clear), null, Shader.TileMode.CLAMP)
    }

    /** Never drives its parent's size: takes zero unless given an exact size (match_parent in a measured parent). */
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(resolveSize(0, widthMeasureSpec), resolveSize(0, heightMeasureSpec))
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        bounds.set(0f, 0f, w.toFloat(), h.toFloat())
        clip.reset()
        clip.addRoundRect(bounds, h / 2f, h / 2f, Path.Direction.CW)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (progress !in 0f..1f) return
        val x = -bandWidth + (width + bandWidth * 2) * progress
        canvas.save()
        canvas.clipPath(clip)
        canvas.translate(x, 0f)
        canvas.skew(SKEW, 0f)
        canvas.drawRect(0f, 0f, bandWidth, height.toFloat(), paint)
        canvas.restore()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (!Motion.enabled(context)) return
        animator = ValueAnimator.ofFloat(0f, SWEEP_WITH_PAUSE).apply {
            duration = resources.getInteger(R.integer.shine_cycle).toLong()
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                progress = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        animator = null
        super.onDetachedFromWindow()
    }

    private companion object {
        /** The sweep covers 0..1; the rest of the cycle is a pause between sweeps. */
        const val SWEEP_WITH_PAUSE = 2.2f
        const val SKEW = -0.3f
        const val TRANSPARENT_MASK = 0x00FFFFFF
    }
}
