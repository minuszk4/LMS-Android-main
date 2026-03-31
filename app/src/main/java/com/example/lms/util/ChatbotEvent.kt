package com.example.lms.util

sealed class ChatbotEvent {
    data class ShowError(val message: String) : ChatbotEvent()
}

