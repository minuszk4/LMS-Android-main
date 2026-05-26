package com.example.lms2.util

/**
 * Khai báo tập sự kiện giao diện cho luồng Payment.
 * Mỗi nhánh trong sealed class biểu diễn một hành động của người dùng hoặc một tín hiệu mà ViewModel cần xử lý.
 * Cấu trúc event riêng giúp luồng unidirectional data flow rõ ràng và dễ theo dõi hơn.
 */

sealed class PaymentEvent {
    data class ShowError(val message: String) : PaymentEvent()
    data class CheckoutSuccess(val orderId: String) : PaymentEvent()
    data class AwaitingTransferConfirmation(val orderId: String) : PaymentEvent()
    data class OpenExternalPayment(val url: String) : PaymentEvent()
}

