package com.example.lms2.data.model

/**
 * Định nghĩa mô hình dữ liệu InstructorPayout dùng trong ứng dụng LMS Android.
 * File này khai báo contract dữ liệu được trao đổi giữa Firestore, repository, ViewModel và giao diện Compose.
 * Việc tách riêng model giúp các luồng nghiệp vụ dùng chung một cấu trúc dữ liệu nhất quán và dễ bảo trì.
 */

data class InstructorPayout(
    val id: String = "",
    val orderId: String = "",
    val orderItemId: String = "",
    val courseId: String = "",
    val courseTitle: String = "",
    val courseThumbnailUrl: String = "",
    val studentId: String = "",
    val studentName: String = "",
    val instructorId: String = "",
    val instructorName: String = "",
    val grossAmount: Double = 0.0,
    val payoutAmount: Double = 0.0,
    val payoutStatus: PayoutStatus = PayoutStatus.PENDING,
    val orderConfirmedAt: Long = 0L,
    val paidAt: Long = 0L,
    val paidByAdminUid: String = "",
    val manualTransferReference: String = "",
    val bankName: String = "",
    val bankCode: String = "",
    val bankAccountNumber: String = "",
    val bankAccountHolder: String = "",
    val hasBankInfo: Boolean = false
)

/**
 * Khai báo PayoutStatus trong file này để phục vụ một trách nhiệm cụ thể của hệ thống.
 */

enum class PayoutStatus {
    PENDING,
    PAID
}
