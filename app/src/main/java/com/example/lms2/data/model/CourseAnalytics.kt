package com.example.lms2.data.model

/**
 * Định nghĩa mô hình dữ liệu CourseAnalytics dùng trong ứng dụng LMS Android.
 * File này khai báo contract dữ liệu được trao đổi giữa Firestore, repository, ViewModel và giao diện Compose.
 * Việc tách riêng model giúp các luồng nghiệp vụ dùng chung một cấu trúc dữ liệu nhất quán và dễ bảo trì.
 */

data class CourseAnalyticsData(
    val course: Course,
    val enrollments: Int,
    val estimatedRevenue: Double,
    val completionRate: Double,
    val quizPassRate: Double,
    val reviews: List<Review>
)

