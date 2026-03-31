package com.example.lms.util

import java.text.NumberFormat
import java.util.Locale

fun formatPrice(price: Double): String {
    return if (price == 0.0) {
        "Miễn phí"
    } else {
        NumberFormat.getInstance(Locale("vi", "VN"))
            .format(price.toLong()) + "đ"
    }
}