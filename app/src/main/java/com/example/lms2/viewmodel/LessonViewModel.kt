package com.example.lms2.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.lms2.data.model.Attachment
import com.example.lms2.data.model.Lesson
import com.example.lms2.data.repository.CurriculumRepository
import com.example.lms2.util.CloudinaryManager
import com.example.lms2.util.LessonEvent
import com.example.lms2.util.LessonUiState
import com.example.lms2.util.ResultState
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Điều phối trạng thái giao diện trong LessonViewModel.
 * File này kết nối màn hình Compose với repository, cập nhật `uiState` và phát event một lần cho các thao tác điều hướng hoặc thông báo.
 * Đây là nơi tập trung phần lớn logic trình bày và điều phối nghiệp vụ ở phía ứng dụng Android.
 */

class LessonViewModel(
    private val repository: CurriculumRepository = CurriculumRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(LessonUiState())
    val uiState: StateFlow<LessonUiState> = _uiState.asStateFlow()

    private val _eventChannel = Channel<LessonEvent>(Channel.BUFFERED)
    val events = _eventChannel.receiveAsFlow()

    /**
     * Khởi tạo form bài học ở chế độ tạo mới hoặc chỉnh sửa.
     * Khi `lesson` khác null, dữ liệu cũ được nạp vào form để giảng viên cập nhật.
     */

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

    /**
     * Cập nhật tiêu đề bài học và xóa lỗi validation của trường tiêu đề.
     */

    fun onTitleChange(value: String) = _uiState.update { it.copy(title = value, titleError = null) }
    /**
     * Cập nhật mô tả bài học và xóa lỗi validation của trường mô tả.
     */

    fun onDescriptionChange(value: String) = _uiState.update { it.copy(description = value, descriptionError = null) }
    /**
     * Cập nhật đường dẫn video bài giảng và xóa lỗi validation của trường video.
     */

    fun onVideoUrlChange(value: String) = _uiState.update { it.copy(videoUrl = value, videoUrlError = null) }
    /**
     * Cập nhật thời lượng bài học và xóa lỗi validation của trường thời lượng.
     */

    fun onDurationChange(value: String) = _uiState.update { it.copy(duration = value, durationError = null) }

    /**
     * Thêm tệp đính kèm vào danh sách tạm của form bài học.
     * File mới chỉ lưu local URI trong state và sẽ được upload lên Cloudinary khi bấm lưu.
     */

    fun addAttachment(uri: Uri, fileName: String, fileSize: String, mimeType: String) {
        val newAttachment = Attachment(
            name = fileName,
            url = uri.toString(), // Lưu tạm local URI
            type = mimeType,
            size = fileSize
        )
        _uiState.update { it.copy(attachments = it.attachments + newAttachment) }
    }

    /**
     * Xóa một tệp đính kèm khỏi form trước khi lưu bài học.
     */

    fun removeAttachment(attachment: Attachment) {
        _uiState.update { it.copy(attachments = it.attachments - attachment) }
    }

    /**
     * Lưu bài học sau khi validate dữ liệu.
     * Các tệp mới có URI `content://` sẽ được upload lên Cloudinary trước khi lesson được ghi xuống Firestore.
     */

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
        var isValid = true

        if (state.title.isBlank()) {
            _uiState.update { it.copy(titleError = "Vui lòng nhập tiêu đề") }
            isValid = false
        }
        if (state.description.isBlank()) {
            _uiState.update { it.copy(descriptionError = "Vui lòng nhập mô tả") }
            isValid = false
        }
        if (state.videoUrl.isBlank()) {
            _uiState.update { it.copy(videoUrlError = "Vui lòng nhập link video") }
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
