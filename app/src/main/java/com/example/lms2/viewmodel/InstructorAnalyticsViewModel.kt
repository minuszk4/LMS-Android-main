package com.example.lms2.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.lms2.data.repository.InstructorAnalyticsRepository
import com.example.lms2.util.InstructorAnalyticsEvent
import com.example.lms2.util.InstructorAnalyticsUiState
import com.example.lms2.util.InstructorTimeRange
import com.example.lms2.util.ResultState
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Điều phối trạng thái giao diện trong InstructorAnalyticsViewModel.
 * File này kết nối màn hình Compose với repository, cập nhật `uiState` và phát event một lần cho các thao tác điều hướng hoặc thông báo.
 * Đây là nơi tập trung phần lớn logic trình bày và điều phối nghiệp vụ ở phía ứng dụng Android.
 */

class InstructorAnalyticsViewModel(
    private val repository: InstructorAnalyticsRepository = InstructorAnalyticsRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(InstructorAnalyticsUiState())
    val uiState: StateFlow<InstructorAnalyticsUiState> = _uiState.asStateFlow()

    private val _event = MutableSharedFlow<InstructorAnalyticsEvent>()
    val event = _event.asSharedFlow()

    private var lastInstructorId: String = ""

    /**
     * Thực hiện phần xử lý chính của luồng nghiệp vụ hoặc giao diện tương ứng.
     * Hàm này chủ yếu cập nhật `uiState`, gọi repository và phát event cho giao diện khi cần.
     */

    fun init(instructorId: String) {
        if (instructorId.isBlank()) return
        if (_uiState.value.hasLoadedOnce && lastInstructorId == instructorId) return
        load(instructorId = instructorId, refresh = false)
    }

    /**
     * Thực hiện phần xử lý chính của luồng nghiệp vụ hoặc giao diện tương ứng.
     * Hàm này chủ yếu cập nhật `uiState`, gọi repository và phát event cho giao diện khi cần.
     */

    fun refresh(instructorId: String) {
        if (instructorId.isBlank()) return
        load(instructorId = instructorId, refresh = true)
    }

    /**
     * Thực hiện phần xử lý chính của luồng nghiệp vụ hoặc giao diện tương ứng.
     * Hàm này chủ yếu cập nhật `uiState`, gọi repository và phát event cho giao diện khi cần.
     */

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

