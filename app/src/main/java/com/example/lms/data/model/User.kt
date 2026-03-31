package com.example.lms.data.model

data class User(
    val uid: String = "",
    val fullName: String = "",
    val email: String = "",
    val role: UserRole = UserRole.STUDENT,
    val avatarUrl: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)