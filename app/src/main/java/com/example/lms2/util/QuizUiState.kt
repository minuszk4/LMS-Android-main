package com.example.lms2.util

import com.example.lms2.data.model.Question

/**
 * Mô tả trạng thái giao diện của luồng Quiz.
 * Đối tượng này gom dữ liệu hiển thị, cờ tải, lỗi kiểm tra và các trạng thái tạm mà Compose cần quan sát.
 * Việc gom toàn bộ state vào một nơi giúp màn hình render nhất quán và dễ kiểm thử hơn.
 */

data class QuizUiState(
    val id: String = "",
    val courseId: String = "",
    val title: String = "",
    val description: String = "",
    val questions: List<Question> = emptyList(),
    val durationMinutes: String = "15",
    val passingScore: String = "80",
    val orderIndex: Int = 0,
    
    /**
     * Các cờ điều khiển trạng thái xử lý ở giao diện.
     */
    val isSaving: Boolean = false,
    val isImporting: Boolean = false,
    val isEditMode: Boolean = false,
    
    /**
     * Thông báo lỗi dùng để hiển thị trực tiếp tại từng trường nhập liệu.
     */
    val titleError: String? = null,
    val durationError: String? = null,
    val passingScoreError: String? = null,
    val questionsError: String? = null
)
