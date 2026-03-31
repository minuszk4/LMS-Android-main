package com.example.lms.ui.screen.student

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.lms.ui.component.TopBar
import com.example.lms.util.QuizAttemptEvent
import com.example.lms.util.QuizAttemptUiState
import com.example.lms.viewmodel.QuizAttemptViewModel
import kotlinx.coroutines.flow.collectLatest

@Composable
fun QuizAttemptRoute(
    courseId: String,
    quizId: String,
    userId: String,
    viewModel: QuizAttemptViewModel,
    onBackClick: () -> Unit,
    onNavigateToResult: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(userId, courseId, quizId) {
        viewModel.loadQuiz(userId = userId, courseId = courseId, quizId = quizId)
    }

    LaunchedEffect(viewModel) {
        viewModel.event.collectLatest { event ->
            when (event) {
                QuizAttemptEvent.QuizTimeUp -> viewModel.submitQuiz()
                QuizAttemptEvent.SubmitQuizSuccess -> onNavigateToResult()
                is QuizAttemptEvent.ShowError -> {
                    snackbarHostState.showSnackbar(message = event.message)
                }
                QuizAttemptEvent.RetakeQuizSuccess -> Unit
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        QuizAttemptScreen(
            uiState = uiState,
            onBackClick = onBackClick,
            onAnswerSelected = viewModel::selectAnswer,
            onPreviousClick = viewModel::previousQuestion,
            onNextClick = viewModel::nextQuestion,
            onSubmitClick = viewModel::submitQuiz
        )

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuizAttemptScreen(
    uiState: QuizAttemptUiState,
    onBackClick: () -> Unit,
    onAnswerSelected: (questionIndex: Int, answerIndex: Int) -> Unit,
    onPreviousClick: () -> Unit,
    onNextClick: () -> Unit,
    onSubmitClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val totalQuestions = uiState.questions.size
    if (totalQuestions == 0) {
        Scaffold(
            topBar = {
                TopBar(title = uiState.quizTitle, onBackClick = onBackClick)
            },
            containerColor = Color(0xFFF5F6FA)
        ) { paddingValues ->
            Box(
                modifier = modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Text("Bài kiểm tra chưa có câu hỏi", color = Color(0xFF6B7280))
            }
        }
        return
    }

    val safeIndex = uiState.currentQuestionIndex.coerceIn(0, totalQuestions - 1)
    val currentQuestion = uiState.questions[safeIndex]
    val isLastQuestion = safeIndex == totalQuestions - 1
    val isFirstQuestion = safeIndex == 0

    val answeredCount = uiState.selectedAnswers.size
    val canSubmit = answeredCount == totalQuestions
    val progress = (answeredCount.toFloat() / totalQuestions.toFloat()).coerceIn(0f, 1f)

    Scaffold(
        topBar = {
            TopBar(title = uiState.quizTitle, onBackClick = onBackClick)
        },
        containerColor = Color(0xFFF5F6FA),
        bottomBar = {
            QuizAttemptBottomBar(
                isFirstQuestion = isFirstQuestion,
                isLastQuestion = isLastQuestion,
                onPreviousClick = {
                    if (!isFirstQuestion) {
                        onPreviousClick()
                    }
                },
                onNextOrSubmitClick = {
                    if (isLastQuestion) {
                        onSubmitClick()
                    } else {
                        onNextClick()
                    }
                },
                isSubmitEnabled = canSubmit
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Tiến trình",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFF6B7280)
                            )
                            Text(
                                text = "$answeredCount/$totalQuestions",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF4B5CC4)
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        QuizProgressBar(
                            progress = progress,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .wrapContentWidth(Alignment.End)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFFF0F1FF))
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Default.Timer,
                                contentDescription = null,
                                tint = if (uiState.remainingSeconds <= 60)
                                    Color(0xFFEF4444)
                                else
                                    Color(0xFF4B5CC4),
                                modifier = Modifier.size(18.dp)
                            )

                            Spacer(modifier = Modifier.width(6.dp))

                            Text(
                                text = formatTime(uiState.remainingSeconds),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (uiState.remainingSeconds <= 60)
                                    Color(0xFFEF4444)
                                else
                                    Color(0xFF4B5CC4)
                            )
                        }
                    }
                }
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Câu ${safeIndex + 1}",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF4B5CC4)
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Text(
                            text = currentQuestion.text,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            lineHeight = 26.sp,
                            color = Color(0xFF1A1D2E)
                        )

                        Spacer(modifier = Modifier.height(18.dp))

                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            currentQuestion.options.forEachIndexed { index, option ->
                                QuizOption(
                                    text = option,
                                    isSelected = uiState.selectedAnswers[safeIndex] == index,
                                    onClick = { onAnswerSelected(safeIndex, index) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun QuizOption(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val borderColor = if (isSelected) Color(0xFF4B5CC4) else Color(0xFFE5E7EB)
    val backgroundColor = if (isSelected) Color(0xFFF0F1FF) else Color.White

    Card(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 62.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        border = BorderStroke(1.dp, borderColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {

            RadioButton(
                selected = isSelected,
                onClick = onClick,
                colors = RadioButtonDefaults.colors(
                    selectedColor = Color(0xFF4B5CC4)
                )
            )

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = text,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                lineHeight = 20.sp,
                color = if (isSelected) Color(0xFF4B5CC4) else Color(0xFF1A1D2E)
            )
        }
    }
}

@Composable
private fun QuizAttemptBottomBar(
    isFirstQuestion: Boolean,
    isLastQuestion: Boolean,
    onPreviousClick: () -> Unit,
    onNextOrSubmitClick: () -> Unit,
    isSubmitEnabled: Boolean
) {
    Surface(modifier = Modifier.fillMaxWidth(), shadowElevation = 0.dp, color = Color.White) {
        HorizontalDivider(thickness = 1.dp, color = Color(0xFFE2E4ED))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = onPreviousClick,
                enabled = !isFirstQuestion,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = Color(0xFF4B5CC4),
                    disabledContainerColor = Color(0xFFF3F4F6),
                    disabledContentColor = Color(0xFF9CA3AF)
                ),
                border = BorderStroke(1.dp, Color(0xFFE5E7EB)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(50.dp)
            ) {
                Text(text = "Trước", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            }

            Button(
                onClick = onNextOrSubmitClick,
                enabled = !isLastQuestion || isSubmitEnabled,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF4B5CC4),
                    contentColor = Color.White,
                    disabledContainerColor = Color(0xFFA5AFEB),
                    disabledContentColor = Color.White
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(50.dp)
            ) {
                Text(
                    text = if (isLastQuestion) "Nộp bài" else "Tiếp",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp
                )
            }
        }
    }
}

private fun formatTime(totalSeconds: Int): String {
    val safeSeconds = totalSeconds.coerceAtLeast(0)
    val minutes = safeSeconds / 60
    val seconds = safeSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}

@Composable
private fun QuizProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    trackColor: Color = Color(0xFFE5E7EB),
    progressColor: Color = Color(0xFF4B5CC4)
) {
    val safeProgress = progress.coerceIn(0f, 1f)
    val animatedProgress by animateFloatAsState(
        targetValue = safeProgress,
        animationSpec = tween(durationMillis = 450, easing = FastOutSlowInEasing),
        label = "QuizProgress"
    )

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(trackColor)
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(animatedProgress)
                .clip(RoundedCornerShape(999.dp))
                .background(progressColor)
        )
    }
}

