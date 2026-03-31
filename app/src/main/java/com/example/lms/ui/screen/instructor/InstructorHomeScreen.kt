package com.example.lms.ui.screen.instructor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.lms.data.model.InstructorCoursePerformance
import com.example.lms.util.InstructorAnalyticsEvent
import com.example.lms.util.InstructorTimeRange
import com.example.lms.viewmodel.InstructorAnalyticsViewModel
import java.text.NumberFormat
import java.util.Locale

private val HomeBackground = Color(0xFFF8FAFC)
private val CardBackground = Color(0xFFFFFFFF)
private val BorderColor = Color(0xFFE2E8F0)
private val TextPrimary = Color(0xFF1E293B)
private val TextSecondary = Color(0xFF64748B)
private val Primary = Color(0xFF4B5CC4)

@Composable
fun InstructorHomeRoute(
    instructorId: String,
    instructorName: String,
    viewModel: InstructorAnalyticsViewModel,
    onMyCoursesClick: () -> Unit,
    onCreateCourseClick: () -> Unit,
    onViewStatisticsClick: () -> Unit,
    onTopCourseClick: (String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(instructorId) {
        viewModel.selectRange(instructorId, InstructorTimeRange.ALL_TIME)
    }

    LaunchedEffect(Unit) {
        viewModel.event.collect { event ->
            if (event is InstructorAnalyticsEvent.ShowError) {
                snackbarHostState.showSnackbar(event.message)
            }
        }
    }

    InstructorHomeScreen(
        instructorName = instructorName,
        isLoading = uiState.isLoading,
        isRefreshing = uiState.isRefreshing,
        hasData = !uiState.isEmpty,
        totalCourses = uiState.analytics.kpi.totalCourses,
        totalEnrollments = uiState.analytics.kpi.totalEnrollments,
        reviewCount = uiState.analytics.kpi.totalReviews,
        averageRating = uiState.analytics.kpi.averageRating,
        estimatedRevenue = uiState.analytics.kpi.estimatedRevenue,
        publishedCourses = uiState.analytics.kpi.publishedCourses,
        topCourses = uiState.analytics.topCourses,
        snackbarHostState = snackbarHostState,
        onRefresh = { viewModel.refresh(instructorId) },
        onMyCoursesClick = onMyCoursesClick,
        onCreateCourseClick = onCreateCourseClick,
        onViewStatisticsClick = onViewStatisticsClick,
        onTopCourseClick = onTopCourseClick
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InstructorHomeScreen(
    instructorName: String,
    isLoading: Boolean,
    isRefreshing: Boolean,
    hasData: Boolean,
    totalCourses: Int,
    totalEnrollments: Int,
    reviewCount: Int,
    averageRating: Double,
    estimatedRevenue: Double,
    publishedCourses: Int,
    topCourses: List<InstructorCoursePerformance>,
    snackbarHostState: SnackbarHostState,
    onRefresh: () -> Unit,
    onMyCoursesClick: () -> Unit,
    onCreateCourseClick: () -> Unit,
    onViewStatisticsClick: () -> Unit,
    onTopCourseClick: (String) -> Unit
) {
    val currencyFormatter = remember { NumberFormat.getCurrencyInstance(Locale("vi", "VN")) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "Trang chủ giảng viên",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                },
                actions = {
                    IconButton(onClick = onRefresh, enabled = !isRefreshing) {
                        if (isRefreshing) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = "Tải lại")
                        }
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.White)
            )
        },
        containerColor = HomeBackground,
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        if (isLoading) {
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

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = CardBackground),
                    modifier = Modifier.border(1.dp, BorderColor, RoundedCornerShape(18.dp))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.BarChart,
                                contentDescription = null,
                                tint = Primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Dashboard",
                                color = Primary,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Xin chào, $instructorName",
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 19.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Theo dõi hiệu suất, tối ưu nội dung và hành động nhanh với khóa học của bạn.",
                            color = TextSecondary,
                            fontSize = 13.sp
                        )
                    }
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    KpiCard(
                        modifier = Modifier.weight(1f),
                        title = "Tổng số khóa học",
                        value = totalCourses.toString(),
                        subtitle = "$publishedCourses công khai"
                    )
                    KpiCard(
                        modifier = Modifier.weight(1f),
                        title = "Đánh giá trung bình",
                        value = String.format(Locale.US, "%.1f", averageRating),
                        subtitle = "$reviewCount đánh giá"
                    )
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    KpiCard(
                        modifier = Modifier.weight(1f),
                        title = "Tổng số học viên",
                        value = totalEnrollments.toString()
                    )
                    KpiCard(
                        modifier = Modifier.weight(1f),
                        title = "Tổng doanh thu",
                        value = currencyFormatter.format(estimatedRevenue)
                    )
                }
            }

            item {
                OutlinedButton(
                    onClick = onViewStatisticsClick,
                    modifier = Modifier.fillMaxWidth(),
                    border = BorderStroke(1.dp, Primary),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.TrendingUp, contentDescription = null)
                    Spacer(modifier = Modifier.size(6.dp))
                    Text("Xem thống kê", color = Primary)
                }
            }

            item {
                Text(
                    text = "Khóa học nổi bật",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            }

            if (!hasData) {
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = CardBackground),
                        modifier = Modifier.border(1.dp, BorderColor, RoundedCornerShape(16.dp))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Bạn chưa có khóa học nào.",
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Tạo khóa học đầu tiên để bắt đầu có dữ liệu thống kê.",
                                color = TextSecondary,
                                fontSize = 13.sp
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            FilledTonalButton(onClick = onCreateCourseClick) {
                                Text("Tạo khóa học ngay")
                            }
                        }
                    }
                }
            } else {
                items(topCourses.take(2)) { course ->
                    TopCourseCard(
                        course = course,
                        onClick = { onTopCourseClick(course.courseId) }
                    )
                }

                item {
                    Spacer(modifier = Modifier.height(6.dp))
                    FilledTonalButton(
                        onClick = onMyCoursesClick,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = Color(0xFF4B5CC4),
                            contentColor = Color.White
                        )
                    ) {
                        Text("Đến quản lý khóa học")
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }
            }
        }
    }
}

@Composable
private fun KpiCard(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    subtitle: String? = null
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = CardBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, BorderColor, RoundedCornerShape(14.dp))
                .padding(horizontal = 12.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(text = title, color = TextSecondary, fontSize = 12.sp, maxLines = 1)
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = value,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    color = TextSecondary.copy(alpha = 0.8f),
                    fontSize = 11.sp,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
fun TopCourseCard(
    course: InstructorCoursePerformance,
    onClick: () -> Unit
) {
    val currencyFormatter = remember { NumberFormat.getCurrencyInstance(Locale("vi", "VN")) }
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        modifier = Modifier.border(1.dp, BorderColor, RoundedCornerShape(14.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = course.thumbnailUrl,
                contentDescription = null,
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(BorderColor, RoundedCornerShape(10.dp)),
                contentScale = ContentScale.Crop
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = course.title,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        tint = Color(0xFFF59E0B),
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        text = String.format(Locale.US, "%.1f", course.rating),
                        color = TextSecondary,
                        fontSize = 11.sp
                    )
                    Text(
                        text = "• ${course.reviewCount} đánh giá",
                        color = TextSecondary,
                        fontSize = 11.sp
                    )
                    Text(
                        text = "• ${course.enrollments} học viên",
                        color = TextSecondary,
                        fontSize = 11.sp
                    )
                }

                Text(
                    text = "${currencyFormatter.format(course.revenue)}",
                    color = Primary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                contentDescription = null,
                tint = TextSecondary,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}
