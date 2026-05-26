package com.example.lms2.data.model

/**
 * Định nghĩa mô hình dữ liệu Lesson dùng trong ứng dụng LMS Android.
 * File này khai báo contract dữ liệu được trao đổi giữa Firestore, repository, ViewModel và giao diện Compose.
 * Việc tách riêng model giúp các luồng nghiệp vụ dùng chung một cấu trúc dữ liệu nhất quán và dễ bảo trì.
 */

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

/**
 * Khai báo Attachment trong file này để phục vụ một trách nhiệm cụ thể của hệ thống.
 */

data class Attachment(
    val name: String = "",
    val url: String = "",
    val type: String = "",
    val size: String = ""
)
