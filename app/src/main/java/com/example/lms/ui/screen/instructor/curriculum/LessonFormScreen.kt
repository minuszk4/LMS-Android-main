package com.example.lms.ui.screen.instructor.curriculum

import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.lms.data.model.Attachment
import com.example.lms.ui.component.FormFieldWithValidation
import com.example.lms.ui.component.SectionCard
import com.example.lms.ui.component.TopBar
import com.example.lms.util.LessonEvent
import com.example.lms.viewmodel.LessonViewModel
import kotlinx.coroutines.flow.collectLatest


@Composable
fun LessonFormScreen(
    viewModel: LessonViewModel = viewModel(),
    onBackClick: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val primaryIndigo = Color(0xFF4B5CC4)

    LaunchedEffect(Unit) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is LessonEvent.ShowSnackbar -> {
                    Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
                }
                LessonEvent.SaveSuccess -> {
                    onBackClick()
                }
            }
        }
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val contentResolver = context.contentResolver
            var fileName = "file"
            var fileSize = "0 KB"
            val mimeType = contentResolver.getType(it) ?: "application/octet-stream"

            contentResolver.query(it, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (cursor.moveToFirst()) {
                    fileName = cursor.getString(nameIndex)
                    val sizeInBytes = cursor.getLong(sizeIndex)
                    fileSize = when {
                        sizeInBytes < 1024 -> "$sizeInBytes B"
                        sizeInBytes < 1024 * 1024 -> "${sizeInBytes / 1024} KB"
                        else -> java.lang.String.format(java.util.Locale.getDefault(), "%.1f MB", sizeInBytes / (1024f * 1024f))
                    }
                }
            }
            viewModel.addAttachment(it, fileName, fileSize, mimeType)
        }
    }

    Scaffold(
        topBar = {
            TopBar(
                title = if (uiState.isEditMode) "Chỉnh sửa bài học" else "Thêm bài học mới",
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

            // 1. Thông tin cơ bản
            SectionCard(title = "Nội dung bài học", icon = Icons.Outlined.Description) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    FormFieldWithValidation(
                        label = "Tiêu đề bài học",
                        value = uiState.title,
                        onValueChange = viewModel::onTitleChange,
                        placeholder = "Ví dụ: Giới thiệu về Kotlin",
                        error = uiState.titleError
                    )
                    FormFieldWithValidation(
                        label = "Mô tả",
                        value = uiState.description,
                        onValueChange = viewModel::onDescriptionChange,
                        placeholder = "Mô tả ngắn về nội dung bài học...",
                        minLines = 3,
                        error = uiState.descriptionError
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 2. Video bài giảng
            SectionCard(title = "Video bài giảng", icon = Icons.Outlined.Link) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    FormFieldWithValidation(
                        label = "Link YouTube",
                        value = uiState.videoUrl,
                        onValueChange = viewModel::onVideoUrlChange,
                        placeholder = "https://www.youtube.com/watch?v=...",
                        error = uiState.videoUrlError
                    )
                    FormFieldWithValidation(
                        label = "Thời lượng",
                        value = uiState.duration,
                        onValueChange = viewModel::onDurationChange,
                        placeholder = "Ví dụ: 10:30",
                        error = uiState.durationError
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 3. Tài liệu đính kèm
            SectionCard(title = "Tài liệu đính kèm", icon = Icons.Default.AttachFile) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFFF0F0F4))
                            .clickable(enabled = !uiState.isUploadingFile) { filePickerLauncher.launch("*/*") },
                        contentAlignment = Alignment.Center
                    ) {
                        if (uiState.isUploadingFile) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(color = primaryIndigo, modifier = Modifier.size(32.dp))
                                Spacer(Modifier.height(8.dp))
                                Text("Đang tải lên tài liệu...", fontSize = 14.sp, color = primaryIndigo, fontWeight = FontWeight.Medium)
                            }
                        } else {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.CloudUpload, null, tint = primaryIndigo, modifier = Modifier.size(40.dp))
                                Text("Nhấn để tải lên tài liệu (PDF, ZIP, ...)", fontSize = 14.sp, color = Color(0xFF64748B))
                            }
                        }
                    }

                    // Danh sách file đã tải lên hiển thị bên dưới
                    if (uiState.attachments.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Danh sách tài liệu (${uiState.attachments.size})",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1E293B)
                        )
                        uiState.attachments.forEach { attachment ->
                            AttachmentItem(
                                attachment = attachment,
                                onDelete = { viewModel.removeAttachment(attachment) }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // 4. Nút Lưu
            Button(
                onClick = viewModel::save,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .height(56.dp),
                shape = RoundedCornerShape(12.dp),
                enabled = !uiState.isSaving && !uiState.isUploadingFile,
                colors = ButtonDefaults.buttonColors(containerColor = primaryIndigo)
            ) {
                if (uiState.isSaving) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                } else {
                    Text(
                        text = if (uiState.isEditMode) "Cập nhật bài học" else "Lưu bài học",
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 16.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

@Composable
private fun AttachmentItem(
    attachment: Attachment,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(Color(0xFF4B5CC4).copy(alpha = 0.1f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.InsertDriveFile,
                    contentDescription = null,
                    tint = Color(0xFF4B5CC4),
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = attachment.name,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF1E293B),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 18.sp
                )
                Text(
                    text = attachment.size,
                    fontSize = 12.sp,
                    color = Color(0xFF64748B),
                    lineHeight = 16.sp
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(36.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(Color.White, CircleShape)
                        .border(1.dp, Color(0xFFE2E8F0), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = null,
                        tint = Color(0xFFEF4444),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}
