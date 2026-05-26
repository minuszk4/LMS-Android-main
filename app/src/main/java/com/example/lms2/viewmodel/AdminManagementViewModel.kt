package com.example.lms2.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.lms2.data.model.Category
import com.example.lms2.data.model.Course
import com.example.lms2.data.model.Enrollment
import com.example.lms2.data.model.InstructorApplicationStatus
import com.example.lms2.data.model.Progress
import com.example.lms2.data.model.User
import com.example.lms2.data.repository.AuthRepository
import com.example.lms2.data.repository.CategoryRepository
import com.example.lms2.data.repository.CourseRepository
import com.example.lms2.data.repository.EnrollmentRepository
import com.example.lms2.data.repository.ProgressRepository
import com.example.lms2.util.ResultState
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Điều phối trạng thái giao diện trong AdminManagementViewModel.
 * File này kết nối màn hình Compose với repository, cập nhật `uiState` và phát event một lần cho các thao tác điều hướng hoặc thông báo.
 * Đây là nơi tập trung phần lớn logic trình bày và điều phối nghiệp vụ ở phía ứng dụng Android.
 */

data class AdminDashboardSummary(
    val totalUsers: Int = 0,
    val activeUsers: Int = 0,
    val pendingInstructorRequests: Int = 0,
    val totalCourses: Int = 0,
    val unpublishedCourses: Int = 0
)

/**
 * Khai báo AdminManagementUiState trong file này để phục vụ một trách nhiệm cụ thể của hệ thống.
 */

data class AdminManagementUiState(
    val isLoadingSummary: Boolean = false,
    val isLoadingUsers: Boolean = false,
    val isLoadingCourses: Boolean = false,
    val isLoadingLearningData: Boolean = false,
    val isLoadingCategories: Boolean = false,
    val isProcessing: Boolean = false,
    val summary: AdminDashboardSummary = AdminDashboardSummary(),
    val users: List<User> = emptyList(),
    val courses: List<Course> = emptyList(),
    val enrollments: List<Enrollment> = emptyList(),
    val progresses: List<Progress> = emptyList(),
    val categories: List<Category> = emptyList()
)

/**
 * Khai báo AdminManagementEvent trong file này để phục vụ một trách nhiệm cụ thể của hệ thống.
 */

sealed class AdminManagementEvent {
    data class ShowError(val message: String) : AdminManagementEvent()
    data class ShowSuccess(val message: String) : AdminManagementEvent()
}

/**
 * Khai báo AdminManagementViewModel trong file này để phục vụ một trách nhiệm cụ thể của hệ thống.
 */

