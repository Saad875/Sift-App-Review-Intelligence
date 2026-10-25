package com.sift.app.presentation.settings

import android.os.Bundle
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import com.sift.app.BuildConfig
import com.sift.app.R
import com.sift.app.databinding.FragmentSettingsBinding
import com.sift.app.domain.model.ThemeMode
import com.sift.app.presentation.common.BaseFragment
import com.sift.app.presentation.common.LanguageLabels
import com.sift.app.presentation.common.Motion
import com.sift.app.presentation.common.ThemeApplier
import com.sift.app.presentation.common.UiState
import com.sift.app.presentation.common.applySystemBarBottomMargin
import com.sift.app.presentation.common.applySystemBarPadding
import com.sift.app.presentation.common.collectWhileStarted
import com.sift.app.presentation.common.errorMessage
import com.sift.app.presentation.common.fadeVisible
import com.sift.app.presentation.common.snackbar
import com.sift.app.presentation.navigation.Transition
import com.sift.app.presentation.onboarding.LanguageFragment
import com.sift.app.presentation.onboarding.PremiumFragment

/** Settings and About: language, Sift Pro, theme, local data, credits and licence. */
class SettingsFragment : BaseFragment<FragmentSettingsBinding>(FragmentSettingsBinding::inflate) {

    private val viewModel: SettingsViewModel by viewModels { SettingsViewModel.factory(container) }
    private var panelShown = false

    private val themeByButton = mapOf(
        R.id.themeDark to ThemeMode.DARK,
        R.id.themeLight to ThemeMode.LIGHT,
        R.id.themeSystem to ThemeMode.SYSTEM
    )

    private val closePanelOnBack = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() = viewModel.setConfirmClearOpen(false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        panelShown = false
        binding.toolbar.applySystemBarPadding(top = true)
        binding.scroll.applySystemBarPadding(bottom = true)
        binding.confirmPanel.applySystemBarBottomMargin()
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, closePanelOnBack)

        binding.toolbar.setNavigationOnClickListener { navigator.back() }
        binding.languageRow.rowIcon.setImageResource(R.drawable.ic_public)
        binding.languageRow.rowTitle.setText(R.string.settings_language)
        binding.languageRow.root.setOnClickListener {
            navigator.push(LanguageFragment.newInstance(LanguageFragment.Mode.SETTINGS), Transition.FORWARD)
        }
        binding.proRow.rowIcon.setImageResource(R.drawable.ic_workspace_premium)
        binding.proRow.rowTitle.setText(R.string.settings_pro)
        binding.proRow.rowValue.setText(R.string.settings_pro_caption)
        binding.proRow.root.contentDescription =
            getString(R.string.cd_language_option, getString(R.string.settings_pro), getString(R.string.settings_pro_caption))
        binding.proRow.root.setOnClickListener {
            navigator.push(PremiumFragment.newInstance(PremiumFragment.Mode.SETTINGS), Transition.FORWARD)
        }
        binding.versionText.text = getString(R.string.settings_version, BuildConfig.VERSION_NAME)

        binding.themeToggle.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) themeByButton[checkedId]?.let(viewModel::setThemeMode)
        }

        binding.clearData.setOnClickListener { viewModel.setConfirmClearOpen(true) }
        binding.scrim.setOnClickListener { viewModel.setConfirmClearOpen(false) }
        binding.cancelClear.setOnClickListener { viewModel.setConfirmClearOpen(false) }
        binding.confirmClear.setOnClickListener { viewModel.clearData() }
        binding.licenseToggle.setOnClickListener { viewModel.toggleLicense() }

        collectWhileStarted(viewModel.state, ::render)
        collectWhileStarted(viewModel.events, ::handle)
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshLanguage()
    }

    private fun render(state: UiState<SettingsContent>) {
        val content = (state as? UiState.Content)?.data ?: return
        val languageName = getString(LanguageLabels.nativeName(content.language))
        binding.languageRow.rowValue.text = languageName
        binding.languageRow.root.contentDescription = getString(R.string.cd_settings_language, languageName)

        val buttonId = themeByButton.entries.first { it.value == content.themeMode }.key
        if (binding.themeToggle.checkedButtonId != buttonId) binding.themeToggle.check(buttonId)

        renderPanel(content.confirmClearOpen)
        binding.confirmClear.isEnabled = !content.clearing
        binding.cancelClear.isEnabled = !content.clearing

        binding.licenseText.isVisible = content.licenseExpanded
        binding.licenseToggle.contentDescription = getString(
            if (content.licenseExpanded) R.string.cd_license_collapse else R.string.cd_license_expand
        )
        binding.licenseToggle.setCompoundDrawablesRelativeWithIntrinsicBounds(
            0, 0, if (content.licenseExpanded) R.drawable.ic_expand_less else R.drawable.ic_expand_more, 0
        )
    }

    private fun renderPanel(open: Boolean) {
        closePanelOnBack.isEnabled = open
        if (open == panelShown) return
        panelShown = open
        binding.scrim.fadeVisible(open)
        val panel = binding.confirmPanel
        val context = requireContext()
        if (!Motion.enabled(context)) {
            panel.isVisible = open
            return
        }
        val offset = resources.getDimension(R.dimen.space_16)
        if (open) {
            panel.translationY = offset
            panel.alpha = 0f
            panel.isVisible = true
            panel.animate().translationY(0f).alpha(1f)
                .setDuration(Motion.duration(context, R.integer.motion_long))
                .setInterpolator(Motion.emphasizedDecelerate)
                .start()
        } else {
            panel.animate().translationY(offset).alpha(0f)
                .setDuration(Motion.duration(context, R.integer.motion_short))
                .setInterpolator(Motion.standard)
                .withEndAction { panel.isVisible = false }
                .start()
        }
    }

    private fun handle(event: SettingsEvent) {
        when (event) {
            SettingsEvent.DataCleared -> binding.root.snackbar(R.string.settings_cleared)
            is SettingsEvent.ApplyTheme -> ThemeApplier.apply(event.mode)
            is SettingsEvent.ShowError -> binding.root.snackbar(requireContext().errorMessage(event.error))
        }
    }

    companion object {
        fun newInstance() = SettingsFragment()
    }
}
