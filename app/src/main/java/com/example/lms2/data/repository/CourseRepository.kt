package com.example.lms2.data.repository

import com.example.lms2.data.cache.RepositoryCache
import com.example.lms2.data.cache.CacheTTL
import com.example.lms2.data.model.Course
import com.example.lms2.data.model.NotificationType
import com.example.lms2.data.paging.PageRequest
import com.example.lms2.data.paging.PageResult
import com.example.lms2.util.ResultState
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await

class CourseRepository {

    companion object {
        private val notificationThrottleLock = Any()
        private val lastCourseUpdateNotifyAtByCourseId = mutableMapOf<String, Long>()
        private const val PUBLISHED_COURSE_CACHE_PREFIX = "courses:published"
        private const val ADMIN_COURSE_CACHE_PREFIX = "courses:admin"
    }

    private val firestore = FirebaseFirestore.getInstance()
    private val coursesCollection = firestore.collection("courses")
    private val lessonsCollection = firestore.collection("lessons")
    private val quizzesCollection = firestore.collection("quizzes")
    private val enrollmentsCollection = firestore.collection("enrollments")
    private val reviewsCollection = firestore.collection("reviews")
    private val progressCollection = firestore.collection("progress")
    private val lessonProgressCollection = firestore.collection("lessonProgress")
    private val quizProgressCollection = firestore.collection("quizProgress")
    private val notificationRepository = NotificationRepository()
    private val courseUpdateNotificationCooldownMs = 30 * 60 * 1000L

