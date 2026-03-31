package com.example.lms.ui.screen.instructor.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.lms.data.model.InstructorCoursePerformance
import com.example.lms.ui.component.SearchBar
import com.example.lms.ui.component.TopBar
import com.example.lms.ui.screen.instructor.TopCourseCard
import com.example.lms.viewmodel.CourseViewModel

@Composable
fun CourseAnalyticsSearchRoute(
    instructorId: String,
    courseViewModel: CourseViewModel,
    onBackClick: () -> Unit,
    onCourseClick: (String) -> Unit
) {
    val uiState by courseViewModel.uiState.collectAsStateWithLifecycle()
    var keyword by remember { mutableStateOf("") }

    LaunchedEffect(instructorId) {
        if (instructorId.isNotBlank()) {
            courseViewModel.getMyCourses(instructorId)
        }
    }

    val filtered = uiState.courses
        .filter { it.title.contains(keyword.trim(), ignoreCase = true) }
        .map { course ->
            InstructorCoursePerformance(
                courseId = course.id,
                title = course.title,
                thumbnailUrl = course.thumbnailUrl,
                enrollments = course.enrollmentCount,
                rating = course.rating,
                reviewCount = course.reviewCount,
                revenue = course.price
            )
        }

    Scaffold(
        topBar = {
            TopBar(
                title = "Tìm kiếm khóa học",
                onBackClick = onBackClick
            )
        },
        containerColor = Color(0xFFF8FAFC)
    ) { paddingValues ->
        if (uiState.isLoading && uiState.courses.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

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
                        query = keyword,
                        onQueryChange = { keyword = it },
                        placeholder = "Tìm theo tên khóa học"
                    )
                }
            }

            if (filtered.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Không tìm thấy khóa học phù hợp", color = Color(0xFF64748B))
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filtered, key = { it.courseId }) { course ->
                        TopCourseCard(
                            course = course,
                            onClick = { onCourseClick(course.courseId) }
                        )
                    }
                    item { Spacer(modifier = Modifier.height(10.dp)) }
                }
            }
        }
    }
}

