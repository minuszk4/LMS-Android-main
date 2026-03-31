package com.example.lms2.data.paging

data class PageRequest(
    val pageSize: Int = 20,
    val cursor: String? = null,
    val useCache: Boolean = true,
    val refresh: Boolean = false
) {
    val normalizedPageSize: Int
        get() = pageSize.coerceIn(1, 100)
}
