package com.example.lms2.data.model

/**
 * Định nghĩa mô hình dữ liệu Order dùng trong ứng dụng LMS Android.
 * File này khai báo contract dữ liệu được trao đổi giữa Firestore, repository, ViewModel và giao diện Compose.
 * Việc tách riêng model giúp các luồng nghiệp vụ dùng chung một cấu trúc dữ liệu nhất quán và dễ bảo trì.
 */

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

/**
 * Khai báo PaymentMethod trong file này để phục vụ một trách nhiệm cụ thể của hệ thống.
 */

enum class PaymentMethod {
    E_WALLET
}

/**
 * Khai báo PaymentStatus trong file này để phục vụ một trách nhiệm cụ thể của hệ thống.
 */

enum class PaymentStatus {
    PENDING,
    SUCCESS,
    FAILED,
    CANCELED
}
