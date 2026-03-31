package com.example.lms2.util

sealed class NotificationEvent {
    data class ShowError(val message: String) : NotificationEvent()
}

