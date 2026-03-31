package com.example.lms.data.model

data class CourseAnalyticsData(
    val course: Course,
    val enrollments: Int,
    val estimatedRevenue: Double,
    val completionRate: Double,
    val quizPassRate: Double,
    val reviews: List<Review>
)

