package com.example.lms2.util

sealed class PaymentEvent {
    data class ShowError(val message: String) : PaymentEvent()
    data class CheckoutSuccess(val orderId: String) : PaymentEvent()
    data class AwaitingTransferConfirmation(val orderId: String) : PaymentEvent()
    data class OpenExternalPayment(val url: String) : PaymentEvent()
}

