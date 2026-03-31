package com.example.lms.data.repository

import com.example.lms.data.cache.RepositoryCache
import com.example.lms.data.cache.CacheTTL
import com.example.lms.data.model.Category
import com.example.lms.data.paging.PageRequest
import com.example.lms.data.paging.PageResult
import com.example.lms.util.ResultState
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class CategoryRepository {
    companion object {
        private const val CACHE_KEY_ALL = "categories:all"
    }

    private val firestore = FirebaseFirestore.getInstance()
    private val categoriesCollection = firestore.collection("categories")

    suspend fun getCategories(): ResultState<List<Category>> {
        return try {
            val categories = getAllCategoriesCached(useCache = true, refresh = false)
            ResultState.Success(categories)
        } catch (e: Exception) {
            ResultState.Error(e.message ?: "Lỗi khi lấy danh mục")
        }
    }

    suspend fun getCategoriesPage(pageRequest: PageRequest = PageRequest()): ResultState<PageResult<Category>> {
        return try {
            val categories = getAllCategoriesCached(
                useCache = pageRequest.useCache,
                refresh = pageRequest.refresh
            )

            val startIndex = pageRequest.cursor?.toIntOrNull()?.coerceAtLeast(0) ?: 0
            if (startIndex >= categories.size) {
                return ResultState.Success(
                    PageResult(
                        items = emptyList(),
                        nextCursor = null,
                        hasMore = false,
                        fromCache = pageRequest.useCache && !pageRequest.refresh
                    )
                )
            }

            val pageSize = pageRequest.normalizedPageSize
            val endIndex = (startIndex + pageSize).coerceAtMost(categories.size)
            val pageItems = categories.subList(startIndex, endIndex)
            val nextCursor = if (endIndex < categories.size) endIndex.toString() else null

            ResultState.Success(
                PageResult(
                    items = pageItems,
                    nextCursor = nextCursor,
                    hasMore = nextCursor != null,
                    fromCache = pageRequest.useCache && !pageRequest.refresh
                )
            )
        } catch (e: Exception) {
            ResultState.Error(e.message ?: "Lỗi khi lấy danh mục")
        }
    }

    private suspend fun getAllCategoriesCached(useCache: Boolean, refresh: Boolean): List<Category> {
        if (useCache && !refresh) {
            RepositoryCache.get<List<Category>>(CACHE_KEY_ALL)?.let { return it }
        }

        val snapshot = categoriesCollection.get().await()
        val categories = snapshot.documents
            .map { doc ->
                val categoryId = doc.getString("id").orEmpty().ifBlank { doc.id }
                val categoryName = doc.getString("name")
                    .orEmpty()
                    .ifBlank { doc.getString("title").orEmpty() }
                    .ifBlank { doc.getString("categoryName").orEmpty() }
                    .ifBlank { categoryId }

                Category(
                    id = categoryId,
                    name = categoryName
                )
            }
            .filter { it.id.isNotBlank() }
            .distinctBy { it.id }
            .sortedBy { it.name.lowercase() }

        RepositoryCache.put(CACHE_KEY_ALL, categories, CacheTTL.LONG)
        return categories
    }
}
