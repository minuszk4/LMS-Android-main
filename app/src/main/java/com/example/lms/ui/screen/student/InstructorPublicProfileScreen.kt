package com.example.lms.ui.screen.student

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.lms.ui.component.TopBar
import com.example.lms.util.InstructorPublicProfileEvent
import com.example.lms.viewmodel.InstructorPublicProfileViewModel

@Composable
fun InstructorPublicProfileRoute(
    instructorId: String,
    instructorName: String,
    instructorAvatarUrl: String,
    viewModel: InstructorPublicProfileViewModel,
    onBackClick: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(instructorId) {
        viewModel.init(instructorId)
    }

    LaunchedEffect(Unit) {
        viewModel.event.collect { event ->
            if (event is InstructorPublicProfileEvent.ShowError) {
                snackbarHostState.showSnackbar(event.message)
            }
        }
    }

    Scaffold(
        topBar = {
            TopBar(
                title = "Hồ sơ giảng viên",
                onBackClick = onBackClick
            )
        },
        containerColor = Color(0xFFF8FAFC),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { paddingValues ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Color(0xFF4B5CC4))
            }
            return@Scaffold
        }

        val instructor = uiState.instructor
        if (instructor == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Chưa có thông tin giảng viên",
                    color = Color(0xFF64748B),
                    fontSize = 14.sp
                )
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            contentPadding = PaddingValues(bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                HeaderCard(
                    instructorName = instructorName,
                    instructorAvatarUrl = instructorAvatarUrl,
                    expertise = instructor.expertise
                )
            }
            item {
                InfoCard(
                    title = "Số năm kinh nghiệm",
                    value = if (instructor.experienceYears > 0) {
                        "${instructor.experienceYears} năm"
                    } else {
                        "Chưa cập nhật"
                    }
                )
            }
            item {
                InfoCard(
                    title = "Trình độ/Bằng cấp",
                    value = instructor.qualification.ifBlank { "Chưa cập nhật" }
                )
            }
        }
    }
}

@Composable
private fun HeaderCard(
    instructorName: String,
    instructorAvatarUrl: String,
    expertise: String
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(92.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFE2E8F0)),
                contentAlignment = Alignment.Center
            ) {
                if (instructorAvatarUrl.isNotBlank()) {
                    AsyncImage(
                        model = instructorAvatarUrl,
                        contentDescription = "Instructor Avatar",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = Color(0xFF94A3B8),
                        modifier = Modifier.size(44.dp)
                    )
                }
            }

            Text(
                text = instructorName.ifBlank { "Giảng viên" },
                color = Color(0xFF1E293B),
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp
            )

            AssistChip(
                onClick = {},
                enabled = false,
                label = {
                    Text(
                        text = expertise.ifBlank { "Chưa cập nhật chuyên môn" },
                        fontSize = 12.sp,
                        color = Color(0xFF4B5CC4)
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Badge,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = Color(0xFF4B5CC4)
                    )
                },
                colors = AssistChipDefaults.assistChipColors(
                    disabledContainerColor = Color(0xFFEFF3FF),
                    disabledLabelColor = Color(0xFF4B5CC4),
                    disabledLeadingIconContentColor = Color(0xFF4B5CC4)
                ),
                border = null
            )
        }
    }
}

@Composable
private fun InfoCard(
    title: String,
    value: String
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(14.dp)),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White)
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = Color(0xFF64748B),
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = value,
                    color = Color(0xFF1E293B),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

