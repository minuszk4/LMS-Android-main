package com.example.lms2.ui.screen.admin

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import com.example.lms2.data.model.Course
import com.example.lms2.viewmodel.AdminManagementEvent
import com.example.lms2.viewmodel.AdminManagementViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminCoursesScreen(
    viewModel: AdminManagementViewModel
) {
    val uiState = viewModel.uiState.collectAsStateWithLifecycle().value
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.loadCourses()
    }

    LaunchedEffect(Unit) {
        viewModel.event.collect { event ->
            when (event) {
                is AdminManagementEvent.ShowError -> snackbarHostState.showSnackbar(event.message)
                is AdminManagementEvent.ShowSuccess -> snackbarHostState.showSnackbar(event.message)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Quản lý khóa học", fontWeight = FontWeight.Bold) },
                actions = {
                    TextButton(onClick = { viewModel.loadCourses() }, enabled = !uiState.isLoadingCourses) {
                        Text("Làm mới")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            when {
                uiState.isLoadingCourses -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                uiState.courses.isEmpty() -> Text("Chưa có khóa học", modifier = Modifier.align(Alignment.Center))
                else -> {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(uiState.courses, key = { it.id }) { course ->
                            CourseManagementCard(
                                course = course,
                                disableAction = uiState.isProcessing,
                                onTogglePublish = { viewModel.toggleCoursePublished(course) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CourseManagementCard(
    course: Course,
    disableAction: Boolean,
    onTogglePublish: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SubcomposeAsyncImage(
                model = course.thumbnailUrl.ifBlank { "https://picsum.photos/seed/fallback_course/640/360" },
                contentDescription = course.title,
                modifier = Modifier
                    .size(96.dp)
                    .clip(RoundedCornerShape(12.dp)),
                contentScale = ContentScale.Crop,
                loading = {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                    }
                },
                error = {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No image", style = MaterialTheme.typography.labelSmall)
                    }
                },
                success = {
                    SubcomposeAsyncImageContent()
                }
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = course.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(course.instructorName, style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(10.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(onClick = {}, label = { Text(if (course.isPublished) "Đang xuất bản" else "Chưa xuất bản") })
                    AssistChip(onClick = {}, label = { Text("${course.enrollmentCount} học viên") })
                }

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = onTogglePublish,
                    enabled = !disableAction,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (course.isPublished) "Ẩn khóa học" else "Xuất bản khóa học")
                }
            }
        }
    }
}
