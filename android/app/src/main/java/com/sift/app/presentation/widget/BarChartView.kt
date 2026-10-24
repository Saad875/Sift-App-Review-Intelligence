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
import kotlin.math.max

/**
 * Horizontal bar chart drawn on Canvas: a label on the left, a rounded bar,
 * and the value on the right. Bars grow in when data is set.
 */
class BarChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    /** One row of the chart. */
    data class Bar(val label: String, val value: Int, @ColorInt val color: Int, val valueText: String)

    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val labelPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
    private val valuePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.RIGHT }
    private val rect = RectF()

    private var barHeight = 0f
    private var rowSpacing = 0f
    private var labelWidth = 0f
    private var valueWidth = 0f

    private var bars: List<Bar> = emptyList()
    private var progress = 1f
    private var animator: ValueAnimator? = null

    init {
        context.obtainStyledAttributes(attrs, R.styleable.BarChartView, defStyleAttr, 0).use { a ->
            barHeight = a.getDimension(R.styleable.BarChartView_barHeight, resources.getDimension(R.dimen.bar_height))
            rowSpacing = a.getDimension(
                R.styleable.BarChartView_barRowSpacing, resources.getDimension(R.dimen.bar_row_spacing)
            )
            labelWidth = a.getDimension(
                R.styleable.BarChartView_barLabelWidth, resources.getDimension(R.dimen.bar_label_width)
            )
            valueWidth = a.getDimension(
                R.styleable.BarChartView_barValueWidth, resources.getDimension(R.dimen.bar_value_width)
            )
            trackPaint.color = a.getColor(
                R.styleable.BarChartView_barTrackColor, ContextCompat.getColor(context, R.color.sift_chart_track)
            )
            val labelColor = a.getColor(
                R.styleable.BarChartView_barLabelColor, ContextCompat.getColor(context, R.color.sift_text_secondary)
            )
            labelPaint.color = labelColor
            valuePaint.color = labelColor
            val textSize = a.getDimension(
                R.styleable.BarChartView_barTextSize, resources.getDimension(R.dimen.text_chart_label)
            )
            labelPaint.textSize = textSize
            valuePaint.textSize = textSize
        }
    }

    /** Replaces the rows and animates the bars when [animate] is true. */
    fun setBars(newBars: List<Bar>, animate: Boolean = true) {
        val sizeChanged = newBars.size != bars.size
        bars = newBars
        if (sizeChanged) requestLayout()
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

    private fun rowHeight(): Float = max(barHeight, labelPaint.descent() - labelPaint.ascent())

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val rows = bars.size
        val content = if (rows == 0) 0f else rows * rowHeight() + (rows - 1) * rowSpacing
        val desiredHeight = (content + paddingTop + paddingBottom).toInt()
        setMeasuredDimension(
            getDefaultSize(suggestedMinimumWidth, widthMeasureSpec),
            resolveSize(desiredHeight, heightMeasureSpec)
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (bars.isEmpty()) return
        val maxValue = bars.maxOf { it.value }.coerceAtLeast(1)
        val rowHeight = rowHeight()
        val trackStart = paddingLeft + labelWidth
        val trackEnd = width - paddingRight - valueWidth
        val trackWidth = (trackEnd - trackStart).coerceAtLeast(0f)
        val radius = barHeight / 2f
        val textOffset = -(labelPaint.ascent() + labelPaint.descent()) / 2f

        bars.forEachIndexed { index, bar ->
            val top = paddingTop + index * (rowHeight + rowSpacing)
            val centerY = top + rowHeight / 2f
            canvas.drawText(bar.label, paddingLeft.toFloat(), centerY + textOffset, labelPaint)

            rect.set(trackStart, centerY - barHeight / 2f, trackEnd, centerY + barHeight / 2f)
            canvas.drawRoundRect(rect, radius, radius, trackPaint)

            val fill = trackWidth * (bar.value.toFloat() / maxValue) * progress
            if (fill > 0f) {
                rect.set(trackStart, centerY - barHeight / 2f, trackStart + max(fill, barHeight), centerY + barHeight / 2f)
                barPaint.color = bar.color
                canvas.drawRoundRect(rect, radius, radius, barPaint)
            }
            canvas.drawText(bar.valueText, (width - paddingRight).toFloat(), centerY + textOffset, valuePaint)
        }
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        super.onDetachedFromWindow()
    }
}
