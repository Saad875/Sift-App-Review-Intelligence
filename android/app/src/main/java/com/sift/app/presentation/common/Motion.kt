package com.sift.app.presentation.common

import android.animation.ValueAnimator
import android.content.Context
import android.provider.Settings
import android.view.View
import android.view.animation.PathInterpolator
import android.widget.TextView
import androidx.core.view.isVisible
import com.sift.app.R
import java.text.NumberFormat

/** Central place for motion decisions so every animation respects "Remove animations". */
object Motion {
    /** Material standard easing. */
    val standard = PathInterpolator(0.2f, 0f, 0f, 1f)

    /** Material emphasized decelerate easing for entrances. */
    val emphasizedDecelerate = PathInterpolator(0.05f, 0.7f, 0.1f, 1f)

    /** False when the user turned animations off (animator duration scale is 0). */
    fun enabled(context: Context): Boolean = Settings.Global.getFloat(
        context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f
    ) != 0f

    fun duration(context: Context, resId: Int): Long = context.resources.getInteger(resId).toLong()
}

/** Fades a view in with a small upward slide; instant when animations are off. */
fun View.fadeInUp(delayMillis: Long = 0) {
    if (!Motion.enabled(context)) {
        isVisible = true
        alpha = 1f
        translationY = 0f
        return
    }
    alpha = 0f
    translationY = resources.getDimension(R.dimen.stagger_offset)
    isVisible = true
    animate().alpha(1f).translationY(0f)
        .setStartDelay(delayMillis)
        .setDuration(Motion.duration(context, R.integer.motion_long))
        .setInterpolator(Motion.emphasizedDecelerate)
        .start()
}

/** Cross-fades visibility. */
fun View.fadeVisible(visible: Boolean) {
    if (isVisible == visible && alpha == 1f) return
    animate().cancel()
    if (!Motion.enabled(context)) {
        isVisible = visible
        alpha = 1f
        return
    }
    if (visible) {
        alpha = 0f
        isVisible = true
        animate().alpha(1f).setDuration(Motion.duration(context, R.integer.motion_short)).start()
    } else {
        animate().alpha(0f).setDuration(Motion.duration(context, R.integer.motion_short))
            .withEndAction { isVisible = false; alpha = 1f }.start()
    }
}

/** Animates an integer from 0 to [target]; formats with the device locale. */
fun TextView.countUp(target: Int, format: (Int) -> String = { NumberFormat.getIntegerInstance().format(it) }) {
    (getTag(R.id.tag_count_animator) as? ValueAnimator)?.cancel()
    if (!Motion.enabled(context) || target == 0) {
        text = format(target)
        return
    }
    val animator = ValueAnimator.ofInt(0, target).apply {
        duration = Motion.duration(context, R.integer.count_up_duration)
        interpolator = Motion.standard
        addUpdateListener { text = format(it.animatedValue as Int) }
    }
    setTag(R.id.tag_count_animator, animator)
    animator.start()
}

/** Animates a decimal number such as an average rating. */
fun TextView.countUpDecimal(target: Double, format: (Double) -> String) {
    (getTag(R.id.tag_count_animator) as? ValueAnimator)?.cancel()
    if (!Motion.enabled(context)) {
        text = format(target)
        return
    }
    val animator = ValueAnimator.ofFloat(0f, target.toFloat()).apply {
        duration = Motion.duration(context, R.integer.count_up_duration)
        interpolator = Motion.standard
        addUpdateListener { text = format((it.animatedValue as Float).toDouble()) }
    }
    setTag(R.id.tag_count_animator, animator)
    animator.start()
}
