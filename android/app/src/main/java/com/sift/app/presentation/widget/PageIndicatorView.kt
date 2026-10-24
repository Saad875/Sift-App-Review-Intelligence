package com.sift.app.presentation.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.view.ViewCompat
import com.sift.app.R

/**
 * Pager dots where the current page is a stretched pill. Follows the pager's scroll
 * offset, so the pill morphs smoothly from one dot to the next. Mirrors in RTL.
 */
class PageIndicatorView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val dot = resources.getDimension(R.dimen.indicator_dot)
    private val activeDot = resources.getDimension(R.dimen.indicator_dot_active)
    private val gap = resources.getDimension(R.dimen.indicator_gap)
    private val activeColor = ContextCompat.getColor(context, R.color.sift_primary)
    private val idleColor = ContextCompat.getColor(context, R.color.sift_text_tertiary)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()

    private var count = 0
    private var position = 0
    private var offset = 0f

    fun setCount(value: Int) {
        count = value
        requestLayout()
        updateDescription()
    }

    /** Call from ViewPager2's onPageScrolled. */
    fun setProgress(page: Int, pageOffset: Float) {
        position = page
        offset = pageOffset
        invalidate()
        updateDescription()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = if (count == 0) 0f else activeDot + (count - 1) * (dot + gap)
        setMeasuredDimension(
            resolveSize((width + paddingLeft + paddingRight).toInt(), widthMeasureSpec),
            resolveSize((dot + paddingTop + paddingBottom).toInt(), heightMeasureSpec)
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (count == 0) return
        val rtl = ViewCompat.getLayoutDirection(this) == ViewCompat.LAYOUT_DIRECTION_RTL
        val totalWidth = activeDot + (count - 1) * (dot + gap)
        var x = paddingLeft + (width - paddingLeft - paddingRight - totalWidth) / 2f
        val top = paddingTop + (height - paddingTop - paddingBottom - dot) / 2f
        for (visual in 0 until count) {
            val index = if (rtl) count - 1 - visual else visual
            // How "active" this dot is: 1 for the current page, blending into the next while swiping.
            val weight = when (index) {
                position -> 1f - offset
                position + 1 -> offset
                else -> 0f
            }
            val w = dot + (activeDot - dot) * weight
            rect.set(x, top, x + w, top + dot)
            paint.color = ColorUtils.blendARGB(idleColor, activeColor, weight)
            canvas.drawRoundRect(rect, dot / 2f, dot / 2f, paint)
            x += w + gap
        }
    }

    private fun updateDescription() {
        if (count > 0) contentDescription = context.getString(R.string.cd_page_indicator, position + 1, count)
    }
}
