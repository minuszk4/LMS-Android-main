package com.example.lms2.data.model

import com.google.firebase.firestore.PropertyName

/**
 * Định nghĩa mô hình dữ liệu Progress dùng trong ứng dụng LMS Android.
 * File này khai báo contract dữ liệu được trao đổi giữa Firestore, repository, ViewModel và giao diện Compose.
 * Việc tách riêng model giúp các luồng nghiệp vụ dùng chung một cấu trúc dữ liệu nhất quán và dễ bảo trì.
 */

data class Progress(
    val userId: String = "",
    val courseId: String = "",
    val lastAccessedAt: Long = System.currentTimeMillis(),
    val lastLessonId: String = "",
    val completedLessons: Int = 0,
    @get:PropertyName("isCompleted")
    @set:PropertyName("isCompleted")
    var isCompleted: Boolean = false
)

/**
 * Khai báo LessonProgress trong file này để phục vụ một trách nhiệm cụ thể của hệ thống.
 */

data class LessonProgress(
    val lessonId: String = "",
    val userId: String = "",
    val courseId: String = "",
    @get:PropertyName("isCompleted")
    @set:PropertyName("isCompleted")
    var isCompleted: Boolean = false
)

/**
 * Khai báo QuizProgress trong file này để phục vụ một trách nhiệm cụ thể của hệ thống.
 */

data class QuizProgress(
    val quizId: String = "",
    val userId: String = "",
    val courseId: String = "",
    val attempts: Int = 0,
    val bestScore: Int = 0,
    @get:PropertyName("isPassed")
    @set:PropertyName("isPassed")
    var isPassed: Boolean = false,
    val lastAttemptAt: Long = 0L,
    val lastAnswers: List<Int> = emptyList(),
    val lastCorrectCount: Int = 0,
    val lastWrongCount: Int = 0
)
