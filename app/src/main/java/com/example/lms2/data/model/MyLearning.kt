package com.example.lms2.data.model

/**
 * Định nghĩa mô hình dữ liệu MyLearning dùng trong ứng dụng LMS Android.
 * File này khai báo contract dữ liệu được trao đổi giữa Firestore, repository, ViewModel và giao diện Compose.
 * Việc tách riêng model giúp các luồng nghiệp vụ dùng chung một cấu trúc dữ liệu nhất quán và dễ bảo trì.
 */

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

/**
 * Khai báo MyLearningData trong file này để phục vụ một trách nhiệm cụ thể của hệ thống.
 */

data class MyLearningData(
    val inProgress: List<MyLearningItem> = emptyList(),
    val completed: List<MyLearningItem> = emptyList()
)

