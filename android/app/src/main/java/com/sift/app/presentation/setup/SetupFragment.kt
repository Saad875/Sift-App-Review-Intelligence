package com.sift.app.presentation.setup

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.activity.OnBackPressedCallback
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.chip.Chip
import com.google.android.material.snackbar.Snackbar
import com.sift.app.R
import com.sift.app.databinding.FragmentSetupBinding
import com.sift.app.domain.model.AppSummary
import com.sift.app.domain.util.CompetitorRules
import com.sift.app.presentation.common.BaseFragment
import com.sift.app.presentation.common.Formatters
import com.sift.app.presentation.common.Motion
import com.sift.app.presentation.common.UiState
import com.sift.app.presentation.common.applySystemBarBottomMargin
import com.sift.app.presentation.common.applySystemBarPadding
import com.sift.app.presentation.common.collectWhileStarted
import com.sift.app.presentation.common.errorMessage
import com.sift.app.presentation.common.fadeVisible
import com.sift.app.presentation.common.hideKeyboard
import com.sift.app.presentation.common.loadAppIcon
import com.sift.app.presentation.progress.ProgressFragment

/** Analysis setup: selected app or file, country, optional competitors, start. */
class SetupFragment : BaseFragment<FragmentSetupBinding>(FragmentSetupBinding::inflate) {

    private val viewModel: SetupViewModel by viewModels { SetupViewModel.factory(container) }
    private lateinit var countryAdapter: CountryAdapter
    private var renderedCompetitors: List<String>? = null
    private var panelShown = false

    private val closePanelOnBack = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() = viewModel.setCountryPanelOpen(false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.toolbar.applySystemBarPadding(top = true)
        binding.startButton.applySystemBarBottomMargin()
        binding.countryPanel.applySystemBarBottomMargin()
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, closePanelOnBack)

        binding.toolbar.setNavigationOnClickListener { navigator.back() }
        binding.countryCard.setOnClickListener { viewModel.setCountryPanelOpen(true) }
        binding.scrim.setOnClickListener { viewModel.setCountryPanelOpen(false) }
        binding.panelClose.setOnClickListener { viewModel.setCountryPanelOpen(false) }
        binding.startButton.setOnClickListener {
            binding.root.hideKeyboard()
            viewModel.start()
        }

        countryAdapter = CountryAdapter(viewModel::selectCountry)
        binding.countryList.adapter = countryAdapter

