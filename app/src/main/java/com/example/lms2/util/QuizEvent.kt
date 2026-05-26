package com.example.lms2.util

/**
 * Khai báo tập sự kiện giao diện cho luồng Quiz.
 * Mỗi nhánh trong sealed class biểu diễn một hành động của người dùng hoặc một tín hiệu mà ViewModel cần xử lý.
 * Cấu trúc event riêng giúp luồng unidirectional data flow rõ ràng và dễ theo dõi hơn.
 */

sealed class QuizEvent {
    data class ShowSnackbar(val message: String) : QuizEvent()
    object SaveSuccess : QuizEvent()
}
