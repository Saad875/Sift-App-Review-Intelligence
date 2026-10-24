package com.sift.app.presentation.widget

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.text.TextPaint
import android.util.AttributeSet
import android.view.View
import androidx.annotation.ColorInt
import androidx.core.content.ContextCompat
import androidx.core.content.res.use
import com.sift.app.R
import com.sift.app.presentation.common.Motion
import com.sift.app.presentation.common.appTypeface
import kotlin.math.min
import java.text.NumberFormat
import kotlin.math.roundToInt

/** A small ring that fills to a 0 to 100 score with the number in the middle. */
class ScoreRingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = context.appTypeface(R.font.poppins_bold)
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    private val bounds = RectF()
    private val defaultSize = resources.getDimensionPixelSize(R.dimen.score_badge_size)

    private var score = 0
    private var progress = 1f
    private var animator: ValueAnimator? = null

    init {
        context.obtainStyledAttributes(attrs, R.styleable.ScoreRingView, defStyleAttr, 0).use { a ->
            val stroke = a.getDimension(
                R.styleable.ScoreRingView_ringStrokeWidth, resources.getDimension(R.dimen.stroke_thin) * 2
            )
            ringPaint.strokeWidth = stroke
            trackPaint.strokeWidth = stroke
            trackPaint.color = a.getColor(
                R.styleable.ScoreRingView_ringTrackColor, ContextCompat.getColor(context, R.color.sift_chart_track)
            )
            textPaint.color = a.getColor(
                R.styleable.ScoreRingView_ringTextColor, ContextCompat.getColor(context, R.color.sift_text_primary)
            )
            textPaint.textSize = a.getDimension(
                R.styleable.ScoreRingView_ringTextSize, resources.getDimension(R.dimen.text_body_small)
            )
        }
        ringPaint.color = ContextCompat.getColor(context, R.color.sift_primary)
    }

    /** Sets the score (clamped to 0..100) and ring colour. */
    fun setScore(value: Int, @ColorInt color: Int, animate: Boolean = true) {
        score = value.coerceIn(0, MAX_SCORE)
        ringPaint.color = color
        animator?.cancel()
        if (!animate || !Motion.enabled(context)) {
            progress = 1f
            invalidate()
            return
        }
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = Motion.duration(context, R.integer.chart_draw_duration)
            interpolator = Motion.emphasizedDecelerate
            addUpdateListener {
                progress = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val size = min(resolveSize(defaultSize, widthMeasureSpec), resolveSize(defaultSize, heightMeasureSpec))
        setMeasuredDimension(size, size)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val inset = ringPaint.strokeWidth / 2f
        bounds.set(paddingLeft + inset, paddingTop + inset, width - paddingRight - inset, height - paddingBottom - inset)
        canvas.drawArc(bounds, 0f, FULL_CIRCLE, false, trackPaint)
        val sweep = FULL_CIRCLE * score / MAX_SCORE * progress
        if (sweep > 0f) canvas.drawArc(bounds, START_ANGLE, sweep, false, ringPaint)
        val shown = NumberFormat.getIntegerInstance().format((score * progress).roundToInt())
        val y = height / 2f - (textPaint.ascent() + textPaint.descent()) / 2f
        canvas.drawText(shown, width / 2f, y, textPaint)
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        super.onDetachedFromWindow()
    }

    private companion object {
        const val FULL_CIRCLE = 360f
        const val START_ANGLE = -90f
        const val MAX_SCORE = 100
    }
}
