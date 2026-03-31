package com.example.lms.data.model

data class InstructorKpi(
    val totalCourses: Int = 0,
    val publishedCourses: Int = 0,
    val draftCourses: Int = 0,
    val totalEnrollments: Int = 0,
    val averageRating: Double = 0.0,
    val totalReviews: Int = 0,
    val estimatedRevenue: Double = 0.0,
    val completionRate: Double = 0.0,
    val quizPassRate: Double = 0.0
)

data class InstructorCoursePerformance(
    val courseId: String,
    val title: String,
    val thumbnailUrl: String,
    val enrollments: Int,
    val rating: Double,
    val reviewCount: Int,
    val revenue: Double
)

data class AnalyticsTrendPoint(
    val label: String,
    val value: Double
)

data class InstructorAnalyticsData(
    val kpi: InstructorKpi = InstructorKpi(),
    val topCourses: List<InstructorCoursePerformance> = emptyList(),
    val enrollmentTrend: List<AnalyticsTrendPoint> = emptyList(),
    val revenueTrend: List<AnalyticsTrendPoint> = emptyList()
)

