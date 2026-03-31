package com.example.lms2.data.paging

data class PageResult<T>(
    val items: List<T>,
    val nextCursor: String?,
    val hasMore: Boolean,
    val totalCount: Int = 0,
    val fromCache: Boolean = false
)
