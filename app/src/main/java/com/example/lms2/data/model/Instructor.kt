package com.example.lms2.data.model

/**
 * Định nghĩa mô hình dữ liệu Instructor dùng trong ứng dụng LMS Android.
 * File này khai báo contract dữ liệu được trao đổi giữa Firestore, repository, ViewModel và giao diện Compose.
 * Việc tách riêng model giúp các luồng nghiệp vụ dùng chung một cấu trúc dữ liệu nhất quán và dễ bảo trì.
 */

data class Instructor(
    val uid: String = "",
    val expertise: String = "",
    val experienceYears: Int = 0,
    val qualification: String = "",
    val bankAccount: String = "",
    val bankName: String = "",
    val bankCode: String = "",
    val bankAccountNumber: String = "",
    val bankAccountHolder: String = "",
)

