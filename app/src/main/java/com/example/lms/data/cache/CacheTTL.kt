package com.example.lms.data.cache

/**
 * Cache TTL (Time To Live) levels for different types of data.
 */
enum class CacheTTL(val durationMillis: Long) {
    SHORT(1 * 60 * 1000),           // 1 minute
    MEDIUM(5 * 60 * 1000),          // 5 minutes
    LONG(60 * 60 * 1000),           // 60 minutes
    VERY_LONG(24 * 60 * 60 * 1000); // 24 hours
}
