package com.sift.app.presentation.widget

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.content.Context
import android.content.res.ColorStateList
import android.util.AttributeSet
import android.view.LayoutInflater
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.view.isInvisible
import com.sift.app.R
import com.sift.app.databinding.ViewTimelineStepBinding
import com.sift.app.domain.model.StepState
import com.sift.app.presentation.common.Motion

/**
 * One row of the progress timeline: an indicator, a connector to the next
 * row, a title, a status line and a state badge. The running state pulses.
 */
class TimelineStepView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ConstraintLayout(context, attrs, defStyleAttr) {

    private val binding = ViewTimelineStepBinding.inflate(LayoutInflater.from(context), this)
    private var state: StepState? = null
    private var pulse: ObjectAnimator? = null

    /** Hides the connector on the last row. */
    var isLast: Boolean = false
        set(value) {
            field = value
            binding.connectorTrack.isInvisible = value
            binding.connectorFill.isInvisible = value
        }

    fun bind(title: String, subtitle: String, newState: StepState) {
        binding.title.text = title
        binding.subtitle.text = subtitle
        if (newState == state) return
        val previous = state
        state = newState
        val style = styleFor(newState)
        binding.indicator.setImageResource(style.icon)
        binding.indicator.backgroundTintList = ColorStateList.valueOf(color(style.background))
        binding.indicator.imageTintList = ColorStateList.valueOf(color(style.iconTint))
        binding.title.setTextColor(color(style.titleColor))
        binding.stateLabel.setText(style.label)
        binding.stateLabel.setTextColor(color(style.labelColor))
        contentDescription = context.getString(R.string.cd_timeline_step, title, context.getString(style.label), subtitle)
        animateConnector(newState == StepState.DONE)
        if (previous != null) popIndicator()
        if (newState == StepState.RUNNING) startPulse() else stopPulse()
    }

    private fun animateConnector(filled: Boolean) {
        val target = if (filled) 1f else 0f
        binding.connectorFill.pivotY = 0f
        if (!Motion.enabled(context)) {
            binding.connectorFill.scaleY = target
            return
        }
        binding.connectorFill.animate().scaleY(target)
            .setDuration(Motion.duration(context, R.integer.motion_extra_long))
            .setInterpolator(Motion.standard)
            .start()
    }

    private fun popIndicator() {
        if (!Motion.enabled(context)) return
        binding.indicator.scaleX = POP_START
        binding.indicator.scaleY = POP_START
        binding.indicator.animate().scaleX(1f).scaleY(1f)
            .setDuration(Motion.duration(context, R.integer.motion_medium))
            .setInterpolator(Motion.emphasizedDecelerate)
            .start()
    }

    private fun startPulse() {
        if (pulse != null || !Motion.enabled(context)) return
        pulse = ObjectAnimator.ofPropertyValuesHolder(
            binding.halo,
            PropertyValuesHolder.ofFloat(SCALE_X, 1f, PULSE_SCALE),
            PropertyValuesHolder.ofFloat(SCALE_Y, 1f, PULSE_SCALE),
            PropertyValuesHolder.ofFloat(ALPHA, PULSE_ALPHA, 0f)
        ).apply {
            duration = Motion.duration(context, R.integer.motion_extra_long) * PULSE_DURATION_FACTOR
            repeatCount = ValueAnimator.INFINITE
            interpolator = Motion.standard
            start()
        }
    }

    private fun stopPulse() {
        pulse?.cancel()
        pulse = null
        binding.halo.alpha = 0f
    }

    override fun onDetachedFromWindow() {
        stopPulse()
        super.onDetachedFromWindow()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (state == StepState.RUNNING) startPulse()
    }

    private fun color(@ColorRes res: Int): Int = ContextCompat.getColor(context, res)

    private data class StepStyle(
        @DrawableRes val icon: Int,
        @ColorRes val background: Int,
        @ColorRes val iconTint: Int,
        @ColorRes val titleColor: Int,
        @StringRes val label: Int,
        @ColorRes val labelColor: Int
    )

    private fun styleFor(state: StepState): StepStyle = when (state) {
        StepState.WAITING -> StepStyle(
            R.drawable.ic_dot, R.color.sift_surface_raised, R.color.sift_text_tertiary,
            R.color.sift_text_secondary, R.string.step_state_waiting, R.color.sift_text_tertiary
        )
        StepState.RUNNING -> StepStyle(
            R.drawable.ic_auto_awesome, R.color.sift_primary, R.color.sift_on_primary,
            R.color.sift_text_primary, R.string.step_state_running, R.color.sift_primary_text
        )
        StepState.DONE -> StepStyle(
            R.drawable.ic_check, R.color.sift_primary_container, R.color.sift_primary_text,
            R.color.sift_text_primary, R.string.step_state_done, R.color.sift_positive
        )
        StepState.FAILED -> StepStyle(
            R.drawable.ic_close, R.color.sift_negative_container, R.color.sift_negative,
            R.color.sift_text_primary, R.string.step_state_failed, R.color.sift_negative
        )
        StepState.SKIPPED -> StepStyle(
            R.drawable.ic_remove, R.color.sift_surface_raised, R.color.sift_text_tertiary,
            R.color.sift_text_secondary, R.string.step_state_skipped, R.color.sift_text_tertiary
        )
    }

    private companion object {
        const val SCALE_X = "scaleX"
        const val SCALE_Y = "scaleY"
        const val ALPHA = "alpha"
        const val PULSE_SCALE = 1.7f
        const val PULSE_ALPHA = 0.45f
        const val PULSE_DURATION_FACTOR = 4
        const val POP_START = 0.7f
    }
}
