package com.example.lms.util

import com.example.lms.data.model.Category
import com.example.lms.data.model.Course
import com.example.lms.data.model.CurriculumItem
import com.example.lms.data.model.Progress
import com.example.lms.data.model.Review
import com.example.lms.data.model.User

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