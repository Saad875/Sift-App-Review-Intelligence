package com.sift.app.presentation.common

import android.view.View
import com.sift.app.R

/**
 * Staggered entrance for list items: each item fades and slides in once, with
 * a small delay per position. Only the first screenful is staggered.
 */
class ItemEntrance {
    private var lastAnimatedPosition = -1

    fun animate(view: View, position: Int) {
        if (position <= lastAnimatedPosition) {
            view.alpha = 1f
            view.translationY = 0f
            return
        }
        lastAnimatedPosition = position
        val resources = view.resources
        val maxStaggered = resources.getInteger(R.integer.motion_stagger_max_items)
        val step = resources.getInteger(R.integer.motion_stagger_step).toLong()
        view.fadeInUp(delayMillis = step * position.coerceAtMost(maxStaggered))
    }

    /** Call when the list content is replaced so new items animate again. */
    fun reset() {
        lastAnimatedPosition = -1
    }
}
