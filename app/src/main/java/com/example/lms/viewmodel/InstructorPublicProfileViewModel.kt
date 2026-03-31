package com.example.lms.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.lms.data.repository.InstructorRepository
import com.example.lms.util.InstructorPublicProfileEvent
import com.example.lms.util.InstructorPublicProfileUiState
import com.example.lms.util.ResultState
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class InstructorPublicProfileViewModel(
    private val repository: InstructorRepository = InstructorRepository()
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
            _uiState.update { it.copy(isLoading = true) }

            when (val result = repository.getInstructorById(instructorId)) {
                is ResultState.Success -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            instructor = result.data
                        )
                    }
                }

                is ResultState.Error -> {
                    _uiState.update { it.copy(isLoading = false) }
                    _event.emit(InstructorPublicProfileEvent.ShowError(result.message))
                }

                else -> {
                    _uiState.update { it.copy(isLoading = false) }
                }
            }
        }
    }
}

