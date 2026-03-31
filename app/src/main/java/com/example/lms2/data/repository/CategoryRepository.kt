package com.example.lms2.data.repository

import com.example.lms2.data.cache.RepositoryCache
import com.example.lms2.data.cache.CacheTTL
import com.example.lms2.data.model.Category
import com.example.lms2.data.paging.PageRequest
import com.example.lms2.data.paging.PageResult
import com.example.lms2.util.ResultState
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class CategoryRepository {
    companion object {
        private const val CACHE_KEY_ALL = "categories:all"
    }

    private val firestore = FirebaseFirestore.getInstance()
    private val categoriesCollection = firestore.collection("categories")

    suspend fun createCategory(name: String): ResultState<Category> {
        return try {
            val normalizedName = name.trim()
            if (normalizedName.isBlank()) {
                return ResultState.Error("Tên danh mục không được để trống")
            }

            val duplicated = categoriesCollection
                .whereEqualTo("name", normalizedName)
                .get()
                .await()
                .documents
                .isNotEmpty()

            if (duplicated) {
                return ResultState.Error("Danh mục đã tồn tại")
            }

            val docRef = categoriesCollection.document()
            val category = Category(
                id = docRef.id,
                name = normalizedName
            )

            docRef.set(category).await()
            RepositoryCache.invalidateByPrefix("categories:")
            ResultState.Success(category)
        } catch (e: Exception) {
            ResultState.Error(e.message ?: "Không thể tạo danh mục")
        }
    }

    suspend fun deleteCategory(categoryId: String): ResultState<Unit> {
        return try {
            if (categoryId.isBlank()) {
                return ResultState.Error("Danh mục không hợp lệ")
            }

            categoriesCollection.document(categoryId).delete().await()
            RepositoryCache.invalidateByPrefix("categories:")
            ResultState.Success(Unit)
        } catch (e: Exception) {
            ResultState.Error(e.message ?: "Không thể xóa danh mục")
        }
    }

    suspend fun getCategories(forceRefresh: Boolean = false): ResultState<List<Category>> {
        return try {
            var categories = getAllCategoriesCached(useCache = true, refresh = forceRefresh)
            // Avoid keeping an empty in-memory cache forever when data was added externally.
            if (categories.isEmpty() && !forceRefresh) {
                categories = getAllCategoriesCached(useCache = true, refresh = true)
            }
            ResultState.Success(categories)
        } catch (e: Exception) {
            ResultState.Error(e.message ?: "Lỗi khi lấy danh mục")
        }
    }

    suspend fun getCategoriesPage(pageRequest: PageRequest = PageRequest()): ResultState<PageResult<Category>> {
        return try {
            var categories = getAllCategoriesCached(
                useCache = pageRequest.useCache,
                refresh = pageRequest.refresh
            )

            if (categories.isEmpty() && pageRequest.useCache && !pageRequest.refresh) {
                categories = getAllCategoriesCached(useCache = true, refresh = true)
            }

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
