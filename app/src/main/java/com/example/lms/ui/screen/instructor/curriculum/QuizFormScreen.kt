package com.example.lms.ui.screen.instructor.curriculum

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.lms.data.model.Question
import com.example.lms.ui.component.FormFieldWithValidation
import com.example.lms.ui.component.SectionCard
import com.example.lms.ui.component.TopBar
import com.example.lms.util.QuizEvent
import com.example.lms.viewmodel.QuizViewModel
import kotlinx.coroutines.flow.collectLatest

@Composable
fun QuizFormScreen(
    viewModel: QuizViewModel = viewModel(),
    onBackClick: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val primaryIndigo = Color(0xFF4B5CC4)
    var questionToDelete by remember { mutableStateOf<Question?>(null) }

    LaunchedEffect(Unit) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is QuizEvent.ShowSnackbar -> Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
                QuizEvent.SaveSuccess -> onBackClick()
            }
        }
    }

    Scaffold(
        topBar = {
            TopBar(
                title = if (uiState.isEditMode) "Chỉnh sửa bài kiểm tra" else "Tạo bài kiểm tra mới",
                onBackClick = onBackClick
            )
        },
        containerColor = Color(0xFFF8FAFC)
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // 1. Thông tin chung
            SectionCard(title = "Thông tin chung", icon = Icons.Outlined.Description) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    FormFieldWithValidation(
                        label = "Tiêu đề bài kiểm tra",
                        value = uiState.title,
                        onValueChange = viewModel::onTitleChange,
                        placeholder = "Ví dụ: Kiểm tra kiến thức Kotlin cơ bản",
                        error = uiState.titleError
                    )

                    FormFieldWithValidation(
                        label = "Mô tả",
                        value = uiState.description,
                        onValueChange = viewModel::onDescriptionChange,
                        placeholder = "Mô tả ngắn gọn về bài kiểm tra...",
                        minLines = 3
                    )
                    
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        FormFieldWithValidation(
                            label = "Thời gian (phút)",
                            value = uiState.durationMinutes,
                            onValueChange = viewModel::onDurationChange,
                            placeholder = "15",
                            modifier = Modifier.weight(1f),
                            error = uiState.durationError
                        )
                        FormFieldWithValidation(
                            label = "Điểm đạt (%)",
                            value = uiState.passingScore,
                            onValueChange = viewModel::onPassingScoreChange,
                            placeholder = "80",
                            modifier = Modifier.weight(1f),
                            error = uiState.passingScoreError
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 2. Danh sách câu hỏi
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Quiz, null, tint = primaryIndigo, modifier = Modifier.size(20.dp))
                    Text("Câu hỏi (${uiState.questions.size})", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E293B))
                }
                TextButton(onClick = viewModel::addQuestion) {
                    Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Thêm câu hỏi")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            uiState.questions.forEachIndexed { index, question ->
                QuestionItem(
                    index = index,
                    question = question,
                    onTextChange = { viewModel.updateQuestionText(question.id, it) },
                    onOptionChange = { optIdx, text -> viewModel.updateOptionText(question.id, optIdx, text) },
                    onCorrectAnswerChange = { viewModel.updateCorrectAnswer(question.id, it) },
                    onDelete = { questionToDelete = question }
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Hiển thị lỗi danh sách câu hỏi nếu có
            AnimatedVisibility(
                visible = uiState.questionsError != null,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Text(
                    text = uiState.questionsError ?: "",
                    color = Color(0xFFEF4444),
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 3. Nút Lưu
            Button(
                onClick = viewModel::save,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .height(56.dp),
                shape = RoundedCornerShape(12.dp),
                enabled = !uiState.isSaving,
                colors = ButtonDefaults.buttonColors(containerColor = primaryIndigo)
            ) {
                if (uiState.isSaving) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                } else {
                    Text(
                        text = if (uiState.isEditMode) "Cập nhật bài kiểm tra" else "Lưu bài kiểm tra",
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 16.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(40.dp))
        }
    }

    // Confirmation Dialog
    questionToDelete?.let { question ->
        AlertDialog(
            onDismissRequest = { questionToDelete = null },
            title = { Text("Xóa câu hỏi", fontWeight = FontWeight.Bold) },
            text = { Text("Bạn có chắc chắn muốn xóa câu hỏi này không?") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.removeQuestion(question.id)
                        questionToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                ) {
                    Text("Xóa", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { questionToDelete = null }) {
                    Text("Hủy", color = Color(0xFF64748B))
                }
            },
            containerColor = Color.White,
            shape = RoundedCornerShape(16.dp)
        )
    }
}

@Composable
private fun QuestionItem(
    index: Int,
    question: Question,
    onTextChange: (String) -> Unit,
    onOptionChange: (Int, String) -> Unit,
    onCorrectAnswerChange: (Int) -> Unit,
    onDelete: () -> Unit
) {
    val primaryIndigo = Color(0xFF4B5CC4)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        shadowElevation = 1.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Câu hỏi ${index + 1}", fontWeight = FontWeight.Bold, color = primaryIndigo, fontSize = 14.sp)
                IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Delete, null, tint = Color(0xFFEF4444), modifier = Modifier.size(18.dp))
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            OutlinedTextField(
                value = question.text,
                onValueChange = onTextChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Nhập nội dung câu hỏi...", fontSize = 14.sp) },
                shape = RoundedCornerShape(10.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color(0xFFF8FAFC),
                    unfocusedContainerColor = Color(0xFFF8FAFC),
                    focusedBorderColor = primaryIndigo,
                    unfocusedBorderColor = Color(0xFFE2E8F0)
                )
            )

            Spacer(modifier = Modifier.height(16.dp))
            Text("Các lựa chọn (Tích vào đáp án đúng)", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Color(0xFF64748B))
            Spacer(modifier = Modifier.height(8.dp))

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                question.options.forEachIndexed { optIdx, optionText ->
                    val isSelected = question.correctAnswerIndex == optIdx
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        RadioButton(
                            selected = isSelected,
                            onClick = { onCorrectAnswerChange(optIdx) },
                            colors = RadioButtonDefaults.colors(selectedColor = primaryIndigo)
                        )
                        OutlinedTextField(
                            value = optionText,
                            onValueChange = { onOptionChange(optIdx, it) },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text("Lựa chọn ${optIdx + 1}", fontSize = 13.sp) },
                            shape = RoundedCornerShape(10.dp),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = if (isSelected) primaryIndigo.copy(alpha = 0.05f) else Color(0xFFF8FAFC),
                                unfocusedContainerColor = if (isSelected) primaryIndigo.copy(alpha = 0.05f) else Color(0xFFF8FAFC),
                                focusedBorderColor = if (isSelected) primaryIndigo else Color(0xFFE2E8F0),
                                unfocusedBorderColor = if (isSelected) primaryIndigo.copy(alpha = 0.5f) else Color(0xFFE2E8F0)
                            )
                        )
                    }
                }
            }
        }
    }
}
