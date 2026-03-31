package com.example.lms.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.lms.data.repository.AuthRepository
import com.example.lms.util.CloudinaryManager
import com.example.lms.util.AuthUiState
import com.example.lms.util.ResultState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.example.lms.util.AuthEvent
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class AuthViewModel(
    private val repository: AuthRepository = AuthRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    private val _event = MutableSharedFlow<AuthEvent>()
    val event = _event.asSharedFlow()

    init {
        if (repository.isUserLoggedIn()) {
            getCurrentUser()
        }
    }

    fun getCurrentUser() {
        val uid = repository.getCurrentUserId() ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            when (val result = repository.getUserDetails(uid)) {
                is ResultState.Success -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        currentUser = result.data
                    )
                }
                is ResultState.Error -> {
                    _uiState.value = _uiState.value.copy(isLoading = false)
                    viewModelScope.launch { _event.emit(AuthEvent.ShowError(result.message)) }
                }
                else -> {}
            }
        }
    }

    fun getCurrentUserId(): String = repository.getCurrentUserId() ?: ""

    fun isUserLoggedIn(): Boolean = repository.isUserLoggedIn()

    /* ========================
       UI STATE UPDATE
       ======================== */

    fun onFullNameChange(name: String) {
        _uiState.value = _uiState.value.copy(fullName = name)
    }

    fun onEmailChange(email: String) {
        _uiState.value = _uiState.value.copy(email = email)
    }

    fun onPasswordChange(password: String) {
        _uiState.value = _uiState.value.copy(password = password)
    }

    fun onConfirmPasswordChange(password: String) {
        _uiState.value = _uiState.value.copy(confirmPassword = password)
    }

    fun togglePasswordVisibility() {
        _uiState.value = _uiState.value.copy(
            passwordVisible = !_uiState.value.passwordVisible
        )
    }

    fun toggleConfirmPasswordVisibility() {
        _uiState.value = _uiState.value.copy(
            confirmPasswordVisible = !_uiState.value.confirmPasswordVisible
        )
    }

    /* ========================
       LOGIN / REGISTER
       ======================== */

    fun login() {
        val currentState = _uiState.value
        if (currentState.email.isBlank() || currentState.password.isBlank()) {
            viewModelScope.launch {
                _event.emit(AuthEvent.ShowError("Vui lòng nhập đầy đủ thông tin"))
            }
            return
        }

        viewModelScope.launch {
            _uiState.value = currentState.copy(isLoading = true, errorMessage = null)
            when (val loginResult = repository.login(currentState.email, currentState.password)) {
                is ResultState.Success -> {
                    when (val userResult = repository.getUserDetails(loginResult.data)) {
                        is ResultState.Success -> {
                            _uiState.value = _uiState.value.copy(
                                isLoading = false,
                                currentUser = userResult.data
                            )
                            _event.emit(AuthEvent.NavigateToHome(loginResult.data))
                        }
                        is ResultState.Error -> {
                            _uiState.value = _uiState.value.copy(isLoading = false)
                            _event.emit(AuthEvent.ShowError("Lỗi lấy thông tin: ${userResult.message}"))
                        }
                        else -> {}
                    }
                }
                is ResultState.Error -> {
                    _uiState.value = _uiState.value.copy(isLoading = false)
                    _event.emit(AuthEvent.ShowError(loginResult.message))
                }
                else -> {}
            }
        }
    }

    fun register() {
        val currentState = _uiState.value
        
        if (currentState.fullName.isBlank() || currentState.email.isBlank() || currentState.password.isBlank()) {
            viewModelScope.launch {
                _event.emit(AuthEvent.ShowError("Vui lòng nhập đầy đủ thông tin"))
            }
            return
        }

        if (currentState.password != currentState.confirmPassword) {
            viewModelScope.launch {
                _event.emit(AuthEvent.ShowError("Mật khẩu xác nhận không khớp"))
            }
            return
        }

        viewModelScope.launch {
            _uiState.value = currentState.copy(isLoading = true, errorMessage = null)
            when (val result = repository.register(
                currentState.email, 
                currentState.password, 
                currentState.fullName
            )) {
                is ResultState.Success -> {
                    _uiState.value = _uiState.value.copy(isLoading = false)
                    _event.emit(AuthEvent.RegisterSuccess)
                }
                is ResultState.Error -> {
                    _uiState.value = _uiState.value.copy(isLoading = false)
                    _event.emit(AuthEvent.ShowError(result.message))
                }
                ResultState.Loading -> { }
            }
        }
    }

    /* ========================
       GOOGLE SIGN IN
       ======================== */

    fun signInWithGoogle(idToken: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)

            when (val result = repository.signInWithGoogle(idToken)) {
            is ResultState.Success -> {
                val userResult = repository.getUserDetails(result.data)
                if (userResult is ResultState.Success) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        currentUser = userResult.data
                    )
                    _event.emit(AuthEvent.NavigateToHome(result.data))
                } else {
                    _uiState.value = _uiState.value.copy(isLoading = false)
                    _event.emit(AuthEvent.ShowError("Không thể lấy thông tin profile Google"))
                }
            }
            is ResultState.Error -> {
                _uiState.value = _uiState.value.copy(isLoading = false)
                _event.emit(AuthEvent.ShowError(result.message))
            }
            else -> {}
        }
        }
    }

    /* ========================
       FORGOT PASSWORD
       ======================== */

    fun sendPasswordResetEmail() {
        val email = _uiState.value.email
        if (email.isBlank()) {
            viewModelScope.launch {
                _event.emit(AuthEvent.ShowError("Vui lòng nhập email"))
            }
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            when (val result = repository.sendPasswordResetEmail(email)) {
                is ResultState.Success -> {
                    _uiState.value = _uiState.value.copy(isLoading = false)
                    _event.emit(AuthEvent.PasswordResetEmailSent)
                }
                is ResultState.Error -> {
                    _uiState.value = _uiState.value.copy(isLoading = false)
                    _event.emit(AuthEvent.ShowError(result.message))
                }
                ResultState.Loading -> { }
            }
        }
    }

    fun saveStudentProfile(
        fullName: String,
        currentAvatarUrl: String,
        selectedAvatarUri: Uri?
    ) {
        val trimmedName = fullName.trim()
        if (trimmedName.isBlank()) {
            viewModelScope.launch {
                _event.emit(AuthEvent.ShowError("Họ và tên không được để trống"))
            }
            return
        }

        val uid = getCurrentUserId()
        if (uid.isBlank()) {
            viewModelScope.launch {
                _event.emit(AuthEvent.ShowError("Không tìm thấy người dùng hiện tại"))
            }
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isUpdatingProfile = true)
            var finalAvatarUrl = currentAvatarUrl

            if (selectedAvatarUri != null) {
                when (val uploadResult = CloudinaryManager.uploadImage(selectedAvatarUri)) {
                    is ResultState.Success -> {
                        finalAvatarUrl = uploadResult.data.first
                    }
                    is ResultState.Error -> {
                        _uiState.value = _uiState.value.copy(isUpdatingProfile = false)
                        _event.emit(AuthEvent.ShowError(uploadResult.message))
                        return@launch
                    }
                    ResultState.Loading -> Unit
                }
            }

            when (val result = repository.updateProfile(uid, trimmedName, finalAvatarUrl)) {
                is ResultState.Success -> {
                    val updatedUser = _uiState.value.currentUser?.copy(
                        fullName = trimmedName,
                        avatarUrl = finalAvatarUrl.trim().ifBlank { null }
                    )
                    _uiState.value = _uiState.value.copy(
                        isUpdatingProfile = false,
                        currentUser = updatedUser
                    )
                    _event.emit(AuthEvent.ProfileUpdated)
                }
                is ResultState.Error -> {
                    _uiState.value = _uiState.value.copy(isUpdatingProfile = false)
                    _event.emit(AuthEvent.ShowError(result.message))
                }
                ResultState.Loading -> Unit
            }
        }
    }

    /* ========================
       OTHER
       ======================== */

    fun onShowError(message: String) {
        viewModelScope.launch {
            _event.emit(AuthEvent.ShowError(message))
        }
    }

    fun logout() {
        repository.logout()
        _uiState.value = AuthUiState()
    }
}
