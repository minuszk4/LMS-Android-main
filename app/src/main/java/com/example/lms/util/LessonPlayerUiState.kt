package com.example.lms.util

import com.example.lms.data.model.Course
import com.example.lms.data.model.CurriculumItem
import com.example.lms.data.model.LessonProgress
import com.example.lms.data.model.Progress
import com.example.lms.data.model.QuizProgress

data class LessonPlayerUiState(
    val isLoading: Boolean = false,
    val course: Course? = null,
    val curriculum: List<CurriculumItem> = emptyList(),
    val selectedItemId: String = "",

    // Progress
    val progress: Progress? = null,
    val lessonProgressMap: Map<String, LessonProgress> = emptyMap(),
    val quizProgressMap: Map<String, QuizProgress> = emptyMap(),

    // Quiz state
    val isSubmittingQuiz: Boolean = false,
    val isTogglingLesson: Boolean = false
) {
    // Computed properties

    val selectedItem: CurriculumItem?
        get() = curriculum.find { it.id == selectedItemId }

    val currentLessonProgress: LessonProgress?
        get() = lessonProgressMap[selectedItemId]

    val currentQuizProgress: QuizProgress?
        get() = quizProgressMap[selectedItemId]

    val courseProgressPercent: Float
        get() {
            val total = course?.lessonCount ?: return 0f
            if (total == 0) return 0f
            return (progress?.completedLessons ?: 0).toFloat() / total
        }
}