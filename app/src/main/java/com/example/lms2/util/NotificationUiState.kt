package com.example.lms2.util

import com.example.lms2.data.model.NotificationItem

data class NotificationUiState(
    val isLoading: Boolean = false,
    val hasLoadedOnce: Boolean = false,
    val notifications: List<NotificationItem> = emptyList(),
    // Pagination
    val isLoadingMore: Boolean = false,
    val currentCursor: String? = null,
    val hasMoreNotifications: Boolean = true
) {
    val unreadCount: Int
        get() = notifications.count { !it.isRead }
}

