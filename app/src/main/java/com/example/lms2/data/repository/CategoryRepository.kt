package com.example.lms2.data.repository

import com.example.lms2.data.cache.CacheTTL
import com.example.lms2.data.cache.RepositoryCache
import com.example.lms2.data.model.Category
import com.example.lms2.data.paging.PageRequest
import com.example.lms2.data.paging.PageResult
import com.example.lms2.util.ResultState
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

/**
 * Repository quản lý danh mục khóa học.
 *
 * Vì category được dùng ở nhiều nơi như form tạo course, bộ lọc, admin
 * và recommendation theo category, lớp này ưu tiên:
 * - chuẩn hóa dữ liệu đọc từ Firestore,
 * - cache dài hơn để giảm truy vấn lặp lại,
 * - giữ giao diện API đơn giản cho ViewModel.
 */
class CategoryRepository {
    companion object {
        private const val CACHE_KEY_ALL = "categories:all"
    }

    private val firestore = FirebaseFirestore.getInstance()
    private val categoriesCollection = firestore.collection("categories")

    /**
     * Tạo mới một category sau khi chuẩn hóa và kiểm tra trùng tên.
     *
     * Repository kiểm tra trùng ngay ở tầng dữ liệu để tránh việc nhiều màn hình admin
     * vô tình tạo ra các danh mục cùng tên nhưng khác document id.
     */
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

    /**
     * Xóa một category theo id.
     *
     * Hàm này hiện thực hiện xóa trực tiếp trên collection `categories`;
     * nếu sau này cần ràng buộc “không xóa category đang được course sử dụng”
     * thì đây là nơi phù hợp để thêm validation.
     */
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

    /**
     * Tải toàn bộ danh mục, ưu tiên dùng cache nếu có.
     *
     * Nếu cache trả về rỗng trong khi caller không yêu cầu force refresh,
     * repository sẽ thử refresh một lần nữa để tránh giữ mãi một cache rỗng.
     */
    suspend fun getCategories(forceRefresh: Boolean = false): ResultState<List<Category>> {
        return try {
            var categories = getAllCategoriesCached(useCache = true, refresh = forceRefresh)
            if (categories.isEmpty() && !forceRefresh) {
                categories = getAllCategoriesCached(useCache = true, refresh = true)
            }
            ResultState.Success(categories)
        } catch (e: Exception) {
            ResultState.Error(e.message ?: "Lỗi khi lấy danh mục")
        }
    }

    /**
     * Trả về một trang category cho những màn hình cần phân trang phía client.
     *
     * Vì số lượng category thường nhỏ, repository lấy tập đầy đủ một lần,
     * sau đó cắt trang trong bộ nhớ để giữ logic đơn giản và dễ cache.
     */
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
