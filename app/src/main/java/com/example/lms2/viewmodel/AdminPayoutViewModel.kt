package com.example.lms2.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.lms2.data.model.InstructorPayout
import com.example.lms2.data.repository.PayoutRepository
import com.example.lms2.util.ResultState
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Điều phối trạng thái giao diện trong AdminPayoutViewModel.
 * File này kết nối màn hình Compose với repository, cập nhật `uiState` và phát event một lần cho các thao tác điều hướng hoặc thông báo.
 * Đây là nơi tập trung phần lớn logic trình bày và điều phối nghiệp vụ ở phía ứng dụng Android.
 */

data class AdminPayoutUiState(
    val isLoading: Boolean = false,
    val isProcessing: Boolean = false,
    val payouts: List<InstructorPayout> = emptyList()
)

/**
 * Khai báo AdminPayoutEvent trong file này để phục vụ một trách nhiệm cụ thể của hệ thống.
 */

sealed class AdminPayoutEvent {
    data class ShowError(val message: String) : AdminPayoutEvent()
    data class ShowSuccess(val message: String) : AdminPayoutEvent()
}

/**
 * Khai báo AdminPayoutViewModel trong file này để phục vụ một trách nhiệm cụ thể của hệ thống.
 */

class AdminPayoutViewModel(
    private val payoutRepository: PayoutRepository = PayoutRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(AdminPayoutUiState())
    val uiState = _uiState.asStateFlow()

    private val _event = MutableSharedFlow<AdminPayoutEvent>()
    val event = _event.asSharedFlow()

    /**
     * Tải dữ liệu và cập nhật trạng thái hiển thị liên quan.
     * Hàm này chủ yếu cập nhật `uiState`, gọi repository và phát event cho giao diện khi cần.
     */

    fun loadPayouts() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            when (val result = payoutRepository.getInstructorPayouts()) {
                is ResultState.Success -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        payouts = result.data
                    )
                }

                is ResultState.Error -> {
                    _uiState.value = _uiState.value.copy(isLoading = false)
                    _event.emit(AdminPayoutEvent.ShowError(result.message))
                }

                ResultState.Loading -> Unit
            }
        }
    }

    /**
     * Thực hiện phần xử lý chính của luồng nghiệp vụ hoặc giao diện tương ứng.
     * Hàm này chủ yếu cập nhật `uiState`, gọi repository và phát event cho giao diện khi cần.
     */

    fun markPayoutGroupAsPaid(
        payoutIds: List<String>,
        adminUid: String,
        manualTransferReference: String
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isProcessing = true)

            when (
                val result = payoutRepository.markPayoutsAsPaid(
                    payoutIds = payoutIds,
                    adminUid = adminUid,
                    manualTransferReference = manualTransferReference
                )
            ) {
                is ResultState.Success -> {
                    _event.emit(AdminPayoutEvent.ShowSuccess("Da danh dau chuyen tien thanh cong"))
                    loadPayouts()
                }

                is ResultState.Error -> {
                    _event.emit(AdminPayoutEvent.ShowError(result.message))
                }

                ResultState.Loading -> Unit
            }

            _uiState.value = _uiState.value.copy(isProcessing = false)
        }
    }
}
