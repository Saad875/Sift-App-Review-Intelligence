package com.sift.app.presentation.common

import android.graphics.drawable.Animatable
import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.ImageView
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.google.android.material.button.MaterialButton
import com.sift.app.R

/** A light confirmation tap on key actions. Needs no permission and follows the system haptics setting. */
fun View.hapticTap() {
    val constant = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        HapticFeedbackConstants.CONFIRM
    } else {
        HapticFeedbackConstants.VIRTUAL_KEY
    }
    performHapticFeedback(constant)
}

/**
 * Plays the animated Sift mark (dots dropping into the funnel) while the view is on
 * screen. Shows the still mark when animations are off.
 */
fun ImageView.playLogo() {
    setImageResource(R.drawable.avd_logo)
    val animatable = drawable as? Animatable ?: return
    if (!Motion.enabled(context)) return
    if (isAttachedToWindow) animatable.start()
    addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
        override fun onViewAttachedToWindow(v: View) = animatable.start()
        override fun onViewDetachedFromWindow(v: View) = animatable.stop()
    })
}

/**
 * Morphs a button into a short "done" state (check icon and new label), pops the
 * icon in, then restores the original label and icon.
 */
fun MaterialButton.showSuccess(@StringRes doneText: Int, @DrawableRes doneIcon: Int = R.drawable.ic_check) {
    val originalText = text
    val originalIcon = icon
    hapticTap()
    text = context.getString(doneText)
    setIconResource(doneIcon)
    if (Motion.enabled(context)) {
        scaleX = SUCCESS_POP_SCALE
        scaleY = SUCCESS_POP_SCALE
        animate().scaleX(1f).scaleY(1f)
            .setDuration(Motion.duration(context, R.integer.motion_long))
            .setInterpolator(Motion.emphasizedDecelerate)
            .start()
    }
    removeCallbacks(getTag(R.id.tag_success_reset) as? Runnable)
    val reset = Runnable {
        text = originalText
        icon = originalIcon
    }
    setTag(R.id.tag_success_reset, reset)
    postDelayed(reset, resources.getInteger(R.integer.success_hold).toLong())
}

/** Spins an icon button a quarter turn, for example the settings gear before opening Settings. */
fun View.spinOnce() {
    if (!Motion.enabled(context)) return
    animate().rotationBy(QUARTER_TURN)
        .setDuration(Motion.duration(context, R.integer.motion_extra_long))
        .setInterpolator(Motion.standard)
        .start()
}

private const val SUCCESS_POP_SCALE = 0.92f
private const val QUARTER_TURN = 90f
