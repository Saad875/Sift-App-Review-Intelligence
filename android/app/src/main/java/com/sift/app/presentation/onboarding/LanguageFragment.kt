package com.sift.app.presentation.onboarding

import android.os.Bundle
import android.view.View
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.sift.app.R
import com.sift.app.databinding.FragmentLanguageBinding
import com.sift.app.presentation.common.BaseFragment
import com.sift.app.presentation.common.UiState
import com.sift.app.presentation.common.applySystemBarBottomMargin
import com.sift.app.presentation.common.applySystemBarPadding
import com.sift.app.presentation.common.collectWhileStarted
import com.sift.app.presentation.common.fadeInUp
import com.sift.app.presentation.navigation.Transition

/**
 * Picks the app language from the 15 supported ones. In onboarding it leads on to the
 * welcome pages; from Settings it simply returns. The choice applies immediately.
 */
class LanguageFragment : BaseFragment<FragmentLanguageBinding>(FragmentLanguageBinding::inflate) {

    enum class Mode { ONBOARDING, SETTINGS }

    private val viewModel: LanguageViewModel by viewModels { LanguageViewModel.factory(container) }
    private val mode: Mode get() = Mode.valueOf(requireArguments().getString(ARG_MODE, Mode.ONBOARDING.name))

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.header.applySystemBarPadding(top = true)
        binding.list.applySystemBarPadding(bottom = true, horizontal = false)
        binding.continueButton.applySystemBarBottomMargin()

        val adapter = LanguageAdapter(viewModel::select)
        binding.list.layoutManager = LinearLayoutManager(requireContext())
        binding.list.adapter = adapter

        val fromSettings = mode == Mode.SETTINGS
        binding.backButton.isVisible = fromSettings
        binding.backButton.setOnClickListener { navigator.back() }
        binding.continueButton.setText(if (fromSettings) R.string.action_done else R.string.language_continue)
        binding.continueButton.setOnClickListener {
            if (fromSettings) navigator.back()
            else navigator.push(WelcomeFragment.newInstance(), Transition.FORWARD)
        }

        if (savedInstanceState == null) {
            binding.title.fadeInUp()
            binding.subtitle.fadeInUp(delayMillis = resources.getInteger(R.integer.motion_stagger_step).toLong())
        }

        collectWhileStarted(viewModel.state) { state ->
            val content = (state as? UiState.Content)?.data ?: return@collectWhileStarted
            if (adapter.currentList.isEmpty()) adapter.submitList(content.languages)
            adapter.selected = content.selected
        }
    }

    companion object {
        private const val ARG_MODE = "mode"

        fun newInstance(mode: Mode) = LanguageFragment().apply {
            arguments = bundleOf(ARG_MODE to mode.name)
        }
    }
}
