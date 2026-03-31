package com.example.lms.data.model

enum class NotificationType {
    PURCHASE_SUCCESS,
    STUDY_REMINDER,
    COURSE_UPDATED,
    NEW_LESSON,
    QUIZ_AVAILABLE,
    SYSTEM
}

data class NotificationItem(
    val id: String = "",
    val userId: String = "",
    val title: String = "",
    val body: String = "",
    val type: NotificationType = NotificationType.SYSTEM,
    val isRead: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

