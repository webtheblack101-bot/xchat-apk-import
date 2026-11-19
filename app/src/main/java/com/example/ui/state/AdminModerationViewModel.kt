package com.example.ui.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.ModerationAction
import com.example.data.model.ReportItem
import com.example.data.model.UiState
import com.example.data.model.userFriendlyMessage
import com.example.data.repository.SafetyNotificationRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AdminDashboardBundle(
    val reports: List<ReportItem>,
    val actions: List<ModerationAction>
)

class AdminModerationViewModel(
    private val safetyRepository: SafetyNotificationRepository
) : ViewModel() {

    private val _feedback = MutableStateFlow<String?>(null)
    val feedback: StateFlow<String?> = _feedback.asStateFlow()

    val dashboardState: StateFlow<UiState<AdminDashboardBundle>> = combine(
        safetyRepository.observeReportsForAdmin(),
        safetyRepository.observeModerationActionsForAdmin()
    ) { reports, actions ->
        AdminDashboardBundle(reports, actions)
    }
        .asUiState("AdminModerationVM")
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = UiState.Loading
        )

    fun clearFeedback() {
        _feedback.value = null
    }

    fun takeAction(reportId: String?, targetUserId: String, actionType: String, reason: String) {
        viewModelScope.launch {
            val res = safetyRepository.executeModerationAction(reportId, targetUserId, actionType, reason)
            _feedback.value = res.fold(
                onSuccess = { "Moderation action '$actionType' recorded." },
                onFailure = { it.userFriendlyMessage("Moderation action failed.") }
            )
        }
    }
}
