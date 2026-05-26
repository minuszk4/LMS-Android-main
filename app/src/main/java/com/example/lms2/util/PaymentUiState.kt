package com.example.lms2.util

import com.example.lms2.data.model.CartItem
import com.example.lms2.data.model.PaymentMethod
import com.example.lms2.data.model.Order

/**
 * Mô tả trạng thái giao diện của luồng Payment.
 * Đối tượng này gom dữ liệu hiển thị, cờ tải, lỗi kiểm tra và các trạng thái tạm mà Compose cần quan sát.
 * Việc gom toàn bộ state vào một nơi giúp màn hình render nhất quán và dễ kiểm thử hơn.
 */

enum class CheckoutSource {
    CART,
    DIRECT
}

/**
 * Khai báo PaymentUiState trong file này để phục vụ một trách nhiệm cụ thể của hệ thống.
 */

data class PaymentUiState(
    val isLoading: Boolean = false,
    val isSubmitting: Boolean = false,
    val checkoutSource: CheckoutSource = CheckoutSource.CART,
    val selectedCourseIds: List<String> = emptyList(),
    val selectedItems: List<CartItem> = emptyList(),
    val paymentMethod: PaymentMethod = PaymentMethod.E_WALLET,
    val externalPaymentUrl: String = "",
    val pendingOrder: Order? = null,
    val isCheckingPaymentStatus: Boolean = false
) {
    val itemCount: Int
        get() = selectedItems.size

    val totalAmount: Double
        get() = selectedItems.sumOf { it.coursePrice }
}

