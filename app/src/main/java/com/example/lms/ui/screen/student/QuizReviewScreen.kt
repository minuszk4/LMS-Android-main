package com.example.lms.ui.screen.student

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.lms.data.model.Question
import com.example.lms.ui.component.TopBar
import com.example.lms.viewmodel.QuizAttemptViewModel

@Composable
fun QuizReviewRoute(
	viewModel: QuizAttemptViewModel,
	onBackClick: () -> Unit,
	onContinueClick: (() -> Unit)? = null
) {
	val uiState by viewModel.uiState.collectAsStateWithLifecycle()

	QuizReviewScreen(
		quizTitle = uiState.quizTitle,
		questions = uiState.questions,
		selectedAnswers = uiState.selectedAnswers,
		onBackClick = onBackClick,
		onContinueClick = onContinueClick
	)
}

private val Indigo = Color(0xFF4B5CC4)
private val SurfaceGray = Color(0xFFF5F6FA)
private val CardWhite = Color(0xFFFFFFFF)
private val TextPrimary = Color(0xFF1A1D2E)
private val TextSecondary = Color(0xFF6B7280)
private val SuccessGreen = Color(0xFF16A34A)
private val ErrorRed = Color(0xFFEF4444)

@Composable
fun QuizReviewScreen(
	quizTitle: String,
	questions: List<Question>,
	selectedAnswers: Map<Int, Int>,
	onBackClick: () -> Unit,
	onContinueClick: (() -> Unit)? = null
) {
	val answeredCount = selectedAnswers.size
	val correctCount = questions.countIndexed { index, question ->
		selectedAnswers[index] == question.correctAnswerIndex
	}

	Scaffold(
		topBar = {
			TopBar(
				title = quizTitle.ifBlank { "Xem đáp án" },
				onBackClick = onBackClick
			)
		},
		containerColor = SurfaceGray,
		bottomBar = {
			if (onContinueClick != null) {
				QuizReviewBottomBar(onContinueClick = onContinueClick)
			}
		}
	) { paddingValues ->
		if (questions.isEmpty()) {
			EmptyReviewState(paddingValues = paddingValues)
			return@Scaffold
		}

		LazyColumn(
			modifier = Modifier
				.fillMaxSize()
				.padding(paddingValues),
			contentPadding = PaddingValues(16.dp),
			verticalArrangement = Arrangement.spacedBy(12.dp)
		) {
			item {
				ReviewSummaryCard(
					totalQuestions = questions.size,
					answeredCount = answeredCount,
					correctCount = correctCount
				)
			}

			itemsIndexed(questions) { index, question ->
				ReviewQuestionCard(
					questionNumber = index + 1,
					question = question,
					selectedAnswerIndex = selectedAnswers[index]
				)
			}
		}
	}
}

@Composable
private fun ReviewSummaryCard(
	totalQuestions: Int,
	answeredCount: Int,
	correctCount: Int
) {
	Card(
		modifier = Modifier.fillMaxWidth(),
		shape = RoundedCornerShape(14.dp),
		colors = CardDefaults.cardColors(containerColor = CardWhite)
	) {
		Column(modifier = Modifier.padding(16.dp)) {
			Text(
				text = "Tổng quan bài làm",
				color = TextPrimary,
				fontWeight = FontWeight.Bold,
				fontSize = 16.sp
			)
			Spacer(modifier = Modifier.height(10.dp))

			Row(
				modifier = Modifier.fillMaxWidth(),
				horizontalArrangement = Arrangement.SpaceBetween
			) {
				Text("Đã trả lời: $answeredCount/$totalQuestions", color = TextSecondary)
				Text("Đúng: $correctCount", color = SuccessGreen, fontWeight = FontWeight.SemiBold)
			}
		}
	}
}

@Composable
private fun ReviewQuestionCard(
	questionNumber: Int,
	question: Question,
	selectedAnswerIndex: Int?
) {
	val correctAnswerIndex = question.correctAnswerIndex

	Card(
		modifier = Modifier.fillMaxWidth(),
		shape = RoundedCornerShape(14.dp),
		colors = CardDefaults.cardColors(containerColor = CardWhite)
	) {
		Column(modifier = Modifier.padding(16.dp)) {
			Text(
				text = "Câu $questionNumber",
				color = Indigo,
				fontWeight = FontWeight.Bold,
				fontSize = 16.sp
			)

			Spacer(modifier = Modifier.height(8.dp))

			Text(
				text = question.text,
				style = MaterialTheme.typography.titleMedium,
				color = TextPrimary,
				lineHeight = 24.sp
			)

			Spacer(modifier = Modifier.height(14.dp))

			Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
				question.options.forEachIndexed { optionIndex, optionText ->
					val state = resolveOptionState(
						optionIndex = optionIndex,
						selectedAnswerIndex = selectedAnswerIndex,
						correctAnswerIndex = correctAnswerIndex
					)
					ReviewOptionRow(
						text = optionText,
						state = state
					)
				}
			}
		}
	}
}

