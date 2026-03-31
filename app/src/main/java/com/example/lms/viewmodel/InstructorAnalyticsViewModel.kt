package com.example.lms.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.lms.data.repository.InstructorAnalyticsRepository
import com.example.lms.util.InstructorAnalyticsEvent
import com.example.lms.util.InstructorAnalyticsUiState
import com.example.lms.util.InstructorTimeRange
import com.example.lms.util.ResultState
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class InstructorAnalyticsViewModel(
    private val repository: InstructorAnalyticsRepository = InstructorAnalyticsRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(InstructorAnalyticsUiState())
    val uiState: StateFlow<InstructorAnalyticsUiState> = _uiState.asStateFlow()

    private val _event = MutableSharedFlow<InstructorAnalyticsEvent>()
    val event = _event.asSharedFlow()

    private var lastInstructorId: String = ""

    fun init(instructorId: String) {
        if (instructorId.isBlank()) return
        if (_uiState.value.hasLoadedOnce && lastInstructorId == instructorId) return
        load(instructorId = instructorId, refresh = false)
    }

    fun refresh(instructorId: String) {
        if (instructorId.isBlank()) return
        load(instructorId = instructorId, refresh = true)
    }

    fun selectRange(instructorId: String, range: InstructorTimeRange) {
        if (instructorId.isBlank()) return
        if (_uiState.value.selectedRange == range) return
        _uiState.update { it.copy(selectedRange = range) }
        load(instructorId = instructorId, refresh = false)
    }

    private fun load(instructorId: String, refresh: Boolean) {
        viewModelScope.launch {
            lastInstructorId = instructorId
            _uiState.update {
                it.copy(
                    isLoading = !it.hasLoadedOnce && !refresh,
                    isRefreshing = refresh
                )
            }

            when (
                val result = repository.getInstructorAnalytics(
                    instructorId = instructorId,
                    range = _uiState.value.selectedRange
                )
            ) {
                is ResultState.Success -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isRefreshing = false,
                            analytics = result.data,
                            hasLoadedOnce = true
                        )
                    }
                }

                is ResultState.Error -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isRefreshing = false,
                            hasLoadedOnce = true
                        )
                    }
                    _event.emit(InstructorAnalyticsEvent.ShowError(result.message))
                }

                else -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isRefreshing = false,
                            hasLoadedOnce = true
                        )
                    }
                }
            }
        }
    }
}

