package com.example.lms2.util

import com.example.lms2.data.model.Cart
import com.example.lms2.data.model.CartItem

/**
 * Mô tả trạng thái giao diện của luồng Cart.
 * Đối tượng này gom dữ liệu hiển thị, cờ tải, lỗi kiểm tra và các trạng thái tạm mà Compose cần quan sát.
 * Việc gom toàn bộ state vào một nơi giúp màn hình render nhất quán và dễ kiểm thử hơn.
 */

data class CartUiState(
    val isLoading: Boolean = false,
    val hasLoadedOnce: Boolean = false,
    val cart: Cart? = null,
    val items: List<CartItem> = emptyList(),
    val selectedCourseIds: Set<String> = emptySet(),
    // Pagination
    val isLoadingMore: Boolean = false,
    val currentCursor: String? = null,
    val hasMoreItems: Boolean = true
) {
    val selectedItems: List<CartItem>
        get() = items.filter { it.courseId in selectedCourseIds }

    val selectedItemCount: Int
        get() = selectedItems.size

    val selectedTotalAmount: Double
        get() = selectedItems.sumOf { it.coursePrice }
}

