package com.example.lms2.data.model

/**
 * Định nghĩa mô hình dữ liệu Cart dùng trong ứng dụng LMS Android.
 * File này khai báo contract dữ liệu được trao đổi giữa Firestore, repository, ViewModel và giao diện Compose.
 * Việc tách riêng model giúp các luồng nghiệp vụ dùng chung một cấu trúc dữ liệu nhất quán và dễ bảo trì.
 */

data class Cart(
    val id: String = "",
    val userId: String = "",
    val status: CartStatus = CartStatus.ACTIVE,
    val itemCount: Int = 0,
    val totalAmount: Double = 0.0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * Khai báo CartStatus trong file này để phục vụ một trách nhiệm cụ thể của hệ thống.
 */

enum class CartStatus {
    ACTIVE,
    CHECKED_OUT,
    ABANDONED
}

