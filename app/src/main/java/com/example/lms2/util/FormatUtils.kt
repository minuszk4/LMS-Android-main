package com.example.lms2.util

import java.text.NumberFormat
import java.util.Locale

/**
 * Cung cấp tiện ích hoặc kiểu hỗ trợ FormatUtils cho ứng dụng LMS Android.
 * File này được dùng lại ở nhiều nơi để tránh lặp logic và chuẩn hóa cách xử lý dữ liệu hoặc trạng thái.
 * Những helper như thế này giúp mã nguồn gọn hơn và dễ tái sử dụng khi mở rộng tính năng.
 */

fun formatPrice(price: Double): String {
    return if (price == 0.0) {
        "Miễn phí"
    } else {
        NumberFormat.getInstance(Locale("vi", "VN"))
            .format(price.toLong()) + "đ"
    }
}
