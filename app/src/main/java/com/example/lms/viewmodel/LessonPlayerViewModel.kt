package com.example.lms.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.lms.data.model.CurriculumItem
import com.example.lms.data.model.LessonProgress
import com.example.lms.data.repository.CourseRepository
import com.example.lms.data.repository.CurriculumRepository
import com.example.lms.data.repository.ProgressRepository
import com.example.lms.util.LessonPlayerEvent
import com.example.lms.util.LessonPlayerUiState
import com.example.lms.util.ResultState
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class LessonPlayerViewModel(
    private val courseRepository: CourseRepository = CourseRepository(),
    private val curriculumRepository: CurriculumRepository = CurriculumRepository(),
    private val progressRepository: ProgressRepository = ProgressRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(LessonPlayerUiState())
    val uiState: StateFlow<LessonPlayerUiState> = _uiState.asStateFlow()

    private val _event = MutableSharedFlow<LessonPlayerEvent>()
    val event = _event.asSharedFlow()

    fun loadData(userId: String, courseId: String, itemId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            val courseDeferred = async { courseRepository.getCourseById(courseId) }
            val curriculumDeferred = async { curriculumRepository.getCurriculum(courseId) }
            val progressDeferred = async { progressRepository.loadCourseProgress(userId, courseId) }

            val courseResult = courseDeferred.await()
            val curriculumResult = curriculumDeferred.await()
            val progressResult = progressDeferred.await()

            if (courseResult is ResultState.Error) {
                _uiState.update { it.copy(isLoading = false) }
                _event.emit(LessonPlayerEvent.ShowError(courseResult.message))
                return@launch
            }
            if (curriculumResult is ResultState.Error) {
                _event.emit(LessonPlayerEvent.ShowError(curriculumResult.message))
            }
            if (progressResult is ResultState.Error) {
                _event.emit(LessonPlayerEvent.ShowError(progressResult.message))
            }

            val course = (courseResult as ResultState.Success).data
            val curriculum = (curriculumResult as? ResultState.Success)?.data ?: emptyList()
            val (progress, lessonProgressList, quizProgressList) =
                (progressResult as? ResultState.Success)?.data
                    ?: Triple(null, emptyList(), emptyList())

            val lessonProgressMap = lessonProgressList.associateBy { it.lessonId }
            val quizProgressMap = quizProgressList.associateBy { it.quizId }

            val currentSelectedItemId = _uiState.value.selectedItemId
            val resolvedItemId = when {
                currentSelectedItemId.isNotBlank() && curriculum.any { it.id == currentSelectedItemId } -> currentSelectedItemId
                curriculum.any { it.id == itemId } -> itemId
                else -> curriculum.firstOrNull()?.id ?: ""
            }

            _uiState.update {
                it.copy(
                    isLoading = false,
                    course = course,
                    curriculum = curriculum,
                    selectedItemId = resolvedItemId,
                    progress = progress,
                    lessonProgressMap = lessonProgressMap,
                    quizProgressMap = quizProgressMap
                )
            }

            val selectedItem = curriculum.find { it.id == resolvedItemId }
            if (selectedItem is CurriculumItem.LessonItem) {
                progressRepository.updateLastAccessed(userId, courseId, resolvedItemId)
            }
        }
    }

    fun selectItem(userId: String, courseId: String, itemId: String) {
        _uiState.update { it.copy(selectedItemId = itemId) }

        val selectedItem = _uiState.value.curriculum.find { it.id == itemId }
        if (selectedItem is CurriculumItem.LessonItem) {
            if (_uiState.value.progress?.lastLessonId != itemId) {
                viewModelScope.launch {
                    val result = progressRepository.updateLastAccessed(userId, courseId, itemId)
                    if (result is ResultState.Success) {
                        _uiState.update {
                            it.copy(progress = it.progress?.copy(lastLessonId = itemId))
                        }
                    } else if (result is ResultState.Error) {
                        _event.emit(LessonPlayerEvent.ShowError(result.message))
                    }
                }
            }
        }
    }

    fun toggleLessonComplete(
        userId: String,
        courseId: String,
        lessonId: String
    ) {
        if (_uiState.value.isTogglingLesson) return

        viewModelScope.launch {
            _uiState.update { it.copy(isTogglingLesson = true) }

            val currentIsCompleted = _uiState.value.lessonProgressMap[lessonId]?.isCompleted ?: false
            val newIsCompleted = !currentIsCompleted
            val totalLessons = _uiState.value.course?.lessonCount ?: 0

            val result = progressRepository.toggleLessonComplete(
                userId = userId,
                courseId = courseId,
                lessonId = lessonId,
                isCompleted = newIsCompleted,
                totalLessons = totalLessons
            )

            when (result) {
                is ResultState.Success -> {
                    val updatedLessonProgressMap = _uiState.value.lessonProgressMap.toMutableMap()
                    updatedLessonProgressMap[lessonId] = LessonProgress(
                        lessonId = lessonId,
                        userId = userId,
                        courseId = courseId,
                        isCompleted = newIsCompleted
                    )

                    val newCompleted = updatedLessonProgressMap.values.count { it.isCompleted }
                    val updatedProgress = _uiState.value.progress?.copy(
                        completedLessons = newCompleted,
                        isCompleted = newCompleted >= totalLessons
                    )

                    _uiState.update {
                        it.copy(
                            isTogglingLesson = false,
                            lessonProgressMap = updatedLessonProgressMap,
                            progress = updatedProgress
                        )
                    }
                    _event.emit(LessonPlayerEvent.ToggleLessonCompleteSuccess)
                }
                is ResultState.Error -> {
                    _uiState.update { it.copy(isTogglingLesson = false) }
                    _event.emit(LessonPlayerEvent.ShowError(result.message))
                }
                else -> _uiState.update { it.copy(isTogglingLesson = false) }
            }
        }
    }

    fun reloadProgress(userId: String, courseId: String) {
        viewModelScope.launch {
            when (val result = progressRepository.loadCourseProgress(userId, courseId)) {
                is ResultState.Success -> {
                    val (progress, lessonProgressList, quizProgressList) = result.data
                    _uiState.update { it ->
                        it.copy(
                            progress = progress,
                            lessonProgressMap = lessonProgressList.associateBy { it.lessonId },
                            quizProgressMap = quizProgressList.associateBy { it.quizId }
                        )
                    }
                }
                is ResultState.Error -> {
                    _event.emit(LessonPlayerEvent.ShowError(result.message))
                }
                else -> {}
            }
        }
    }
}