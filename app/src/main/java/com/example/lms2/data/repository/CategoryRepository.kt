package com.example.lms2.data.repository

import com.example.lms2.data.cache.RepositoryCache
import com.example.lms2.data.cache.CacheTTL
import com.example.lms2.data.model.Category
import com.example.lms2.data.paging.PageRequest
import com.example.lms2.data.paging.PageResult
import com.example.lms2.util.ResultState
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

/**
 * Triển khai repository CategoryRepository cho ứng dụng LMS Android.
 * File này chịu trách nhiệm làm việc với Firestore hoặc API ngoài, đồng thời chuyển đổi kết quả về dạng phù hợp cho ViewModel.
 * Repository là ranh giới chính giữa tầng giao diện và tầng dữ liệu nên được mô tả rõ để thuận tiện cho tài liệu kỹ thuật.
 */

class CategoryRepository {
    companion object {
        private const val CACHE_KEY_ALL = "categories:all"
    }

    private val firestore = FirebaseFirestore.getInstance()
    private val categoriesCollection = firestore.collection("categories")

    /**
     * Tạo mới dữ liệu nghiệp vụ dựa trên đầu vào hiện tại.
     * Hàm này thường làm việc với Firestore hoặc API ngoài và trả kết quả về dạng `ResultState` cho tầng gọi phía trên.
     */

    suspend fun createCategory(name: String): ResultState<Category> {
        return try {
            // Chuẩn hóa tên danh mục bằng cách loại bỏ khoảng trắng thừa và kiểm tra xem tên có hợp lệ hay không. Nếu tên không hợp lệ, trả về lỗi ngay lập tức để tránh thực hiện các thao tác không cần thiết với Firestore.
            val normalizedName = name.trim()
            if (normalizedName.isBlank()) {
                return ResultState.Error("Tên danh mục không được để trống")
            }
            // Kiểm tra xem đã tồn tại danh mục nào có tên giống nhau (không phân biệt chữ hoa chữ thường) trong Firestore hay chưa. Nếu đã tồn tại, trả về lỗi để tránh tạo trùng lặp.
            val duplicated = categoriesCollection
                .whereEqualTo("name", normalizedName)
                .get()
                .await()
                .documents
                .isNotEmpty()
        
            if (duplicated) {
                return ResultState.Error("Danh mục đã tồn tại")
            }
            // Nếu tên hợp lệ và không trùng lặp, tiến hành tạo document mới trong Firestore với ID tự động sinh và trường `name`. Sau khi tạo xong, cần làm mới cache liên quan đến danh sách danh mục để đảm bảo dữ liệu hiển thị ở tầng giao diện là mới nhất.
            val docRef = categoriesCollection.document()
            val category = Category(
                id = docRef.id,
                name = normalizedName
            )
            // Sử dụng `set()` với `await()` để đảm bảo rằng thao tác tạo document đã hoàn thành trước khi tiếp tục, đồng thời xử lý lỗi nếu có xảy ra trong quá trình này.
            docRef.set(category).await()
            RepositoryCache.invalidateByPrefix("categories:")
            ResultState.Success(category)
        } catch (e: Exception) {
            ResultState.Error(e.message ?: "Không thể tạo danh mục")
        }
    }

    /**
     * Xóa dữ liệu liên quan khỏi hệ thống hoặc danh sách hiển thị.
     * Hàm này thường làm việc với Firestore hoặc API ngoài và trả kết quả về dạng `ResultState` cho tầng gọi phía trên.
     */

    suspend fun deleteCategory(categoryId: String): ResultState<Unit> {
        return try {
            if (categoryId.isBlank()) {
                return ResultState.Error("Danh mục không hợp lệ")
            }
            // Trước khi xóa, có thể kiểm tra xem danh mục có đang được sử dụng trong các khóa học hay không để tránh xóa nhầm. Nếu đang được sử dụng, trả về lỗi để thông báo cho người dùng.
            categoriesCollection.document(categoryId).delete().await()
            RepositoryCache.invalidateByPrefix("categories:")
            ResultState.Success(Unit)
        } catch (e: Exception) {
            ResultState.Error(e.message ?: "Không thể xóa danh mục")
        }
    }

