package com.example.lms.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.lms.data.repository.CourseAnalyticsRepository
import com.example.lms.util.CourseAnalyticsEvent
import com.example.lms.util.CourseAnalyticsUiState
import com.example.lms.util.ResultState
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class CourseAnalyticsViewModel(
    private val repository: CourseAnalyticsRepository = CourseAnalyticsRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(CourseAnalyticsUiState())
    val uiState: StateFlow<CourseAnalyticsUiState> = _uiState.asStateFlow()

    private val _event = MutableSharedFlow<CourseAnalyticsEvent>()
    val event = _event.asSharedFlow()

    private var loadedCourseId: String = ""

    fun init(courseId: String) {
        if (courseId.isBlank()) return
        if (loadedCourseId == courseId && _uiState.value.analytics != null) return
        load(courseId)
    }

    fun refresh(courseId: String) {
        if (courseId.isBlank()) return
        load(courseId)
    }

    private fun load(courseId: String) {
        viewModelScope.launch {
            val isDifferentCourse = loadedCourseId != courseId
            loadedCourseId = courseId
            _uiState.update {
                it.copy(
                    isLoading = true,
                    analytics = if (isDifferentCourse) null else it.analytics
                )
            }

            when (val result = repository.getCourseAnalytics(courseId)) {
                is ResultState.Success -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            analytics = result.data
                        )
                    }
                }

                is ResultState.Error -> {
                    _uiState.update { it.copy(isLoading = false) }
                    _event.emit(CourseAnalyticsEvent.ShowError(result.message))
                }

                else -> {
                    _uiState.update { it.copy(isLoading = false) }
                }
            }
        }
    }
}

