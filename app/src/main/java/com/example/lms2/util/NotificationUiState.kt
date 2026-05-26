package com.example.lms2.util

import com.example.lms2.data.model.NotificationItem

/**
 * Mô tả trạng thái giao diện của luồng Notification.
 * Đối tượng này gom dữ liệu hiển thị, cờ tải, lỗi kiểm tra và các trạng thái tạm mà Compose cần quan sát.
 * Việc gom toàn bộ state vào một nơi giúp màn hình render nhất quán và dễ kiểm thử hơn.
 */

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

