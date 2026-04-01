package com.example.lms2.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.lms2.data.repository.InstructorRepository
import com.example.lms2.util.InstructorPersonalInfoEvent
import com.example.lms2.util.InstructorPersonalInfoUiState
import com.example.lms2.util.ResultState
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class InstructorPersonalInfoViewModel(
    private val repository: InstructorRepository = InstructorRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(InstructorPersonalInfoUiState())
    val uiState: StateFlow<InstructorPersonalInfoUiState> = _uiState.asStateFlow()

    private val _event = MutableSharedFlow<InstructorPersonalInfoEvent>()
    val event = _event.asSharedFlow()

    private var loadedInstructorId: String = ""

    fun init(instructorId: String) {
        if (instructorId.isBlank()) return
        if (loadedInstructorId == instructorId && _uiState.value.instructor != null) return
        load(instructorId)
    }

    fun refresh(instructorId: String) {
        if (instructorId.isBlank()) return
        load(instructorId)
    }

    fun saveBankInfo(
        instructorId: String,
        bankName: String,
        bankCode: String,
        bankAccountNumber: String,
        bankAccountHolder: String
    ) {
        if (instructorId.isBlank()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }

            when (
                val result = repository.updateBankInfo(
                    instructorId = instructorId,
                    bankName = bankName,
                    bankCode = bankCode,
                    bankAccountNumber = bankAccountNumber,
                    bankAccountHolder = bankAccountHolder
                )
            ) {
                is ResultState.Success -> {
                    _uiState.update { it.copy(isSaving = false) }
                    _event.emit(InstructorPersonalInfoEvent.ShowSuccess("Đã cập nhật tài khoản ngân hàng"))
                    load(instructorId)
                }

                is ResultState.Error -> {
                    _uiState.update { it.copy(isSaving = false) }
                    _event.emit(InstructorPersonalInfoEvent.ShowError(result.message))
                }

                else -> {
                    _uiState.update { it.copy(isSaving = false) }
                }
            }
        }
    }

    fun saveInstructorProfile(
        instructorId: String,
        expertise: String,
        qualification: String,
        experienceYears: Int
    ) {
        if (instructorId.isBlank()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }

            when (
                val result = repository.updateInstructorProfile(
                    instructorId = instructorId,
                    expertise = expertise,
                    qualification = qualification,
                    experienceYears = experienceYears
                )
            ) {
                is ResultState.Success -> {
                    _uiState.update { it.copy(isSaving = false) }
                    _event.emit(InstructorPersonalInfoEvent.ShowSuccess("Đã cập nhật thông tin giảng viên"))
                    load(instructorId)
                }

                is ResultState.Error -> {
                    _uiState.update { it.copy(isSaving = false) }
                    _event.emit(InstructorPersonalInfoEvent.ShowError(result.message))
                }

                else -> {
                    _uiState.update { it.copy(isSaving = false) }
                }
            }
        }
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
                    _event.emit(InstructorPersonalInfoEvent.ShowError(result.message))
                }

                else -> {
                    _uiState.update { it.copy(isLoading = false) }
                }
            }
        }
    }
}

