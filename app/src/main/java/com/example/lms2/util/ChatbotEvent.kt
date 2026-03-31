package com.example.lms2.util

sealed class ChatbotEvent {
    data class ShowError(val message: String) : ChatbotEvent()
}

