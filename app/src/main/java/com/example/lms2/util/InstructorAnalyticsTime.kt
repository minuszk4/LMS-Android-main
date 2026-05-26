package com.example.lms2.util

/**
 * Cung cấp tiện ích hoặc kiểu hỗ trợ InstructorAnalyticsTime cho ứng dụng LMS Android.
 * File này được dùng lại ở nhiều nơi để tránh lặp logic và chuẩn hóa cách xử lý dữ liệu hoặc trạng thái.
 * Những helper như thế này giúp mã nguồn gọn hơn và dễ tái sử dụng khi mở rộng tính năng.
 */

fun InstructorTimeRange.toStartAtMillis(now: Long): Long? {
    val daysToSubtract = days ?: return null
    return now - daysToSubtract * 24L * 60L * 60L * 1000L
}

