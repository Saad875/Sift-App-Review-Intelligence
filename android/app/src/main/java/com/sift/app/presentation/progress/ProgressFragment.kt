package com.sift.app.presentation.progress

import android.os.Bundle
import android.view.View
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import com.google.android.material.snackbar.Snackbar
import com.sift.app.R
import com.sift.app.databinding.FragmentProgressBinding
import com.sift.app.domain.model.Analysis
import com.sift.app.domain.model.AnalysisSource
import com.sift.app.domain.model.AnalysisStatus
import com.sift.app.domain.model.PipelineStep
import com.sift.app.domain.model.StepKey
import com.sift.app.domain.model.StepState
import com.sift.app.presentation.common.BaseFragment
import com.sift.app.presentation.common.Formatters
import com.sift.app.presentation.common.Motion
import com.sift.app.presentation.common.UiState
import com.sift.app.presentation.common.applySystemBarPadding
import com.sift.app.presentation.common.bind
import com.sift.app.presentation.common.collectWhileStarted
import com.sift.app.presentation.common.errorMessage
import com.sift.app.presentation.common.fadeVisible
import com.sift.app.presentation.common.labelRes
import com.sift.app.presentation.common.loadAppIcon
import com.sift.app.presentation.common.nameRes
import com.sift.app.presentation.common.runningRes
import com.sift.app.presentation.common.titleRes
import com.sift.app.presentation.common.waitingRes
import com.sift.app.presentation.home.HomeFragment
import com.sift.app.presentation.navigation.Transition
import com.sift.app.presentation.report.ReportFragment
import com.sift.app.presentation.widget.TimelineStepView

/**
 * Live progress: a vertical timeline of the five pipeline steps showing which
 * Nemotron model is working. Survives rotation and can be left and resumed.
 */
class ProgressFragment : BaseFragment<FragmentProgressBinding>(FragmentProgressBinding::inflate) {

    private val viewModel: ProgressViewModel by viewModels { ProgressViewModel.factory(container) }
    private var navigatedToReport = false
    private var lastProgress = -1

    private val stepViews: Map<StepKey, TimelineStepView>
        get() = mapOf(
            StepKey.FETCHING to binding.stepFetching,
            StepKey.CLASSIFYING to binding.stepClassifying,
            StepKey.CLUSTERING to binding.stepClustering,
            StepKey.PRIORITIZING to binding.stepPrioritizing,
            StepKey.RESEARCHING to binding.stepResearching
        )

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.toolbar.applySystemBarPadding(top = true)
        binding.content.applySystemBarPadding(bottom = true)
        binding.toolbar.setNavigationOnClickListener { navigator.back() }
        binding.homeButton.setOnClickListener { navigator.setRoot(HomeFragment.newInstance()) }
        binding.stepResearching.isLast = true
        lastProgress = -1

