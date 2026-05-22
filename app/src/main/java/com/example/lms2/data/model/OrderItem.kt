package com.example.lms2.data.model

data class OrderItem(
    val id: String = "",
    val orderId: String = "",
    val userId: String = "",
    val courseId: String = "",
    val courseTitle: String = "",
    val courseThumbnailUrl: String = "",
    val coursePrice: Double = 0.0,
    val instructorId: String = "",
    val instructorName: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

