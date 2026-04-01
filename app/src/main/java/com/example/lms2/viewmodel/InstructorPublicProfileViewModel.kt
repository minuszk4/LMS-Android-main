package com.example.lms2.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.lms2.data.repository.CourseRepository
import com.example.lms2.data.repository.InstructorRepository
import com.example.lms2.util.InstructorPublicProfileEvent
import com.example.lms2.util.InstructorPublicProfileUiState
import com.example.lms2.util.ResultState
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class InstructorPublicProfileViewModel(
    private val repository: InstructorRepository = InstructorRepository(),
    private val courseRepository: CourseRepository = CourseRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(InstructorPublicProfileUiState())
    val uiState: StateFlow<InstructorPublicProfileUiState> = _uiState.asStateFlow()

    private val _event = MutableSharedFlow<InstructorPublicProfileEvent>()
    val event = _event.asSharedFlow()

    private var loadedInstructorId: String = ""

    fun init(instructorId: String) {
        if (instructorId.isBlank()) return
        if (loadedInstructorId == instructorId && _uiState.value.instructor != null) return
        load(instructorId)
    }

    private fun load(instructorId: String) {
        viewModelScope.launch {
            loadedInstructorId = instructorId
            _uiState.update { it.copy(isLoading = true, courses = emptyList()) }

            val instructorResult = repository.getInstructorById(instructorId)
            val coursesResult = courseRepository.getCoursesByInstructor(instructorId)

            when (instructorResult) {
                is ResultState.Success -> {
                    val visibleCourses = when (coursesResult) {
                        is ResultState.Success -> coursesResult.data.filter { it.isPublished }
                        else -> emptyList()
                    }

                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            instructor = instructorResult.data,
                            courses = visibleCourses
                        )
                    }

                    if (coursesResult is ResultState.Error) {
                        _event.emit(InstructorPublicProfileEvent.ShowError(coursesResult.message))
                    }
                }

                is ResultState.Error -> {
                    _uiState.update { it.copy(isLoading = false, courses = emptyList()) }
                    _event.emit(InstructorPublicProfileEvent.ShowError(instructorResult.message))
                }

                else -> {
                    _uiState.update { it.copy(isLoading = false, courses = emptyList()) }
                }
            }
        }
    }
}

