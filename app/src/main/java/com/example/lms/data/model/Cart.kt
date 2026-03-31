package com.example.lms.data.model

data class Cart(
    val id: String = "",
    val userId: String = "",
    val status: CartStatus = CartStatus.ACTIVE,
    val itemCount: Int = 0,
    val totalAmount: Double = 0.0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

enum class CartStatus {
    ACTIVE,
    CHECKED_OUT,
    ABANDONED
}

