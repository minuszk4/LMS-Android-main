package com.example.lms2.data.cache

object RepositoryCache {
    // Cache-first mode: read from in-memory cache by default.
    // Mutations should invalidate relevant prefixes to keep cache synchronized.
    @Volatile
    var enableCacheRead: Boolean = true

    private data class CacheEntry(
        val value: Any,
        val expiresAt: Long
    )

    private val lock = Any()
    private val entries = mutableMapOf<String, CacheEntry>()

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> get(key: String): T? {
        if (!enableCacheRead) return null

        val now = System.currentTimeMillis()
        synchronized(lock) {
            val entry = entries[key] ?: return null
            if (entry.expiresAt <= now) {
                entries.remove(key)
                return null
            }
            return entry.value as? T
        }
    }

    fun put(key: String, value: Any, ttl: CacheTTL) {
        synchronized(lock) {
            entries[key] = CacheEntry(
                value = value,
                expiresAt = System.currentTimeMillis() + ttl.durationMillis
            )
        }
    }

    fun invalidate(key: String) {
        synchronized(lock) {
            entries.remove(key)
        }
    }

    fun invalidateByPrefix(prefix: String) {
        synchronized(lock) {
            val targets = entries.keys.filter { it.startsWith(prefix) }
            targets.forEach { entries.remove(it) }
        }
    }
}
