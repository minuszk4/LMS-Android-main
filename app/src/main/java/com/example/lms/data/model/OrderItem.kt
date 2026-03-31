package com.example.lms.data.model

data class OrderItem(
    val id: String = "",
    val orderId: String = "",
    val userId: String = "",
    val courseId: String = "",
    val courseTitle: String = "",
    val coursePrice: Double = 0.0,
    val createdAt: Long = System.currentTimeMillis()
)

