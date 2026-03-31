package com.example.lms2.util

import com.example.lms2.data.model.ChatMessage
import com.example.lms2.data.model.ChatSession

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

