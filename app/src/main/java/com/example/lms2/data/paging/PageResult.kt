package com.example.lms2.data.paging

/**
 * Khai báo contract phân trang PageResult cho tầng dữ liệu của ứng dụng.
 * File này chuẩn hóa thông tin đầu vào hoặc đầu ra khi repository tải dữ liệu theo từng trang.
 * Cách tổ chức này giúp các màn hình tái sử dụng chung cơ chế tải thêm và theo dõi trạng thái phân trang.
 */

data class PageResult<T>(
    val items: List<T>,
    val nextCursor: String?,
    val hasMore: Boolean,
    val totalCount: Int = 0,
    val fromCache: Boolean = false
)
