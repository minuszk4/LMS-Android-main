package com.example.lms.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.lms.data.model.Question
import com.example.lms.data.model.Quiz
import com.example.lms.data.repository.CurriculumRepository
import com.example.lms.util.QuizEvent
import com.example.lms.util.QuizUiState
import com.example.lms.util.ResultState
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

class QuizViewModel(
    private val repository: CurriculumRepository = CurriculumRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(QuizUiState())
    val uiState: StateFlow<QuizUiState> = _uiState.asStateFlow()

    private val _eventChannel = Channel<QuizEvent>(Channel.BUFFERED)
    val events = _eventChannel.receiveAsFlow()

    fun initWith(quiz: Quiz?, courseId: String) {
        if (quiz != null) {
            _uiState.value = QuizUiState(
                id = quiz.id,
                courseId = courseId,
                title = quiz.title,
                description = quiz.description,
                questions = quiz.questions,
                durationMinutes = quiz.durationMinutes.toString(),
                passingScore = quiz.passingScore.toString(),
                orderIndex = quiz.orderIndex,
                isEditMode = true
            )
        } else {
            _uiState.value = QuizUiState(courseId = courseId)
            // Add one empty question by default
            addQuestion()
        }
    }

    fun onTitleChange(value: String) = _uiState.update { it.copy(title = value, titleError = null) }
    fun onDescriptionChange(value: String) = _uiState.update { it.copy(description = value) }
    fun onDurationChange(value: String) = _uiState.update { it.copy(durationMinutes = value, durationError = null) }
    fun onPassingScoreChange(value: String) = _uiState.update { it.copy(passingScore = value, passingScoreError = null) }

    fun addQuestion() {
        val newQuestion = Question(
            id = UUID.randomUUID().toString(),
            text = "",
            options = listOf("", "", "", ""),
            correctAnswerIndex = 0
        )
        _uiState.update { it.copy(questions = it.questions + newQuestion, questionsError = null) }
    }

    fun removeQuestion(questionId: String) {
        _uiState.update { it.copy(questions = it.questions.filter { q -> q.id != questionId }) }
    }

    fun updateQuestionText(questionId: String, text: String) {
        _uiState.update { state ->
            state.copy(questions = state.questions.map { q ->
                if (q.id == questionId) q.copy(text = text) else q
            })
        }
    }

    fun updateOptionText(questionId: String, optionIndex: Int, text: String) {
        _uiState.update { state ->
            state.copy(questions = state.questions.map { q ->
                if (q.id == questionId) {
                    val newOptions = q.options.toMutableList()
                    newOptions[optionIndex] = text
                    q.copy(options = newOptions)
                } else q
            })
        }
    }

    fun updateCorrectAnswer(questionId: String, index: Int) {
        _uiState.update { state ->
            state.copy(questions = state.questions.map { q ->
                if (q.id == questionId) q.copy(correctAnswerIndex = index) else q
            })
        }
    }

    fun save() {
        if (!validate()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            val state = _uiState.value

            val quiz = Quiz(
                id = state.id,
                courseId = state.courseId,
                title = state.title.trim(),
                description = state.description.trim(),
                orderIndex = state.orderIndex,
                questions = state.questions,
                durationMinutes = state.durationMinutes.toIntOrNull() ?: 15,
                passingScore = state.passingScore.toIntOrNull() ?: 80
            )

            val result = if (state.isEditMode) repository.updateQuiz(quiz) else repository.createQuiz(quiz)

            _uiState.update { it.copy(isSaving = false) }

            when (result) {
                is ResultState.Success -> {
                    sendEvent(QuizEvent.ShowSnackbar(if (state.isEditMode) "Đã cập nhật bài kiểm tra" else "Đã tạo bài kiểm tra"))
                    sendEvent(QuizEvent.SaveSuccess)
                }
                is ResultState.Error -> sendEvent(QuizEvent.ShowSnackbar(result.message))
                else -> {}
            }
        }
    }

    private fun validate(): Boolean {
        val state = _uiState.value
        var isValid = true

        if (state.title.isBlank()) {
            _uiState.update { it.copy(titleError = "Tiêu đề không được để trống") }
            isValid = false
        }

        if (state.durationMinutes.toIntOrNull() == null || state.durationMinutes.toInt() <= 0) {
            _uiState.update { it.copy(durationError = "Thời gian không hợp lệ") }
            isValid = false
        }

        val score = state.passingScore.toIntOrNull()
        if (score == null || score !in 0..100) {
            _uiState.update { it.copy(passingScoreError = "Điểm đạt phải từ 0-100") }
            isValid = false
        }

        if (state.questions.isEmpty()) {
            _uiState.update { it.copy(questionsError = "Phải có ít nhất 1 câu hỏi") }
            isValid = false
        } else {
            val isQuestionsIncomplete = state.questions.any { q ->
                q.text.isBlank() || q.options.any { opt -> opt.isBlank() }
            }
            if (isQuestionsIncomplete) {
                _uiState.update { it.copy(questionsError = "Vui lòng hoàn thành nội dung tất cả câu hỏi") }
                isValid = false
            }
        }

        return isValid
    }

    private fun sendEvent(event: QuizEvent) {
        viewModelScope.launch { _eventChannel.send(event) }
    }
}
