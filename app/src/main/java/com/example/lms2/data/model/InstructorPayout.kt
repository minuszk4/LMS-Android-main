package com.example.lms2.data.model

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

enum class PayoutStatus {
    PENDING,
    PAID
}
