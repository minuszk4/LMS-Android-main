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

/**
 * Điều phối trạng thái giao diện trong CourseViewModel.
 * File này kết nối màn hình Compose với repository, cập nhật `uiState` và phát event một lần cho các thao tác điều hướng hoặc thông báo.
 * Đây là nơi tập trung phần lớn logic trình bày và điều phối nghiệp vụ ở phía ứng dụng Android.
 */

class CourseViewModel(
    private val repository: CourseRepository = CourseRepository(),
    private val categoryRepository: CategoryRepository = CategoryRepository(),
    private val recommendationRepository: RecommendationRepository = RecommendationRepository(),
    private val instructorRepository: InstructorRepository = InstructorRepository()
) : ViewModel() {

    // State trung tâm cho cả luồng khám phá khóa học, form giảng viên và recommendation.
    private val _uiState = MutableStateFlow(CourseUiState())
    val uiState: StateFlow<CourseUiState> = _uiState.asStateFlow()

    // Event một lần dùng cho snackbar, điều hướng hoặc thông báo lỗi.
    private val _event = MutableSharedFlow<CourseEvent>()
    val event = _event.asSharedFlow()

    // Chữ ký dùng để tránh ghi lặp impression recommendation khi Compose recomposition.
    private var lastSuggestedImpressionSignature: String? = null

    init {
        // Tải sẵn categories để form tạo/sửa course có dữ liệu ngay khi mở.
        getCategories()
    }

    /**
     * Tải danh sách danh mục khóa học.
     *
     * Hàm này được gọi ngay khi ViewModel khởi tạo và có thể gọi lại khi người dùng
     * cần làm mới dữ liệu. Trạng thái tải được ghi vào `uiState` để màn hình hiển thị
     * loading hoặc thông báo lỗi tương ứng.
     */
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

    /**
     * Lấy toàn bộ khóa học thuộc về một giảng viên cụ thể.
     *
     * Dữ liệu này phục vụ màn hình quản lý khóa học của giảng viên sau khi đăng nhập.
     */
    fun getMyCourses(instructorId: String) {
        viewModelScope.launch {
            // This list is also reloaded after create/update/delete/publish actions
            // so the instructor dashboard always reflects server state.
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

    /**
     * Tải trang đầu tiên của danh sách khóa học đã phát hành.
     *
     * Hàm sử dụng phân trang và cache để tối ưu màn hình khám phá khóa học dành cho học viên.
     */
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
                    // Replace the current explore feed with a fresh first page
                    // and store cursor metadata for subsequent pagination calls.
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

    /**
     * Tải danh sách khóa học gợi ý cho người dùng ở trang chủ.
     *
     * Nếu thiếu `userId` hoặc backend recommendation gặp lỗi, hàm sẽ fallback sang
     * danh sách khóa học đã phát hành để tránh làm rỗng khu vực gợi ý trên giao diện.
     */
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
                    val courses = result.data.take(5)
                    _uiState.update { it.copy(isLoading = false, suggestedCourses = courses) }
                    // Log impression only after these recommendations are really shown on screen.
                    logSuggestedImpressionsIfNeeded(userId, courses)
                }
                is ResultState.Error -> {
                    // Fallback to published courses if recommendation fails.
                    when (val fallbackResult = repository.getAllPublishedCourses()) {
                        is ResultState.Success -> {
                            val courses = fallbackResult.data.take(5)
                            _uiState.update { it.copy(isLoading = false, suggestedCourses = courses) }
                            logSuggestedImpressionsIfNeeded(userId, courses)
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

    /**
     * Ghi nhận việc người dùng nhấn vào một khóa học trong cụm gợi ý.
     *
     * Sự kiện này được gửi sang repository recommendation để phục vụ huấn luyện hoặc
     * đánh giá chất lượng mô hình gợi ý sau này.
     */
    fun onSuggestedCourseClicked(userId: String, courseId: String) {
        if (userId.isBlank() || courseId.isBlank()) return
        viewModelScope.launch {
            recommendationRepository.logRecommendationFeedback(
                userId = userId,
                courseId = courseId,
                eventType = "CLICK",
                source = "home_recommendation"
            )
        }
    }

    /**
     * Chỉ log impression khi tập khóa học gợi ý thực sự thay đổi.
     *
     * Cơ chế chữ ký giúp tránh ghi trùng dữ liệu analytics mỗi lần Compose recomposition.
     */
    private fun logSuggestedImpressionsIfNeeded(userId: String, courses: List<Course>) {
        if (userId.isBlank() || courses.isEmpty()) return
        val signature = buildString {
            append(userId)
            append("|")
            append(courses.joinToString(",") { it.id })
        }
        if (lastSuggestedImpressionSignature == signature) return
        lastSuggestedImpressionSignature = signature

        viewModelScope.launch {
            recommendationRepository.logRecommendationImpressions(
                userId = userId,
                courseIds = courses.map { it.id },
                source = "home_recommendation"
            )
        }
    }

    /**
     * Xóa lỗi validation của trường tiêu đề khi giảng viên chỉnh sửa input.
     */

    fun onTitleChange() = _uiState.update { it.copy(titleError = null) }
    /**
     * Xóa lỗi validation của trường mô tả khi giảng viên chỉnh sửa input.
     */

    fun onDescriptionChange() = _uiState.update { it.copy(descriptionError = null) }
    /**
     * Xóa lỗi validation của trường giá khi giảng viên chỉnh sửa input.
     */

    fun onPriceChange() = _uiState.update { it.copy(priceError = null) }
    /**
     * Xóa lỗi validation của trường danh mục khi giảng viên chọn danh mục.
     */

    fun onCategorySelected() = _uiState.update { it.copy(categoryError = null) }
    /**
     * Xóa lỗi validation của trường thời lượng khi giảng viên chỉnh sửa input.
     */

    fun onDurationChange() = _uiState.update { it.copy(durationError = null) }
    /**
     * Xóa lỗi validation của thumbnail sau khi giảng viên chọn ảnh đại diện khóa học.
     */

    fun onThumbnailSelected() = _uiState.update { it.copy(thumbnailUrlError = null) }
    /**
     * Xóa lỗi validation của video giới thiệu khi giảng viên chỉnh sửa URL.
     */

    fun onIntroVideoUrlChange() = _uiState.update { it.copy(introVideoUrlError = null) }

    /**
     * Kiểm tra hợp lệ dữ liệu đầu vào trước khi tạo hoặc cập nhật khóa học.
     *
     * Hàm này không chỉ trả về đúng hoặc sai mà còn ghi lỗi chi tiết vào `uiState`
     * để form có thể hiển thị thông báo ngay bên cạnh trường nhập liệu tương ứng.
     */
    private fun validate(course: Course, isFree: Boolean, priceStr: String): Boolean {
        var isValid = true

        // Clear old field errors first so the form only shows messages
        // produced by the current validation pass.
        _uiState.update { it.copy(
            titleError = null,
            descriptionError = null,
            priceError = null,
            categoryError = null,
            durationError = null,
            thumbnailUrlError = null,
            introVideoUrlError = null
        )}

        // Validate the required fields before create/update is allowed to continue.
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

        // Intro video is optional, but if present it must be a supported URL format
        // that the app can preview or play reliably.
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

    /**
     * Tạo mới khóa học sau khi đã vượt qua bước kiểm tra dữ liệu.
     *
     * Pipeline chính gồm: kiểm tra thông tin tài khoản ngân hàng của giảng viên,
     * tải ảnh thumbnail lên Cloudinary nếu người dùng vừa chọn ảnh cục bộ, sau đó
     * gửi dữ liệu hoàn chỉnh xuống repository để lưu trên Firestore.
     */
    fun createCourse(course: Course, isFree: Boolean, priceStr: String) {
        if (!validate(course, isFree, priceStr)) return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            // A course should not be created if the instructor has not completed
            // bank information required later for payout processing.
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

            // When the thumbnail comes from the local device picker, upload it first
            // so Firestore stores a durable public URL instead of a content URI.
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
                    // Emit success and reload the instructor list from source of truth.
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

    /**
     * Cập nhật khóa học hiện có.
     *
     * Nếu thumbnail là URI cục bộ mới chọn từ thiết bị, hàm sẽ upload lại trước khi
     * gọi repository cập nhật để tránh lưu trực tiếp `content://` vào CSDL.
     */
    fun updateCourse(course: Course, isFree: Boolean, priceStr: String) {
        if (!validate(course, isFree, priceStr)) return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            var finalThumbnailUrl = course.thumbnailUrl
            var finalPublicId = course.thumbnailPublicId

            // Re-upload only when the current thumbnail is a newly selected local image.
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
                    // Refresh the management list so the latest persisted values are shown.
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

    /**
     * Xóa khóa học và làm mới danh sách quản lý của giảng viên.
     *
     * The ViewModel reloads from repository instead of removing the item locally
     * so the UI stays aligned with the actual persisted state.
     */
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

    /**
     * Đảo trạng thái phát hành của khóa học giữa nháp và công khai.
     *
     * The current state is read from `uiState.courses`, then inverted and sent
     * to the repository. A reload follows so publish flags and timestamps stay in sync.
     */
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

    /**
     * Gán khóa học hiện tại vào trạng thái màn hình form.
     *
     * Đồng thời xóa các lỗi cũ để tránh giữ lại thông báo validation của lần thao tác trước.
     */
    fun onCourseSelected(course: Course?) {
        _uiState.update { 
            it.copy(
                currentCourse = course,
                // Reset old validation errors when switching to another course
                // or when opening the form for a new create flow.
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

    /**
     * Tải thêm các khóa học đã phát hành ở trang khám phá.
     *
     * Hàm dừng sớm nếu đang tải hoặc đã hết dữ liệu nhằm tránh gửi nhiều truy vấn trùng nhau.
     */
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
                        // Merge the next page with the existing list and de-duplicate by id
                        // in case cache or cursor boundaries return overlapping items.
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
