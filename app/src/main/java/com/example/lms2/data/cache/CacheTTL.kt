package com.example.lms2.data.cache

/**
 * Cache TTL (Time To Live) levels for different types of data.
 */
enum class CacheTTL(val durationMillis: Long) {
    SHORT(5 * 1000),     // 5 seconds
    MEDIUM(5 * 1000),    // 5 seconds
    LONG(5 * 1000),      // 5 seconds
    VERY_LONG(5 * 1000); // 5 seconds
}
