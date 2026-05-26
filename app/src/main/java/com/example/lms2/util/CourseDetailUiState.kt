package com.example.lms2.util

import com.example.lms2.data.model.Category
import com.example.lms2.data.model.Course
import com.example.lms2.data.model.CurriculumItem
import com.example.lms2.data.model.Progress
import com.example.lms2.data.model.Review
import com.example.lms2.data.model.User

/**
 * Mô tả trạng thái giao diện của luồng CourseDetail.
 * Đối tượng này gom dữ liệu hiển thị, cờ tải, lỗi kiểm tra và các trạng thái tạm mà Compose cần quan sát.
 * Việc gom toàn bộ state vào một nơi giúp màn hình render nhất quán và dễ kiểm thử hơn.
 */

data class CourseDetailUiState(
    val isLoading: Boolean = false,
    val course: Course? = null,
    val curriculum: List<CurriculumItem> = emptyList(),
    val isEnrolled: Boolean = false,
    val isEnrolling: Boolean = false,
    val isInCart: Boolean = false,
    val isTogglingCart: Boolean = false,
    val isBuyingNow: Boolean = false,
    val categories: List<Category> = emptyList(),
    val instructor: User? = null,
    val progress: Progress? = null,

    // Review tab
    val reviews: List<Review> = emptyList(),
    val myReview: Review? = null,
    val isLoadingReviews: Boolean = false,
    val isSubmittingReview: Boolean = false,
    val isDeletingReview: Boolean = false,
    val reviewDraftRating: Int = 5,
    val reviewDraftContent: String = "",
    val isEditingReview: Boolean = false
)
