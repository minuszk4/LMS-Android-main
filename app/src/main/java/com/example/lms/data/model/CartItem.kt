package com.example.lms.data.model

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

