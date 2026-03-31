package com.example.lms2.data.repository

import com.example.lms2.data.cache.RepositoryCache
import com.example.lms2.data.cache.CacheTTL
import com.example.lms2.data.model.Enrollment
import com.example.lms2.data.paging.PageRequest
import com.example.lms2.data.paging.PageResult
import com.example.lms2.util.ResultState
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await

class EnrollmentRepository {

    private val firestore = FirebaseFirestore.getInstance()
    private val enrollmentsCollection = firestore.collection("enrollments")
    private val enrollmentCachePrefix = "enrollments:user"

    suspend fun enrollCourse(userId: String, courseId: String): ResultState<Unit> {
        return try {
            val id = "${userId}_${courseId}"
            val enrollment = Enrollment(
                id = id,
                userId = userId,
                courseId = courseId,
                enrolledAt = System.currentTimeMillis()
            )
            enrollmentsCollection.document(id).set(enrollment).await()
            invalidateEnrollmentCache(userId)
            ResultState.Success(Unit)
        } catch (e: Exception) {
            ResultState.Error(e.message ?: "Đăng ký khóa học thất bại")
        }
    }

    suspend fun isEnrolled(userId: String, courseId: String): ResultState<Boolean> {
        return try {
            val id = "${userId}_${courseId}"
            val snapshot = enrollmentsCollection.document(id).get().await()
            ResultState.Success(snapshot.exists())
        } catch (e: Exception) {
            ResultState.Error(e.message ?: "Kiểm tra đăng ký thất bại")
        }
    }

    suspend fun getEnrolledCourseIds(userId: String): ResultState<List<String>> {
        return try {
            when (val enrollments = getUserEnrollments(userId)) {
                is ResultState.Success -> ResultState.Success(enrollments.data.map { it.courseId })
                is ResultState.Error -> ResultState.Error(enrollments.message)
                ResultState.Loading -> ResultState.Loading
            }
        } catch (e: Exception) {
            ResultState.Error(e.message ?: "Lấy danh sách khóa học đã đăng ký thất bại")
        }
    }

    suspend fun getUserEnrollments(userId: String): ResultState<List<Enrollment>> {
        return try {
            val enrollments = mutableListOf<Enrollment>()
            var cursor: String? = null
            var hasMore = true

            while (hasMore) {
                when (
                    val pageResult = getUserEnrollmentsPage(
                        userId = userId,
                        pageRequest = PageRequest(pageSize = 100, cursor = cursor, useCache = true)
                    )
                ) {
                    is ResultState.Success -> {
                        enrollments += pageResult.data.items
                        cursor = pageResult.data.nextCursor
                        hasMore = pageResult.data.hasMore
                    }

                    is ResultState.Error -> return ResultState.Error(pageResult.message)
                    ResultState.Loading -> return ResultState.Loading
                }
            }

            ResultState.Success(enrollments)
        } catch (e: Exception) {
            ResultState.Error(e.message ?: "Lấy danh sách đăng ký thất bại")
        }
    }

    suspend fun getUserEnrollmentsPage(
        userId: String,
        pageRequest: PageRequest = PageRequest()
    ): ResultState<PageResult<Enrollment>> {
        if (userId.isBlank()) return ResultState.Error("Thiếu thông tin người dùng")

        return try {
            val cacheKey = buildEnrollmentCacheKey(userId, pageRequest)
            if (pageRequest.useCache && !pageRequest.refresh) {
                RepositoryCache.get<PageResult<Enrollment>>(cacheKey)?.let {
                    return ResultState.Success(it.copy(fromCache = true))
                }
            }

            var query = enrollmentsCollection
                .whereEqualTo("userId", userId)
                .orderBy("enrolledAt", Query.Direction.DESCENDING)
                .limit((pageRequest.normalizedPageSize + 1).toLong())

            val cursorId = pageRequest.cursor
            if (!cursorId.isNullOrBlank()) {
                val cursorSnapshot = enrollmentsCollection.document(cursorId).get().await()
                if (cursorSnapshot.exists()) {
                    query = query.startAfter(cursorSnapshot)
                }
            }

            val snapshot = query.get().await()
            val rawItems = snapshot.documents.mapNotNull { document ->
                document.toObject(Enrollment::class.java)?.let { enrollment ->
                    if (enrollment.id.isBlank()) {
                        enrollment.copy(id = document.id)
                    } else {
                        enrollment
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
            ResultState.Error(e.message ?: "Lấy danh sách đăng ký thất bại")
        }
    }

    private fun buildEnrollmentCacheKey(userId: String, pageRequest: PageRequest): String {
        val cursorPart = pageRequest.cursor ?: "first"
        return "$enrollmentCachePrefix:$userId:${pageRequest.normalizedPageSize}:$cursorPart"
    }

    private fun invalidateEnrollmentCache(userId: String) {
        if (userId.isBlank()) return
        RepositoryCache.invalidateByPrefix("$enrollmentCachePrefix:$userId")
    }
}
