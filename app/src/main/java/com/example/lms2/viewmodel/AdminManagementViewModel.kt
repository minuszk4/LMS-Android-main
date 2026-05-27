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
 * ViewModel điều phối toàn bộ màn quản trị tổng hợp.
 *
 * Đây là nơi gom state của nhiều tab admin:
 * - dashboard summary,
 * - danh sách user,
 * - danh sách course,
 * - dữ liệu enrollment/progress toàn hệ thống,
 * - category management.
 *
 * Mục tiêu của lớp là giữ cho UI chỉ cần lắng nghe `uiState` và `event`,
 * còn phần gọi repository, tổng hợp dữ liệu và xử lý thành công/thất bại
 * được gom hết về đây.
 */
data class AdminDashboardSummary(
    val totalUsers: Int = 0,
    val activeUsers: Int = 0,
    val pendingInstructorRequests: Int = 0,
    val totalCourses: Int = 0,
    val unpublishedCourses: Int = 0
)

/**
 * State tổng hợp cho các màn hình admin management.
 *
 * Mỗi cờ `isLoading...` đại diện cho một khối dữ liệu riêng, nhờ đó UI có thể
 * hiển thị loading độc lập theo từng tab thay vì khóa cứng toàn bộ màn hình.
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
 * Event one-shot cho admin UI.
 *
 * Dùng cho snackbar/toast hoặc các phản hồi ngắn hạn không nên lưu cứng vào state.
 */
sealed class AdminManagementEvent {
    data class ShowError(val message: String) : AdminManagementEvent()
    data class ShowSuccess(val message: String) : AdminManagementEvent()
}

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
     * Tải dashboard summary cho admin.
     *
     * ViewModel gọi user list và course list, sau đó tự tổng hợp các chỉ số
     * như tổng user, user active, số đơn giảng viên đang chờ duyệt và số course chưa publish.
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
                    pendingInstructorRequests = users.count {
                        it.instructorRequestStatus == InstructorApplicationStatus.PENDING
                    },
                    totalCourses = courses.size,
                    unpublishedCourses = courses.count { !it.isPublished }
                )
            )
        }
    }

    /**
     * Tải danh sách user cho tab quản lý tài khoản.
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
     * Tải danh sách toàn bộ course cho tab admin courses.
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
     * Tải dữ liệu học tập tổng hợp cho admin.
     *
     * `Enrollment` và `Progress` được lấy song song để giảm thời gian chờ
     * của tab learning analytics.
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
     * Tải danh mục cho tab category management.
     *
     * `forceRefresh` được dùng sau các thao tác create/delete để tránh UI dùng lại cache cũ.
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
     * Tạo mới một category từ màn admin.
     *
     * Sau khi tạo thành công, ViewModel reload category list để UI luôn phản ánh
     * đúng dữ liệu mới thay vì tự chèn local state thủ công.
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
     * Xóa một category rồi tải lại danh sách.
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
     * Khóa hoặc mở khóa tài khoản người dùng.
     *
     * Sau khi thao tác xong, ViewModel refresh cả danh sách user lẫn dashboard summary
     * vì hai vùng này cùng phụ thuộc vào trạng thái `isActive`.
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
     * Publish hoặc unpublish một khóa học.
     *
     * Thành công xong sẽ reload course list và dashboard vì hai khối này
     * cùng phụ thuộc vào số course public/chưa public.
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
     * Tạo nhanh tài khoản giảng viên từ phía admin.
     *
     * Đây là đường đi khác với luồng “học viên nộp đơn xin làm giảng viên”.
     * Sau khi tạo xong, ViewModel refresh cả danh sách user lẫn summary.
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
