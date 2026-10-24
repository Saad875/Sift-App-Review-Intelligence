package com.sift.app.presentation.onboarding

import android.os.Bundle
import android.view.View
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.sift.app.R
import com.sift.app.databinding.FragmentSplashBinding
import com.sift.app.presentation.common.BaseFragment
import com.sift.app.presentation.common.Motion
import com.sift.app.presentation.common.fadeInUp
import com.sift.app.presentation.common.popIn
import com.sift.app.presentation.common.startPulse
import com.sift.app.presentation.navigation.Transition
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * First onboarding screen: the logo springs in over a breathing glow, then the
 * flow moves on to the language picker. Shown once, on the very first launch.
 */
class SplashFragment : BaseFragment<FragmentSplashBinding>(FragmentSplashBinding::inflate) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val animated = Motion.enabled(requireContext())
        val step = resources.getInteger(R.integer.motion_stagger_step).toLong()

        binding.glowRing.popIn()
        binding.logoTile.popIn(delayMillis = step)
        binding.wordmark.fadeInUp(delayMillis = step * WORDMARK_STEPS)
        binding.tagline.fadeInUp(delayMillis = step * TAGLINE_STEPS)
        binding.poweredBy.fadeInUp(delayMillis = step * FOOTER_STEPS)
        binding.glowRing.postDelayed({ if (view.isAttachedToWindow) binding.glowRing.startPulse() },
            resources.getInteger(R.integer.motion_extra_long).toLong())

        val hold = resources.getInteger(if (animated) R.integer.splash_hold else R.integer.splash_hold_static).toLong()
        // Only navigate while resumed, so leaving the app mid-splash never commits after onSaveInstanceState.
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                delay(hold)
                navigator.setRoot(LanguageFragment.newInstance(LanguageFragment.Mode.ONBOARDING), Transition.FADE_THROUGH)
            }
        }
    }

    companion object {
        private const val WORDMARK_STEPS = 4
        private const val TAGLINE_STEPS = 6
        private const val FOOTER_STEPS = 8

        fun newInstance() = SplashFragment()
    }
}
