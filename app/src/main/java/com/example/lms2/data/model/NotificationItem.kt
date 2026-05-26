package com.example.lms2.data.model

/**
 * Định nghĩa mô hình dữ liệu NotificationItem dùng trong ứng dụng LMS Android.
 * File này khai báo contract dữ liệu được trao đổi giữa Firestore, repository, ViewModel và giao diện Compose.
 * Việc tách riêng model giúp các luồng nghiệp vụ dùng chung một cấu trúc dữ liệu nhất quán và dễ bảo trì.
 */

enum class NotificationType {
    PURCHASE_SUCCESS,
    STUDY_REMINDER,
    COURSE_UPDATED,
    NEW_LESSON,
    QUIZ_AVAILABLE,
    SYSTEM
}

/**
 * Khai báo NotificationItem trong file này để phục vụ một trách nhiệm cụ thể của hệ thống.
 */

data class NotificationItem(
    val id: String = "",
    val userId: String = "",
    val title: String = "",
    val body: String = "",
    val type: NotificationType = NotificationType.SYSTEM,
    val isRead: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

