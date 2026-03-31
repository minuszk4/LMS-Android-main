package com.example.lms2.data.model

data class Enrollment(
    val id: String = "",
    val userId: String = "",
    val courseId: String = "",
    val enrolledAt: Long = System.currentTimeMillis(),
)
