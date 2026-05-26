package com.example.lms2.data.model

import com.google.firebase.firestore.PropertyName

/**
 * Định nghĩa mô hình dữ liệu Course dùng trong ứng dụng LMS Android.
 * File này khai báo contract dữ liệu được trao đổi giữa Firestore, repository, ViewModel và giao diện Compose.
 * Việc tách riêng model giúp các luồng nghiệp vụ dùng chung một cấu trúc dữ liệu nhất quán và dễ bảo trì.
 */

data class Course(
    val id: String = "",
    val title: String = "",
    val instructorId: String = "",
    val instructorName: String = "",
    val thumbnailUrl: String = "",
    val thumbnailPublicId: String = "",
    val introVideoUrl: String = "",
    val description: String = "",
    val categoryId: String = "",
    val level: CourseLevel = CourseLevel.BEGINNER,
    val price: Double = 0.0,
    val rating: Double = 0.0,
    val reviewCount: Int = 0,
    val enrollmentCount: Int = 0,
    val lessonCount: Int = 0,
    val duration: String = "",
    
    @get:PropertyName("isPublished")
    @set:PropertyName("isPublished")
    var isPublished: Boolean = false,
    
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * Phân loại độ khó của khóa học để hỗ trợ lọc và hiển thị.
 */
enum class CourseLevel {
    BEGINNER, INTERMEDIATE, ADVANCED
}
