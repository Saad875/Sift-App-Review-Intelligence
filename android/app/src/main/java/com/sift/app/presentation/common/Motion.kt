package com.sift.app.presentation.common

import android.animation.ValueAnimator
import android.content.Context
import android.provider.Settings
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.view.animation.PathInterpolator
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
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

/** Springs a view in from a smaller scale; instant when animations are off. */
fun View.popIn(delayMillis: Long = 0) {
    if (!Motion.enabled(context)) {
        isVisible = true
        alpha = 1f
        scaleX = 1f
        scaleY = 1f
        return
    }
    alpha = 0f
    scaleX = POP_START_SCALE
    scaleY = POP_START_SCALE
    isVisible = true
    animate().alpha(1f).scaleX(1f).scaleY(1f)
        .setStartDelay(delayMillis)
        .setDuration(Motion.duration(context, R.integer.motion_extra_long))
        .setInterpolator(OvershootInterpolator(ResourcesCompat.getFloat(resources, R.dimen.overshoot_tension)))
        .start()
}

/** Gently breathes a decorative view's scale and alpha until it is detached. */
fun View.startPulse(maxScale: Float = PULSE_SCALE) {
    startAmbient(R.integer.glow_pulse_cycle) { fraction ->
        val scale = 1f + (maxScale - 1f) * fraction
        scaleX = scale
        scaleY = scale
        alpha = PULSE_MIN_ALPHA + (1f - PULSE_MIN_ALPHA) * (1f - fraction)
    }
}

/** Floats a decorative view up and down by [distancePx] until it is detached. */
fun View.startFloat(distancePx: Float, startFraction: Float = 0f) {
    startAmbient(R.integer.float_cycle, startFraction) { fraction ->
        translationY = -distancePx * fraction
    }
}

/**
 * Runs a reversing ambient loop on this view (decorative, so it may run longer than
 * screen transitions). Does nothing when animations are off; stops on detach.
 */
private fun View.startAmbient(durationRes: Int, startFraction: Float = 0f, onFrame: View.(Float) -> Unit) {
    // Re-binding the same view keeps the loop that is already running.
    if ((getTag(R.id.tag_ambient_animator) as? ValueAnimator)?.isRunning == true) return
    if (!Motion.enabled(context)) return
    val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = Motion.duration(context, durationRes)
        repeatCount = ValueAnimator.INFINITE
        repeatMode = ValueAnimator.REVERSE
        interpolator = AccelerateDecelerateInterpolator()
        addUpdateListener { onFrame(it.animatedValue as Float) }
        currentPlayTime = (duration * startFraction).toLong()
    }
    setTag(R.id.tag_ambient_animator, animator)
    addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
        override fun onViewAttachedToWindow(v: View) = Unit
        override fun onViewDetachedFromWindow(v: View) {
            animator.cancel()
            v.removeOnAttachStateChangeListener(this)
        }
    })
    animator.start()
}

private const val POP_START_SCALE = 0.6f
private const val PULSE_SCALE = 1.12f
private const val PULSE_MIN_ALPHA = 0.6f
