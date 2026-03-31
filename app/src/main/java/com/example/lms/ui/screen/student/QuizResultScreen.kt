package com.example.lms.ui.screen.student

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.lms.viewmodel.QuizAttemptViewModel

@Composable
fun QuizResultRoute(
	viewModel: QuizAttemptViewModel,
	studentName: String,
	onContinueClick: () -> Unit,
	onReviewAnswerClick: () -> Unit,
	onRetakeClick: () -> Unit
) {
	val uiState by viewModel.uiState.collectAsStateWithLifecycle()
	val elapsedSeconds = (uiState.durationSeconds - uiState.remainingSeconds).coerceAtLeast(0)

	QuizResultScreen(
		studentName = studentName,
		scorePercent = uiState.score,
		elapsedSeconds = elapsedSeconds,
		correctCount = uiState.correctCount,
		wrongCount = uiState.wrongCount,
		onContinueClick = onContinueClick,
		onReviewAnswerClick = onReviewAnswerClick,
		onRetakeClick = onRetakeClick
	)
}

private val Indigo = Color(0xFF4B5CC4)
private val SurfaceGray = Color(0xFFF5F6FA)
private val CardWhite = Color(0xFFFFFFFF)
private val TextPrimary = Color(0xFF151724)
private val TextSecondary = Color(0xFF6B7280)
private val RingTrack = Color(0xFFE8EAF2)
private val SuccessGreen = Color(0xFF16A34A)
private val ErrorRed = Color(0xFFEF4444)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuizResultScreen(
	studentName: String,
	scorePercent: Int,
	elapsedSeconds: Int,
	correctCount: Int,
	wrongCount: Int,
	onContinueClick: () -> Unit,
	onReviewAnswerClick: () -> Unit,
	onRetakeClick: () -> Unit
) {
	Scaffold(
		topBar = {
			CenterAlignedTopAppBar(
				title = {
					Text(
						text = "Kết quả",
						fontWeight = FontWeight.Bold,
						fontSize = 20.sp,
						color = TextPrimary
					)
				},
				colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
					containerColor = SurfaceGray
				)
			)
		},
		containerColor = SurfaceGray
	) { paddingValues ->
		LazyColumn(
			modifier = Modifier
				.fillMaxSize()
				.padding(paddingValues),
			contentPadding = PaddingValues(horizontal = 16.dp, vertical = 20.dp),
			verticalArrangement = Arrangement.spacedBy(14.dp)
		) {
			item {
				Column(
					modifier = Modifier.fillMaxWidth(),
					horizontalAlignment = Alignment.CenterHorizontally
				) {
					Text(
						text = "Chúc mừng!\n$studentName",
						style = MaterialTheme.typography.headlineMedium,
						fontWeight = FontWeight.ExtraBold,
						color = TextPrimary,
						textAlign = TextAlign.Center
					)
					Spacer(modifier = Modifier.height(8.dp))
					Text(
						text = "Bạn đã hoàn thành bài kiểm tra",
						style = MaterialTheme.typography.titleMedium,
						color = TextSecondary,
						textAlign = TextAlign.Center
					)
				}
			}

			item {
				ScoreRing(percent = scorePercent)
			}

			item {
				StatsRow(
					elapsedSeconds = elapsedSeconds,
					correctCount = correctCount,
					wrongCount = wrongCount
				)
			}

			item {
				Button(
					onClick = onContinueClick,
					modifier = Modifier
						.fillMaxWidth()
						.height(58.dp),
					shape = RoundedCornerShape(14.dp),
					colors = ButtonDefaults.buttonColors(containerColor = Indigo)
				) {
					Row(
						verticalAlignment = Alignment.CenterVertically,
						horizontalArrangement = Arrangement.spacedBy(8.dp)
					) {
						Text(
							text = "Tiếp tục bài học tiếp theo",
							fontSize = 16.sp,
							fontWeight = FontWeight.Bold,
							color = Color.White
						)
						Icon(
							imageVector = Icons.AutoMirrored.Filled.ArrowForward,
							contentDescription = null,
							tint = Color.White
						)
					}
				}
			}

			item {
				Box(
					modifier = Modifier
						.fillMaxWidth()
						.height(58.dp)
						.border(1.5.dp, Indigo.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
						.background(CardWhite, RoundedCornerShape(14.dp))
						.clickable(onClick = onReviewAnswerClick),
					contentAlignment = Alignment.Center
				) {
					Row(
						verticalAlignment = Alignment.CenterVertically,
						horizontalArrangement = Arrangement.spacedBy(8.dp)
					) {
						Text(
							text = "Xem đáp án",
							color = Indigo,
							fontWeight = FontWeight.Bold,
							fontSize = 16.sp
						)
						Icon(
							imageVector = Icons.Default.TaskAlt,
							contentDescription = null,
							tint = Indigo
						)
					}
				}
			}

			item {
				Row(
					modifier = Modifier
						.fillMaxWidth()
						.padding(top = 6.dp),
					horizontalArrangement = Arrangement.Center,
					verticalAlignment = Alignment.CenterVertically
				) {
					Icon(
						imageVector = Icons.AutoMirrored.Filled.RotateRight,
						contentDescription = null,
						tint = TextSecondary,
						modifier = Modifier.size(18.dp)
					)
					Spacer(modifier = Modifier.size(6.dp))
					Text(
						text = "Làm lại",
						color = TextSecondary,
						fontWeight = FontWeight.SemiBold,
						modifier = Modifier.clickable(onClick = onRetakeClick)
					)
				}
			}
		}
	}
}

