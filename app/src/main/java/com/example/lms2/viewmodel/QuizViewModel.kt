package com.example.lms2.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.lms2.data.model.Question
import com.example.lms2.data.model.Quiz
import com.example.lms2.data.repository.CurriculumRepository
import com.example.lms2.util.QuizEvent
import com.example.lms2.util.QuizUiState
import com.example.lms2.util.ResultState
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.InputStream
import java.util.UUID

/**
 * Điều phối trạng thái giao diện trong QuizViewModel.
 * File này kết nối màn hình Compose với repository, cập nhật `uiState` và phát event một lần cho các thao tác điều hướng hoặc thông báo.
 * Đây là nơi tập trung phần lớn logic trình bày và điều phối nghiệp vụ ở phía ứng dụng Android.
 */

class QuizViewModel(
    private val repository: CurriculumRepository = CurriculumRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(QuizUiState())
    val uiState: StateFlow<QuizUiState> = _uiState.asStateFlow()

    private val _eventChannel = Channel<QuizEvent>(Channel.BUFFERED)
    val events = _eventChannel.receiveAsFlow()

    /**
     * Thực hiện phần xử lý chính của luồng nghiệp vụ hoặc giao diện tương ứng.
     * Hàm này chủ yếu cập nhật `uiState`, gọi repository và phát event cho giao diện khi cần.
     */

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

    /**
     * Xử lý một sự kiện giao diện và cập nhật state hoặc event liên quan.
     * Hàm này chủ yếu cập nhật `uiState`, gọi repository và phát event cho giao diện khi cần.
     */

    fun onTitleChange(value: String) = _uiState.update { it.copy(title = value, titleError = null) }
    /**
     * Xử lý một sự kiện giao diện và cập nhật state hoặc event liên quan.
     * Hàm này chủ yếu cập nhật `uiState`, gọi repository và phát event cho giao diện khi cần.
     */

    fun onDescriptionChange(value: String) = _uiState.update { it.copy(description = value) }
    /**
     * Xử lý một sự kiện giao diện và cập nhật state hoặc event liên quan.
     * Hàm này chủ yếu cập nhật `uiState`, gọi repository và phát event cho giao diện khi cần.
     */

    fun onDurationChange(value: String) = _uiState.update { it.copy(durationMinutes = value, durationError = null) }
    /**
     * Xử lý một sự kiện giao diện và cập nhật state hoặc event liên quan.
     * Hàm này chủ yếu cập nhật `uiState`, gọi repository và phát event cho giao diện khi cần.
     */

    fun onPassingScoreChange(value: String) = _uiState.update { it.copy(passingScore = value, passingScoreError = null) }

    /**
     * Thêm dữ liệu hoặc đối tượng mới vào luồng xử lý hiện tại.
     * Hàm này chủ yếu cập nhật `uiState`, gọi repository và phát event cho giao diện khi cần.
     */

    fun addQuestion() {
        val newQuestion = Question(
            id = UUID.randomUUID().toString(),
            text = "",
            options = listOf("", "", "", ""),
            correctAnswerIndex = 0
        )
        _uiState.update { it.copy(questions = it.questions + newQuestion, questionsError = null) }
    }

    /**
     * Loại bỏ phần tử tương ứng khỏi tập dữ liệu đang quản lý.
     * Hàm này chủ yếu cập nhật `uiState`, gọi repository và phát event cho giao diện khi cần.
     */

    fun removeQuestion(questionId: String) {
        _uiState.update { it.copy(questions = it.questions.filter { q -> q.id != questionId }) }
    }

    /**
     * Cập nhật dữ liệu hiện có và đồng bộ lại trạng thái liên quan.
     * Hàm này chủ yếu cập nhật `uiState`, gọi repository và phát event cho giao diện khi cần.
     */

    fun updateQuestionText(questionId: String, text: String) {
        _uiState.update { state ->
            state.copy(questions = state.questions.map { q ->
                if (q.id == questionId) q.copy(text = text) else q
            })
        }
    }

    /**
     * Cập nhật dữ liệu hiện có và đồng bộ lại trạng thái liên quan.
     * Hàm này chủ yếu cập nhật `uiState`, gọi repository và phát event cho giao diện khi cần.
     */

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

    /**
     * Cập nhật dữ liệu hiện có và đồng bộ lại trạng thái liên quan.
     * Hàm này chủ yếu cập nhật `uiState`, gọi repository và phát event cho giao diện khi cần.
     */

    fun updateCorrectAnswer(questionId: String, index: Int) {
        _uiState.update { state ->
            state.copy(questions = state.questions.map { q ->
                if (q.id == questionId) q.copy(correctAnswerIndex = index) else q
            })
        }
    }

    /**
     * Thực hiện phần xử lý chính của luồng nghiệp vụ hoặc giao diện tương ứng.
     * Hàm này chủ yếu cập nhật `uiState`, gọi repository và phát event cho giao diện khi cần.
     */

    fun importQuestionsFromFile(inputStream: InputStream, fileName: String?, mimeType: String?) {
        viewModelScope.launch {
            _uiState.update { it.copy(isImporting = true, questionsError = null) }
            try {
                val ext = fileName?.substringAfterLast('.', "")?.lowercase()
                val normalizedMimeType = mimeType?.lowercase()
                val isCsvByMime = normalizedMimeType == "text/csv" ||
                    normalizedMimeType == "application/csv" ||
                    normalizedMimeType == "text/comma-separated-values" ||
                    normalizedMimeType == "text/plain"

                val questions = if (ext == "csv" || isCsvByMime) {
                    parseCsvQuestions(inputStream)
                } else {
                    sendEvent(QuizEvent.ShowSnackbar("Hiện chỉ hỗ trợ CSV, hãy xuất Excel ra CSV rồi nhập lại"))
                    return@launch
                }

                if (questions.isEmpty()) {
                    sendEvent(QuizEvent.ShowSnackbar("File không có câu hỏi hợp lệ"))
                    return@launch
                }

                _uiState.update { it.copy(questions = questions, questionsError = null) }
                sendEvent(QuizEvent.ShowSnackbar("Đã nhập ${questions.size} câu hỏi"))
            } catch (e: Exception) {
                sendEvent(QuizEvent.ShowSnackbar("Không thể đọc file: ${e.message ?: "Lỗi không xác định"}"))
            } finally {
                _uiState.update { it.copy(isImporting = false) }
            }
        }
    }

    /**
     * Thực hiện phần xử lý chính của luồng nghiệp vụ hoặc giao diện tương ứng.
     * Hàm này chủ yếu cập nhật `uiState`, gọi repository và phát event cho giao diện khi cần.
     */

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

    private fun parseCsvQuestions(inputStream: InputStream): List<Question> {
        return inputStream.bufferedReader().useLines { lines ->
            lines
                .drop(1) // skip header
                .mapNotNull { line ->
                    val cols = line.split(',').map { it.trim() }
                    if (cols.size < 6) return@mapNotNull null
                    val questionText = cols[0]
                    val options = cols.subList(1, 5)
                    if (questionText.isBlank() || options.any { it.isBlank() }) return@mapNotNull null
                    val correctIndex = parseCorrectIndex(cols[5])

                    Question(
                        id = UUID.randomUUID().toString(),
                        text = questionText,
                        options = options,
                        correctAnswerIndex = correctIndex
                    )
                }
                .toList()
        }
    }

    private fun parseCorrectIndex(raw: String): Int {
        val value = raw.trim()
        if (value.isBlank()) return 0

        val numeric = value.toIntOrNull()
        if (numeric != null && numeric in 1..4) return numeric - 1

        return when (value.first().uppercaseChar()) {
            'A' -> 0
            'B' -> 1
            'C' -> 2
            'D' -> 3
            else -> 0
        }
    }

    private fun sendEvent(event: QuizEvent) {
        viewModelScope.launch { _eventChannel.send(event) }
    }
}
