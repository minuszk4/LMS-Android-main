package com.example.lms2.util

import com.example.lms2.data.model.Attachment

/**
 * Mô tả trạng thái giao diện của luồng Lesson.
 * Đối tượng này gom dữ liệu hiển thị, cờ tải, lỗi kiểm tra và các trạng thái tạm mà Compose cần quan sát.
 * Việc gom toàn bộ state vào một nơi giúp màn hình render nhất quán và dễ kiểm thử hơn.
 */

data class LessonUiState(
    val id: String = "",
    val courseId: String = "",
    val title: String = "",
    val description: String = "",
    val videoUrl: String = "",
    val duration: String = "",
    val orderIndex: Int = 0,
    val attachments: List<Attachment> = emptyList(),
    
    // Status
    val isSaving: Boolean = false,
    val isUploadingFile: Boolean = false,
    val isEditMode: Boolean = false,
    
    // Validation
    val titleError: String? = null,
    val descriptionError: String? = null,
    val videoUrlError: String? = null,
    val durationError: String? = null
)
