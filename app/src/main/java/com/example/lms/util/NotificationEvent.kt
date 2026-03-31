package com.example.lms.util

sealed class NotificationEvent {
    data class ShowError(val message: String) : NotificationEvent()
}

