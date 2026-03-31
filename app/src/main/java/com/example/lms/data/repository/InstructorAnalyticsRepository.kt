package com.example.lms.data.repository

import com.example.lms.data.model.AnalyticsTrendPoint
import com.example.lms.data.model.Course
import com.example.lms.data.model.Enrollment
import com.example.lms.data.model.InstructorAnalyticsData
import com.example.lms.data.model.InstructorCoursePerformance
import com.example.lms.data.model.InstructorKpi
import com.example.lms.data.model.OrderItem
import com.example.lms.data.model.Progress
import com.example.lms.data.model.QuizProgress
import com.example.lms.util.InstructorTimeRange
import com.example.lms.util.ResultState
import com.example.lms.util.toStartAtMillis
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await
import java.util.Calendar
import kotlin.math.ceil
import kotlin.math.max

class InstructorAnalyticsRepository {

    private val firestore = FirebaseFirestore.getInstance()
    private val coursesCollection = firestore.collection("courses")
    private val enrollmentsCollection = firestore.collection("enrollments")
    private val orderItemsCollection = firestore.collection("orderItems")
    private val progressCollection = firestore.collection("progress")
    private val quizProgressCollection = firestore.collection("quizProgress")

    suspend fun getInstructorAnalytics(
        instructorId: String,
        range: InstructorTimeRange
    ): ResultState<InstructorAnalyticsData> {
        if (instructorId.isBlank()) return ResultState.Error("Thiếu thông tin giảng viên")

        return try {
            val now = System.currentTimeMillis()
            val startAt = range.toStartAtMillis(now)

            val coursesSnapshot = coursesCollection
                .whereEqualTo("instructorId", instructorId)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .get()
                .await()

            val courses = coursesSnapshot.toObjects(Course::class.java)
            if (courses.isEmpty()) {
                return ResultState.Success(InstructorAnalyticsData())
            }

            val courseIds = courses.map { it.id }
            val enrollments = fetchEnrollments(courseIds, startAt)
            val orderItems = fetchOrderItems(courseIds, startAt)
            val progresses = fetchProgresses(courseIds, startAt)
            val quizProgresses = fetchQuizProgresses(courseIds, startAt)

            val totalCourses = courses.size
            val publishedCourses = courses.count { it.isPublished }
            val draftCourses = totalCourses - publishedCourses
            val totalEnrollments = enrollments.size
            val totalReviews = courses.sumOf { it.reviewCount }
            val weightedRating = courses.sumOf { it.rating * it.reviewCount }
            val averageRating = if (totalReviews > 0) weightedRating / totalReviews else 0.0
            val estimatedRevenue = orderItems.sumOf { it.coursePrice }

            val completedProgressCount = progresses.count { it.isCompleted }
            val completionRate = if (progresses.isEmpty()) {
                0.0
            } else {
                completedProgressCount * 100.0 / progresses.size
            }

            val passedQuizCount = quizProgresses.count { it.isPassed }
            val quizPassRate = if (quizProgresses.isEmpty()) {
                0.0
            } else {
                passedQuizCount * 100.0 / quizProgresses.size
            }

            val topCourses = courses
                .map { course ->
                    InstructorCoursePerformance(
                        courseId = course.id,
                        title = course.title,
                        thumbnailUrl = course.thumbnailUrl,
                        enrollments = enrollments.count { it.courseId == course.id },
                        rating = course.rating,
                        reviewCount = course.reviewCount,
                        revenue = course.price
                    )
                }
                .sortedByDescending { it.enrollments }
                .take(3)

            val enrollmentTrend = buildCountTrend(
                points = enrollments.map { it.enrolledAt },
                range = range,
                now = now
            )

            val revenueTrend = buildAmountTrend(
                points = orderItems.map { it.createdAt to it.coursePrice },
                range = range,
                now = now
            )

            ResultState.Success(
                InstructorAnalyticsData(
                    kpi = InstructorKpi(
                        totalCourses = totalCourses,
                        publishedCourses = publishedCourses,
                        draftCourses = draftCourses,
                        totalEnrollments = totalEnrollments,
                        averageRating = averageRating,
                        totalReviews = totalReviews,
                        estimatedRevenue = estimatedRevenue,
                        completionRate = completionRate,
                        quizPassRate = quizPassRate
                    ),
                    topCourses = topCourses,
                    enrollmentTrend = enrollmentTrend,
                    revenueTrend = revenueTrend
                )
            )
        } catch (e: Exception) {
            ResultState.Error(e.message ?: "Tải thống kê giảng viên thất bại")
        }
    }

    private suspend fun fetchEnrollments(courseIds: List<String>, startAt: Long?): List<Enrollment> {
        if (courseIds.isEmpty()) return emptyList()
        val all = mutableListOf<Enrollment>()

        courseIds.chunked(10).forEach { chunk ->
            var query = enrollmentsCollection.whereIn("courseId", chunk)
            if (startAt != null) {
                query = query.whereGreaterThanOrEqualTo("enrolledAt", startAt)
            }
            all += query.get().await().toObjects(Enrollment::class.java)
        }

        return all
    }

