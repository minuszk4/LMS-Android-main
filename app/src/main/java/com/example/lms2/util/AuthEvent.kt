package com.example.lms2.util

/**
 * Khai báo tập sự kiện giao diện cho luồng Auth.
 * Mỗi nhánh trong sealed class biểu diễn một hành động của người dùng hoặc một tín hiệu mà ViewModel cần xử lý.
 * Cấu trúc event riêng giúp luồng unidirectional data flow rõ ràng và dễ theo dõi hơn.
 */

sealed class AuthEvent {
    data class NavigateToHome(val userId: String) : AuthEvent()
    data class ShowError(val message: String) : AuthEvent()
    object PasswordResetEmailSent : AuthEvent()
    object RegisterSuccess : AuthEvent()
    object ProfileUpdated : AuthEvent()
    object InstructorApplicationSubmitted : AuthEvent()
}
