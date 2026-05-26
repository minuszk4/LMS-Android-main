package com.example.lms2.data.model

/**
 * Định nghĩa mô hình dữ liệu CurriculumItem dùng trong ứng dụng LMS Android.
 * File này khai báo contract dữ liệu được trao đổi giữa Firestore, repository, ViewModel và giao diện Compose.
 * Việc tách riêng model giúp các luồng nghiệp vụ dùng chung một cấu trúc dữ liệu nhất quán và dễ bảo trì.
 */

sealed class CurriculumItem {
    abstract val id: String
    abstract val courseId: String
    abstract val orderIndex: Int

    data class LessonItem(val lesson: Lesson) : CurriculumItem() {
        override val id = lesson.id
        override val courseId = lesson.courseId
        override val orderIndex = lesson.orderIndex
    }

    data class QuizItem(val quiz: Quiz) : CurriculumItem() {
        override val id = quiz.id
        override val courseId = quiz.courseId
        override val orderIndex = quiz.orderIndex
    }
}
