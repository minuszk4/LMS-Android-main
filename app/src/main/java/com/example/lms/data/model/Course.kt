package com.example.lms.data.model

import com.google.firebase.firestore.PropertyName

data class Course(
    val id: String = "",
    val title: String = "",
    val instructorId: String = "",
    val instructorName: String = "",
    val thumbnailUrl: String = "",
    val thumbnailPublicId: String = "",
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

enum class CourseLevel {
    BEGINNER, INTERMEDIATE, ADVANCED
}
