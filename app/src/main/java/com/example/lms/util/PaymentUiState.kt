package com.example.lms.util

import com.example.lms.data.model.CartItem
import com.example.lms.data.model.PaymentMethod
import com.example.lms.data.model.Order

enum class CheckoutSource {
    CART,
    DIRECT
}

data class PaymentUiState(
    val isLoading: Boolean = false,
    val isSubmitting: Boolean = false,
    val checkoutSource: CheckoutSource = CheckoutSource.CART,
    val selectedCourseIds: List<String> = emptyList(),
    val selectedItems: List<CartItem> = emptyList(),
    val paymentMethod: PaymentMethod = PaymentMethod.E_WALLET,
    val pendingOrder: Order? = null,
    val isCheckingPaymentStatus: Boolean = false
) {
    val itemCount: Int
        get() = selectedItems.size

    val totalAmount: Double
        get() = selectedItems.sumOf { it.coursePrice }
}

