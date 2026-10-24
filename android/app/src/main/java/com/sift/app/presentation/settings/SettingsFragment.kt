package com.sift.app.presentation.settings

import android.os.Bundle
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import android.view.inputmethod.EditorInfo
import androidx.activity.OnBackPressedCallback
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.widget.TextViewCompat
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.viewModels
import com.sift.app.BuildConfig
import com.sift.app.R
import com.sift.app.databinding.FragmentSettingsBinding
import com.sift.app.domain.model.ModelRole
import com.sift.app.domain.model.NemotronVariant
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
import com.sift.app.presentation.common.hideKeyboard
import com.sift.app.presentation.common.nameRes
import com.sift.app.presentation.common.snackbar
import com.sift.app.presentation.navigation.Transition
import com.sift.app.presentation.onboarding.LanguageFragment
import com.sift.app.presentation.onboarding.PremiumFragment

/** Settings and About: backend URL, connection test, theme, local data, credits and licence. */
class SettingsFragment : BaseFragment<FragmentSettingsBinding>(FragmentSettingsBinding::inflate) {

    private val viewModel: SettingsViewModel by viewModels { SettingsViewModel.factory(container) }
    private var panelShown = false
    private var urlInitialised = false

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
        // The EditText restores its own text after recreation; only fill it on first creation.
        urlInitialised = savedInstanceState != null
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
        binding.proRow.root.setOnClickListener {
            navigator.push(PremiumFragment.newInstance(PremiumFragment.Mode.SETTINGS), Transition.FORWARD)
        }
        binding.versionText.text = getString(R.string.settings_version, BuildConfig.VERSION_NAME)

        binding.urlInput.doAfterTextChanged { viewModel.clearUrlError() }
        binding.urlInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                saveUrl()
                true
            } else {
                false
            }
        }
        binding.saveUrl.setOnClickListener { saveUrl() }
        binding.resetUrl.setOnClickListener {
            binding.root.hideKeyboard()
            viewModel.resetBackendUrl()
        }
        binding.testConnection.setOnClickListener {
            binding.root.hideKeyboard()
            viewModel.testConnection()
        }

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

    private fun saveUrl() {
        binding.root.hideKeyboard()
        viewModel.saveBackendUrl(binding.urlInput.text?.toString().orEmpty())
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshLanguage()
    }

    private fun render(state: UiState<SettingsContent>) {
        val content = (state as? UiState.Content)?.data ?: return
        if (!urlInitialised) {
            urlInitialised = true
            binding.urlInput.setText(content.backendUrl)
        }
        binding.urlLayout.error = content.urlError?.let { getString(it) }
        binding.resetUrl.isEnabled = !content.isDefaultUrl
        val languageName = getString(LanguageLabels.nativeName(content.language))
        binding.languageRow.rowValue.text = languageName
        binding.languageRow.root.contentDescription = getString(R.string.cd_settings_language, languageName)

        val buttonId = themeByButton.entries.first { it.value == content.themeMode }.key
        if (binding.themeToggle.checkedButtonId != buttonId) binding.themeToggle.check(buttonId)

        renderHealth(content.health)
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

    private fun renderHealth(health: HealthState) {
        val context = requireContext()
        binding.testConnection.isEnabled = health != HealthState.Checking
        binding.healthProgress.isVisible = health == HealthState.Checking
        binding.healthStatus.isVisible = health != HealthState.Idle
        val (text, icon, color) = when (health) {
            HealthState.Idle -> Triple("", 0, R.color.sift_text_secondary)
            HealthState.Checking -> Triple(getString(R.string.settings_checking), 0, R.color.sift_text_secondary)
            is HealthState.Connected -> {
                val configured = when {
                    health.health.nebiusConfigured && health.health.tavilyConfigured -> R.string.settings_connected_all
                    health.health.nebiusConfigured -> R.string.settings_connected_no_tavily
                    else -> R.string.settings_connected_no_nebius
                }
                val tint = if (health.health.nebiusConfigured) R.color.sift_positive else R.color.sift_neutral
                Triple(getString(configured), R.drawable.ic_check, tint)
            }
            is HealthState.Failed -> Triple(context.errorMessage(health.error), R.drawable.ic_cloud_off, R.color.sift_negative)
        }
        binding.healthStatus.text = text
        binding.healthStatus.setCompoundDrawablesRelativeWithIntrinsicBounds(icon, 0, 0, 0)
        TextViewCompat.setCompoundDrawableTintList(binding.healthStatus, ContextCompat.getColorStateList(context, color))

        val connected = (health as? HealthState.Connected)?.health
        if (connected == null) {
            binding.fastModel.setText(R.string.settings_models_unknown)
            binding.reasoningModel.isVisible = false
        } else {
            binding.fastModel.text = modelLine(R.string.settings_fast_model, connected.fastModel, ModelRole.FAST)
            binding.reasoningModel.text = modelLine(
                R.string.settings_reasoning_model, connected.reasoningModel, ModelRole.REASONING
            )
            binding.reasoningModel.isVisible = true
        }
    }

    private fun modelLine(@StringRes template: Int, modelId: String, role: ModelRole): String {
        val name = getString(NemotronVariant.fromModelId(modelId, role).nameRes)
        val shown = if (modelId.isBlank()) name else getString(R.string.settings_model_with_id, name, modelId)
        return getString(template, shown)
    }

    private fun renderPanel(open: Boolean) {
        closePanelOnBack.isEnabled = open
        if (open == panelShown) return
        panelShown = open
        binding.scrim.fadeVisible(open)
        val panel = binding.confirmPanel
        val title = binding.confirmTitle
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
                .withEndAction {
                    title.performAccessibilityAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS, null)
                }
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
            SettingsEvent.UrlSaved -> binding.root.snackbar(R.string.settings_saved)
            SettingsEvent.UrlReset -> {
                (viewModel.state.value as? UiState.Content)?.data?.backendUrl?.let(binding.urlInput::setText)
                binding.root.snackbar(R.string.settings_reset_done)
            }
            SettingsEvent.DataCleared -> binding.root.snackbar(R.string.settings_cleared)
            is SettingsEvent.ApplyTheme -> ThemeApplier.apply(event.mode)
            is SettingsEvent.ShowError -> binding.root.snackbar(requireContext().errorMessage(event.error))
        }
    }

    companion object {
        fun newInstance() = SettingsFragment()
    }
}
