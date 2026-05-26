package com.example.lms2.util

import com.example.lms2.data.model.User

/**
 * Mô tả trạng thái giao diện của luồng Auth.
 * Đối tượng này gom dữ liệu hiển thị, cờ tải, lỗi kiểm tra và các trạng thái tạm mà Compose cần quan sát.
 * Việc gom toàn bộ state vào một nơi giúp màn hình render nhất quán và dễ kiểm thử hơn.
 */

data class AuthUiState(
    val email: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val fullName: String = "",
    val passwordVisible: Boolean = false,
    val confirmPasswordVisible: Boolean = false,
    val isLoading: Boolean = false,
    val isUpdatingProfile: Boolean = false,
    val errorMessage: String? = null,
    val currentUser: User? = null
)
