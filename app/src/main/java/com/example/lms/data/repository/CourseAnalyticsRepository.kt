package com.example.lms.data.repository

import com.example.lms.data.model.CourseAnalyticsData
import com.example.lms.data.model.Progress
import com.example.lms.data.model.QuizProgress
import com.example.lms.util.ResultState
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class CourseAnalyticsRepository(
    private val courseRepository: CourseRepository = CourseRepository(),
    private val reviewRepository: ReviewRepository = ReviewRepository()
) {

    private val firestore = FirebaseFirestore.getInstance()
    private val enrollmentsCollection = firestore.collection("enrollments")
    private val orderItemsCollection = firestore.collection("orderItems")
    private val progressCollection = firestore.collection("progress")
    private val quizProgressCollection = firestore.collection("quizProgress")

    suspend fun getCourseAnalytics(courseId: String): ResultState<CourseAnalyticsData> {
        if (courseId.isBlank()) return ResultState.Error("Thiếu mã khóa học")

        return try {
            val courseResult = courseRepository.getCourseById(courseId)
            val course = when (courseResult) {
                is ResultState.Success -> courseResult.data
                is ResultState.Error -> return ResultState.Error(courseResult.message)
                else -> return ResultState.Error("Không tải được thông tin khóa học")
            }

            val enrollmentsSnapshot = enrollmentsCollection
                .whereEqualTo("courseId", courseId)
                .get()
                .await()
            val enrollments = enrollmentsSnapshot.size()

            val orderItemsSnapshot = orderItemsCollection
                .whereEqualTo("courseId", courseId)
                .get()
                .await()
            val estimatedRevenue = orderItemsSnapshot.documents
                .sumOf { it.getDouble("coursePrice") ?: 0.0 }

            val progressSnapshot = progressCollection
                .whereEqualTo("courseId", courseId)
                .get()
                .await()
            val progresses = progressSnapshot.toObjects(Progress::class.java)
            val completionRate = if (progresses.isEmpty()) {
                0.0
            } else {
                progresses.count { it.isCompleted } * 100.0 / progresses.size
            }

            val quizProgressSnapshot = quizProgressCollection
                .whereEqualTo("courseId", courseId)
                .get()
                .await()
            val quizProgresses = quizProgressSnapshot.toObjects(QuizProgress::class.java)
            val quizPassRate = if (quizProgresses.isEmpty()) {
                0.0
            } else {
                quizProgresses.count { it.isPassed } * 100.0 / quizProgresses.size
            }

            val reviewsResult = reviewRepository.getCourseReviews(courseId)
            val reviews = when (reviewsResult) {
                is ResultState.Success -> reviewsResult.data
                is ResultState.Error -> emptyList()
                else -> emptyList()
            }

            ResultState.Success(
                CourseAnalyticsData(
                    course = course,
                    enrollments = enrollments,
                    estimatedRevenue = estimatedRevenue,
                    completionRate = completionRate,
                    quizPassRate = quizPassRate,
                    reviews = reviews
                )
            )
        } catch (e: Exception) {
            ResultState.Error(e.message ?: "Tải thống kê khóa học thất bại")
        }
    }
}

