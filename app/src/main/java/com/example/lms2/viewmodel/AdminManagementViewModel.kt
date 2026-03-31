package com.example.lms2.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.lms2.data.model.Category
import com.example.lms2.data.model.Course
import com.example.lms2.data.model.InstructorApplicationStatus
import com.example.lms2.data.model.User
import com.example.lms2.data.repository.AuthRepository
import com.example.lms2.data.repository.CategoryRepository
import com.example.lms2.data.repository.CourseRepository
import com.example.lms2.util.ResultState
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AdminDashboardSummary(
    val totalUsers: Int = 0,
    val activeUsers: Int = 0,
    val pendingInstructorRequests: Int = 0,
    val totalCourses: Int = 0,
    val unpublishedCourses: Int = 0
)

data class AdminManagementUiState(
    val isLoadingSummary: Boolean = false,
    val isLoadingUsers: Boolean = false,
    val isLoadingCourses: Boolean = false,
    val isLoadingCategories: Boolean = false,
    val isProcessing: Boolean = false,
    val summary: AdminDashboardSummary = AdminDashboardSummary(),
    val users: List<User> = emptyList(),
    val courses: List<Course> = emptyList(),
    val categories: List<Category> = emptyList()
)

sealed class AdminManagementEvent {
    data class ShowError(val message: String) : AdminManagementEvent()
    data class ShowSuccess(val message: String) : AdminManagementEvent()
}

class AdminManagementViewModel(
    private val authRepository: AuthRepository = AuthRepository(),
    private val courseRepository: CourseRepository = CourseRepository(),
    private val categoryRepository: CategoryRepository = CategoryRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(AdminManagementUiState())
    val uiState = _uiState.asStateFlow()

    private val _event = MutableSharedFlow<AdminManagementEvent>()
    val event = _event.asSharedFlow()

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
