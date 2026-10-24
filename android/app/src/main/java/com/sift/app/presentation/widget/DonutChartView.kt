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
import kotlin.math.min

/**
 * Animated donut chart drawn on Canvas. Segments sweep in clockwise from the
 * top; the centre shows a headline value and a caption.
 */
class DonutChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    /** One slice of the donut. */
    data class Segment(val value: Float, @ColorInt val color: Int)

    private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val centerPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val captionPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val bounds = RectF()
    private val defaultSize = resources.getDimensionPixelSize(R.dimen.donut_size)
    private var gapPx = 0f

    private var segments: List<Segment> = emptyList()
    private var progress = 1f
    private var animator: ValueAnimator? = null

    var centerText: String = ""
        set(value) { field = value; invalidate() }

    var captionText: String = ""
        set(value) { field = value; invalidate() }

    init {
        context.obtainStyledAttributes(attrs, R.styleable.DonutChartView, defStyleAttr, 0).use { a ->
            val stroke = a.getDimension(
                R.styleable.DonutChartView_donutStrokeWidth, resources.getDimension(R.dimen.donut_stroke)
            )
            arcPaint.strokeWidth = stroke
            trackPaint.strokeWidth = stroke
            gapPx = a.getDimension(R.styleable.DonutChartView_donutGap, resources.getDimension(R.dimen.donut_gap))
            trackPaint.color = a.getColor(
                R.styleable.DonutChartView_donutTrackColor, ContextCompat.getColor(context, R.color.sift_chart_track)
            )
            centerPaint.color = a.getColor(
                R.styleable.DonutChartView_donutCenterTextColor,
                ContextCompat.getColor(context, R.color.sift_text_primary)
            )
            captionPaint.color = a.getColor(
                R.styleable.DonutChartView_donutCaptionTextColor,
                ContextCompat.getColor(context, R.color.sift_text_secondary)
            )
            centerPaint.textSize = a.getDimension(
                R.styleable.DonutChartView_donutCenterTextSize, resources.getDimension(R.dimen.text_donut_center)
            )
            captionPaint.textSize = a.getDimension(
                R.styleable.DonutChartView_donutCaptionTextSize, resources.getDimension(R.dimen.text_donut_caption)
            )
        }
        centerPaint.isFakeBoldText = true
    }

    /** Replaces the data and, when [animate] is true, sweeps the arcs in. */
    fun setSegments(newSegments: List<Segment>, animate: Boolean = true) {
        segments = newSegments.filter { it.value > 0f }
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
        val width = resolveSize(defaultSize, widthMeasureSpec)
        val height = resolveSize(defaultSize, heightMeasureSpec)
        val size = min(width, height)
        setMeasuredDimension(size, size)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val inset = arcPaint.strokeWidth / 2f
        bounds.set(
            paddingLeft + inset, paddingTop + inset,
            width - paddingRight - inset, height - paddingBottom - inset
        )
        canvas.drawArc(bounds, 0f, FULL_CIRCLE, false, trackPaint)

        val total = segments.sumOf { it.value.toDouble() }.toFloat()
        if (total > 0f) {
            val radius = bounds.width() / 2f
            val gapDegrees = if (segments.size > 1) {
                Math.toDegrees(((gapPx + arcPaint.strokeWidth) / radius).toDouble()).toFloat()
            } else {
                0f
            }
            var start = START_ANGLE
            val available = FULL_CIRCLE * progress
            segments.forEach { segment ->
                val sweep = segment.value / total * available
                val visibleSweep = (sweep - gapDegrees).coerceAtLeast(MIN_SWEEP)
                arcPaint.color = segment.color
                canvas.drawArc(bounds, start + gapDegrees / 2f, visibleSweep, false, arcPaint)
                start += sweep
            }
        }

        val cx = width / 2f
        val cy = height / 2f
        val centerHeight = centerPaint.descent() - centerPaint.ascent()
        val captionHeight = if (captionText.isEmpty()) 0f else captionPaint.descent() - captionPaint.ascent()
        val top = cy - (centerHeight + captionHeight) / 2f
        canvas.drawText(centerText, cx, top - centerPaint.ascent(), centerPaint)
        if (captionText.isNotEmpty()) {
            canvas.drawText(captionText, cx, top + centerHeight - captionPaint.ascent(), captionPaint)
        }
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        super.onDetachedFromWindow()
    }

    private companion object {
        const val FULL_CIRCLE = 360f
        const val START_ANGLE = -90f
        const val MIN_SWEEP = 0.5f
    }
}
