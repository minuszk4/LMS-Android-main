package com.example.lms2.util

import com.example.lms2.data.model.ChatMessage
import com.example.lms2.data.model.ChatSession

/**
 * Mô tả trạng thái giao diện của luồng Chatbot.
 * Đối tượng này gom dữ liệu hiển thị, cờ tải, lỗi kiểm tra và các trạng thái tạm mà Compose cần quan sát.
 * Việc gom toàn bộ state vào một nơi giúp màn hình render nhất quán và dễ kiểm thử hơn.
 */

data class ChatbotUiState(
    val isLoading: Boolean = false,
    val isSending: Boolean = false,
    val hasLoadedOnce: Boolean = false,
    val sessionId: String = "",
    val sessionTitle: String = "",
    val sessions: List<ChatSession> = emptyList(),
    val messages: List<ChatMessage> = emptyList(),
    val canCreateNewSession: Boolean = true
)

