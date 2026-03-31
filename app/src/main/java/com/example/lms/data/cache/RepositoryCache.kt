package com.example.lms.data.cache

object RepositoryCache {
    private data class CacheEntry(
        val value: Any,
        val expiresAt: Long
    )

    private val lock = Any()
    private val entries = mutableMapOf<String, CacheEntry>()

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> get(key: String): T? {
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
