package com.example.lms.ui.screen.instructor.stats

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
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
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.lms.data.model.Review
import com.example.lms.ui.component.TopBar
import com.example.lms.util.CourseAnalyticsEvent
import com.example.lms.viewmodel.CourseAnalyticsViewModel
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val AnalyticsBg = Color(0xFFF8FAFC)
private val AnalyticsCard = Color.White
private val AnalyticsBorder = Color(0xFFE2E8F0)
private val AnalyticsTextPrimary = Color(0xFF1E293B)
private val AnalyticsTextSecondary = Color(0xFF64748B)

@Composable
fun CourseAnalyticsRoute(
    courseId: String,
    viewModel: CourseAnalyticsViewModel,
    onBackClick: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(courseId) {
        viewModel.init(courseId)
    }

    LaunchedEffect(Unit) {
        viewModel.event.collect { event ->
            if (event is CourseAnalyticsEvent.ShowError) {
                snackbarHostState.showSnackbar(event.message)
            }
        }
    }

    val analytics = uiState.analytics
    val formatter = remember { NumberFormat.getCurrencyInstance(Locale("vi", "VN")) }

    Scaffold(
        topBar = {
            TopBar(
                title = "Thống kê chi tiết khóa học",
                onBackClick = onBackClick
            )
        },
        containerColor = AnalyticsBg,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { paddingValues ->
        if (uiState.isLoading && analytics == null) {
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

        if (analytics == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Text("Không có dữ liệu khóa học", color = AnalyticsTextSecondary)
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
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = AnalyticsCard),
                    modifier = Modifier.border(1.dp, AnalyticsBorder, RoundedCornerShape(16.dp))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncImage(
                            model = analytics.course.thumbnailUrl,
                            contentDescription = null,
                            modifier = Modifier
                                .size(64.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(AnalyticsBorder, RoundedCornerShape(10.dp)),
                            contentScale = ContentScale.Crop
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = analytics.course.title,
                                color = AnalyticsTextPrimary,
                                fontWeight = FontWeight.Bold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Giá: ${formatter.format(analytics.course.price)}",
                                color = AnalyticsTextSecondary,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MetricCard(
                        modifier = Modifier.weight(1f),
                        title = "Học viên",
                        value = analytics.enrollments.toString()
                    )
                    MetricCard(
                        modifier = Modifier.weight(1f),
                        title = "Doanh thu",
                        value = formatter.format(analytics.estimatedRevenue)
                    )
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MetricCard(
                        modifier = Modifier.weight(1f),
                        title = "Hoàn thành",
                        value = "${analytics.completionRate.toInt()}%"
                    )
                    MetricCard(
                        modifier = Modifier.weight(1f),
                        title = "Pass quiz",
                        value = "${analytics.quizPassRate.toInt()}%"
                    )
                }
            }

            item {
                MetricCard(
                    title = "Đánh giá trung bình",
                    value = String.format(Locale.US, "%.1f (%d đánh giá)", analytics.course.rating, analytics.course.reviewCount)
                )
            }

            item {
                Text(
                    text = "Đánh giá gần đây",
                    color = AnalyticsTextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }

            if (analytics.reviews.isEmpty()) {
                item {
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = AnalyticsCard),
                        modifier = Modifier.border(1.dp, AnalyticsBorder, RoundedCornerShape(12.dp))
                    ) {
                        Text(
                            text = "Khóa học chưa có đánh giá nào",
                            modifier = Modifier.padding(14.dp),
                            color = AnalyticsTextSecondary,
                            fontSize = 13.sp
                        )
                    }
                }
            } else {
                items(analytics.reviews.take(12)) { review ->
                    ReviewCard(review = review)
                }
            }

            item {
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun MetricCard(
    modifier: Modifier = Modifier,
    title: String,
    value: String
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = AnalyticsCard),
        border = BorderStroke(1.dp, AnalyticsBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(title, color = AnalyticsTextSecondary, fontSize = 12.sp)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                value,
                color = AnalyticsTextPrimary,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ReviewCard(review: Review) {
    val dateText = remember(review.updatedAt) {
        SimpleDateFormat("dd/MM/yyyy", Locale("vi", "VN")).format(Date(review.updatedAt))
    }

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = AnalyticsCard),
        border = BorderStroke(1.dp, AnalyticsBorder)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (review.userAvatarUrl.isNotBlank()) {
                    AsyncImage(
                        model = review.userAvatarUrl,
                        contentDescription = null,
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(AnalyticsBorder, CircleShape),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .background(AnalyticsBorder, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = review.userName.take(1).uppercase(Locale.getDefault()),
                            color = AnalyticsTextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }
                Spacer(modifier = Modifier.size(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(review.userName, color = AnalyticsTextPrimary, fontWeight = FontWeight.SemiBold)
                    Text(dateText, color = AnalyticsTextSecondary, fontSize = 11.sp)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        tint = Color(0xFFF59E0B),
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.size(2.dp))
                    Text(review.rating.toString(), color = AnalyticsTextPrimary, fontSize = 12.sp)
                }
            }
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 8.dp),
                color = AnalyticsBorder
            )
            Text(
                text = review.content,
                color = AnalyticsTextSecondary,
                fontSize = 13.sp,
                lineHeight = 18.sp
            )
        }
    }
}

