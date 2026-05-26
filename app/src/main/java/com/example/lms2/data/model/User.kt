package com.example.lms2.data.model

/**
 * Định nghĩa mô hình dữ liệu User dùng trong ứng dụng LMS Android.
 * File này khai báo contract dữ liệu được trao đổi giữa Firestore, repository, ViewModel và giao diện Compose.
 * Việc tách riêng model giúp các luồng nghiệp vụ dùng chung một cấu trúc dữ liệu nhất quán và dễ bảo trì.
 */

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