class AdminManagementViewModel(
    private val authRepository: AuthRepository = AuthRepository(),
    private val courseRepository: CourseRepository = CourseRepository(),
    private val categoryRepository: CategoryRepository = CategoryRepository(),
    private val enrollmentRepository: EnrollmentRepository = EnrollmentRepository(),
    private val progressRepository: ProgressRepository = ProgressRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(AdminManagementUiState())
    val uiState = _uiState.asStateFlow()

    private val _event = MutableSharedFlow<AdminManagementEvent>()
    val event = _event.asSharedFlow()

    /**
     * Tải dữ liệu và cập nhật trạng thái hiển thị liên quan.
     * Hàm này chủ yếu cập nhật `uiState`, gọi repository và phát event cho giao diện khi cần.
     */

    fun loadSummary() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingSummary = true)

            val usersResult = authRepository.getAllUsers()
            val coursesResult = courseRepository.getAllCoursesForAdmin()

            if (usersResult is ResultState.Error) {
                _uiState.value = _uiState.value.copy(isLoadingSummary = false)
                _event.emit(AdminManagementEvent.ShowError(usersResult.message))
                return@launch
            }

            if (coursesResult is ResultState.Error) {
                _uiState.value = _uiState.value.copy(isLoadingSummary = false)
                _event.emit(AdminManagementEvent.ShowError(coursesResult.message))
                return@launch
            }

            val users = (usersResult as? ResultState.Success)?.data.orEmpty()
            val courses = (coursesResult as? ResultState.Success)?.data.orEmpty()

            _uiState.value = _uiState.value.copy(
                isLoadingSummary = false,
                summary = AdminDashboardSummary(
                    totalUsers = users.size,
                    activeUsers = users.count { it.isActive },
                    pendingInstructorRequests = users.count { it.instructorRequestStatus == InstructorApplicationStatus.PENDING },
                    totalCourses = courses.size,
                    unpublishedCourses = courses.count { !it.isPublished }
                )
            )
        }
    }

    /**
     * Tải dữ liệu và cập nhật trạng thái hiển thị liên quan.
     * Hàm này chủ yếu cập nhật `uiState`, gọi repository và phát event cho giao diện khi cần.
     */

    fun loadUsers() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingUsers = true)
            when (val result = authRepository.getAllUsers()) {
                is ResultState.Success -> {
                    _uiState.value = _uiState.value.copy(isLoadingUsers = false, users = result.data)
                }

                is ResultState.Error -> {
                    _uiState.value = _uiState.value.copy(isLoadingUsers = false)
                    _event.emit(AdminManagementEvent.ShowError(result.message))
                }

                ResultState.Loading -> Unit
            }
        }
    }

    /**
     * Tải dữ liệu và cập nhật trạng thái hiển thị liên quan.
     * Hàm này chủ yếu cập nhật `uiState`, gọi repository và phát event cho giao diện khi cần.
     */

    fun loadCourses() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingCourses = true)
            when (val result = courseRepository.getAllCoursesForAdmin()) {
                is ResultState.Success -> {
                    _uiState.value = _uiState.value.copy(isLoadingCourses = false, courses = result.data)
                }

                is ResultState.Error -> {
                    _uiState.value = _uiState.value.copy(isLoadingCourses = false)
                    _event.emit(AdminManagementEvent.ShowError(result.message))
                }

                ResultState.Loading -> Unit
            }
        }
    }

    /**
     * Tải dữ liệu và cập nhật trạng thái hiển thị liên quan.
     * Hàm này chủ yếu cập nhật `uiState`, gọi repository và phát event cho giao diện khi cần.
     */

    fun loadLearningData() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingLearningData = true)

            val (enrollmentsResult, progressesResult) = coroutineScope {
                val enrollmentsDeferred = async { enrollmentRepository.getAllEnrollments() }
                val progressesDeferred = async { progressRepository.getAllProgress() }
                enrollmentsDeferred.await() to progressesDeferred.await()
            }

            if (enrollmentsResult is ResultState.Error) {
                _uiState.value = _uiState.value.copy(isLoadingLearningData = false)
                _event.emit(AdminManagementEvent.ShowError(enrollmentsResult.message))
                return@launch
            }

            if (progressesResult is ResultState.Error) {
                _uiState.value = _uiState.value.copy(isLoadingLearningData = false)
                _event.emit(AdminManagementEvent.ShowError(progressesResult.message))
                return@launch
            }

            _uiState.value = _uiState.value.copy(
                isLoadingLearningData = false,
                enrollments = (enrollmentsResult as? ResultState.Success)?.data.orEmpty(),
                progresses = (progressesResult as? ResultState.Success)?.data.orEmpty()
            )
        }
    }

    /**
     * Tải dữ liệu và cập nhật trạng thái hiển thị liên quan.
     * Hàm này chủ yếu cập nhật `uiState`, gọi repository và phát event cho giao diện khi cần.
     */

    fun loadCategories(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingCategories = true)
            when (val result = categoryRepository.getCategories(forceRefresh = forceRefresh)) {
                is ResultState.Success -> {
                    _uiState.value = _uiState.value.copy(
                        isLoadingCategories = false,
                        categories = result.data
                    )
                }

                is ResultState.Error -> {
                    _uiState.value = _uiState.value.copy(isLoadingCategories = false)
                    _event.emit(AdminManagementEvent.ShowError(result.message))
                }

                ResultState.Loading -> Unit
            }
        }
    }

    /**
     * Tạo mới dữ liệu nghiệp vụ dựa trên đầu vào hiện tại.
     * Hàm này chủ yếu cập nhật `uiState`, gọi repository và phát event cho giao diện khi cần.
     */

    fun createCategory(name: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isProcessing = true)
            when (val result = categoryRepository.createCategory(name)) {
                is ResultState.Success -> {
                    _event.emit(AdminManagementEvent.ShowSuccess("Đã tạo danh mục ${result.data.name}"))
                    loadCategories(forceRefresh = true)
                }

                is ResultState.Error -> {
                    _event.emit(AdminManagementEvent.ShowError(result.message))
                }

                ResultState.Loading -> Unit
            }
            _uiState.value = _uiState.value.copy(isProcessing = false)
        }
    }

    /**
     * Xóa dữ liệu liên quan khỏi hệ thống hoặc danh sách hiển thị.
     * Hàm này chủ yếu cập nhật `uiState`, gọi repository và phát event cho giao diện khi cần.
     */

    fun deleteCategory(category: Category) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isProcessing = true)
            when (val result = categoryRepository.deleteCategory(category.id)) {
                is ResultState.Success -> {
                    _event.emit(AdminManagementEvent.ShowSuccess("Đã xóa danh mục ${category.name}"))
                    loadCategories(forceRefresh = true)
                }

                is ResultState.Error -> {
                    _event.emit(AdminManagementEvent.ShowError(result.message))
                }

                ResultState.Loading -> Unit
            }
            _uiState.value = _uiState.value.copy(isProcessing = false)
        }
    }

    /**
     * Đảo trạng thái hiện tại của đối tượng hoặc lựa chọn tương ứng.
     * Hàm này chủ yếu cập nhật `uiState`, gọi repository và phát event cho giao diện khi cần.
     */

    fun toggleUserActive(user: User) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isProcessing = true)
            when (val result = authRepository.setUserActive(user.uid, !user.isActive)) {
                is ResultState.Success -> {
                    val message = if (user.isActive) {
                        "Đã khóa tài khoản ${user.fullName}"
                    } else {
                        "Đã mở khóa tài khoản ${user.fullName}"
                    }
                    _event.emit(AdminManagementEvent.ShowSuccess(message))
                    loadUsers()
                    loadSummary()
                }

                is ResultState.Error -> {
                    _event.emit(AdminManagementEvent.ShowError(result.message))
                }

                ResultState.Loading -> Unit
            }
            _uiState.value = _uiState.value.copy(isProcessing = false)
        }
    }

    /**
     * Đảo trạng thái hiện tại của đối tượng hoặc lựa chọn tương ứng.
     * Hàm này chủ yếu cập nhật `uiState`, gọi repository và phát event cho giao diện khi cần.
     */

    fun toggleCoursePublished(course: Course) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isProcessing = true)
            when (val result = courseRepository.updatePublishStatus(course.id, !course.isPublished)) {
                is ResultState.Success -> {
                    val message = if (course.isPublished) {
                        "Đã ẩn khóa học ${course.title}"
                    } else {
                        "Đã xuất bản khóa học ${course.title}"
                    }
                    _event.emit(AdminManagementEvent.ShowSuccess(message))
                    loadCourses()
                    loadSummary()
                }

                is ResultState.Error -> {
                    _event.emit(AdminManagementEvent.ShowError(result.message))
                }

                ResultState.Loading -> Unit
            }
            _uiState.value = _uiState.value.copy(isProcessing = false)
        }
    }

    /**
     * Tạo mới dữ liệu nghiệp vụ dựa trên đầu vào hiện tại.
     * Hàm này chủ yếu cập nhật `uiState`, gọi repository và phát event cho giao diện khi cần.
     */

    fun createInstructorAccount(
        adminUid: String,
        fullName: String,
        email: String,
        password: String
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isProcessing = true)
            when (
                val result = authRepository.createInstructorAccountByAdmin(
                    adminUid = adminUid,
                    email = email,
                    password = password,
                    fullName = fullName
                )
            ) {
                is ResultState.Success -> {
                    _event.emit(AdminManagementEvent.ShowSuccess("Đã tạo tài khoản giảng viên thành công"))
                    loadUsers()
                    loadSummary()
                }

                is ResultState.Error -> {
                    _event.emit(AdminManagementEvent.ShowError(result.message))
                }

                ResultState.Loading -> Unit
            }
            _uiState.value = _uiState.value.copy(isProcessing = false)
        }
    }
}
