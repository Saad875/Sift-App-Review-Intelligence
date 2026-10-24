package com.sift.app.presentation.onboarding

import android.os.Bundle
import android.view.View
import androidx.viewpager2.widget.ViewPager2
import com.sift.app.R
import com.sift.app.databinding.FragmentWelcomeBinding
import com.sift.app.databinding.ItemWelcomePageBinding
import com.sift.app.presentation.common.BaseFragment
import com.sift.app.presentation.common.Motion
import com.sift.app.presentation.common.applySystemBarBottomMargin
import com.sift.app.presentation.common.applySystemBarPadding
import com.sift.app.presentation.common.hapticTap
import com.sift.app.presentation.navigation.Transition
import kotlin.math.abs

/**
 * Three swipeable pages that explain what Sift does. The artwork moves slower than
 * the page (parallax) and the pill indicator follows the swipe.
 */
class WelcomeFragment : BaseFragment<FragmentWelcomeBinding>(FragmentWelcomeBinding::inflate) {

    private val pageCallback = object : ViewPager2.OnPageChangeCallback() {
        override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {
            binding.indicator.setProgress(position, positionOffset)
        }

        override fun onPageSelected(position: Int) {
            val last = position == WelcomePage.entries.lastIndex
            val label = getString(if (last) R.string.welcome_get_started else R.string.welcome_next)
            // Re-setting identical text mid-layout makes MaterialButton misplace its end icon.
            if (binding.nextButton.text.toString() != label) binding.nextButton.text = label
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.topBar.applySystemBarPadding(top = true)
        binding.nextButtonContainer.applySystemBarBottomMargin()

        binding.pager.adapter = WelcomePagerAdapter()
        binding.indicator.setCount(WelcomePage.entries.size)
        binding.pager.registerOnPageChangeCallback(pageCallback)
        if (Motion.enabled(requireContext())) binding.pager.setPageTransformer(::transformPage)

        binding.skipButton.setOnClickListener { openPro() }
        binding.nextButton.setOnClickListener {
            it.hapticTap()
            val pager = binding.pager
            if (pager.currentItem < WelcomePage.entries.lastIndex) pager.currentItem = pager.currentItem + 1
            else openPro()
        }
    }

    override fun onDestroyView() {
        binding.pager.unregisterOnPageChangeCallback(pageCallback)
        super.onDestroyView()
    }

    /** Parallax: the artwork lags behind the swipe and the copy fades as the page leaves. */
    private fun transformPage(page: View, position: Float) {
        val pageBinding = ItemWelcomePageBinding.bind(page)
        val distance = abs(position).coerceAtMost(1f)
        val direction = if (page.layoutDirection == View.LAYOUT_DIRECTION_RTL) -1f else 1f
        pageBinding.art.translationX = -position * page.width * ART_PARALLAX * direction
        pageBinding.art.scaleX = 1f - distance * ART_SHRINK
        pageBinding.art.scaleY = 1f - distance * ART_SHRINK
        pageBinding.title.translationX = position * page.width * TEXT_PARALLAX * direction
        pageBinding.title.alpha = 1f - distance
        pageBinding.body.alpha = 1f - distance
    }

    private fun openPro() {
        navigator.push(PremiumFragment.newInstance(PremiumFragment.Mode.ONBOARDING), Transition.FORWARD)
    }

    companion object {
        private const val ART_PARALLAX = 0.5f
        private const val ART_SHRINK = 0.15f
        private const val TEXT_PARALLAX = 0.2f

        fun newInstance() = WelcomeFragment()
    }
}
