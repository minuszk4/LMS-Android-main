package com.example.lms.util

import com.example.lms.data.model.Attachment

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
