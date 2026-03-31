package com.example.lms2.util

sealed class AuthEvent {
    data class NavigateToHome(val userId: String) : AuthEvent()
    data class ShowError(val message: String) : AuthEvent()
    object PasswordResetEmailSent : AuthEvent()
    object RegisterSuccess : AuthEvent()
    object ProfileUpdated : AuthEvent()
    object InstructorApplicationSubmitted : AuthEvent()
}
