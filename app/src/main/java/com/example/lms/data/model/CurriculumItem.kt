package com.example.lms.data.model

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