        collectWhileStarted(viewModel.state, ::render)
        collectWhileStarted(viewModel.events, ::handle)
    }

    private fun render(state: UiState<ProgressContent>) {
        binding.skeleton.root.isVisible = state is UiState.Loading
        binding.scroll.isVisible = state is UiState.Content
        binding.errorState.root.isVisible = state is UiState.Error
        when (state) {
            is UiState.Content -> renderContent(state.data)
            is UiState.Error -> binding.errorState.bind(state.error, viewModel::retry)
            else -> Unit
        }
    }

    private fun renderContent(content: ProgressContent) {
        val analysis = content.analysis
        binding.toolbar.title = analysis.appName
        binding.appName.text = analysis.appName
        binding.appIcon.loadAppIcon(analysis.iconUrl.takeIf { analysis.source == AnalysisSource.APP_STORE })
        val meta = mutableListOf(getString(analysis.source.labelRes))
        if (analysis.source == AnalysisSource.APP_STORE) meta += Formatters.countryName(analysis.country)
        binding.appMeta.text = meta.joinToString(getString(R.string.meta_separator))

        renderProgress(analysis.progress)
        renderSteps(analysis)

        binding.connectionBanner.fadeVisible(content.connectionIssue != null)
        content.connectionIssue?.let {
            binding.connectionText.text = getString(R.string.progress_reconnecting_detail, requireContext().errorMessage(it))
        }

        val failed = analysis.status == AnalysisStatus.FAILED
        binding.failureCard.isVisible = failed
        binding.leaveHint.isVisible = !failed && analysis.status != AnalysisStatus.COMPLETED
        if (failed) {
            binding.failureMessage.text = analysis.error.ifBlank { getString(R.string.error_generic_message) }
            binding.restartButton.isVisible = content.canRestart
            binding.restartButton.isEnabled = !content.isRestarting
            binding.restartButton.setOnClickListener { viewModel.restart(analysis) }
        }

        if (content.reportReady && !navigatedToReport) {
            navigatedToReport = true
            val delay = if (Motion.enabled(requireContext())) {
                Motion.duration(requireContext(), R.integer.motion_extra_long)
            } else {
                0L
            }
            binding.root.postDelayed({
                if (isAdded) navigator.replaceCurrent(ReportFragment.newInstance(analysis.id), Transition.FADE_THROUGH)
            }, delay)
        }
    }

    private fun renderProgress(progress: Int) {
        if (progress == lastProgress) return
        val animate = lastProgress >= 0 && Motion.enabled(requireContext())
        lastProgress = progress
        binding.percent.text = getString(R.string.percent_value, progress)
        binding.progressBar.setProgressCompat(progress, animate)
        binding.progressBar.contentDescription = getString(R.string.cd_progress, progress)
    }

    private fun renderSteps(analysis: Analysis) {
        val byKey = analysis.steps.associateBy { it.key }
        stepViews.forEach { (key, view) ->
            val step = byKey[key] ?: PipelineStep(key, StepState.WAITING, null, null)
            view.bind(getString(key.titleRes), subtitleFor(step, analysis), step.state)
        }
    }

    private fun subtitleFor(step: PipelineStep, analysis: Analysis): String {
        val stats = analysis.stats
        return when (step.state) {
            StepState.WAITING -> getString(step.key.waitingRes)
            StepState.RUNNING -> getString(step.key.runningRes, getString(step.variant.nameRes))
            StepState.SKIPPED -> getString(R.string.step_skipped_message)
            StepState.FAILED -> getString(R.string.step_failed_message)
            StepState.DONE -> when (step.key) {
                StepKey.FETCHING -> resources.getQuantityString(
                    R.plurals.step_fetching_done, stats.totalReviews, Formatters.count(stats.totalReviews)
                )
                StepKey.CLASSIFYING -> resources.getQuantityString(
                    R.plurals.step_classifying_done, stats.classifiedReviews, Formatters.count(stats.classifiedReviews)
                )
                StepKey.CLUSTERING -> resources.getQuantityString(
                    R.plurals.step_clustering_done, stats.themeCount, stats.themeCount
                )
                StepKey.PRIORITIZING -> resources.getQuantityString(
                    R.plurals.step_prioritizing_done, stats.backlogCount, stats.backlogCount
                )
                StepKey.RESEARCHING -> resources.getQuantityString(
                    R.plurals.step_researching_done, stats.competitorCount, stats.competitorCount
                )
            }
        }
    }

    private fun handle(event: ProgressEvent) {
        when (event) {
            is ProgressEvent.Restarted -> navigator.replaceCurrent(newInstance(event.analysisId), Transition.FADE_THROUGH)
            is ProgressEvent.ShowError -> Snackbar.make(
                binding.root, requireContext().errorMessage(event.error), Snackbar.LENGTH_LONG
            ).show()
        }
    }

    companion object {
        fun newInstance(analysisId: String) = ProgressFragment().apply {
            arguments = bundleOf(ProgressViewModel.KEY_ANALYSIS_ID to analysisId)
        }
    }
}
