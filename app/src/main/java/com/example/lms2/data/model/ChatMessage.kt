package com.example.lms2.data.model

enum class ChatSender {
    USER,
    BOT,
    SYSTEM
}

enum class ChatMessageType {
    TEXT,
    COURSE_CARD,
    COURSE_LIST,
    PROGRESS_CHART,
    FUNCTION_CALL
}

data class ChatMessage(
    val id: String = "",
    val sessionId: String = "",
    val sender: ChatSender = ChatSender.USER,
    val content: String = "",
    val messageType: ChatMessageType = ChatMessageType.TEXT,
    val metadata: Map<String, Any> = emptyMap(),
    val createdAt: Long = System.currentTimeMillis()
)

