package com.example.lms.data.model

data class Quiz(
    val id: String = "",
    val courseId: String = "",
    val title: String = "",
    val description: String = "",
    val orderIndex: Int = 0,
    val questions: List<Question> = emptyList(),
    val durationMinutes: Int = 15,
    val passingScore: Int = 80,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

data class Question(
    val id: String = "",
    val text: String = "",
    val options: List<String> = listOf("", "", "", ""),
    val correctAnswerIndex: Int = 0
)
