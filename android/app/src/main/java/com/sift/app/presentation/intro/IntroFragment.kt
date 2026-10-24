package com.sift.app.presentation.intro

import android.os.Bundle
import android.view.View
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.sift.app.R
import com.sift.app.databinding.FragmentIntroBinding
import com.sift.app.databinding.ItemIntroFeatureBinding
import com.sift.app.presentation.common.BaseFragment
import com.sift.app.presentation.common.applySystemBarBottomMargin
import com.sift.app.presentation.common.applySystemBarPadding
import com.sift.app.presentation.common.fadeInUp
import com.sift.app.presentation.home.HomeFragment
import com.sift.app.presentation.navigation.Transition

/** One-screen introduction shown only on first launch. */
class IntroFragment : BaseFragment<FragmentIntroBinding>(FragmentIntroBinding::inflate) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.content.applySystemBarPadding(top = true)
        binding.getStarted.applySystemBarBottomMargin()

        binding.featureCollect.bind(R.drawable.ic_search, R.string.intro_feature_collect_title, R.string.intro_feature_collect_body)
        binding.featureUnderstand.bind(
            R.drawable.ic_auto_awesome, R.string.intro_feature_understand_title, R.string.intro_feature_understand_body
        )
        binding.featureAct.bind(R.drawable.ic_trending_up, R.string.intro_feature_act_title, R.string.intro_feature_act_body)

        binding.getStarted.setOnClickListener {
            container.preferences.completeIntro()
            navigator.setRoot(HomeFragment.newInstance(), Transition.FADE_THROUGH)
        }

        if (savedInstanceState == null) {
            listOf(binding.headline, binding.body, binding.featureCollect.root, binding.featureUnderstand.root,
                binding.featureAct.root).forEachIndexed { index, target ->
                target.fadeInUp(delayMillis = index * resources.getInteger(R.integer.motion_stagger_step).toLong())
            }
        }
    }

    private fun ItemIntroFeatureBinding.bind(@DrawableRes icon: Int, @StringRes title: Int, @StringRes body: Int) {
        featureIcon.setImageResource(icon)
        featureTitle.setText(title)
        featureBody.setText(body)
    }

    companion object {
        fun newInstance() = IntroFragment()
    }
}
