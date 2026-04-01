package com.example.lms2.data.repository

import com.example.lms2.data.cache.RepositoryCache
import com.example.lms2.data.cache.CacheTTL
import com.example.lms2.data.model.NotificationItem
import com.example.lms2.data.model.NotificationType
import com.example.lms2.data.paging.PageRequest
import com.example.lms2.data.paging.PageResult
import com.example.lms2.util.ResultState
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.delay
import kotlinx.coroutines.tasks.await

class NotificationRepository {

    data class NotificationTemplate(
        val title: String,
        val body: String
    )

    private val firestore = FirebaseFirestore.getInstance()
    private val notificationsCollection = firestore.collection("notifications")
    private val enrollmentsCollection = firestore.collection("enrollments")
    private val notificationCachePrefix = "notifications:user"

    suspend fun getNotifications(userId: String): ResultState<List<NotificationItem>> {
        if (userId.isBlank()) return ResultState.Error("Thiếu thông tin người dùng")

        return try {
            val notifications = mutableListOf<NotificationItem>()
            var cursor: String? = null
            var hasMore = true

            while (hasMore) {
                when (
                    val pageResult = getNotificationsPage(
                        userId = userId,
                        pageRequest = PageRequest(pageSize = 50, cursor = cursor, useCache = true)
                    )
                ) {
                    is ResultState.Success -> {
                        notifications += pageResult.data.items
                        cursor = pageResult.data.nextCursor
                        hasMore = pageResult.data.hasMore
                    }

                    is ResultState.Error -> return ResultState.Error(pageResult.message)
                    ResultState.Loading -> return ResultState.Loading
                }
            }

            ResultState.Success(notifications)
        } catch (e: Exception) {
            ResultState.Error(e.message ?: "Tải thông báo thất bại")
        }
    }

    suspend fun getNotificationsPage(
        userId: String,
        pageRequest: PageRequest = PageRequest()
    ): ResultState<PageResult<NotificationItem>> {
        if (userId.isBlank()) return ResultState.Error("Thiếu thông tin người dùng")

        return try {
            val cacheKey = buildNotificationCacheKey(userId, pageRequest)
            if (pageRequest.useCache && !pageRequest.refresh) {
                RepositoryCache.get<PageResult<NotificationItem>>(cacheKey)?.let {
                    return ResultState.Success(it.copy(fromCache = true))
                }
            }

            var query = notificationsCollection
                .whereEqualTo("userId", userId)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit((pageRequest.normalizedPageSize + 1).toLong())

            val cursorId = pageRequest.cursor
            if (!cursorId.isNullOrBlank()) {
                val cursorSnapshot = notificationsCollection.document(cursorId).get().await()
                if (cursorSnapshot.exists()) {
                    query = query.startAfter(cursorSnapshot)
                }
            }

            val snapshot = query.get().await()
            val now = System.currentTimeMillis()
            val rawItems = snapshot.documents.map { document ->
                val typeText = document.getString("type").orEmpty()
                val type = NotificationType.entries.firstOrNull { it.name.equals(typeText, ignoreCase = true) }
                    ?: NotificationType.SYSTEM

                NotificationItem(
                    id = document.id,
                    userId = document.getString("userId") ?: userId,
                    title = document.getString("title").orEmpty(),
                    body = document.getString("body").orEmpty(),
                    type = type,
                    isRead = document.getBoolean("isRead") ?: false,
                    createdAt = document.getLong("createdAt") ?: now
                )
            }

            val hasMore = rawItems.size > pageRequest.normalizedPageSize
            val pageItems = if (hasMore) rawItems.take(pageRequest.normalizedPageSize) else rawItems
            val nextCursor = if (hasMore) pageItems.lastOrNull()?.id else null
            val pageResult = PageResult(
                items = pageItems,
                nextCursor = nextCursor,
                hasMore = hasMore,
                fromCache = false
            )

            RepositoryCache.put(cacheKey, pageResult, CacheTTL.SHORT)
            ResultState.Success(pageResult)
        } catch (e: Exception) {
            ResultState.Error(e.message ?: "Tải thông báo thất bại")
        }
    }

    suspend fun addNotification(notification: NotificationItem): ResultState<Unit> {
        if (notification.userId.isBlank()) return ResultState.Error("Thiếu thông tin người dùng")
        if (notification.title.isBlank()) return ResultState.Error("Thiếu tiêu đề thông báo")

        return try {
            val createdAt = if (notification.createdAt > 0) {
                notification.createdAt
            } else {
                System.currentTimeMillis()
            }

            val docRef = if (notification.id.isBlank()) {
                notificationsCollection.document()
            } else {
                notificationsCollection.document(notification.id)
            }

            val payload = mapOf(
                "userId" to notification.userId,
                "title" to notification.title,
                "body" to notification.body,
                "type" to notification.type.name,
                "isRead" to notification.isRead,
                "createdAt" to createdAt
            )

            docRef.set(payload).await()
            invalidateUserNotificationCache(notification.userId)
            ResultState.Success(Unit)
        } catch (e: Exception) {
            ResultState.Error(e.message ?: "Tạo thông báo thất bại")
        }
    }

