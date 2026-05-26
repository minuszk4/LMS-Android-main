package com.example.lms2.util

import com.example.lms2.data.model.CurriculumItem

/**
 * Mô tả trạng thái giao diện của luồng Curriculum.
 * Đối tượng này gom dữ liệu hiển thị, cờ tải, lỗi kiểm tra và các trạng thái tạm mà Compose cần quan sát.
 * Việc gom toàn bộ state vào một nơi giúp màn hình render nhất quán và dễ kiểm thử hơn.
 */

sealed class CurriculumUiState {
    object Idle : CurriculumUiState()
    object Loading : CurriculumUiState()
    data class Success(val items: List<CurriculumItem>) : CurriculumUiState()
    data class Error(val message: String) : CurriculumUiState()
}