        binding.competitorLayout.setEndIconOnClickListener { addCompetitor() }
        binding.competitorInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) addCompetitor()
            actionId == EditorInfo.IME_ACTION_DONE
        }

        collectWhileStarted(viewModel.state) { state -> if (state is UiState.Content) render(state.data) }
        collectWhileStarted(viewModel.events, ::handle)
    }

    private fun addCompetitor() {
        val text = binding.competitorInput.text?.toString().orEmpty()
        if (text.isBlank()) return
        if (viewModel.addCompetitor(text)) {
            binding.competitorInput.text = null
            binding.competitorLayout.error = null
        } else {
            binding.competitorLayout.error = getString(R.string.setup_competitor_invalid)
        }
    }

    private fun render(content: SetupContent) {
        renderTarget(content.target)
        val isApp = content.target is SetupTarget.App
        binding.countryLabel.isVisible = isApp
        binding.countryCard.isVisible = isApp
        binding.countryFlag.text = Formatters.countryFlag(content.country)
        binding.countryName.text = Formatters.countryName(content.country)
        binding.countryCard.contentDescription = getString(R.string.cd_country_card, binding.countryName.text)
        countryAdapter.setSelected(content.country)

        renderCompetitors(content.competitors)
        binding.competitorCount.text = getString(
            R.string.setup_competitor_count, content.competitors.size, CompetitorRules.MAX_COMPETITORS
        )
        binding.competitorLayout.isEnabled = content.canAddCompetitor
        binding.competitorLayout.helperText = if (content.canAddCompetitor) null else getString(R.string.setup_competitor_full)

        binding.startButton.isEnabled = !content.isStarting
        binding.startButton.setText(if (content.isStarting) R.string.setup_starting else R.string.setup_start)
        renderPanel(content.isCountryPanelOpen, content.country)
    }

    private fun renderTarget(target: SetupTarget) {
        when (target) {
            is SetupTarget.App -> {
                val app: AppSummary = target.app
                binding.targetIcon.loadAppIcon(app.iconUrl)
                binding.targetName.text = app.name
                binding.targetSubtitle.text = app.developer
                val parts = mutableListOf<String>()
                app.rating?.let { parts += Formatters.rating(requireContext(), it) }
                app.ratingCount?.let { parts += resources.getQuantityString(R.plurals.rating_count, it, Formatters.count(it)) }
                binding.targetMeta.text = parts.joinToString(getString(R.string.meta_separator))
                binding.targetMeta.isVisible = parts.isNotEmpty()
            }
            is SetupTarget.Csv -> {
                binding.targetIcon.loadAppIcon(null)
                binding.targetName.text = target.document.displayName ?: getString(R.string.setup_csv_default_name)
                binding.targetSubtitle.setText(R.string.setup_csv_subtitle)
                binding.targetMeta.setText(R.string.setup_csv_meta)
                binding.targetMeta.isVisible = true
            }
        }
    }

    private fun renderCompetitors(competitors: List<String>) {
        if (competitors == renderedCompetitors) return
        renderedCompetitors = competitors
        binding.competitorChips.removeAllViews()
        val inflater = LayoutInflater.from(requireContext())
        competitors.forEach { name ->
            val chip = inflater.inflate(R.layout.item_competitor_chip, binding.competitorChips, false) as Chip
            chip.text = name
            chip.closeIconContentDescription = getString(R.string.cd_remove_competitor, name)
            chip.setOnCloseIconClickListener { viewModel.removeCompetitor(name) }
            binding.competitorChips.addView(chip)
        }
    }

    private fun renderPanel(open: Boolean, country: String) {
        closePanelOnBack.isEnabled = open
        if (open == panelShown) return
        panelShown = open
        binding.scrim.fadeVisible(open)
        if (open) {
            binding.root.hideKeyboard()
            (binding.countryList.layoutManager as? LinearLayoutManager)
                ?.scrollToPositionWithOffset(countryAdapter.positionOf(country), 0)
            showPanel()
        } else {
            hidePanel()
        }
    }

    private fun showPanel() {
        val panel = binding.countryPanel
        panel.isVisible = true
        if (!Motion.enabled(requireContext())) return
        panel.translationY = resources.getDimension(R.dimen.space_16)
        panel.alpha = 0f
        panel.animate().translationY(0f).alpha(1f)
            .setDuration(Motion.duration(requireContext(), R.integer.motion_long))
            .setInterpolator(Motion.emphasizedDecelerate)
            .start()
    }

    private fun hidePanel() {
        val panel = binding.countryPanel
        if (!Motion.enabled(requireContext())) {
            panel.isVisible = false
            return
        }
        panel.animate().translationY(resources.getDimension(R.dimen.space_16)).alpha(0f)
            .setDuration(Motion.duration(requireContext(), R.integer.motion_short))
            .setInterpolator(Motion.standard)
            .withEndAction { panel.isVisible = false }
            .start()
    }

    private fun handle(event: SetupEvent) {
        when (event) {
            is SetupEvent.Started -> navigator.pushFromRoot(ProgressFragment.newInstance(event.analysisId))
            is SetupEvent.ShowError -> Snackbar.make(
                binding.root, requireContext().errorMessage(event.error), Snackbar.LENGTH_LONG
            ).setAction(R.string.action_retry) { viewModel.start() }.show()
        }
    }

    companion object {
        fun forApp(app: AppSummary, country: String) = SetupFragment().apply {
            arguments = SetupArgs.forApp(app, country)
        }

        fun forCsv(uri: String, displayName: String?) = SetupFragment().apply {
            arguments = SetupArgs.forCsv(uri, displayName)
        }
    }
}
