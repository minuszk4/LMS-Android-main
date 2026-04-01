package com.example.lms2.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.lms2.data.model.Course
import com.example.lms2.data.paging.PageRequest
import com.example.lms2.data.repository.CategoryRepository
import com.example.lms2.data.repository.CourseRepository
import com.example.lms2.data.repository.InstructorRepository
import com.example.lms2.data.repository.RecommendationRepository
import com.example.lms2.util.CloudinaryManager
import com.example.lms2.util.CourseEvent
import com.example.lms2.util.CourseUiState
import com.example.lms2.util.ResultState
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class CourseViewModel(
    private val repository: CourseRepository = CourseRepository(),
    private val categoryRepository: CategoryRepository = CategoryRepository(),
    private val recommendationRepository: RecommendationRepository = RecommendationRepository(),
    private val instructorRepository: InstructorRepository = InstructorRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(CourseUiState())
    val uiState: StateFlow<CourseUiState> = _uiState.asStateFlow()

    private val _event = MutableSharedFlow<CourseEvent>()
    val event = _event.asSharedFlow()

    init {
        getCategories()
    }

    fun getCategories(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingCategories = true) }
            when (val result = categoryRepository.getCategories(forceRefresh = forceRefresh)) {
                is ResultState.Success -> {
                    _uiState.update {
                        it.copy(
                            isLoadingCategories = false,
                            categories = result.data
                        )
                    }
                }
                is ResultState.Error -> {
                    _uiState.update { it.copy(isLoadingCategories = false) }
                    _event.emit(CourseEvent.ShowError(result.message))
                }
                else -> {
                    _uiState.update { it.copy(isLoadingCategories = false) }
                }
            }
        }
    }

    fun getMyCourses(instructorId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            when (val result = repository.getCoursesByInstructor(instructorId)) {
                is ResultState.Success -> {
                    _uiState.update { it.copy(isLoading = false, courses = result.data) }
                }
                is ResultState.Error -> {
                    _uiState.update { it.copy(isLoading = false) }
                    _event.emit(CourseEvent.ShowError(result.message))
                }
                else -> {}
            }
        }
    }

    fun getAllPublishedCourses() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            when (
                val result = repository.getAllPublishedCoursesPage(
                    PageRequest(
                        pageSize = 10,
                        cursor = null,
                        refresh = true,
                        useCache = true
                    )
                )
            ) {
                is ResultState.Success -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            allPublishedCourses = result.data.items,
                            currentCursor = result.data.nextCursor,
                            hasMorePublishedCourses = result.data.hasMore,
                            isLoadingMore = false
                        )
                    }
                }
                is ResultState.Error -> {
                    _uiState.update { it.copy(isLoading = false) }
                    _event.emit(CourseEvent.ShowError(result.message))
                }
                else -> {}
            }
        }
    }

    fun getSuggestedCourses(userId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            if (userId.isBlank()) {
                when (val fallbackResult = repository.getAllPublishedCourses()) {
                    is ResultState.Success -> {
                        val courses = fallbackResult.data.take(5)
                        _uiState.update { it.copy(isLoading = false, suggestedCourses = courses) }
                    }
                    is ResultState.Error -> {
                        _uiState.update { it.copy(isLoading = false) }
                        _event.emit(CourseEvent.ShowError(fallbackResult.message))
                    }
                    else -> {
                        _uiState.update { it.copy(isLoading = false) }
                    }
                }
                return@launch
            }

            when (val result = recommendationRepository.getRecommendedCourses(userId, limit = 5)) {
                is ResultState.Success -> {
                    _uiState.update { it.copy(isLoading = false, suggestedCourses = result.data.take(5)) }
                }
                is ResultState.Error -> {
                    // Fallback to published courses if recommendation fails.
                    when (val fallbackResult = repository.getAllPublishedCourses()) {
                        is ResultState.Success -> {
                            val courses = fallbackResult.data.take(5)
                            _uiState.update { it.copy(isLoading = false, suggestedCourses = courses) }
                        }
                        is ResultState.Error -> {
                            _uiState.update { it.copy(isLoading = false) }
                            _event.emit(CourseEvent.ShowError(result.message))
                        }
                        else -> {
                            _uiState.update { it.copy(isLoading = false) }
                        }
                    }
                }
                else -> {
                    _uiState.update { it.copy(isLoading = false) }
                }
            }
        }
    }

    fun onTitleChange() = _uiState.update { it.copy(titleError = null) }
    fun onDescriptionChange() = _uiState.update { it.copy(descriptionError = null) }
    fun onPriceChange() = _uiState.update { it.copy(priceError = null) }
    fun onCategorySelected() = _uiState.update { it.copy(categoryError = null) }
    fun onDurationChange() = _uiState.update { it.copy(durationError = null) }
    fun onThumbnailSelected() = _uiState.update { it.copy(thumbnailUrlError = null) }
    fun onIntroVideoUrlChange() = _uiState.update { it.copy(introVideoUrlError = null) }

    private fun validate(course: Course, isFree: Boolean, priceStr: String): Boolean {
        var isValid = true
        
        _uiState.update { it.copy(
            titleError = null,
            descriptionError = null,
            priceError = null,
            categoryError = null,
            durationError = null,
            thumbnailUrlError = null,
            introVideoUrlError = null
        )}

        if (course.title.isBlank()) {
            _uiState.update { it.copy(titleError = "Tên khóa học không được để trống") }
            isValid = false
        }
        if (course.description.isBlank()) {
            _uiState.update { it.copy(descriptionError = "Mô tả không được để trống") }
            isValid = false
        }
        if (course.categoryId.isBlank()) {
            _uiState.update { it.copy(categoryError = "Vui lòng chọn danh mục") }
            isValid = false
        }
        if (course.thumbnailUrl.isBlank()) {
            _uiState.update { it.copy(thumbnailUrlError = "Vui lòng chọn ảnh đại diện") }
            isValid = false
        }
        
        // Price Validation
        if (!isFree) {
            val p = priceStr.toDoubleOrNull()
            if (priceStr.isBlank()) {
                _uiState.update { it.copy(priceError = "Vui lòng nhập giá khóa học") }
                isValid = false
            } else if (p == null || p <= 0) {
                _uiState.update { it.copy(priceError = "Giá phải lớn hơn 0") }
                isValid = false
            }
        }

        if (course.duration.isBlank()) {
            _uiState.update { it.copy(durationError = "Thời lượng không được để trống") }
            isValid = false
        }

        val introUrl = course.introVideoUrl.trim()
        if (introUrl.isNotEmpty()) {
            val isHttp = introUrl.startsWith("http", ignoreCase = true)
            val looksLikeYoutube = introUrl.contains("youtube.com", ignoreCase = true) || introUrl.contains("youtu.be", ignoreCase = true)
            val looksLikeVideoFile = introUrl.endsWith(".mp4", ignoreCase = true) || introUrl.endsWith(".m3u8", ignoreCase = true)
            val looksLikeCloudinary = introUrl.contains("res.cloudinary.com", ignoreCase = true)

            val isValidIntro = isHttp && (looksLikeYoutube || looksLikeVideoFile || looksLikeCloudinary)
            if (!isValidIntro) {
                _uiState.update { it.copy(introVideoUrlError = "Link video giới thiệu phải là YouTube hoặc tệp mp4/m3u8 hợp lệ") }
                isValid = false
            }
        }

        return isValid
    }

    fun createCourse(course: Course, isFree: Boolean, priceStr: String) {
        if (!validate(course, isFree, priceStr)) return
        
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            when (val instructorResult = instructorRepository.getInstructorById(course.instructorId)) {
                is ResultState.Success -> {
                    if (!instructorRepository.hasValidBankInfo(instructorResult.data)) {
                        _uiState.update { it.copy(isLoading = false) }
                        _event.emit(
                            CourseEvent.ShowError(
                                "Giảng viên chưa cập nhật tài khoản ngân hàng. Vui lòng vào Hồ sơ giảng viên để cập nhật trước khi tạo khóa học."
                            )
                        )
                        return@launch
                    }
                }

                is ResultState.Error -> {
                    _uiState.update { it.copy(isLoading = false) }
                    _event.emit(
                        CourseEvent.ShowError(
                            "Không kiểm tra được thông tin tài khoản ngân hàng của giảng viên: ${instructorResult.message}"
                        )
                    )
                    return@launch
                }

                else -> {
                    _uiState.update { it.copy(isLoading = false) }
                    return@launch
                }
            }
            
            var finalThumbnailUrl = course.thumbnailUrl
            var finalPublicId = course.thumbnailPublicId

            if (course.thumbnailUrl.startsWith("content://")) {
                when (val uploadResult = CloudinaryManager.uploadImage(Uri.parse(course.thumbnailUrl))) {
                    is ResultState.Success -> {
                        finalThumbnailUrl = uploadResult.data.first
                        finalPublicId = uploadResult.data.second
                    }
                    is ResultState.Error -> {
                        _uiState.update { it.copy(isLoading = false) }
                        _event.emit(CourseEvent.ShowError(uploadResult.message))
                        return@launch
                    }
                    else -> {}
                }
            }

            val newCourse = course.copy(
                thumbnailUrl = finalThumbnailUrl,
                thumbnailPublicId = finalPublicId
            )

            when (val result = repository.createCourse(newCourse)) {
                is ResultState.Success -> {
                    _event.emit(CourseEvent.SaveSuccess)
                    getMyCourses(course.instructorId)
                }
                is ResultState.Error -> {
                    _uiState.update { it.copy(isLoading = false) }
                    _event.emit(CourseEvent.ShowError(result.message))
                }
                else -> {}
            }
        }
    }

    fun updateCourse(course: Course, isFree: Boolean, priceStr: String) {
        if (!validate(course, isFree, priceStr)) return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            var finalThumbnailUrl = course.thumbnailUrl
            var finalPublicId = course.thumbnailPublicId

            if (course.thumbnailUrl.startsWith("content://")) {
                when (val uploadResult = CloudinaryManager.uploadImage(Uri.parse(course.thumbnailUrl))) {
                    is ResultState.Success -> {
                        finalThumbnailUrl = uploadResult.data.first
                        finalPublicId = uploadResult.data.second
                    }
                    is ResultState.Error -> {
                        _uiState.update { it.copy(isLoading = false) }
                        _event.emit(CourseEvent.ShowError(uploadResult.message))
                        return@launch
                    }
                    else -> {}
                }
            }

            val updatedCourse = course.copy(
                thumbnailUrl = finalThumbnailUrl,
                thumbnailPublicId = finalPublicId
            )

            when (val result = repository.updateCourse(updatedCourse)) {
                is ResultState.Success -> {
                    _event.emit(CourseEvent.SaveSuccess)
                    getMyCourses(course.instructorId)
                }
                is ResultState.Error -> {
                    _uiState.update { it.copy(isLoading = false) }
                    _event.emit(CourseEvent.ShowError(result.message))
                }
                else -> {}
            }
        }
    }

    fun deleteCourse(courseId: String, instructorId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            when (val result = repository.deleteCourse(courseId)) {
                is ResultState.Success -> getMyCourses(instructorId)
                is ResultState.Error -> {
                    _uiState.update { it.copy(isLoading = false) }
                    _event.emit(CourseEvent.ShowError(result.message))
                }
                else -> {}
            }
        }
    }

    fun togglePublishStatus(courseId: String, instructorId: String) {
        viewModelScope.launch {
            val course = _uiState.value.courses.find { it.id == courseId } ?: return@launch
            _uiState.update { it.copy(isLoading = true) }
            when (val result = repository.updatePublishStatus(courseId, !course.isPublished)) {
                is ResultState.Success -> getMyCourses(instructorId)
                is ResultState.Error -> {
                    _uiState.update { it.copy(isLoading = false) }
                    _event.emit(CourseEvent.ShowError(result.message))
                }
                else -> {}
            }
        }
    }

    fun onCourseSelected(course: Course?) {
        _uiState.update { 
            it.copy(
                currentCourse = course,
                titleError = null,
                descriptionError = null,
                priceError = null,
                categoryError = null,
                durationError = null,
                thumbnailUrlError = null,
                introVideoUrlError = null,
                errorMessage = null
            )
        }
    }

    fun loadMorePublishedCourses() {
        val currentState = _uiState.value
        if (currentState.isLoading || currentState.isLoadingMore || !currentState.hasMorePublishedCourses) return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingMore = true) }
            val pageRequest = PageRequest(
                pageSize = 10,
                cursor = currentState.currentCursor
            )
            when (val result = repository.getAllPublishedCoursesPage(pageRequest)) {
                is ResultState.Success -> {
                    _uiState.update {
                        val mergedCourses = (it.allPublishedCourses + result.data.items)
                            .distinctBy { course -> course.id }
                        it.copy(
                            isLoadingMore = false,
                            allPublishedCourses = mergedCourses,
                            currentCursor = result.data.nextCursor,
                            hasMorePublishedCourses = result.data.hasMore
                        )
                    }
                }
                is ResultState.Error -> {
                    _uiState.update { it.copy(isLoadingMore = false) }
                    _event.emit(CourseEvent.ShowError(result.message))
                }
                else -> {
                    _uiState.update { it.copy(isLoadingMore = false) }
                }
            }
        }
    }
}
