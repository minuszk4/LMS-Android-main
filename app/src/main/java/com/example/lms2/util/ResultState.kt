package com.example.lms2.util
/**
 * Cung cấp tiện ích hoặc kiểu hỗ trợ ResultState cho ứng dụng LMS Android.
 * File này được dùng lại ở nhiều nơi để tránh lặp logic và chuẩn hóa cách xử lý dữ liệu hoặc trạng thái.
 * Những helper như thế này giúp mã nguồn gọn hơn và dễ tái sử dụng khi mở rộng tính năng.
 */

sealed class ResultState<out T> {
    object Loading : ResultState<Nothing>()
    data class Success<T>(val data: T) : ResultState<T>()
    data class Error(val message: String) : ResultState<Nothing>()
}
