package com.example.lms2.ui.screen.student

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.filled.Update
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.lms2.data.model.NotificationItem
import com.example.lms2.data.model.NotificationType
import com.example.lms2.ui.component.TopBar
import com.example.lms2.util.NotificationEvent
import com.example.lms2.viewmodel.NotificationViewModel
import java.util.Calendar
import java.util.concurrent.TimeUnit

private val NotiBg = Color(0xFFF8FAFC)
private val NotiCard = Color.White
private val NotiBorder = Color(0xFFE2E8F0)
private val NotiTextPrimary = Color(0xFF1E293B)
private val NotiTextSecondary = Color(0xFF64748B)
private val NotiPrimary = Color(0xFF4B5CC4)

private enum class NotificationSection(val title: String) {
    TODAY("Hôm nay"),
    YESTERDAY("Hôm qua"),
    RECENT("Gần đây")
}

@Composable
fun NotificationRoute(
    userId: String,
    viewModel: NotificationViewModel
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(userId) {
        viewModel.init(userId)
    }

    DisposableEffect(lifecycleOwner, userId) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refresh(userId)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.event.collect { event ->
            if (event is NotificationEvent.ShowError) {
                snackbarHostState.showSnackbar(event.message)
            }
        }
    }

    NotificationScreen(
        userId = userId,
        isLoading = uiState.isLoading,
        isLoadingMore = uiState.isLoadingMore,
        hasMore = uiState.hasMoreNotifications,
        unreadCount = uiState.unreadCount,
        notifications = uiState.notifications,
        snackbarHostState = snackbarHostState,
        onNotificationClick = { notificationId ->
            viewModel.markAsRead(notificationId)
        },
        onLoadMore = { viewModel.loadMore(userId) },
        onMarkAllAsRead = {
            viewModel.markAllAsRead(userId)
        }
    )
}

@Composable
private fun NotificationScreen(
    userId: String,
    isLoading: Boolean,
    isLoadingMore: Boolean,
    hasMore: Boolean,
    unreadCount: Int,
    notifications: List<NotificationItem>,
    snackbarHostState: SnackbarHostState,
    onNotificationClick: (String) -> Unit,
    onLoadMore: () -> Unit,
    onMarkAllAsRead: () -> Unit
) {
    val listState = rememberLazyListState()
    val shouldLoadMore by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val totalItems = layoutInfo.totalItemsCount
            val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            totalItems > 0 && lastVisible >= totalItems - 3
        }
    }

    LaunchedEffect(shouldLoadMore, isLoading, isLoadingMore, hasMore) {
        if (shouldLoadMore && !isLoading && !isLoadingMore && hasMore) {
            onLoadMore()
        }
    }

    Scaffold(
        topBar = {
            TopBar(
                title = "Thông báo",
                onBackClick = {},
                showBackButton = false
            )
        },
        containerColor = NotiBg,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { paddingValues ->
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = NotiPrimary)
            }
            return@Scaffold
        }

        if (notifications.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Bạn chưa có thông báo nào",
                    color = NotiTextSecondary,
                    fontSize = 14.sp
                )
            }
            return@Scaffold
        }

        val grouped = notifications.groupBy { sectionOf(it.createdAt) }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (unreadCount > 0) "$unreadCount chưa đọc" else "Tất cả đã đọc",
                        color = NotiTextSecondary,
                        fontSize = 13.sp
                    )
                    androidx.compose.foundation.layout.Spacer(modifier = Modifier.weight(1f))
                    TextButton(
                        onClick = onMarkAllAsRead,
                        enabled = unreadCount > 0 && userId.isNotBlank()
                    ) {
                        Text("Đánh dấu tất cả đã đọc", color = NotiPrimary, fontSize = 13.sp)
                    }
                }
            }

            NotificationSection.entries.forEach { section ->
                val itemsInSection = grouped[section].orEmpty()
                if (itemsInSection.isNotEmpty()) {
                    item {
                        Text(
                            text = section.title,
                            color = NotiTextPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                        )
                    }

                    items(itemsInSection, key = { it.id }) { notification ->
                        NotificationCard(
                            notification = notification,
                            onClick = { onNotificationClick(notification.id) }
                        )
                    }
                }
            }

            if (isLoadingMore) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = NotiPrimary,
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.dp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationCard(
    notification: NotificationItem,
    onClick: () -> Unit
) {
    val (icon, iconTint, iconBackground) = iconStyleFor(notification.type)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(NotiCard, RoundedCornerShape(16.dp))
            .border(1.dp, NotiBorder, RoundedCornerShape(16.dp))
            .clickable(enabled = !notification.isRead) {
                onClick()
            }
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(54.dp)
                .background(iconBackground, RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(24.dp)
            )
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                text = notification.title,
                color = NotiTextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = relativeTime(notification.createdAt),
                color = NotiPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = notification.body,
                color = NotiTextSecondary,
                fontSize = 15.sp,
                lineHeight = 21.sp
            )
        }

        if (!notification.isRead) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(NotiPrimary, CircleShape)
            )
        }
    }
}

private fun iconStyleFor(type: NotificationType): Triple<ImageVector, Color, Color> {
    return when (type) {
        NotificationType.PURCHASE_SUCCESS -> Triple(Icons.Default.CheckCircle, Color(0xFF059669), Color(0xFFE8F8F1))
        NotificationType.STUDY_REMINDER -> Triple(Icons.Default.Alarm, Color(0xFFEA580C), Color(0xFFFFF2E6))
        NotificationType.COURSE_UPDATED -> Triple(Icons.Default.Update, Color(0xFF4B5CC4), Color(0xFFEFF3FF))
        NotificationType.NEW_LESSON -> Triple(Icons.AutoMirrored.Filled.MenuBook, Color(0xFF4B5CC4), Color(0xFFEFF3FF))
        NotificationType.QUIZ_AVAILABLE -> Triple(Icons.Default.Quiz, Color(0xFF7C3AED), Color(0xFFF4EEFF))
        NotificationType.SYSTEM -> Triple(Icons.Default.Info, Color(0xFF2563EB), Color(0xFFEFF6FF))
    }
}

private fun sectionOf(createdAt: Long): NotificationSection {
    val now = System.currentTimeMillis()
    val startToday = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    val startYesterday = startToday - TimeUnit.DAYS.toMillis(1)

    return when {
        createdAt >= startToday && createdAt <= now -> NotificationSection.TODAY
        createdAt >= startYesterday && createdAt < startToday -> NotificationSection.YESTERDAY
        else -> NotificationSection.RECENT
    }
}

private fun relativeTime(createdAt: Long): String {
    val diff = (System.currentTimeMillis() - createdAt).coerceAtLeast(0L)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(diff)
    val hours = TimeUnit.MILLISECONDS.toHours(diff)
    val days = TimeUnit.MILLISECONDS.toDays(diff)

    return when {
        minutes < 60 -> "${minutes.coerceAtLeast(1)} phút trước"
        hours < 24 -> "$hours giờ trước"
        else -> "$days ngày trước"
    }
}