    suspend fun markAsRead(notificationId: String): ResultState<Unit> {
        if (notificationId.isBlank()) return ResultState.Error("Thiếu mã thông báo")

        return try {
            notificationsCollection
                .document(notificationId)
                .update("isRead", true)
                .await()

            RepositoryCache.invalidateByPrefix(notificationCachePrefix)

            ResultState.Success(Unit)
        } catch (e: Exception) {
            ResultState.Error(e.message ?: "Cập nhật trạng thái thông báo thất bại")
        }
    }

    suspend fun markAllAsRead(userId: String): ResultState<Unit> {
        if (userId.isBlank()) return ResultState.Error("Thiếu thông tin người dùng")

        return try {
            val snapshot = notificationsCollection
                .whereEqualTo("userId", userId)
                .whereEqualTo("isRead", false)
                .get()
                .await()

            if (snapshot.isEmpty) return ResultState.Success(Unit)

            val batch = firestore.batch()
            snapshot.documents.forEach { document ->
                batch.update(document.reference, "isRead", true)
            }
            batch.commit().await()

            invalidateUserNotificationCache(userId)

            ResultState.Success(Unit)
        } catch (e: Exception) {
            ResultState.Error(e.message ?: "Đánh dấu đã đọc tất cả thất bại")
        }
    }

    suspend fun addNotificationToCourseEnrollments(
        courseId: String,
        title: String,
        body: String,
        type: NotificationType
    ): ResultState<Unit> {
        if (courseId.isBlank()) return ResultState.Error("Thiếu thông tin khóa học")
        if (title.isBlank()) return ResultState.Error("Thiếu tiêu đề thông báo")

        return try {
            val enrollmentsSnapshot = enrollmentsCollection
                .whereEqualTo("courseId", courseId)
                .get()
                .await()

            val userIds = enrollmentsSnapshot.documents
                .mapNotNull { it.getString("userId") }
                .filter { it.isNotBlank() }
                .distinct()

            if (userIds.isEmpty()) return ResultState.Success(Unit)

            val now = System.currentTimeMillis()
            userIds.chunked(450).forEach { chunk ->
                val batch = firestore.batch()
                chunk.forEach { userId ->
                    val docRef = notificationsCollection.document()
                    batch.set(
                        docRef,
                        mapOf(
                            "userId" to userId,
                            "title" to title,
                            "body" to body,
                            "type" to type.name,
                            "isRead" to false,
                            "createdAt" to now
                        )
                    )
                }
                batch.commit().await()
            }

            userIds.forEach { userId ->
                invalidateUserNotificationCache(userId)
            }

            ResultState.Success(Unit)
        } catch (e: Exception) {
            ResultState.Error(e.message ?: "Gửi thông báo cho học viên thất bại")
        }
    }

    private fun invalidateUserNotificationCache(userId: String) {
        if (userId.isBlank()) return
        RepositoryCache.invalidateByPrefix("$notificationCachePrefix:$userId")
    }

    private fun buildNotificationCacheKey(userId: String, pageRequest: PageRequest): String {
        val cursorPart = pageRequest.cursor ?: "first"
        return "$notificationCachePrefix:$userId:${pageRequest.normalizedPageSize}:$cursorPart"
    }

    fun purchaseSuccessTemplate(itemCount: Int, courseTitle: String? = null): NotificationTemplate {
        val normalizedCount = itemCount.coerceAtLeast(1)
        val body = if (normalizedCount == 1 && !courseTitle.isNullOrBlank()) {
            "Bạn đã thanh toán thành công khóa học $courseTitle."
        } else {
            "Bạn đã thanh toán thành công $normalizedCount khóa học."
        }
        return NotificationTemplate(
            title = "Thanh toán thành công",
            body = body
        )
    }

    fun courseUpdatedTemplate(courseTitle: String): NotificationTemplate {
        return NotificationTemplate(
            title = "Khóa học được cập nhật",
            body = "Giảng viên vừa cập nhật nội dung khóa $courseTitle."
        )
    }

