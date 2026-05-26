package com.example.lms2.data.cache

/**
 * Cung cấp tiện ích cache cho tầng dữ liệu thông qua CacheTTL.
 * File này hỗ trợ repository lưu tạm kết quả truy vấn để giảm số lần đọc Firestore và cải thiện tốc độ phản hồi.
 * Cơ chế cache được tách riêng nhằm giữ cho logic truy xuất dữ liệu và logic tối ưu hiệu năng không bị trộn lẫn.
 */

enum class CacheTTL(val durationMillis: Long) {
    SHORT(5 * 1000),     // 5 seconds
    MEDIUM(5 * 1000),    // 5 seconds
    LONG(5 * 1000),      // 5 seconds
    VERY_LONG(5 * 1000); // 5 seconds
}