@Composable
private fun ScoreRing(percent: Int) {
	val clamped = percent.coerceIn(0, 100)
	val progress = clamped / 100f

	Box(
		modifier = Modifier
			.fillMaxWidth()
			.padding(top = 8.dp, bottom = 8.dp),
		contentAlignment = Alignment.Center
	) {
		Box(
			modifier = Modifier
				.size(188.dp)
				.drawBehind {
					val stroke = 14.dp.toPx()
					drawArc(
						color = RingTrack,
						startAngle = -90f,
						sweepAngle = 360f,
						useCenter = false,
						topLeft = Offset(stroke / 2f, stroke / 2f),
						size = Size(size.width - stroke, size.height - stroke),
						style = Stroke(width = stroke, cap = StrokeCap.Round)
					)
					drawArc(
						color = Color(0xFF4B5CC4),
						startAngle = -90f,
						sweepAngle = 360f * progress,
						useCenter = false,
						topLeft = Offset(stroke / 2f, stroke / 2f),
						size = Size(size.width - stroke, size.height - stroke),
						style = Stroke(width = stroke, cap = StrokeCap.Round)
					)
				},
			contentAlignment = Alignment.Center
		) {
			Column(horizontalAlignment = Alignment.CenterHorizontally) {
				Text(
					text = "$clamped%",
					fontSize = 52.sp,
					color = TextPrimary,
					fontWeight = FontWeight.ExtraBold
				)
				Text(
					text = "SCORE",
					color = Indigo.copy(alpha = 0.7f),
					letterSpacing = 2.sp,
					fontWeight = FontWeight.Bold,
					fontSize = 14.sp
				)
			}
		}
	}
}

@Composable
private fun StatsRow(
	elapsedSeconds: Int,
	correctCount: Int,
	wrongCount: Int
) {
	Row(
		modifier = Modifier.fillMaxWidth(),
		horizontalArrangement = Arrangement.spacedBy(10.dp)
	) {
		StatCard(
			title = "Thời gian",
			value = formatElapsedTime(elapsedSeconds),
			valueColor = TextPrimary,
			modifier = Modifier.weight(1f)
		)
		StatCard(
			title = "Đúng",
			value = correctCount.toString(),
			valueColor = SuccessGreen,
			icon = {
				Icon(
					imageVector = Icons.Default.CheckCircle,
					contentDescription = null,
					tint = SuccessGreen,
					modifier = Modifier.size(14.dp)
				)
			},
			modifier = Modifier.weight(1f)
		)
		StatCard(
			title = "Sai",
			value = wrongCount.toString(),
			valueColor = ErrorRed,
			icon = {
				Icon(
					imageVector = Icons.Default.Close,
					contentDescription = null,
					tint = ErrorRed,
					modifier = Modifier.size(14.dp)
				)
			},
			modifier = Modifier.weight(1f)
		)
	}
}

@Composable
private fun StatCard(
	title: String,
	value: String,
	valueColor: Color,
	modifier: Modifier = Modifier,
	icon: (@Composable () -> Unit)? = null
) {
	Card(
		modifier = modifier,
		shape = RoundedCornerShape(14.dp),
		colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F2F6))
	) {
		Column(
			modifier = Modifier
				.fillMaxWidth()
				.padding(horizontal = 10.dp, vertical = 12.dp),
			horizontalAlignment = Alignment.CenterHorizontally,
		) {
			Text(
				text = title,
				color = if (title == "Đúng") SuccessGreen else if (title == "Sai") ErrorRed else TextSecondary,
				fontSize = 13.sp,
				fontWeight = FontWeight.Medium
			)
			Spacer(modifier = Modifier.height(4.dp))
			Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
				if (icon != null) icon()
				Text(
					text = value,
					color = valueColor,
					fontWeight = FontWeight.Bold,
					fontSize = 18.sp
				)
			}
		}
	}
}

private fun formatElapsedTime(totalSeconds: Int): String {
	val safe = totalSeconds.coerceAtLeast(0)
	val minutes = safe / 60
	val seconds = safe % 60
	return "${minutes}m ${seconds}s"
}

