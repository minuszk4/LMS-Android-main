package com.example.lms.data.model

enum class ChatSessionStatus {
    ACTIVE,
    ARCHIVED,
    CLOSED
}

data class ChatSession(
    val id: String = "",
    val userId: String = "",
    val title: String = "",
    val status: ChatSessionStatus = ChatSessionStatus.ACTIVE,
    val lastMessageAt: Long = 0L,
    val createdAt: Long = System.currentTimeMillis()
)

