package com.example.lms2.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.lms2.data.model.User
import com.example.lms2.data.model.UserRole
import com.example.lms2.data.repository.AuthRepository
import com.example.lms2.util.ResultState
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AdminApprovalUiState(
    val isLoading: Boolean = false,
    val isProcessing: Boolean = false,
    val pendingUsers: List<User> = emptyList()
)

sealed class AdminApprovalEvent {
    data class ShowError(val message: String) : AdminApprovalEvent()
    data class ShowSuccess(val message: String) : AdminApprovalEvent()
}

class AdminApprovalViewModel(
    private val authRepository: AuthRepository = AuthRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(AdminApprovalUiState())
    val uiState = _uiState.asStateFlow()

    private val _event = MutableSharedFlow<AdminApprovalEvent>()
    val event = _event.asSharedFlow()

    fun loadPendingRequests() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            when (val result = authRepository.getPendingInstructorApplications()) {
                is ResultState.Success -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        pendingUsers = result.data
                    )
                }
                is ResultState.Error -> {
                    _uiState.value = _uiState.value.copy(isLoading = false)
                    _event.emit(AdminApprovalEvent.ShowError(result.message))
                }
                ResultState.Loading -> Unit
            }
        }
    }

    fun approveInstructor(targetUser: User, adminUid: String) {
        if (adminUid.isBlank()) {
            viewModelScope.launch { _event.emit(AdminApprovalEvent.ShowError("Không tìm thấy tài khoản admin")) }
            return
        }

        if (targetUser.role != UserRole.STUDENT) {
            viewModelScope.launch { _event.emit(AdminApprovalEvent.ShowError("Người dùng này không ở vai trò học viên")) }
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isProcessing = true)
            when (val result = authRepository.approveInstructorApplication(targetUser.uid, adminUid)) {
                is ResultState.Success -> {
                    _event.emit(AdminApprovalEvent.ShowSuccess("Đã phê duyệt giảng viên: ${targetUser.fullName}"))
                    loadPendingRequests()
                }
                is ResultState.Error -> {
                    _event.emit(AdminApprovalEvent.ShowError(result.message))
                    _uiState.value = _uiState.value.copy(isProcessing = false)
                }
                ResultState.Loading -> Unit
            }
            _uiState.value = _uiState.value.copy(isProcessing = false)
        }
    }

    fun rejectInstructor(targetUser: User, adminUid: String, reason: String) {
        if (adminUid.isBlank()) {
            viewModelScope.launch { _event.emit(AdminApprovalEvent.ShowError("Không tìm thấy tài khoản admin")) }
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isProcessing = true)
            when (val result = authRepository.rejectInstructorApplication(targetUser.uid, adminUid, reason)) {
                is ResultState.Success -> {
                    _event.emit(AdminApprovalEvent.ShowSuccess("Đã từ chối đơn của: ${targetUser.fullName}"))
                    loadPendingRequests()
                }
                is ResultState.Error -> {
                    _event.emit(AdminApprovalEvent.ShowError(result.message))
                    _uiState.value = _uiState.value.copy(isProcessing = false)
                }
                ResultState.Loading -> Unit
            }
            _uiState.value = _uiState.value.copy(isProcessing = false)
        }
    }
}
