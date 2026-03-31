package com.example.lms.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.lms.data.model.CurriculumItem
import com.example.lms.data.model.Question
import com.example.lms.data.repository.CurriculumRepository
import com.example.lms.data.repository.ProgressRepository
import com.example.lms.data.repository.QuizAttemptRepository
import com.example.lms.util.QuizAttemptEvent
import com.example.lms.util.QuizAttemptUiState
import com.example.lms.util.ResultState
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class QuizAttemptViewModel(
    private val quizAttemptRepository: QuizAttemptRepository = QuizAttemptRepository(),
    private val progressRepository: ProgressRepository = ProgressRepository(),
    private val curriculumRepository: CurriculumRepository = CurriculumRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(QuizAttemptUiState())
    val uiState: StateFlow<QuizAttemptUiState> = _uiState.asStateFlow()

    private val _event = MutableSharedFlow<QuizAttemptEvent>()
    val event = _event.asSharedFlow()

    private var timerJob: Job? = null
    private var loadQuizJob: Job? = null

    // ─────────────────────────────────────────
    // INIT - Load Quiz Data
    // ─────────────────────────────────────────

    fun loadQuiz(
        userId: String,
        courseId: String,
        quizId: String
    ) {
        val currentState = _uiState.value

        val isSameQuizLoaded = currentState.quizId == quizId &&
            currentState.courseId == courseId &&
            currentState.userId == userId &&
            currentState.questions.isNotEmpty() &&
            !currentState.isLoadingQuiz

        if (isSameQuizLoaded) {
            // Retake flow da reset state trong VM, khong can load lai.
            if (currentState.isRetaking) {
                _uiState.update { it.copy(isRetaking = false) }
                return
            }

            // Dang lam do thi giu nguyen de tranh mat state khi quay lai man hinh.
            val hasInProgressAttempt = !currentState.showResults &&
                (currentState.selectedAnswers.isNotEmpty() ||
                    currentState.remainingSeconds < currentState.durationSeconds)

            if (hasInProgressAttempt) {
                return
            }
        }

        loadQuizJob?.cancel()
        pauseTimer()

        loadQuizJob = viewModelScope.launch {
            _uiState.update { state ->
                state.copy(
                    isLoadingQuiz = true,
                    loadErrorMessage = null,
                    isSubmitting = false,
                    showResults = false
                )
            }

            val (curriculumResult, progressResult) = coroutineScope {
                val curriculumDeferred = async { curriculumRepository.getCurriculum(courseId) }
                val progressDeferred = async { progressRepository.getQuizProgress(userId, quizId) }
                Pair(curriculumDeferred.await(), progressDeferred.await())
            }

            when (curriculumResult) {
                is ResultState.Success<*> -> {
                    val curriculumItems = (curriculumResult.data as? List<*>)
                        ?.filterIsInstance<CurriculumItem>()
                        .orEmpty()

                    val quiz = curriculumItems
                        .asSequence()
                        .filterIsInstance<CurriculumItem.QuizItem>()
                        .firstOrNull { it.quiz.id == quizId }
                        ?.quiz

                    if (quiz == null) {
                        _uiState.update { state ->
                            state.copy(
                                isLoadingQuiz = false,
                                loadErrorMessage = "Bài kiểm tra không tồn tại"
                            )
                        }
                        return@launch
                    }

                    val quizProgress = when (progressResult) {
                        is ResultState.Success<*> -> progressResult.data as? com.example.lms.data.model.QuizProgress
                        else -> null
                    }

                    initializeQuizInternal(
                        userId = userId,
                        courseId = courseId,
                        quizId = quizId,
                        quizTitle = quiz.title,
                        questions = quiz.questions,
                        durationSeconds = quiz.durationMinutes * 60,
                        passingScore = quiz.passingScore,
                        quizProgress = quizProgress
                    )

                    _uiState.update { state ->
                        state.copy(
                            isLoadingQuiz = false,
                            loadErrorMessage = null
                        )
                    }
                }

                is ResultState.Error -> {
                    _uiState.update { state ->
                        state.copy(
                            isLoadingQuiz = false,
                            loadErrorMessage = curriculumResult.message
                        )
                    }
                }

                is ResultState.Loading -> {
                    // Không xảy ra ở đây vì getCurriculum trả về ngay, nhưng handle cho đủ nhánh
                    _uiState.update { state ->
                        state.copy(
                            isLoadingQuiz = true
                        )
                    }
                }
            }
        }
    }

    // Hàm nội bộ giữ nguyên logic khởi tạo attempt như trước đây
    private fun initializeQuizInternal(
        userId: String,
        courseId: String,
        quizId: String,
        quizTitle: String,
        questions: List<Question>,
        durationSeconds: Int,
        passingScore: Int,
        quizProgress: com.example.lms.data.model.QuizProgress?,
        autoStartTimer: Boolean = true
    ) {
        _uiState.update { state ->
            state.copy(
                userId = userId,
                courseId = courseId,
                quizId = quizId,
                quizTitle = quizTitle,
                questions = questions,
                durationSeconds = durationSeconds,
                passingScore = passingScore,
                quizProgress = quizProgress,
                remainingSeconds = durationSeconds,
                selectedAnswers = emptyMap(),
                currentQuestionIndex = 0,
                correctCount = 0,
                wrongCount = 0,
                score = 0,
                showResults = false,
                isRetaking = false,
                isSubmitting = false,
                isTimerRunning = false
            )
        }

        if (autoStartTimer) {
            startTimer()
        }
    }

    // ─────────────────────────────────────────
    // TIMER
    // ─────────────────────────────────────────

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            _uiState.update { it.copy(isTimerRunning = true) }

            while (_uiState.value.remainingSeconds > 0) {
                delay(1000)
                _uiState.update { state ->
                    state.copy(remainingSeconds = state.remainingSeconds - 1)
                }
            }

            // Time's up
            _uiState.update { it.copy(isTimerRunning = false) }
            _event.emit(QuizAttemptEvent.QuizTimeUp)
        }
    }

    fun pauseTimer() {
        timerJob?.cancel()
        _uiState.update { it.copy(isTimerRunning = false) }
    }

    fun resumeTimer() {
        if (_uiState.value.remainingSeconds > 0) {
            startTimer()
        }
    }

    // ─────────────────────────────────────────
    // NAVIGATION
    // ─────────────────────────────────────────

    fun nextQuestion() {
        val state = _uiState.value
        if (state.currentQuestionIndex < state.questions.size - 1) {
            _uiState.update { it.copy(currentQuestionIndex = it.currentQuestionIndex + 1) }
        }
    }

    fun previousQuestion() {
        if (_uiState.value.currentQuestionIndex > 0) {
            _uiState.update { it.copy(currentQuestionIndex = it.currentQuestionIndex - 1) }
        }
    }


    // ─────────────────────────────────────────
    // ANSWER SELECTION
    // ─────────────────────────────────────────

    fun selectAnswer(questionIndex: Int, answerIndex: Int) {
        _uiState.update { state ->
            val updatedAnswers = state.selectedAnswers.toMutableMap()
            updatedAnswers[questionIndex] = answerIndex
            state.copy(selectedAnswers = updatedAnswers)
        }
    }

    // ─────────────────────────────────────────
    // SUBMIT QUIZ
    // ─────────────────────────────────────────

    fun submitQuiz() {
        val state = _uiState.value
        if (state.isSubmitting || state.showResults) return

        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true) }
            pauseTimer()

            val submitState = _uiState.value
            val totalQuestions = submitState.questions.size
            if (totalQuestions <= 0) {
                _uiState.update { it.copy(isSubmitting = false) }
                _event.emit(QuizAttemptEvent.ShowError("Bài kiểm tra chưa có câu hỏi"))
                return@launch
            }

            val answeredCount = submitState.selectedAnswers.size
            if (answeredCount < totalQuestions) {
                val unansweredCount = totalQuestions - answeredCount
                _uiState.update { it.copy(isSubmitting = false) }
                _event.emit(
                    QuizAttemptEvent.ShowError(
                        "Bạn cần trả lời hết tất cả câu hỏi trước khi nộp. Còn $unansweredCount câu chưa trả lời."
                    )
                )
                return@launch
            }

            // Tính điểm
            val correctCount = calculateCorrectCount()
            val wrongCount = totalQuestions - correctCount
            val score = (correctCount * 100) / totalQuestions
            val orderedAnswers = (0 until totalQuestions).map { index ->
                submitState.selectedAnswers[index] ?: -1
            }

            // Submit attempt
            val submitResult = quizAttemptRepository.submitQuizAttempt(
                userId = submitState.userId,
                courseId = submitState.courseId,
                quizId = submitState.quizId,
                selectedAnswers = orderedAnswers,
                correctCount = correctCount,
                wrongCount = wrongCount,
                score = score,
                passingScore = submitState.passingScore
            )

            _uiState.update { it.copy(isSubmitting = false) }

            when (submitResult) {
                is ResultState.Success<*> -> {
                    val updatedProgress = submitResult.data as? com.example.lms.data.model.QuizProgress
                    if (updatedProgress == null) {
                        resumeTimer()
                        _event.emit(QuizAttemptEvent.ShowError("Dữ liệu tiến độ không hợp lệ"))
                        return@launch
                    }
                    pauseTimer()

                    _uiState.update { state ->
                        state.copy(
                            correctCount = correctCount,
                            wrongCount = wrongCount,
                            score = score,
                            showResults = true,
                            quizProgress = updatedProgress,
                            isRetaking = false
                        )
                    }

                    _event.emit(QuizAttemptEvent.SubmitQuizSuccess)
                }
                is ResultState.Error -> {
                    resumeTimer()
                    _event.emit(QuizAttemptEvent.ShowError(submitResult.message))
                }
                else -> {}
            }
        }
    }

    // ─────────────────────────────────────────
    // RETAKE QUIZ
    // ─────────────────────────────────────────

    fun retakeQuiz() {
        pauseTimer()

        _uiState.update { state ->
            state.copy(
                currentQuestionIndex = 0,
                selectedAnswers = emptyMap(),
                remainingSeconds = state.durationSeconds,
                correctCount = 0,
                wrongCount = 0,
                score = 0,
                showResults = false,
                isRetaking = true
            )
        }

        startTimer()
        viewModelScope.launch {
            _event.emit(QuizAttemptEvent.RetakeQuizSuccess)
        }
    }

    // ─────────────────────────────────────────
    // HELPER
    // ─────────────────────────────────────────

    private fun calculateCorrectCount(): Int {
        val state = _uiState.value
        var correctCount = 0

        state.questions.forEachIndexed { index, question ->
            val selectedAnswer = state.selectedAnswers[index]
            if (selectedAnswer == question.correctAnswerIndex) {
                correctCount++
            }
        }

        return correctCount
    }

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
        loadQuizJob?.cancel()
    }
}