package com.example.lms2.data.model

/**
 * Định nghĩa mô hình dữ liệu CartItem dùng trong ứng dụng LMS Android.
 * File này khai báo contract dữ liệu được trao đổi giữa Firestore, repository, ViewModel và giao diện Compose.
 * Việc tách riêng model giúp các luồng nghiệp vụ dùng chung một cấu trúc dữ liệu nhất quán và dễ bảo trì.
 */

data class CartItem(
    val id: String = "",
    val cartId: String = "",
    val userId: String = "",
    val courseId: String = "",
    val courseThumbnail: String = "",
    val courseTitle: String = "",
    val coursePrice: Double = 0.0,
    val addedAt: Long = System.currentTimeMillis()
)

