package com.example.lms2.data.model

data class User(
    val uid: String = "",
    val fullName: String = "",
    val email: String = "",
    val role: UserRole = UserRole.STUDENT,
    val isActive: Boolean = true,
    val instructorRequestStatus: InstructorApplicationStatus = InstructorApplicationStatus.NONE,
    val instructorRequestSubmittedAt: Long? = null,
    val instructorRequestReviewedAt: Long? = null,
    val instructorRequestReviewedBy: String? = null,
    val instructorRequestRejectReason: String? = null,
    val instructorApplication: InstructorApplication? = null,
    val avatarUrl: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)
