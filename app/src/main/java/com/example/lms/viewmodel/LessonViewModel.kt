package com.example.lms.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.lms.data.model.Attachment
import com.example.lms.data.model.Lesson
import com.example.lms.data.repository.CurriculumRepository
import com.example.lms.util.CloudinaryManager
import com.example.lms.util.LessonEvent
import com.example.lms.util.LessonUiState
import com.example.lms.util.ResultState
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class LessonViewModel(
    private val repository: CurriculumRepository = CurriculumRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(LessonUiState())
    val uiState: StateFlow<LessonUiState> = _uiState.asStateFlow()

    private val _eventChannel = Channel<LessonEvent>(Channel.BUFFERED)
    val events = _eventChannel.receiveAsFlow()

    fun initWith(lesson: Lesson?, courseId: String) {
        if (lesson != null) {
            _uiState.value = LessonUiState(
                id = lesson.id,
                courseId = courseId,
                title = lesson.title,
                description = lesson.description,
                videoUrl = lesson.videoUrl,
                duration = lesson.duration,
                orderIndex = lesson.orderIndex,
                attachments = lesson.attachments,
                isEditMode = true
            )
        } else {
            _uiState.value = LessonUiState(courseId = courseId)
        }
    }

    fun onTitleChange(value: String) = _uiState.update { it.copy(title = value, titleError = null) }
    fun onDescriptionChange(value: String) = _uiState.update { it.copy(description = value, descriptionError = null) }
    fun onVideoUrlChange(value: String) = _uiState.update { it.copy(videoUrl = value, videoUrlError = null) }
    fun onDurationChange(value: String) = _uiState.update { it.copy(duration = value, durationError = null) }

    fun addAttachment(uri: Uri, fileName: String, fileSize: String, mimeType: String) {
        val newAttachment = Attachment(
            name = fileName,
            url = uri.toString(), // Lưu tạm local URI
            type = mimeType,
            size = fileSize
        )
        _uiState.update { it.copy(attachments = it.attachments + newAttachment) }
    }

    fun removeAttachment(attachment: Attachment) {
        _uiState.update { it.copy(attachments = it.attachments - attachment) }
    }

    fun save() {
        if (!validate()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            val currentState = _uiState.value

            // Upload các tệp mới (có prefix content://)
            val finalAttachments = currentState.attachments.map { attachment ->
                if (attachment.url.startsWith("content://")) {
                    when (val uploadResult = CloudinaryManager.uploadFile(Uri.parse(attachment.url),attachment.name)) {
                        is ResultState.Success -> attachment.copy(url = uploadResult.data)
                        is ResultState.Error -> {
                            _uiState.update { it.copy(isSaving = false) }
                            sendEvent(LessonEvent.ShowSnackbar("Lỗi tải tệp ${attachment.name}: ${uploadResult.message}"))
                            return@launch
                        }
                        else -> attachment
                    }
                } else {
                    attachment
                }
            }

            val lesson = Lesson(
                id = currentState.id,
                courseId = currentState.courseId,
                title = currentState.title.trim(),
                description = currentState.description.trim(),
                videoUrl = currentState.videoUrl.trim(),
                duration = currentState.duration.trim(),
                orderIndex = currentState.orderIndex,
                attachments = finalAttachments
            )

            val result = if (currentState.isEditMode) repository.updateLesson(lesson) else repository.createLesson(lesson)

            _uiState.update { it.copy(isSaving = false) }

            when (result) {
                is ResultState.Success -> {
                    sendEvent(LessonEvent.ShowSnackbar(if (currentState.isEditMode) "Đã cập nhật" else "Đã thêm bài học"))
                    sendEvent(LessonEvent.SaveSuccess)
                }
                is ResultState.Error -> sendEvent(LessonEvent.ShowSnackbar(result.message))
                else -> {}
            }
        }
    }

    private fun validate(): Boolean {
        val state = _uiState.value
        val youtubeRegex = "^(https?://)?(www\\.)?(youtube\\.com|youtu\\.be)/.+$".toRegex()
        var isValid = true

        if (state.title.isBlank()) {
            _uiState.update { it.copy(titleError = "Vui lòng nhập tiêu đề") }
            isValid = false
        }
        if (state.description.isBlank()) {
            _uiState.update { it.copy(descriptionError = "Vui lòng nhập mô tả") }
            isValid = false
        }
        if (state.videoUrl.isBlank() || !state.videoUrl.matches(youtubeRegex)) {
            _uiState.update { it.copy(videoUrlError = "Link YouTube không hợp lệ") }
            isValid = false
        }
        if (state.duration.isBlank()) {
            _uiState.update { it.copy(durationError = "Vui lòng nhập thời lượng") }
            isValid = false
        }
        return isValid
    }

    private fun sendEvent(event: LessonEvent) {
        viewModelScope.launch { _eventChannel.send(event) }
    }
}
