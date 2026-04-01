package com.example.lms2.util

import com.example.lms2.data.model.User

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
