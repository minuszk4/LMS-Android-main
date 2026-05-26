package com.example.lms2.data.model

/**
 * Định nghĩa mô hình dữ liệu ChatSession dùng trong ứng dụng LMS Android.
 * File này khai báo contract dữ liệu được trao đổi giữa Firestore, repository, ViewModel và giao diện Compose.
 * Việc tách riêng model giúp các luồng nghiệp vụ dùng chung một cấu trúc dữ liệu nhất quán và dễ bảo trì.
 */

enum class ChatSessionStatus {
    ACTIVE,
    ARCHIVED,
    CLOSED
}

/**
 * Khai báo ChatSession trong file này để phục vụ một trách nhiệm cụ thể của hệ thống.
 */

data class ChatSession(
    val id: String = "",
    val userId: String = "",
    val title: String = "",
    val status: ChatSessionStatus = ChatSessionStatus.ACTIVE,
    val lastMessageAt: Long = 0L,
    val createdAt: Long = System.currentTimeMillis()
)