    suspend fun createCourse(course: Course): ResultState<String> {
        return try {
            val docRef = coursesCollection.document()
            val newCourse = course.copy(
                id = docRef.id,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
            docRef.set(newCourse).await()
            RepositoryCache.invalidateByPrefix(PUBLISHED_COURSE_CACHE_PREFIX)
            RepositoryCache.invalidateByPrefix(ADMIN_COURSE_CACHE_PREFIX)
            ResultState.Success(docRef.id)
        } catch (e: Exception) {
            ResultState.Error(e.message ?: "Tạo khóa học thất bại")
        }
    }

    suspend fun updateCourse(course: Course): ResultState<Unit> {
        return try {
            RepositoryCache.invalidateByPrefix(ADMIN_COURSE_CACHE_PREFIX)
            val now = System.currentTimeMillis()
            coursesCollection
                .document(course.id)
                .update(
                    mapOf(
                        "title" to course.title,
                        "description" to course.description,
                        "thumbnailUrl" to course.thumbnailUrl,
                        "price" to course.price,
                        "categoryId" to course.categoryId,
                        "level" to course.level,
                        "duration" to course.duration,
                        "isPublished" to course.isPublished,
                        "updatedAt" to now
                    )
                )
                .await()

            RepositoryCache.invalidateByPrefix(PUBLISHED_COURSE_CACHE_PREFIX)

            val shouldNotify = shouldNotifyCourseUpdate(course.id, now)

            if (shouldNotify) {
                runCatching {
                    val template = notificationRepository.courseUpdatedTemplate(course.title)
                    notificationRepository.addNotificationToCourseEnrollments(
                        courseId = course.id,
                        title = template.title,
                        body = template.body,
                        type = NotificationType.COURSE_UPDATED
                    )
                }
            }

            ResultState.Success(Unit)
        } catch (e: Exception) {
            ResultState.Error(e.message ?: "Cập nhật khóa học thất bại")
        }
    }

    private fun shouldNotifyCourseUpdate(courseId: String, now: Long): Boolean {
        if (courseId.isBlank()) return false

        synchronized(notificationThrottleLock) {
            val lastNotifiedAt = lastCourseUpdateNotifyAtByCourseId[courseId] ?: 0L
            val canNotify = now - lastNotifiedAt >= courseUpdateNotificationCooldownMs
            if (canNotify) {
                lastCourseUpdateNotifyAtByCourseId[courseId] = now
            }
            return canNotify
        }
    }

    suspend fun deleteCourse(courseId: String): ResultState<Unit> {
        return try {
            val refsToDelete = mutableListOf<DocumentReference>()

            refsToDelete += coursesCollection.document(courseId)

            val lessonsSnapshot = lessonsCollection.whereEqualTo("courseId", courseId).get().await()
            refsToDelete += lessonsSnapshot.documents.map { it.reference }

            val quizzesSnapshot = quizzesCollection.whereEqualTo("courseId", courseId).get().await()
            refsToDelete += quizzesSnapshot.documents.map { it.reference }

            val enrollmentsSnapshot = enrollmentsCollection.whereEqualTo("courseId", courseId).get().await()
            refsToDelete += enrollmentsSnapshot.documents.map { it.reference }

            val reviewsSnapshot = reviewsCollection.whereEqualTo("courseId", courseId).get().await()
            refsToDelete += reviewsSnapshot.documents.map { it.reference }

            val progressSnapshot = progressCollection.whereEqualTo("courseId", courseId).get().await()
            refsToDelete += progressSnapshot.documents.map { it.reference }

            val lessonProgressSnapshot = lessonProgressCollection.whereEqualTo("courseId", courseId).get().await()
            refsToDelete += lessonProgressSnapshot.documents.map { it.reference }

            val quizProgressSnapshot = quizProgressCollection.whereEqualTo("courseId", courseId).get().await()
            refsToDelete += quizProgressSnapshot.documents.map { it.reference }

            refsToDelete
                .distinctBy { it.path }
                .chunked(450)
                .forEach { chunk ->
                    val batch = firestore.batch()
                    chunk.forEach { ref -> batch.delete(ref) }
                    batch.commit().await()
                }

            RepositoryCache.invalidateByPrefix(PUBLISHED_COURSE_CACHE_PREFIX)
            RepositoryCache.invalidateByPrefix(ADMIN_COURSE_CACHE_PREFIX)
            ResultState.Success(Unit)
        } catch (e: Exception) {
            ResultState.Error(e.message ?: "Xóa khóa học thất bại")
        }
    }

    suspend fun getCourseById(courseId: String): ResultState<Course> {
        return try {
            val snapshot = coursesCollection
                .document(courseId)
                .get()
                .await()
            val course = snapshot.toObject(Course::class.java)
            if (course != null) {
                ResultState.Success(course)
            } else {
                ResultState.Error("Không tìm thấy khóa học")
            }
        } catch (e: Exception) {
            ResultState.Error(e.message ?: "Lấy thông tin khóa học thất bại")
        }
    }

    suspend fun searchCourses(query: String): ResultState<List<Course>> {
        return try {
            if (query.isBlank()) {
                return ResultState.Success(emptyList())
            }

            val snapshot = coursesCollection
                .whereEqualTo("isPublished", true)
                .get()
                .await()

            val allCourses = snapshot.toObjects(Course::class.java)
            val lowerQuery = query.lowercase()

            // Filter courses by query in title, description, or instructor name
            val filteredCourses = allCourses.filter { course ->
                course.title.lowercase().contains(lowerQuery) ||
                course.description.lowercase().contains(lowerQuery) ||
                course.instructorName.lowercase().contains(lowerQuery)
            }

            ResultState.Success(filteredCourses)
        } catch (e: Exception) {
            ResultState.Error(e.message ?: "Tìm kiếm khóa học thất bại")
        }
    }

    suspend fun getCoursesByInstructor(instructorId: String): ResultState<List<Course>> {
        return try {
            val snapshot = coursesCollection
                .whereEqualTo("instructorId", instructorId)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .get()
                .await()
            val courses = snapshot.toObjects(Course::class.java)
            ResultState.Success(courses)
        } catch (e: Exception) {
            ResultState.Error(e.message ?: "Lấy danh sách khóa học của giảng viên thất bại")
        }
    }

    suspend fun getAllCoursesForAdmin(): ResultState<List<Course>> {
        return try {
            val courses = mutableListOf<Course>()
            var cursor: String? = null
            var hasMore = true

            while (hasMore) {
                when (
                    val pageResult = getAllCoursesForAdminPage(
                        PageRequest(pageSize = 100, cursor = cursor, useCache = true)
                    )
                ) {
                    is ResultState.Success -> {
                        courses += pageResult.data.items
                        cursor = pageResult.data.nextCursor
                        hasMore = pageResult.data.hasMore
                    }

                    is ResultState.Error -> return ResultState.Error(pageResult.message)
                    ResultState.Loading -> return ResultState.Loading
                }
            }

            ResultState.Success(courses)
        } catch (e: Exception) {
            ResultState.Error(e.message ?: "Không tải được danh sách khóa học")
        }
    }

    suspend fun getAllCoursesForAdminPage(
        pageRequest: PageRequest = PageRequest()
    ): ResultState<PageResult<Course>> {
        return try {
            val cacheKey = buildAdminCourseCacheKey(pageRequest)
            if (pageRequest.useCache && !pageRequest.refresh) {
                RepositoryCache.get<PageResult<Course>>(cacheKey)?.let {
                    return ResultState.Success(it.copy(fromCache = true))
                }
            }

            var query = coursesCollection
                .orderBy("updatedAt", Query.Direction.DESCENDING)
                .limit((pageRequest.normalizedPageSize + 1).toLong())

            val cursorId = pageRequest.cursor
            if (!cursorId.isNullOrBlank()) {
                val cursorSnapshot = coursesCollection.document(cursorId).get().await()
                if (cursorSnapshot.exists()) {
                    query = query.startAfter(cursorSnapshot)
                }
            }

            val snapshot = query.get().await()
            val rawItems = snapshot.documents.mapNotNull { doc ->
                doc.toObject(Course::class.java)?.let { course ->
                    if (course.id.isBlank()) course.copy(id = doc.id) else course
                }
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
            ResultState.Error(e.message ?: "Không tải được danh sách khóa học")
        }
    }

    suspend fun getAllPublishedCourses(): ResultState<List<Course>> {
        return try {
            val courses = mutableListOf<Course>()
            var cursor: String? = null
            var hasMore = true

            while (hasMore) {
                when (
                    val pageResult = getAllPublishedCoursesPage(
                        PageRequest(pageSize = 100, cursor = cursor, useCache = true)
                    )
                ) {
                    is ResultState.Success -> {
                        courses += pageResult.data.items
                        cursor = pageResult.data.nextCursor
                        hasMore = pageResult.data.hasMore
                    }

                    is ResultState.Error -> return ResultState.Error(pageResult.message)
                    ResultState.Loading -> return ResultState.Loading
                }
            }

            ResultState.Success(courses)
        } catch (e: Exception) {
            ResultState.Error(e.message ?: "Lấy danh sách khóa học thất bại")
        }
    }

    suspend fun getAllPublishedCoursesPage(
        pageRequest: PageRequest = PageRequest()
    ): ResultState<PageResult<Course>> {
        return try {
            val cacheKey = buildPublishedCourseCacheKey(pageRequest)
            if (pageRequest.useCache && !pageRequest.refresh) {
                RepositoryCache.get<PageResult<Course>>(cacheKey)?.let {
                    return ResultState.Success(it.copy(fromCache = true))
                }
            }

            var query = coursesCollection
                .whereEqualTo("isPublished", true)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit((pageRequest.normalizedPageSize + 1).toLong())

            val cursorId = pageRequest.cursor
            if (!cursorId.isNullOrBlank()) {
                val cursorSnapshot = coursesCollection.document(cursorId).get().await()
                if (cursorSnapshot.exists()) {
                    query = query.startAfter(cursorSnapshot)
                }
            }

            val snapshot = query.get().await()
            val rawItems = snapshot.documents.mapNotNull { doc ->
                doc.toObject(Course::class.java)?.let { course ->
                    if (course.id.isBlank()) {
                        course.copy(id = doc.id)
                    } else {
                        course
                    }
                }
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
            ResultState.Error(e.message ?: "Lấy danh sách khóa học thất bại")
        }
    }

    private fun buildPublishedCourseCacheKey(pageRequest: PageRequest): String {
        return "$PUBLISHED_COURSE_CACHE_PREFIX:${pageRequest.normalizedPageSize}:${pageRequest.cursor ?: "first"}"
    }

    suspend fun updatePublishStatus(courseId: String, isPublished: Boolean): ResultState<Unit> {
        return try {
            coursesCollection
                .document(courseId)
                .update(
                    mapOf(
                        "isPublished" to isPublished,
                        "updatedAt" to System.currentTimeMillis()
                    )
                )
                .await()

            RepositoryCache.invalidateByPrefix(PUBLISHED_COURSE_CACHE_PREFIX)
            RepositoryCache.invalidateByPrefix(ADMIN_COURSE_CACHE_PREFIX)
            ResultState.Success(Unit)
        } catch (e: Exception) {
            ResultState.Error(e.message ?: "Cập nhật trạng thái khóa học thất bại")
        }
    }

    suspend fun incrementEnrollment(courseId: String): ResultState<Unit> {
        return try {
            firestore.runTransaction { transaction ->
                val docRef = coursesCollection.document(courseId)
                val snapshot = transaction.get(docRef)
                val current = snapshot.getLong("enrollmentCount") ?: 0
                transaction.update(
                    docRef,
                    "enrollmentCount",
                    current + 1
                )
            }.await()
            ResultState.Success(Unit)
        } catch (e: Exception) {
            ResultState.Error(e.message ?: "Cập nhật số lượng học viên thất bại")
        }
    }

    private fun buildAdminCourseCacheKey(pageRequest: PageRequest): String {
        return "$ADMIN_COURSE_CACHE_PREFIX:${pageRequest.normalizedPageSize}:${pageRequest.cursor ?: "first"}"
    }
}