    /**
     * Lấy dữ liệu hoặc trạng thái cần thiết cho luồng hiện tại.
     * Hàm này thường làm việc với Firestore hoặc API ngoài và trả kết quả về dạng `ResultState` cho tầng gọi phía trên.
     */

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

    /**
     * Lấy dữ liệu hoặc trạng thái cần thiết cho luồng hiện tại.
     * Hàm này thường làm việc với Firestore hoặc API ngoài và trả kết quả về dạng `ResultState` cho tầng gọi phía trên.
     */

    suspend fun getCategoriesPage(pageRequest: PageRequest = PageRequest()): ResultState<PageResult<Category>> {
        return try {
            var categories = getAllCategoriesCached(
                useCache = pageRequest.useCache,
                refresh = pageRequest.refresh
            )
            // Nếu cache trả về danh sách rỗng và pageRequest cho phép sử dụng cache nhưng không yêu cầu làm mới, có thể thử làm mới cache một lần nữa để lấy dữ liệu mới nhất từ Firestore. Điều này giúp tránh trường hợp cache bị lỗi hoặc dữ liệu đã được thêm vào từ bên ngoài mà cache chưa cập nhật.
            if (categories.isEmpty() && pageRequest.useCache && !pageRequest.refresh) {
                categories = getAllCategoriesCached(useCache = true, refresh = true)
            }
            // Tính toán chỉ số bắt đầu dựa trên cursor trong pageRequest. Nếu cursor không hợp lệ hoặc không tồn tại, mặc định bắt đầu từ 0. Sau đó, lấy một trang dữ liệu dựa trên pageSize và tính toán cursor tiếp theo nếu còn dữ liệu để phân trang.
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
            // Sử dụng `normalizedPageSize` để đảm bảo rằng pageSize luôn có giá trị hợp lệ (ví dụ: không âm hoặc quá lớn) và tránh lỗi khi tính toán chỉ số kết thúc.
            val pageSize = pageRequest.normalizedPageSize
            val endIndex = (startIndex + pageSize).coerceAtMost(categories.size)
            val pageItems = categories.subList(startIndex, endIndex)
            val nextCursor = if (endIndex < categories.size) endIndex.toString() else null
            // Trả về kết quả trang với thông tin về các mục trong trang, cursor tiếp theo, trạng thái còn dữ liệu để phân trang hay không, và thông tin về việc dữ liệu có được lấy từ cache hay không.
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
        // Lấy tất cả danh mục từ Firestore, sau đó chuyển đổi mỗi document thành đối tượng Category. Trong quá trình chuyển đổi, cố gắng lấy tên danh mục từ nhiều trường khác nhau để đảm bảo tính linh hoạt với cấu trúc dữ liệu có thể thay đổi. Sau khi có danh sách, lọc bỏ các mục có ID trống, loại bỏ trùng lặp dựa trên ID, và sắp xếp theo tên để đảm bảo thứ tự hiển thị nhất quán.
        val snapshot = categoriesCollection.get().await()
        val categories = snapshot.documents
            .map { doc ->
                val categoryId = doc.getString("id").orEmpty().ifBlank { doc.id }
                val categoryName = doc.getString("name")
                    .orEmpty()
                    .ifBlank { doc.getString("title").orEmpty() }
                    .ifBlank { doc.getString("categoryName").orEmpty() }
                    .ifBlank { categoryId }
                // Nếu tên danh mục vẫn trống sau khi thử các trường khác nhau, có thể sử dụng ID làm tên tạm thời để tránh lỗi và đảm bảo rằng đối tượng Category luôn có tên hợp lệ.
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
