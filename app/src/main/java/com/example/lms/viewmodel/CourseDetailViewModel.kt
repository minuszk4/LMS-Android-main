package com.example.lms.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.lms.data.model.Review
import com.example.lms.data.repository.CartRepository
import com.example.lms.data.repository.CategoryRepository
import com.example.lms.data.repository.CourseRepository
import com.example.lms.data.repository.CurriculumRepository
import com.example.lms.data.repository.EnrollmentRepository
import com.example.lms.data.repository.AuthRepository
import com.example.lms.data.repository.ProgressRepository
import com.example.lms.data.repository.ReviewRepository
import com.example.lms.util.CourseDetailEvent
import com.example.lms.util.CourseDetailUiState
import com.example.lms.util.ResultState
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class CourseDetailViewModel(
    private val courseRepository: CourseRepository = CourseRepository(),
    private val cartRepository: CartRepository = CartRepository(),
    private val enrollmentRepository: EnrollmentRepository = EnrollmentRepository(),
    private val curriculumRepository: CurriculumRepository = CurriculumRepository(),
    private val categoryRepository: CategoryRepository = CategoryRepository(),
    private val authRepository: AuthRepository = AuthRepository(),
    private val progressRepository: ProgressRepository = ProgressRepository(),
    private val reviewRepository: ReviewRepository = ReviewRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(CourseDetailUiState())
    val uiState: StateFlow<CourseDetailUiState> = _uiState.asStateFlow()

    private val _event = MutableSharedFlow<CourseDetailEvent>()
    val event = _event.asSharedFlow()

    fun loadCourseDetail(courseId: String, userId: String) {
        if (_uiState.value.course?.id == courseId) {
            refreshProgressOnly(courseId, userId)
            refreshCartStatus(userId, courseId)
            return
        }

        viewModelScope.launch {
            _uiState.value = CourseDetailUiState(isLoading = true)

            val courseDeferred = async { courseRepository.getCourseById(courseId) }
            val enrolledDeferred = async { enrollmentRepository.isEnrolled(userId, courseId) }
            val curriculumDeferred = async { curriculumRepository.getCurriculum(courseId) }
            val categoriesDeferred = async { categoryRepository.getCategories() }
            val progressDeferred = async { progressRepository.getProgress(userId, courseId) }
            val isInCartDeferred = async { cartRepository.isCourseInCart(userId, courseId) }
            val reviewsDeferred = async { reviewRepository.getCourseReviews(courseId) }
            val myReviewDeferred = async { reviewRepository.getMyReview(courseId, userId) }

            val courseResult = courseDeferred.await()
            val enrolledResult = enrolledDeferred.await()
            val curriculumResult = curriculumDeferred.await()
            val categoriesResult = categoriesDeferred.await()
            val progressResult = progressDeferred.await()
            val isInCartResult = isInCartDeferred.await()
            val reviewsResult = reviewsDeferred.await()
            val myReviewResult = myReviewDeferred.await()

            if (courseResult is ResultState.Success) {
                val instructorId = courseResult.data.instructorId
                val instructorResult = authRepository.getUserDetails(instructorId)
                val myReview = (myReviewResult as? ResultState.Success)?.data
                val orderedReviews = sortReviewsForUser(
                    userId = userId,
                    reviews = (reviewsResult as? ResultState.Success)?.data ?: emptyList()
                )
                
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        course = courseResult.data,
                        isEnrolled = (enrolledResult as? ResultState.Success)?.data ?: false,
                        curriculum = (curriculumResult as? ResultState.Success)?.data ?: emptyList(),
                        categories = (categoriesResult as? ResultState.Success)?.data ?: emptyList(),
                        instructor = (instructorResult as? ResultState.Success)?.data,
                        progress = (progressResult as? ResultState.Success)?.data,
                        isInCart = (isInCartResult as? ResultState.Success)?.data ?: false,
                        reviews = orderedReviews,
                        myReview = myReview,
                        reviewDraftRating = myReview?.rating ?: 5,
                        reviewDraftContent = myReview?.content.orEmpty(),
                        isEditingReview = false,
                        isLoadingReviews = false
                    )
                }
            } else if (courseResult is ResultState.Error) {
                _uiState.update { it.copy(isLoading = false) }
                _event.emit(CourseDetailEvent.ShowError(courseResult.message))
            }
        }
    }

    fun refreshReviews(courseId: String, userId: String, showLoading: Boolean = false) {
        viewModelScope.launch {
            if (showLoading) {
                _uiState.update { it.copy(isLoadingReviews = true) }
            }

            val reviewsDeferred = async { reviewRepository.getCourseReviews(courseId) }
            val myReviewDeferred = async { reviewRepository.getMyReview(courseId, userId) }

            val reviewsResult = reviewsDeferred.await()
            val myReviewResult = myReviewDeferred.await()

            val reviews = (reviewsResult as? ResultState.Success)?.data
            val myReview = (myReviewResult as? ResultState.Success)?.data

            if (reviews != null) {
                val orderedReviews = sortReviewsForUser(userId = userId, reviews = reviews)
                _uiState.update {
                    it.copy(
                        reviews = orderedReviews,
                        myReview = myReview,
                        reviewDraftRating = if (it.isEditingReview) it.reviewDraftRating else (myReview?.rating ?: it.reviewDraftRating),
                        reviewDraftContent = if (it.isEditingReview) it.reviewDraftContent else myReview?.content.orEmpty(),
                        isLoadingReviews = false
                    )
                }
            } else {
                _uiState.update { it.copy(isLoadingReviews = false) }
                val message = (reviewsResult as? ResultState.Error)?.message
                    ?: (myReviewResult as? ResultState.Error)?.message
                    ?: "Tải đánh giá thất bại"
                _event.emit(CourseDetailEvent.ShowError(message))
            }
        }
    }

    fun refreshProgressOnly(courseId: String, userId: String) {
        viewModelScope.launch {
            val result = progressRepository.getProgress(userId, courseId)
            if (result is ResultState.Success) {
                _uiState.update { it.copy(progress = result.data) }
            }
        }
    }

    fun refreshCartStatus(userId: String, courseId: String) {
        if (userId.isBlank() || courseId.isBlank()) return

        viewModelScope.launch {
            when (val result = cartRepository.isCourseInCart(userId, courseId)) {
                is ResultState.Success -> {
                    _uiState.update { it.copy(isInCart = result.data) }
                }
                is ResultState.Error -> {
                    _event.emit(CourseDetailEvent.ShowError(result.message))
                }
                else -> Unit
            }
        }
    }

    fun toggleCourseInCart(userId: String, courseId: String) {
        if (userId.isBlank() || courseId.isBlank()) return
        val state = _uiState.value
        if (state.isTogglingCart || state.isEnrolled) return

        viewModelScope.launch {
            _uiState.update { it.copy(isTogglingCart = true) }

            val result = if (_uiState.value.isInCart) {
                cartRepository.removeCourseFromCart(userId, courseId)
            } else {
                cartRepository.addCourseToCart(userId, courseId)
            }

            when (result) {
                is ResultState.Success -> {
                    val isNowInCart = !_uiState.value.isInCart
                    _uiState.update {
                        it.copy(
                            isInCart = isNowInCart,
                            isTogglingCart = false
                        )
                    }
                    val message = if (isNowInCart) {
                        "Đã thêm khóa học vào giỏ hàng"
                    } else {
                        "Đã xóa khóa học khỏi giỏ hàng"
                    }
                    _event.emit(CourseDetailEvent.ShowMessage(message))
                }

                is ResultState.Error -> {
                    _uiState.update { it.copy(isTogglingCart = false) }
                    _event.emit(CourseDetailEvent.ShowError(result.message))
                }

                else -> {
                    _uiState.update { it.copy(isTogglingCart = false) }
                }
            }
        }
    }

    fun buyNowCourse(userId: String, courseId: String) {
        if (userId.isBlank() || courseId.isBlank()) return

        val currentState = _uiState.value
        if (currentState.isEnrolled || currentState.isBuyingNow || currentState.isTogglingCart) return

        val course = currentState.course ?: return
        if (course.price == 0.0) {
            enrollCourse(userId, courseId)
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isBuyingNow = true) }
            _event.emit(CourseDetailEvent.NavigateToPayment(listOf(courseId)))
            _uiState.update { it.copy(isBuyingNow = false) }
        }
    }

    fun onReviewRatingChanged(rating: Int) {
        _uiState.update { it.copy(reviewDraftRating = rating.coerceIn(1, 5)) }
    }

    fun onReviewContentChanged(content: String) {
        _uiState.update { it.copy(reviewDraftContent = content) }
    }

    fun startEditMyReview() {
        val myReview = _uiState.value.myReview ?: return
        _uiState.update {
            it.copy(
                reviewDraftRating = myReview.rating,
                reviewDraftContent = myReview.content,
                isEditingReview = true
            )
        }
    }

    fun cancelEditReview() {
        val myReview = _uiState.value.myReview
        _uiState.update {
            it.copy(
                reviewDraftRating = myReview?.rating ?: 5,
                reviewDraftContent = myReview?.content.orEmpty(),
                isEditingReview = false
            )
        }
    }

    fun submitReview(courseId: String, userId: String) {
        if (_uiState.value.isSubmittingReview || _uiState.value.isDeletingReview) return
        if (!_uiState.value.isEnrolled) {
            viewModelScope.launch {
                _event.emit(CourseDetailEvent.ShowError("Bạn cần đăng ký khóa học trước khi đánh giá"))
            }
            return
        }

        val rating = _uiState.value.reviewDraftRating
        val content = _uiState.value.reviewDraftContent.trim()

        if (content.isBlank()) {
            viewModelScope.launch {
                _event.emit(CourseDetailEvent.ShowError("Nội dung đánh giá không được để trống"))
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSubmittingReview = true) }

            val userResult = authRepository.getUserDetails(userId)
            if (userResult !is ResultState.Success) {
                _uiState.update { it.copy(isSubmittingReview = false) }
                val message = (userResult as? ResultState.Error)?.message ?: "Không lấy được thông tin người dùng"
                _event.emit(CourseDetailEvent.ShowError(message))
                return@launch
            }

            val submitResult = reviewRepository.upsertReview(
                courseId = courseId,
                userId = userId,
                userName = userResult.data.fullName,
                userAvatarUrl = userResult.data.avatarUrl.orEmpty(),
                rating = rating,
                content = content
            )

            when (submitResult) {
                is ResultState.Success -> {
                    _uiState.update {
                        it.copy(
                            myReview = submitResult.data,
                            reviewDraftRating = submitResult.data.rating,
                            reviewDraftContent = submitResult.data.content,
                            isEditingReview = false,
                            isSubmittingReview = false,
                            isDeletingReview = false
                        )
                    }

                    refreshReviews(courseId, userId)

                    when (val courseResult = courseRepository.getCourseById(courseId)) {
                        is ResultState.Success -> _uiState.update { it.copy(course = courseResult.data) }
                        is ResultState.Error -> _event.emit(CourseDetailEvent.ShowError(courseResult.message))
                        else -> Unit
                    }
                }
                is ResultState.Error -> {
                    _uiState.update { it.copy(isSubmittingReview = false) }
                    _event.emit(CourseDetailEvent.ShowError(submitResult.message))
                }
                else -> {
                    _uiState.update { it.copy(isSubmittingReview = false) }
                }
            }
        }
    }

    fun deleteMyReview(courseId: String, userId: String) {
        if (_uiState.value.isDeletingReview || _uiState.value.isSubmittingReview) return
        if (_uiState.value.myReview == null) return

        viewModelScope.launch {
            _uiState.update { it.copy(isDeletingReview = true) }

            when (val result = reviewRepository.deleteReview(courseId = courseId, userId = userId)) {
                is ResultState.Success -> {
                    _uiState.update {
                        it.copy(
                            myReview = null,
                            reviewDraftRating = 5,
                            reviewDraftContent = "",
                            isEditingReview = false,
                            isDeletingReview = false
                        )
                    }

                    refreshReviews(courseId, userId)

                    when (val courseResult = courseRepository.getCourseById(courseId)) {
                        is ResultState.Success -> _uiState.update { it.copy(course = courseResult.data) }
                        is ResultState.Error -> _event.emit(CourseDetailEvent.ShowError(courseResult.message))
                        else -> Unit
                    }
                }

                is ResultState.Error -> {
                    _uiState.update { it.copy(isDeletingReview = false) }
                    _event.emit(CourseDetailEvent.ShowError(result.message))
                }

                else -> _uiState.update { it.copy(isDeletingReview = false) }
            }
        }
    }

    fun enrollCourse(userId: String, courseId: String) {
        if (_uiState.value.isEnrolling) return

        viewModelScope.launch {
            _uiState.update { it.copy(isEnrolling = true) }

            when (val enrollResult = enrollmentRepository.enrollCourse(userId, courseId)) {
                is ResultState.Success -> {
                    launch {
                        courseRepository.incrementEnrollment(courseId)
                    }
                    _uiState.update {
                        it.copy(isEnrolling = false, isEnrolled = true)
                    }
                    _event.emit(CourseDetailEvent.EnrollSuccess)
                }
                is ResultState.Error -> {
                    _uiState.update { it.copy(isEnrolling = false) }
                    _event.emit(CourseDetailEvent.ShowError(enrollResult.message))
                }
                else -> {}
            }
        }
    }

    private fun sortReviewsForUser(userId: String, reviews: List<Review>): List<Review> {
        return reviews.sortedWith(
            compareByDescending<Review> { it.userId == userId }
                .thenByDescending { it.updatedAt }
        )
    }
}