    fun quizCreatedTemplate(quizTitle: String, courseTitle: String? = null): NotificationTemplate {
        val body = if (!courseTitle.isNullOrBlank()) {
            "Quiz $quizTitle đã sẵn sàng trong khóa $courseTitle."
        } else {
            "Quiz $quizTitle đã sẵn sàng, vào làm ngay để ôn tập."
        }
        return NotificationTemplate(
            title = "Quiz mới đã mở",
            body = body
        )
    }

    fun studyReminderTemplate(courseTitle: String? = null): NotificationTemplate {
        val normalizedCourseTitle = courseTitle?.trim().orEmpty()
        val body = if (normalizedCourseTitle.isNotBlank()) {
            "Bạn đã đăng ký khóa $normalizedCourseTitle. Dành 15-20 phút hôm nay để bắt đầu bài học đầu tiên nhé."
        } else {
            "Dành 15-20 phút hôm nay để tiếp tục bài học dang dở và giữ nhịp học tập nhé."
        }
        return NotificationTemplate(
            title = "Nhắc nhở học bài",
            body = body
        )
    }

    suspend fun addStudyReminderIfNeeded(
        userId: String,
        courseTitle: String? = null,
        cooldownHours: Int = 24
    ): ResultState<Unit> {
        if (userId.isBlank()) return ResultState.Error("Thiếu thông tin người dùng")

        return try {
            val now = System.currentTimeMillis()
            val cooldownMs = cooldownHours.coerceAtLeast(1) * 60L * 60L * 1000L
            val recentReminderSnapshot = notificationsCollection
                .whereEqualTo("userId", userId)
                .whereEqualTo("type", NotificationType.STUDY_REMINDER.name)
                .whereGreaterThanOrEqualTo("createdAt", now - cooldownMs)
                .limit(1)
                .get()
                .await()

            if (!recentReminderSnapshot.isEmpty) {
                return ResultState.Success(Unit)
            }

            val template = studyReminderTemplate(courseTitle)
            addNotification(
                NotificationItem(
                    userId = userId,
                    title = template.title,
                    body = template.body,
                    type = NotificationType.STUDY_REMINDER,
                    isRead = false,
                    createdAt = now
                )
            )
        } catch (e: Exception) {
            ResultState.Error(e.message ?: "Tạo thông báo nhắc học thất bại")
        }
    }


    // Dữ liệu mẫu để demo UI khi cần.
    suspend fun getNotificationsMock(userId: String): ResultState<List<NotificationItem>> {
        if (userId.isBlank()) return ResultState.Error("Thiếu thông tin người dùng")

        return try {
            delay(180)
            val now = System.currentTimeMillis()

            val notifications = listOf(
                NotificationItem(
                    id = "noti_purchase_1",
                    userId = userId,
                    title = purchaseSuccessTemplate(itemCount = 1, courseTitle = "Jetpack Compose Cơ Bản").title,
                    body = purchaseSuccessTemplate(itemCount = 1, courseTitle = "Jetpack Compose Cơ Bản").body,
                    type = NotificationType.PURCHASE_SUCCESS,
                    isRead = false,
                    createdAt = now - 5 * 60 * 1000L
                ),
                NotificationItem(
                    id = "noti_reminder_1",
                    userId = userId,
                    title = "Nhắc học hôm nay",
                    body = "Bạn còn 2 bài học chưa hoàn thành ở khóa Android nâng cao.",
                    type = NotificationType.STUDY_REMINDER,
                    isRead = false,
                    createdAt = now - 2 * 60 * 60 * 1000L
                ),
                NotificationItem(
                    id = "noti_course_updated_1",
                    userId = userId,
                    title = courseUpdatedTemplate("Firebase cho Android").title,
                    body = courseUpdatedTemplate("Firebase cho Android").body,
                    type = NotificationType.COURSE_UPDATED,
                    isRead = true,
                    createdAt = now - 26 * 60 * 60 * 1000L
                ),
                NotificationItem(
                    id = "noti_lesson_1",
                    userId = userId,
                    title = "Bài học mới",
                    body = "Có bài học mới trong khóa UI/UX Mobile Design.",
                    type = NotificationType.NEW_LESSON,
                    isRead = true,
                    createdAt = now - 3 * 24 * 60 * 60 * 1000L
                ),
                NotificationItem(
                    id = "noti_quiz_1",
                    userId = userId,
                    title = quizCreatedTemplate("Chương 3").title,
                    body = quizCreatedTemplate("Chương 3").body,
                    type = NotificationType.QUIZ_AVAILABLE,
                    isRead = true,
                    createdAt = now - 4 * 24 * 60 * 60 * 1000L
                )
            ).sortedByDescending { it.createdAt }

            ResultState.Success(notifications)
        } catch (e: Exception) {
            ResultState.Error(e.message ?: "Tải thông báo thất bại")
        }
    }
}

