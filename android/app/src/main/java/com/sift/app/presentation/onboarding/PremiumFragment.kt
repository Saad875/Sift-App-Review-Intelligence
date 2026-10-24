package com.sift.app.presentation.onboarding

import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import com.sift.app.R
import com.sift.app.databinding.FragmentPremiumBinding
import com.sift.app.databinding.ItemProFeatureRowBinding
import com.sift.app.databinding.ItemProPlanBinding
import com.sift.app.presentation.common.BaseFragment
import com.sift.app.presentation.common.UiState
import com.sift.app.presentation.common.applySystemBarPadding
import com.sift.app.presentation.common.collectWhileStarted
import com.sift.app.presentation.common.fadeInUp
import com.sift.app.presentation.common.hapticTap
import com.sift.app.presentation.common.matchStatusBarHeight
import com.sift.app.presentation.common.popIn
import com.sift.app.presentation.common.snackbar
import com.sift.app.presentation.common.startPulse
import com.sift.app.presentation.home.HomeFragment
import com.sift.app.presentation.navigation.Transition

/** A row of the Free versus Pro table: either a value per plan, or included / not included. */
private enum class ProFeature(
    @StringRes val label: Int,
    @StringRes val freeValue: Int? = null,
    @StringRes val proValue: Int? = null,
    val includedInFree: Boolean = false
) {
    ANALYSES(R.string.pro_feature_analyses, R.string.pro_feature_analyses_free, R.string.pro_feature_analyses_pro),
    REVIEWS(R.string.pro_feature_reviews, R.string.pro_feature_reviews_free, R.string.pro_feature_reviews_pro),
    COMPETITORS(R.string.pro_feature_competitors, R.string.pro_feature_competitors_free, R.string.pro_feature_competitors_pro),
    REPLIES(R.string.pro_feature_replies, includedInFree = true),
    EXPORT(R.string.pro_feature_export)
}

/**
 * Sift Pro preview: a plan comparison and two plan cards. There is no billing. Continuing
 * only shows "coming soon", and no payment details are ever requested.
 */
class PremiumFragment : BaseFragment<FragmentPremiumBinding>(FragmentPremiumBinding::inflate) {

    enum class Mode { ONBOARDING, SETTINGS }

    private val viewModel: PremiumViewModel by viewModels { PremiumViewModel.factory(container) }
    private val mode: Mode get() = Mode.valueOf(requireArguments().getString(ARG_MODE, Mode.ONBOARDING.name))

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.content.applySystemBarPadding(top = true, bottom = true)
        binding.statusScrim.matchStatusBarHeight()

        ProFeature.entries.forEach(::addFeatureRow)
        binding.planYearly.badge.isVisible = true
        binding.planYearly.root.setOnClickListener {
            it.hapticTap()
            viewModel.selectPlan(ProPlan.YEARLY)
        }
        binding.planMonthly.root.setOnClickListener {
            it.hapticTap()
            viewModel.selectPlan(ProPlan.MONTHLY)
        }
        binding.continueButton.setOnClickListener {
            it.hapticTap()
            viewModel.subscribe()
        }
        binding.laterButton.setOnClickListener { viewModel.leave() }
        binding.closeButton.setOnClickListener { viewModel.leave() }

        if (savedInstanceState == null) {
            val step = resources.getInteger(R.integer.motion_stagger_step).toLong()
            binding.orb.popIn()
            listOf(binding.previewBadge, binding.title, binding.subtitle, binding.compareCard,
                binding.planYearly.root, binding.planMonthly.root, binding.continueContainer)
                .forEachIndexed { index, target -> target.fadeInUp(delayMillis = step * (index + 1)) }
        }
        binding.orbGlow.startPulse()

        collectWhileStarted(viewModel.state) { state ->
            val content = (state as? UiState.Content)?.data ?: return@collectWhileStarted
            bindPlan(binding.planYearly, ProPlan.YEARLY, content.selectedPlan)
            bindPlan(binding.planMonthly, ProPlan.MONTHLY, content.selectedPlan)
        }
        collectWhileStarted(viewModel.events) { event ->
            when (event) {
                PremiumEvent.ComingSoon -> binding.root.snackbar(R.string.pro_coming_soon)
                PremiumEvent.Leave ->
                    if (mode == Mode.SETTINGS) navigator.back()
                    else navigator.setRoot(HomeFragment.newInstance(), Transition.FADE_THROUGH)
            }
        }
    }

    private fun addFeatureRow(feature: ProFeature) {
        val row = ItemProFeatureRowBinding.inflate(layoutInflater, binding.compareRows, false)
        row.label.setText(feature.label)
        val freeText = bindCell(row.freeValue, row.freeIcon, feature.freeValue, feature.includedInFree)
        val proText = bindCell(row.proValue, row.proIcon, feature.proValue, included = true)
        row.root.contentDescription = getString(R.string.cd_pro_row, getString(feature.label), freeText, proText)
        binding.compareRows.addView(row.root)
    }

    /** Shows either the value text or a check / dash icon; returns what a screen reader should say. */
    private fun bindCell(text: TextView, icon: ImageView, @StringRes value: Int?, included: Boolean): String {
        if (value != null) {
            text.setText(value)
            return getString(value)
        }
        text.text = null
        icon.isVisible = true
        icon.setImageResource(if (included) R.drawable.ic_check else R.drawable.ic_remove)
        icon.setColorFilter(ContextCompat.getColor(requireContext(), if (included) R.color.sift_primary_text else R.color.sift_text_tertiary))
        return getString(if (included) R.string.cd_pro_included else R.string.cd_pro_not_included)
    }

    private fun bindPlan(plan: ItemProPlanBinding, value: ProPlan, selected: ProPlan) {
        val context = requireContext()
        val isSelected = value == selected
        plan.planTitle.setText(value.title)
        plan.planPrice.setText(value.price)
        plan.planDetail.setText(value.detail)
        plan.radio.setImageResource(if (isSelected) R.drawable.ic_radio_checked else R.drawable.ic_radio_unchecked)
        plan.root.isSelected = isSelected
        plan.root.strokeColor = ContextCompat.getColor(context, if (isSelected) R.color.sift_primary else R.color.sift_glass_stroke)
        plan.root.strokeWidth = resources.getDimensionPixelSize(if (isSelected) R.dimen.selection_stroke else R.dimen.stroke_hairline)
        plan.root.setCardBackgroundColor(ContextCompat.getColor(context, if (isSelected) R.color.sift_glass_strong else R.color.sift_glass))
        plan.root.contentDescription = getString(
            if (isSelected) R.string.cd_pro_plan_selected else R.string.cd_pro_plan,
            getString(value.title), getString(value.price), getString(value.detail)
        )
    }

    companion object {
        private const val ARG_MODE = "mode"

        fun newInstance(mode: Mode) = PremiumFragment().apply {
            arguments = bundleOf(ARG_MODE to mode.name)
        }
    }
}
