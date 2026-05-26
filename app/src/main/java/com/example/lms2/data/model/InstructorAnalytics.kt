package com.example.lms2.data.model

/**
 * Định nghĩa mô hình dữ liệu InstructorAnalytics dùng trong ứng dụng LMS Android.
 * File này khai báo contract dữ liệu được trao đổi giữa Firestore, repository, ViewModel và giao diện Compose.
 * Việc tách riêng model giúp các luồng nghiệp vụ dùng chung một cấu trúc dữ liệu nhất quán và dễ bảo trì.
 */

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

/**
 * Khai báo InstructorCoursePerformance trong file này để phục vụ một trách nhiệm cụ thể của hệ thống.
 */

data class InstructorCoursePerformance(
    val courseId: String,
    val title: String,
    val thumbnailUrl: String,
    val enrollments: Int,
    val rating: Double,
    val reviewCount: Int,
    val revenue: Double
)

/**
 * Khai báo AnalyticsTrendPoint trong file này để phục vụ một trách nhiệm cụ thể của hệ thống.
 */

data class AnalyticsTrendPoint(
    val label: String,
    val value: Double
)

/**
 * Khai báo InstructorAnalyticsData trong file này để phục vụ một trách nhiệm cụ thể của hệ thống.
 */

data class InstructorAnalyticsData(
    val kpi: InstructorKpi = InstructorKpi(),
    val topCourses: List<InstructorCoursePerformance> = emptyList(),
    val enrollmentTrend: List<AnalyticsTrendPoint> = emptyList(),
    val revenueTrend: List<AnalyticsTrendPoint> = emptyList()
)

