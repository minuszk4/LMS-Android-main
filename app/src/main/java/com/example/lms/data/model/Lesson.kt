package com.example.lms.data.model

data class Lesson(
    val id: String = "",
    val courseId: String = "",
    val title: String = "",
    val description: String = "",
    val videoUrl: String = "",
    val duration: String = "",
    val orderIndex: Int = 0,
    val attachments: List<Attachment> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

data class Attachment(
    val name: String = "",
    val url: String = "",
    val type: String = "",
    val size: String = ""
)
