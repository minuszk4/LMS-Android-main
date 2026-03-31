package com.example.lms.data.model

data class Enrollment(
    val id: String = "",
    val userId: String = "",
    val courseId: String = "",
    val enrolledAt: Long = System.currentTimeMillis(),
)