    private suspend fun fetchOrderItems(courseIds: List<String>, startAt: Long?): List<OrderItem> {
        if (courseIds.isEmpty()) return emptyList()
        val all = mutableListOf<OrderItem>()

        courseIds.chunked(10).forEach { chunk ->
            var query = orderItemsCollection.whereIn("courseId", chunk)
            if (startAt != null) {
                query = query.whereGreaterThanOrEqualTo("createdAt", startAt)
            }
            all += query.get().await().toObjects(OrderItem::class.java)
        }

        return all
    }

    private suspend fun fetchProgresses(courseIds: List<String>, startAt: Long?): List<Progress> {
        if (courseIds.isEmpty()) return emptyList()
        val all = mutableListOf<Progress>()

        courseIds.chunked(10).forEach { chunk ->
            var query = progressCollection.whereIn("courseId", chunk)
            if (startAt != null) {
                query = query.whereGreaterThanOrEqualTo("lastAccessedAt", startAt)
            }
            all += query.get().await().toObjects(Progress::class.java)
        }

        return all
    }

    private suspend fun fetchQuizProgresses(courseIds: List<String>, startAt: Long?): List<QuizProgress> {
        if (courseIds.isEmpty()) return emptyList()
        val all = mutableListOf<QuizProgress>()

        courseIds.chunked(10).forEach { chunk ->
            var query = quizProgressCollection.whereIn("courseId", chunk)
            if (startAt != null) {
                query = query.whereGreaterThanOrEqualTo("lastAttemptAt", startAt)
            }
            all += query.get().await().toObjects(QuizProgress::class.java)
        }

        return all
    }

    private fun buildCountTrend(
        points: List<Long>,
        range: InstructorTimeRange,
        now: Long
    ): List<AnalyticsTrendPoint> {
        val buckets = createBuckets(range, now)
        if (buckets.isEmpty()) return emptyList()

        points.forEach { timestamp ->
            val index = bucketIndex(timestamp, buckets.map { it.startAt })
            if (index >= 0) {
                val old = buckets[index]
                buckets[index] = old.copy(value = old.value + 1.0)
            }
        }

        return buckets.map { AnalyticsTrendPoint(it.label, it.value) }
    }

    private fun buildAmountTrend(
        points: List<Pair<Long, Double>>,
        range: InstructorTimeRange,
        now: Long
    ): List<AnalyticsTrendPoint> {
        val buckets = createBuckets(range, now)
        if (buckets.isEmpty()) return emptyList()

        points.forEach { (timestamp, amount) ->
            val index = bucketIndex(timestamp, buckets.map { it.startAt })
            if (index >= 0) {
                val old = buckets[index]
                buckets[index] = old.copy(value = old.value + amount)
            }
        }

        return buckets.map { AnalyticsTrendPoint(it.label, it.value) }
    }

    private data class Bucket(
        val label: String,
        val startAt: Long,
        val value: Double = 0.0
    )

    private fun createBuckets(
        range: InstructorTimeRange,
        now: Long
    ): MutableList<Bucket> {
        val base = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        return when (range) {
            InstructorTimeRange.LAST_7_DAYS,
            InstructorTimeRange.LAST_30_DAYS,
            InstructorTimeRange.LAST_90_DAYS -> {
                val days = range.days ?: 7
                val labels = max(4, days / 7)
                val step = max(1, ceil(days.toDouble() / labels).toInt())

                (0 until labels).map { index ->
                    val offset = (days - (index * step)).coerceAtLeast(0)
                    val bucketCal = (base.clone() as Calendar).apply {
                        add(Calendar.DAY_OF_YEAR, -offset)
                    }
                    val label = "${bucketCal.get(Calendar.DAY_OF_MONTH)}/${bucketCal.get(Calendar.MONTH) + 1}"
                    Bucket(
                        label = label,
                        startAt = bucketCal.timeInMillis
                    )
                }.toMutableList()
            }

            InstructorTimeRange.ALL_TIME -> {
                (5 downTo 1).map { offset ->
                    val bucketCal = (base.clone() as Calendar).apply {
                        add(Calendar.MONTH, -offset)
                        set(Calendar.DAY_OF_MONTH, 1)
                    }
                    Bucket(
                        label = "T${bucketCal.get(Calendar.MONTH) + 1}",
                        startAt = bucketCal.timeInMillis
                    )
                }.toMutableList()
            }
        }
    }

    private fun bucketIndex(timestamp: Long, starts: List<Long>): Int {
        if (starts.isEmpty()) return -1

        var matchedIndex = -1
        starts.forEachIndexed { index, startAt ->
            if (timestamp >= startAt) {
                matchedIndex = index
            }
        }

        return matchedIndex
    }
}

