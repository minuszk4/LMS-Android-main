package com.example.lms2.data.model

data class Order(
    val id: String = "",
    val userId: String = "",
    val itemCount: Int = 0,
    val paymentMethod: PaymentMethod = PaymentMethod.E_WALLET,
    val paymentStatus: PaymentStatus = PaymentStatus.PENDING,
    val totalAmount: Double = 0.0,
    val createdAt: Long = System.currentTimeMillis(),
    val payeeInstructorId: String = "",
    val bankName: String = "",
    val bankCode: String = "",
    val bankAccountNumber: String = "",
    val bankAccountHolder: String = "",
    val transferContent: String = "",
    val transferContentNormalized: String = "",
    val qrCodeUrl: String = "",
    val confirmedAt: Long = 0L
)

enum class PaymentMethod {
    E_WALLET
}

enum class PaymentStatus {
    PENDING,
    SUCCESS,
    FAILED,
    CANCELED
}
