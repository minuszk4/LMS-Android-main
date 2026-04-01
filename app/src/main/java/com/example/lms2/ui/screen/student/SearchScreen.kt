package com.example.lms2.ui.screen.student

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.example.lms2.data.model.Category
import com.example.lms2.data.model.Course
import com.example.lms2.data.model.CourseLevel
import com.example.lms2.ui.component.SearchBar
import com.example.lms2.ui.component.TopBar
import com.example.lms2.util.formatPrice
import com.example.lms2.viewmodel.CourseViewModel

private val Indigo        = Color(0xFF4B5CC4)
private val StarYellow    = Color(0xFFFFC107)
private val SurfaceGray   = Color(0xFFF5F6FA)
private val TextPrimary   = Color(0xFF1A1D2E)
private val TextSecondary = Color(0xFF6B7280)
private val CardWhite     = Color(0xFFFFFFFF)

@Composable
fun SearchScreen(
    navController: NavController,
    viewModel: CourseViewModel,
    onCourseClick: (Course) -> Unit,
    onInstructorClick: (String, String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategoryId by remember { mutableStateOf("all") }
    val listState = rememberLazyListState()

    val focusManager = LocalFocusManager.current

    LaunchedEffect(Unit) {
        viewModel.getCategories()
        viewModel.getAllPublishedCourses()
    }

    val displayCategories = remember(uiState.categories, uiState.allPublishedCourses) {
        val normalizedCategories = uiState.categories
            .filter { it.id.isNotBlank() }
            .map { category ->
                if (category.name.isBlank()) {
                    category.copy(name = category.id)
                } else {
                    category
                }
            }
            .distinctBy { it.id }
            .sortedBy { it.name.lowercase() }

        if (normalizedCategories.isNotEmpty()) {
            normalizedCategories
        } else {
            // Fallback để vẫn cho chọn category theo categoryId khi collection categories chưa có dữ liệu.
            uiState.allPublishedCourses
                .map { it.categoryId }
                .filter { it.isNotBlank() }
                .distinct()
                .sorted()
                .map { id -> Category(id = id, name = id) }
        }
    }

    val filteredCourses = remember(uiState.allPublishedCourses, searchQuery, selectedCategoryId) {
        uiState.allPublishedCourses.filter { course ->
            val matchesQuery = searchQuery.isBlank() || 
                course.title.contains(searchQuery, ignoreCase = true)
            val matchesCategory = selectedCategoryId == "all" || 
                course.categoryId == selectedCategoryId
            matchesQuery && matchesCategory
        }
    }

    val shouldLoadMore by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val totalItems = layoutInfo.totalItemsCount
            val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            totalItems > 0 && lastVisible >= totalItems - 3
        }
    }

    LaunchedEffect(shouldLoadMore, uiState.isLoading, uiState.isLoadingMore, uiState.hasMorePublishedCourses) {
        if (shouldLoadMore && !uiState.isLoading && !uiState.isLoadingMore && uiState.hasMorePublishedCourses) {
            viewModel.loadMorePublishedCourses()
        }
    }

    Scaffold(
        topBar = {
            TopBar(
                title = "Tìm kiếm khóa học",
                onBackClick = { navController.popBackStack() }
            )
        },
        containerColor = SurfaceGray
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            SearchBar(
                query = searchQuery,
                onQueryChange = { searchQuery = it },
                onSearch = { focusManager.clearFocus() },
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp)
            )

            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    CategoryChip(
                        label = "Tất cả",
                        isSelected = selectedCategoryId == "all",
                        onClick = { selectedCategoryId = "all" }
                    )
                }
                items(displayCategories) { category ->
                    CategoryChip(
                        label = category.name,
                        isSelected = selectedCategoryId == category.id,
                        onClick = { selectedCategoryId = category.id }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (uiState.isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Indigo)
                }
            } else if (filteredCourses.isEmpty()) {
                EmptySearchResult(query = searchQuery)
            } else {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(filteredCourses, key = { it.id }) { course ->
                        SearchCourseCard(
                            course = course,
                            categories = displayCategories,
                            onClick = { onCourseClick(course) },
                            onInstructorClick = { instructorId, instructorName ->
                                onInstructorClick(instructorId, instructorName)
                            }
                        )
                    }

                    if (uiState.isLoadingMore) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(
                                    color = Indigo,
                                    modifier = Modifier.size(22.dp),
                                    strokeWidth = 2.dp
                                )
                            }
                        }
                    }

                    item { Spacer(modifier = Modifier.height(16.dp)) }
                }
            }
        }
    }
}

@Composable
private fun CategoryChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    FilterChip(
        selected = isSelected,
        onClick = onClick,
        label = {
            Text(
                text = label,
                fontSize = 14.sp
            )
        },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = Indigo,
            selectedLabelColor = Color.White,
            containerColor = CardWhite,
            labelColor = TextSecondary
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = isSelected,
            borderColor = Color(0xFFE0E0E0),
            selectedBorderColor = Color.Transparent
        )
    )
}

@Composable
private fun SearchCourseCard(
    course: Course,
    categories: List<Category>,
    onClick: () -> Unit,
    onInstructorClick: (String, String) -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = CardWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
            ) {

                val categoryName = categories.find { it.id == course.categoryId }?.name ?: "Khác"

                Text(
                    text = categoryName,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Indigo
                )

                Text(
                    text = course.title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 21.sp
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = course.instructorName,
                    fontSize = 12.sp,
                    color = Indigo,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.clickable(enabled = course.instructorId.isNotBlank()) {
                        onInstructorClick(course.instructorId, course.instructorName)
                    }
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        tint = StarYellow,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "${course.rating} (${course.reviewCount} đánh giá)",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))

                val levelText = when (course.level) {
                    CourseLevel.BEGINNER -> "Cơ bản"
                    CourseLevel.INTERMEDIATE -> "Trung cấp"
                    CourseLevel.ADVANCED -> "Nâng cao"
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.AccessTime,
                        contentDescription = null,
                        tint = TextSecondary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "${course.duration} • $levelText",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Indigo.copy(alpha = 0.1f))
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = formatPrice(course.price),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Indigo
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            AsyncImage(
                model = course.thumbnailUrl.ifEmpty { null },
                contentDescription = course.title,
                modifier = Modifier
                    .size(120.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Indigo.copy(alpha = 0.12f)),
                contentScale = ContentScale.Crop
            )
        }
    }
}

@Composable
private fun EmptySearchResult(query: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(CircleShape)
                .background(Indigo.copy(alpha = 0.08f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = Indigo.copy(alpha = 0.5f),
                modifier = Modifier.size(44.dp)
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = if (query.isBlank()) "Tìm kiếm khóa học"
            else "Không tìm thấy kết quả",
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextPrimary
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = if (query.isBlank()) "Nhập tên khóa học, giảng viên\nhoặc chủ đề bạn muốn học"
            else "Không có kết quả cho \"$query\"\nThử tìm với từ khóa khác",
            fontSize = 14.sp,
            color = TextSecondary,
            textAlign = TextAlign.Center,
            lineHeight = 22.sp
        )
    }
}
