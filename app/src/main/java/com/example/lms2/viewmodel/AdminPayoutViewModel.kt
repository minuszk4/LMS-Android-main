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
 * ViewModel cho màn hình quản lý payout giảng viên của admin.
 *
 * File này tập trung vào hai tác vụ: tải danh sách payout hiện có
 * và đánh dấu một nhóm payout là đã thanh toán.
 */
data class AdminPayoutUiState(
    val isLoading: Boolean = false,
    val isProcessing: Boolean = false,
    val payouts: List<InstructorPayout> = emptyList()
)

/**
 * Event một lần để hiển thị lỗi hoặc thông báo thành công.
 */
sealed class AdminPayoutEvent {
    data class ShowError(val message: String) : AdminPayoutEvent()
    data class ShowSuccess(val message: String) : AdminPayoutEvent()
}

class AdminPayoutViewModel(
    private val payoutRepository: PayoutRepository = PayoutRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(AdminPayoutUiState())
    val uiState = _uiState.asStateFlow()

    private val _event = MutableSharedFlow<AdminPayoutEvent>()
    val event = _event.asSharedFlow()

    /**
     * Tải danh sách payout hiện tại để admin rà soát và xử lý.
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
     * Đánh dấu một nhóm payout là đã chuyển tiền thành công.
     *
     * `manualTransferReference` được truyền xuống repository để lưu dấu vết
     * cho đợt chi trả thủ công của admin.
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
                    _event.emit(AdminPayoutEvent.ShowSuccess("Đã đánh dấu chuyển tiền thành công"))
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
