package com.example.lms2.util

/**
 * Khai báo tập sự kiện giao diện cho luồng QuizAttempt.
 * Mỗi nhánh trong sealed class biểu diễn một hành động của người dùng hoặc một tín hiệu mà ViewModel cần xử lý.
 * Cấu trúc event riêng giúp luồng unidirectional data flow rõ ràng và dễ theo dõi hơn.
 */

sealed class QuizAttemptEvent {
    data class ShowError(val message: String) : QuizAttemptEvent()
    object SubmitQuizSuccess : QuizAttemptEvent()
    object QuizTimeUp : QuizAttemptEvent()
    object RetakeQuizSuccess : QuizAttemptEvent()
}
