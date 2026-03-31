package com.example.lms.ui.screen.instructor.course

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.lms.data.model.Course
import com.example.lms.ui.component.CourseFilterStatus
import com.example.lms.ui.component.InstructorCourseCard
import com.example.lms.ui.component.SearchBar
import com.example.lms.ui.component.StatusFilterRow
import com.example.lms.util.CourseEvent
import com.example.lms.viewmodel.CourseViewModel


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyCoursesScreen(
    instructorId: String,
    viewModel: CourseViewModel,
    onNavigateToAddCourse: () -> Unit,
    onNavigateToEditCourse: (Course) -> Unit,
    onManageContent: (Course) -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    
    var searchQuery by remember { mutableStateOf("") }
    var selectedStatus by remember { mutableStateOf(CourseFilterStatus.ALL) }

    var showDeleteDialog by remember { mutableStateOf(false) }
    var courseToDelete by remember { mutableStateOf<Course?>(null) }

    val primaryIndigo = Color(0xFF4B5CC4)

    LaunchedEffect(instructorId) {
        viewModel.getMyCourses(instructorId)
    }

    LaunchedEffect(Unit) {
        viewModel.event.collect { event ->
            when (event) {
                is CourseEvent.ShowError -> {
                    Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
                }
                CourseEvent.SaveSuccess -> {
                    Toast.makeText(context, "Thao tác thành công", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val filteredCourses = remember(uiState.courses, searchQuery, selectedStatus) {
        uiState.courses.filter { course ->
            val matchesSearch = course.title.contains(searchQuery, ignoreCase = true)
            val matchesStatus = when (selectedStatus) {
                CourseFilterStatus.ALL -> true
                CourseFilterStatus.PUBLISHED -> course.isPublished
                CourseFilterStatus.DRAFT -> !course.isPublished
            }
            matchesSearch && matchesStatus
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        "Khóa học của tôi",
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = Color(0xFF1E293B)
                    )
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color.White
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNavigateToAddCourse,
                containerColor = primaryIndigo,
                contentColor = Color.White,
                shape = RoundedCornerShape(16.dp),
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Tạo khóa học", fontWeight = FontWeight.Bold) }
            )
        },
        containerColor = Color(0xFFF8FAFC)
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .zIndex(1f),
                color = Color.White,
                shadowElevation = 2.dp
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    SearchBar(
                        query = searchQuery,
                        onQueryChange = { searchQuery = it }
                    )
                    StatusFilterRow(
                        selectedStatus = selectedStatus,
                        onStatusSelected = { selectedStatus = it }
                    )
                }
            }

            Box(modifier = Modifier.fillMaxSize()) {
                if (uiState.isLoading && uiState.courses.isEmpty()) {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = primaryIndigo
                    )
                } else if (filteredCourses.isEmpty()) {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "Không tìm thấy khóa học nào",
                            color = Color(0xFF64748B),
                            fontSize = 16.sp
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(
                            items = filteredCourses,
                            key = { it.id }
                        ) { course ->
                            InstructorCourseCard(
                                course = course,
                                onManageContent = { onManageContent(course) },
                                onEdit = { onNavigateToEditCourse(course) },
                                onDelete = { 
                                    courseToDelete = course
                                    showDeleteDialog = true
                                },
                                onPublishToggle = { viewModel.togglePublishStatus(course.id, instructorId) }
                            )
                        }
                        item { Spacer(modifier = Modifier.height(80.dp)) }
                    }
                }
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Xác nhận xóa", fontWeight = FontWeight.Bold, color = Color(0xFF1E293B)) },
            text = { Text("Bạn có chắc chắn muốn xóa khóa học \"${courseToDelete?.title}\"? Hành động này không thể hoàn tác.", color = Color(0xFF1E293B)) },
            confirmButton = {
                Button(
                    onClick = {
                        courseToDelete?.let { viewModel.deleteCourse(it.id, instructorId) }
                        showDeleteDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                ) {
                    Text("Xóa", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Hủy", color = Color(0xFF64748B))
                }
            },
            containerColor = Color.White,
            shape = RoundedCornerShape(16.dp)
        )
    }
}
