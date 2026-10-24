package com.sift.app.presentation.widget

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import androidx.core.content.res.use
import com.sift.app.R
import com.sift.app.presentation.common.Motion

/**
 * Skeleton loader container. Children are grey placeholder shapes; a soft
 * highlight band sweeps across them. The band is composited with SRC_ATOP so
 * it only lights up the placeholders, never the background. When animations
 * are disabled the placeholders are shown without the sweep.
 */
class ShimmerLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP)
    }
    private val shaderMatrix = Matrix()
    private var highlightColor = ContextCompat.getColor(context, R.color.sift_skeleton_highlight)
    private var autoStart = true
    private var animator: ValueAnimator? = null
    private var offset = 0f
    private var bandWidth = 0f

    init {
        context.obtainStyledAttributes(attrs, R.styleable.ShimmerLayout, defStyleAttr, 0).use { a ->
            highlightColor = a.getColor(R.styleable.ShimmerLayout_shimmerHighlightColor, highlightColor)
            autoStart = a.getBoolean(R.styleable.ShimmerLayout_shimmerAutoStart, true)
        }
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        bandWidth = w * BAND_FRACTION
        val transparent = Color.argb(0, Color.red(highlightColor), Color.green(highlightColor), Color.blue(highlightColor))
        paint.shader = LinearGradient(
            0f, 0f, bandWidth, 0f,
            intArrayOf(transparent, highlightColor, transparent),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP
        )
        if (animator != null) restart()
    }

    fun startShimmer() {
        if (animator != null || width == 0 || !Motion.enabled(context)) return
        animator = ValueAnimator.ofFloat(-bandWidth, width + bandWidth).apply {
            duration = context.resources.getInteger(R.integer.shimmer_cycle).toLong()
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                offset = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    fun stopShimmer() {
        animator?.cancel()
        animator = null
        invalidate()
    }

    private fun restart() {
        stopShimmer()
        startShimmer()
    }

    override fun dispatchDraw(canvas: Canvas) {
        if (animator == null || width == 0 || height == 0) {
            super.dispatchDraw(canvas)
            return
        }
        val layer = canvas.saveLayer(0f, 0f, width.toFloat(), height.toFloat(), null)
        super.dispatchDraw(canvas)
        shaderMatrix.setTranslate(offset, 0f)
        paint.shader?.setLocalMatrix(shaderMatrix)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        canvas.restoreToCount(layer)
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (!autoStart) return
        if (visibility == View.VISIBLE && isShown) post { startShimmer() } else stopShimmer()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (autoStart && isShown) post { startShimmer() }
    }

    override fun onDetachedFromWindow() {
        stopShimmer()
        super.onDetachedFromWindow()
    }

    private companion object {
        const val BAND_FRACTION = 0.6f
    }
}
