package com.sift.app.presentation.onboarding

import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sift.app.R
import com.sift.app.di.AppContainer
import com.sift.app.domain.usecase.PreferencesUseCase
import com.sift.app.presentation.common.UiState
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/** The two preview plans. Prices are display copy only; nothing is ever charged. */
enum class ProPlan(
    @StringRes val title: Int,
    @StringRes val price: Int,
    @StringRes val detail: Int
) {
    YEARLY(R.string.pro_plan_yearly, R.string.pro_plan_yearly_price, R.string.pro_plan_yearly_detail),
    MONTHLY(R.string.pro_plan_monthly, R.string.pro_plan_monthly_price, R.string.pro_plan_monthly_detail)
}

data class PremiumContent(val selectedPlan: ProPlan)

sealed interface PremiumEvent {
    /** Subscribing is not available yet; show the "coming soon" message. */
    data object ComingSoon : PremiumEvent

    /** Leave the screen (and finish onboarding when it came from there). */
    data object Leave : PremiumEvent
}

/**
 * The Sift Pro preview. There is no billing: continuing only shows a message, and
 * no payment details are ever requested.
 */
class PremiumViewModel(
    private val preferences: PreferencesUseCase,
    private val savedState: SavedStateHandle
) : ViewModel() {

    private val _state = MutableStateFlow<UiState<PremiumContent>>(
        UiState.Content(PremiumContent(savedState.get<String>(KEY_PLAN)?.let(ProPlan::valueOf) ?: ProPlan.YEARLY))
    )
    val state: StateFlow<UiState<PremiumContent>> = _state.asStateFlow()

    private val _events = Channel<PremiumEvent>(Channel.BUFFERED)
    val events: Flow<PremiumEvent> = _events.receiveAsFlow()

    fun selectPlan(plan: ProPlan) {
        savedState[KEY_PLAN] = plan.name
        _state.value = UiState.Content(PremiumContent(plan))
    }

    fun subscribe() {
        viewModelScope.launch { _events.send(PremiumEvent.ComingSoon) }
    }

    /** "Maybe later" or close. Marks onboarding as done so it never shows again. */
    fun leave() {
        preferences.completeOnboarding()
        viewModelScope.launch { _events.send(PremiumEvent.Leave) }
    }

    companion object {
        private const val KEY_PLAN = "plan"

        fun factory(container: AppContainer) = viewModelFactory {
            initializer { PremiumViewModel(container.preferences, createSavedStateHandle()) }
        }
    }
}
