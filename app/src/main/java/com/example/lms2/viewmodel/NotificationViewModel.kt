package com.example.lms2.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.lms2.data.paging.PageRequest
import com.example.lms2.data.repository.NotificationRepository
import com.example.lms2.util.NotificationEvent
import com.example.lms2.util.NotificationUiState
import com.example.lms2.util.ResultState
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel cho màn hình thông báo.
 *
 * Nhiệm vụ chính:
 * - tải trang thông báo đầu tiên hoặc tải thêm;
 * - đánh dấu từng thông báo hay toàn bộ thông báo là đã đọc;
 * - giữ `cursor` hiện tại để hỗ trợ pagination.
 */
class NotificationViewModel(
    private val repository: NotificationRepository = NotificationRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(NotificationUiState())
    val uiState: StateFlow<NotificationUiState> = _uiState.asStateFlow()

    private val _event = MutableSharedFlow<NotificationEvent>()
    val event = _event.asSharedFlow()

    private var lastUserId: String = ""

    /**
     * Khởi tạo màn hình thông báo.
     *
     * Nếu cùng một user đã tải trước đó thì tránh gọi lại không cần thiết.
     */
    fun init(userId: String) {
        if (userId.isBlank()) return
        if (_uiState.value.hasLoadedOnce && lastUserId == userId) return
        loadNotifications(userId = userId, refresh = false)
    }

    /**
     * Buộc tải lại trang đầu của danh sách thông báo.
     */
    fun refresh(userId: String) {
        if (userId.isBlank()) return
        loadNotifications(userId = userId, refresh = true)
    }

    /**
     * Đánh dấu một thông báo cụ thể là đã đọc và cập nhật ngay trên state cục bộ.
     */
    fun markAsRead(notificationId: String) {
        if (notificationId.isBlank()) return

        val target = _uiState.value.notifications.firstOrNull { it.id == notificationId } ?: return
        if (target.isRead) return

        viewModelScope.launch {
            when (val result = repository.markAsRead(notificationId)) {
                is ResultState.Success -> {
                    _uiState.update { state ->
                        state.copy(
                            notifications = state.notifications.map { item ->
                                if (item.id == notificationId) item.copy(isRead = true) else item
                            }
                        )
                    }
                }

                is ResultState.Error -> {
                    _event.emit(NotificationEvent.ShowError(result.message))
                }

                else -> Unit
            }
        }
    }

    /**
     * Đánh dấu toàn bộ danh sách hiện tại là đã đọc.
     */
    fun markAllAsRead(userId: String) {
        if (userId.isBlank()) return
        if (_uiState.value.unreadCount == 0) return

        viewModelScope.launch {
            when (val result = repository.markAllAsRead(userId)) {
                is ResultState.Success -> {
                    _uiState.update { state ->
                        state.copy(
                            notifications = state.notifications.map { it.copy(isRead = true) }
                        )
                    }
                }

                is ResultState.Error -> {
                    _event.emit(NotificationEvent.ShowError(result.message))
                }

                else -> Unit
            }
        }
    }

    private fun loadNotifications(userId: String, refresh: Boolean) {
        viewModelScope.launch {
            lastUserId = userId
            _uiState.update {
                it.copy(isLoading = !it.hasLoadedOnce && !refresh)
            }

            when (
                val result = repository.getNotificationsPage(
                    userId = userId,
                    pageRequest = PageRequest(
                        pageSize = 20,
                        cursor = null,
                        refresh = refresh,
                        useCache = true
                    )
                )
            ) {
                is ResultState.Success -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            hasLoadedOnce = true,
                            notifications = result.data.items,
                            currentCursor = result.data.nextCursor,
                            hasMoreNotifications = result.data.hasMore,
                            isLoadingMore = false
                        )
                    }
                }

                is ResultState.Error -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            hasLoadedOnce = true
                        )
                    }
                    _event.emit(NotificationEvent.ShowError(result.message))
                }

                else -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            hasLoadedOnce = true
                        )
                    }
                }
            }
        }
    }

    /**
     * Tải thêm một trang thông báo kế tiếp theo `currentCursor`.
     */
    fun loadMore(userId: String) {
        if (userId.isBlank()) return
        val currentState = _uiState.value
        if (currentState.isLoadingMore || !currentState.hasMoreNotifications) return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingMore = true) }
            val pageRequest = PageRequest(
                pageSize = 20,
                cursor = currentState.currentCursor
            )
            when (val result = repository.getNotificationsPage(userId, pageRequest)) {
                is ResultState.Success -> {
                    _uiState.update {
                        val mergedNotifications = (it.notifications + result.data.items)
                            .distinctBy { item -> item.id }
                        it.copy(
                            isLoadingMore = false,
                            notifications = mergedNotifications,
                            currentCursor = result.data.nextCursor,
                            hasMoreNotifications = result.data.hasMore
                        )
                    }
                }

                is ResultState.Error -> {
                    _uiState.update { it.copy(isLoadingMore = false) }
                    _event.emit(NotificationEvent.ShowError(result.message))
                }

                else -> {
                    _uiState.update { it.copy(isLoadingMore = false) }
                }
            }
        }
    }
}
