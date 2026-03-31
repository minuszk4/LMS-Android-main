package com.example.lms.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.lms.data.model.CurriculumItem
import com.example.lms.data.repository.CurriculumRepository
import com.example.lms.util.CurriculumEvent
import com.example.lms.util.CurriculumUiState
import com.example.lms.util.ResultState
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class CurriculumViewModel(
    private val repository: CurriculumRepository = CurriculumRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow<CurriculumUiState>(CurriculumUiState.Idle)
    val uiState: StateFlow<CurriculumUiState> = _uiState.asStateFlow()

    private val _eventChannel = Channel<CurriculumEvent>(Channel.BUFFERED)
    val events = _eventChannel.receiveAsFlow()

    private var currentCourseId: String = ""

    fun setCourseId(courseId: String) {
        if (courseId == currentCourseId) return
        currentCourseId = courseId
        loadCurriculum()
    }

    fun loadCurriculum() {
        if (currentCourseId.isBlank()) return
        viewModelScope.launch {
            _uiState.value = CurriculumUiState.Loading
            when (val result = repository.getCurriculum(currentCourseId)) {
                is ResultState.Success -> {
                    _uiState.value = CurriculumUiState.Success(result.data)
                }
                is ResultState.Error -> {
                    _uiState.value = CurriculumUiState.Error(result.message)
                    sendEvent(CurriculumEvent.ShowSnackbar(result.message))
                }
                is ResultState.Loading -> Unit
            }
        }
    }

    fun deleteContent(item: CurriculumItem) {
        viewModelScope.launch {
            _uiState.optimisticRemove(item)

            val result = when (item) {
                is CurriculumItem.LessonItem -> repository.deleteLesson(item.id, item.lesson.courseId)
                is CurriculumItem.QuizItem -> repository.deleteQuiz(item.id)
            }

            when (result) {
                is ResultState.Success -> {
                    val label = when (item) {
                        is CurriculumItem.LessonItem -> "Đã xóa bài học \"${item.lesson.title}\""
                        is CurriculumItem.QuizItem -> "Đã xóa bài kiểm tra \"${item.quiz.title}\""
                    }
                    sendEvent(CurriculumEvent.ShowSnackbar(label))
                }
                is ResultState.Error -> {
                    loadCurriculum()
                    sendEvent(CurriculumEvent.ShowSnackbar("Xóa thất bại: ${result.message}"))
                }
                is ResultState.Loading -> Unit
            }
        }
    }

    fun updateOrder(reorderedItems: List<CurriculumItem>) {
        viewModelScope.launch {
            _uiState.update {
                if (it is CurriculumUiState.Success) it.copy(items = reorderedItems) else it
            }

            when (val result = repository.updateOrder(reorderedItems)) {
                is ResultState.Success -> {
                    sendEvent(CurriculumEvent.ShowSnackbar("Đã cập nhật thứ tự"))
                }
                is ResultState.Error -> {
                    loadCurriculum()
                    sendEvent(CurriculumEvent.ShowSnackbar("Cập nhật thứ tự thất bại: ${result.message}"))
                }
                is ResultState.Loading -> Unit
            }
        }
    }

    private fun sendEvent(event: CurriculumEvent) {
        viewModelScope.launch {
            _eventChannel.send(event)
        }
    }

    private fun MutableStateFlow<CurriculumUiState>.optimisticRemove(item: CurriculumItem) {
        update { state ->
            if (state is CurriculumUiState.Success) {
                state.copy(items = state.items.filter { it.id != item.id })
            } else state
        }
    }
}
