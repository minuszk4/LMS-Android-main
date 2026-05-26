package com.example.lms2.data.paging

/**
 * Khai báo contract phân trang PageRequest cho tầng dữ liệu của ứng dụng.
 * File này chuẩn hóa thông tin đầu vào hoặc đầu ra khi repository tải dữ liệu theo từng trang.
 * Cách tổ chức này giúp các màn hình tái sử dụng chung cơ chế tải thêm và theo dõi trạng thái phân trang.
 */

data class PageRequest(
    val pageSize: Int = 20,
    val cursor: String? = null,
    val useCache: Boolean = true,
    val refresh: Boolean = false
) {
    val normalizedPageSize: Int
        get() = pageSize.coerceIn(1, 100)
}
