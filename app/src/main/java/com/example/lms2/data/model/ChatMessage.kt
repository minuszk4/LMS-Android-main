package com.example.lms2.data.model

/**
 * Định nghĩa mô hình dữ liệu ChatMessage dùng trong ứng dụng LMS Android.
 * File này khai báo contract dữ liệu được trao đổi giữa Firestore, repository, ViewModel và giao diện Compose.
 * Việc tách riêng model giúp các luồng nghiệp vụ dùng chung một cấu trúc dữ liệu nhất quán và dễ bảo trì.
 */

enum class ChatSender {
    USER,
    BOT,
    SYSTEM
}

/**
 * Khai báo ChatMessageType trong file này để phục vụ một trách nhiệm cụ thể của hệ thống.
 */

enum class ChatMessageType {
    TEXT,
    COURSE_CARD,
    COURSE_LIST,
    PROGRESS_CHART,
    FUNCTION_CALL
}

/**
 * Khai báo ChatMessage trong file này để phục vụ một trách nhiệm cụ thể của hệ thống.
 */

data class ChatMessage(
    val id: String = "",
    val sessionId: String = "",
    val sender: ChatSender = ChatSender.USER,
    val content: String = "",
    val messageType: ChatMessageType = ChatMessageType.TEXT,
    val metadata: Map<String, Any> = emptyMap(),
    val createdAt: Long = System.currentTimeMillis()
)

