package com.example.lms.data.model

import com.google.firebase.firestore.PropertyName

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

data class LessonProgress(
    val lessonId: String = "",
    val userId: String = "",
    val courseId: String = "",
    @get:PropertyName("isCompleted")
    @set:PropertyName("isCompleted")
    var isCompleted: Boolean = false
)

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