@Composable
private fun ReviewOptionRow(
	text: String,
	state: OptionReviewState
) {
	val background = when (state) {
		OptionReviewState.Correct -> SuccessGreen.copy(alpha = 0.10f)
		OptionReviewState.WrongSelected -> ErrorRed.copy(alpha = 0.10f)
		OptionReviewState.Normal -> Color(0xFFF8FAFC)
	}
	val borderColor = when (state) {
		OptionReviewState.Correct -> SuccessGreen.copy(alpha = 0.35f)
		OptionReviewState.WrongSelected -> ErrorRed.copy(alpha = 0.35f)
		OptionReviewState.Normal -> Color(0xFFE5E7EB)
	}
	val textColor = when (state) {
		OptionReviewState.Correct -> SuccessGreen
		OptionReviewState.WrongSelected -> ErrorRed
		OptionReviewState.Normal -> TextPrimary
	}

	Card(
		modifier = Modifier.fillMaxWidth(),
		shape = RoundedCornerShape(10.dp),
		colors = CardDefaults.cardColors(containerColor = background),
		border = androidx.compose.foundation.BorderStroke(1.dp, borderColor)
	) {
		Row(
			modifier = Modifier
				.fillMaxWidth()
				.padding(horizontal = 12.dp, vertical = 10.dp),
			verticalAlignment = Alignment.CenterVertically,
			horizontalArrangement = Arrangement.spacedBy(8.dp)
		) {
			when (state) {
				OptionReviewState.Correct -> Icon(
					imageVector = Icons.Default.CheckCircle,
					contentDescription = null,
					tint = SuccessGreen,
					modifier = Modifier.size(18.dp)
				)

				OptionReviewState.WrongSelected -> Icon(
					imageVector = Icons.Default.Close,
					contentDescription = null,
					tint = ErrorRed,
					modifier = Modifier.size(18.dp)
				)

				OptionReviewState.Normal -> Icon(
					imageVector = Icons.Default.RadioButtonUnchecked,
					contentDescription = null,
					tint = TextSecondary,
					modifier = Modifier.size(18.dp)
				)
			}

			Text(
				text = text,
				color = textColor,
				fontSize = 14.sp,
				lineHeight = 20.sp,
				fontWeight = if (state == OptionReviewState.Normal) FontWeight.Normal else FontWeight.SemiBold
			)
		}
	}
}

@Composable
private fun QuizReviewBottomBar(onContinueClick: () -> Unit) {
	Box(
		modifier = Modifier
			.fillMaxWidth()
			.background(CardWhite)
			.navigationBarsPadding()
			.padding(horizontal = 16.dp, vertical = 12.dp)
	) {
		Button(
			onClick = onContinueClick,
			modifier = Modifier
				.fillMaxWidth()
				.height(50.dp),
			shape = RoundedCornerShape(12.dp),
			colors = ButtonDefaults.buttonColors(containerColor = Indigo)
		) {
			Row(
				verticalAlignment = Alignment.CenterVertically,
				horizontalArrangement = Arrangement.spacedBy(8.dp)
			) {
				Text(
					text = "Tiếp tục bài học",
					color = Color.White,
					fontWeight = FontWeight.SemiBold,
					fontSize = 15.sp
				)
				Icon(
					imageVector = Icons.AutoMirrored.Filled.ArrowForward,
					contentDescription = null,
					tint = Color.White
				)
			}
		}
	}
}

@Composable
private fun EmptyReviewState(paddingValues: PaddingValues) {
	Box(
		modifier = Modifier
			.fillMaxSize()
			.padding(paddingValues),
		contentAlignment = Alignment.Center
	) {
		Text(
			text = "Chưa có dữ liệu đáp án",
			color = TextSecondary,
			fontSize = 14.sp
		)
	}
}

private enum class OptionReviewState {
	Correct,
	WrongSelected,
	Normal
}

private fun resolveOptionState(
	optionIndex: Int,
	selectedAnswerIndex: Int?,
	correctAnswerIndex: Int
): OptionReviewState {
	return when {
		optionIndex == correctAnswerIndex -> OptionReviewState.Correct
		selectedAnswerIndex != null && selectedAnswerIndex == optionIndex -> OptionReviewState.WrongSelected
		else -> OptionReviewState.Normal
	}
}

private inline fun <T> Iterable<T>.countIndexed(predicate: (index: Int, T) -> Boolean): Int {
    var count = 0
    for ((index, item) in this.withIndex()) {
        if (predicate(index, item)) count++
    }
    return count
}
