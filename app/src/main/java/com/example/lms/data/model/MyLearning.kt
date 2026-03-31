package com.example.lms.data.model

data class MyLearningItem(
    val course: Course,
    val categoryName: String,
    val lastLessonTitle: String,
    val lastLessonOrderIndex: Int,
    val progressPercent: Int,
    val completedLessons: Int,
    val totalLessons: Int,
    val isCompleted: Boolean,
    val lastLessonId: String,
    val lastAccessedAt: Long,
    val enrolledAt: Long
)

data class MyLearningData(
    val inProgress: List<MyLearningItem> = emptyList(),
    val completed: List<MyLearningItem> = emptyList()
)

