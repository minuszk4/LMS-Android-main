package com.example.lms2.util

import com.example.lms2.data.model.Category
import com.example.lms2.data.model.Course

/**
 * Mô tả trạng thái giao diện của luồng Course.
 * Đối tượng này gom dữ liệu hiển thị, cờ tải, lỗi kiểm tra và các trạng thái tạm mà Compose cần quan sát.
 * Việc gom toàn bộ state vào một nơi giúp màn hình render nhất quán và dễ kiểm thử hơn.
 */

data class CourseUiState(
    val isLoading: Boolean = false,
    val isLoadingCategories: Boolean = false,
    val courses: List<Course> = emptyList(),
    val allPublishedCourses: List<Course> = emptyList(),
    val suggestedCourses: List<Course> = emptyList(),
    val categories: List<Category> = emptyList(),
    val errorMessage: String? = null,
    val isSuccess: Boolean = false,
    val currentCourse: Course? = null,
    
    // Pagination for published courses
    val isLoadingMore: Boolean = false,
    val currentCursor: String? = null,
    val hasMorePublishedCourses: Boolean = true,
    
    // Validation Errors
    val titleError: String? = null,
    val descriptionError: String? = null,
    val priceError: String? = null,
    val categoryError: String? = null,
    val durationError: String? = null,
    val thumbnailUrlError: String? = null,
    val introVideoUrlError: String? = null
)
