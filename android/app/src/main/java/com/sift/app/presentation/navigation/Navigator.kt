package com.sift.app.presentation.navigation

import android.content.Context
import androidx.annotation.IdRes
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.commit
import com.google.android.material.transition.MaterialFadeThrough
import com.google.android.material.transition.MaterialSharedAxis
import com.sift.app.R
import com.sift.app.presentation.common.Motion

/** Implemented by the single activity so fragments can reach the [Navigator]. */
interface NavigatorHost {
    val navigator: Navigator
}

/** How the incoming screen is animated. */
enum class Transition {
    /** Forward and backward along a flow (MaterialSharedAxis X). */
    FORWARD,

    /** Switching between unrelated screens (MaterialFadeThrough). */
    FADE_THROUGH
}

/**
 * A small wrapper around FragmentManager transactions and the back stack.
 * Replaces the Navigation component: every screen is a Fragment created through
 * its newInstance factory, and the back stack is the FragmentManager's own.
 */
class Navigator(
    private val context: Context,
    private val fragmentManager: FragmentManager,
    @IdRes private val containerId: Int
) {
    /** Shows [fragment] on top of the current screen and adds it to the back stack. */
    fun push(fragment: Fragment, transition: Transition = Transition.FORWARD) {
        applyTransitions(fragmentManager.findFragmentById(containerId), fragment, transition)
        fragmentManager.commit {
            setReorderingAllowed(true)
            replace(containerId, fragment, fragment.javaClass.name)
            addToBackStack(fragment.javaClass.name)
        }
    }

    /** Clears the back stack and shows [fragment] as the new root. */
    fun setRoot(fragment: Fragment, transition: Transition = Transition.FADE_THROUGH) {
        if (fragmentManager.backStackEntryCount > 0) {
            val first = fragmentManager.getBackStackEntryAt(0)
            fragmentManager.popBackStackImmediate(first.id, FragmentManager.POP_BACK_STACK_INCLUSIVE)
        }
        applyTransitions(fragmentManager.findFragmentById(containerId), fragment, transition)
        fragmentManager.commit {
            setReorderingAllowed(true)
            replace(containerId, fragment, fragment.javaClass.name)
        }
    }

    /**
     * Replaces the current screen without keeping it on the back stack
     * (for example Progress to Report, so Back returns Home).
     */
    fun replaceCurrent(fragment: Fragment, transition: Transition = Transition.FADE_THROUGH) {
        applyTransitions(fragmentManager.findFragmentById(containerId), fragment, transition)
        val hadBackStack = fragmentManager.backStackEntryCount > 0
        // With reordering allowed the pop and the replace run as one optimised transaction.
        if (hadBackStack) fragmentManager.popBackStack()
        fragmentManager.commit {
            setReorderingAllowed(true)
            replace(containerId, fragment, fragment.javaClass.name)
            if (hadBackStack) addToBackStack(fragment.javaClass.name)
        }
    }

    /**
     * Pops everything above the root and pushes [fragment], so Back from the new
     * screen returns to the root (used when an analysis starts from Setup).
     */
    fun pushFromRoot(fragment: Fragment, transition: Transition = Transition.FORWARD) {
        applyTransitions(fragmentManager.findFragmentById(containerId), fragment, transition)
        if (fragmentManager.backStackEntryCount > 0) {
            val first = fragmentManager.getBackStackEntryAt(0)
            fragmentManager.popBackStack(first.id, FragmentManager.POP_BACK_STACK_INCLUSIVE)
        }
        fragmentManager.commit {
            setReorderingAllowed(true)
            replace(containerId, fragment, fragment.javaClass.name)
            addToBackStack(fragment.javaClass.name)
        }
    }

    /** Goes back one screen. Returns false when there is nothing to pop. */
    fun back(): Boolean {
        if (fragmentManager.backStackEntryCount == 0) return false
        fragmentManager.popBackStack()
        return true
    }

    private fun applyTransitions(outgoing: Fragment?, incoming: Fragment, transition: Transition) {
        if (!Motion.enabled(context)) {
            outgoing?.apply { exitTransition = null; reenterTransition = null }
            incoming.apply { enterTransition = null; returnTransition = null }
            return
        }
        val duration = Motion.duration(context, R.integer.motion_long)
        when (transition) {
            Transition.FORWARD -> {
                outgoing?.exitTransition = MaterialSharedAxis(MaterialSharedAxis.X, true).setDurationCompat(duration)
                outgoing?.reenterTransition = MaterialSharedAxis(MaterialSharedAxis.X, false).setDurationCompat(duration)
                incoming.enterTransition = MaterialSharedAxis(MaterialSharedAxis.X, true).setDurationCompat(duration)
                incoming.returnTransition = MaterialSharedAxis(MaterialSharedAxis.X, false).setDurationCompat(duration)
            }
            Transition.FADE_THROUGH -> {
                outgoing?.exitTransition = MaterialFadeThrough().setDurationCompat(duration)
                outgoing?.reenterTransition = MaterialFadeThrough().setDurationCompat(duration)
                incoming.enterTransition = MaterialFadeThrough().setDurationCompat(duration)
                incoming.returnTransition = MaterialFadeThrough().setDurationCompat(duration)
            }
        }
    }

    private fun <T : androidx.transition.Transition> T.setDurationCompat(millis: Long): T = apply { setDuration(millis) }
}
