package com.example.lms.ui.screen.instructor.stats

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.lms.data.model.AnalyticsTrendPoint
import com.example.lms.util.InstructorAnalyticsEvent
import com.example.lms.util.InstructorTimeRange
import com.example.lms.viewmodel.InstructorAnalyticsViewModel
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.max

private val StatsBackground = Color(0xFFF8FAFC)
private val StatsCard = Color.White
private val StatsBorder = Color(0xFFE2E8F0)
private val StatsPrimary = Color(0xFF4B5CC4)
private val StatsTextPrimary = Color(0xFF1E293B)
private val StatsTextSecondary = Color(0xFF64748B)

@Composable
fun InstructorStatisticsRoute(
    instructorId: String,
    viewModel: InstructorAnalyticsViewModel,
    onOpenCourseAnalyticsSearch: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(instructorId) {
        viewModel.init(instructorId)
    }

    LaunchedEffect(Unit) {
        viewModel.event.collect { event ->
            if (event is InstructorAnalyticsEvent.ShowError) {
                snackbarHostState.showSnackbar(event.message)
            }
        }
    }

    InstructorStatisticsScreen(
        isLoading = uiState.isLoading,
        isRefreshing = uiState.isRefreshing,
        selectedRange = uiState.selectedRange,
        completionRate = uiState.analytics.kpi.completionRate,
        passRate = uiState.analytics.kpi.quizPassRate,
        enrollmentTrend = uiState.analytics.enrollmentTrend,
        revenueTrend = uiState.analytics.revenueTrend,
        snackbarHostState = snackbarHostState,
        onRefresh = { viewModel.refresh(instructorId) },
        onSelectRange = { viewModel.selectRange(instructorId, it) },
        onOpenCourseAnalyticsSearch = onOpenCourseAnalyticsSearch
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InstructorStatisticsScreen(
    isLoading: Boolean,
    isRefreshing: Boolean,
    selectedRange: InstructorTimeRange,
    completionRate: Double,
    passRate: Double,
    enrollmentTrend: List<AnalyticsTrendPoint>,
    revenueTrend: List<AnalyticsTrendPoint>,
    snackbarHostState: SnackbarHostState,
    onRefresh: () -> Unit,
    onSelectRange: (InstructorTimeRange) -> Unit,
    onOpenCourseAnalyticsSearch: () -> Unit
) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "Thống kê hiệu suất",
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = StatsTextPrimary
                    )
                },
                actions = {
                    IconButton(onClick = onRefresh, enabled = !isRefreshing) {
                        if (isRefreshing) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Làm mới dữ liệu"
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.White)
            )
        },
        containerColor = StatsBackground,
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
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    InstructorTimeRange.entries.forEach { range ->
                        FilterChip(
                            selected = selectedRange == range,
                            onClick = { onSelectRange(range) },
                            label = { Text(range.label) },
                            border = if (selectedRange == range) null else BorderStroke(1.dp, StatsBorder),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = StatsPrimary,
                                selectedLabelColor = Color.White,
                                containerColor = Color.White,
                                labelColor = StatsTextSecondary
                            )
                        )
                    }
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ProgressRingCard(
                        modifier = Modifier.weight(1f),
                        title = "Hoàn thành khóa học",
                        value = completionRate
                    )
                    ProgressRingCard(
                        modifier = Modifier.weight(1f),
                        title = "Pass Quiz",
                        value = passRate
                    )
                }
            }

            item {
                LineChartCard(
                    title = "Lượt ghi danh theo thời gian",
                    points = enrollmentTrend,
                    lineColor = StatsPrimary,
                    valueFormatter = { value -> value.toInt().toString() }
                )
            }

            item {
                val currencyFormatter = NumberFormat.getCurrencyInstance(Locale("vi", "VN"))
                LineChartCard(
                    title = "Doanh thu theo thời gian",
                    points = revenueTrend,
                    lineColor = Color(0xFF059669),
                    valueFormatter = { value -> currencyFormatter.format(value) }
                )
            }

            item {
                FilledTonalButton(
                    onClick = onOpenCourseAnalyticsSearch,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = Color(0xFF4B5CC4),
                        contentColor = Color.White
                    )
                ) {
                    Text("Thống kê chi tiết khóa học")
                }
            }

            item {
                Text(
                    text = "Chi tiết lượt ghi danh",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = StatsTextPrimary
                )
            }

            items(enrollmentTrend) { point ->
                TimelineItem(label = point.label, value = point.value.toInt().toString())
            }

            item {
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun ProgressRingCard(
    modifier: Modifier = Modifier,
    title: String,
    value: Double
) {
    val progress = (value / 100.0).coerceIn(0.0, 1.0).toFloat()

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = StatsCard),
        border = BorderStroke(1.dp, StatsBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = title, color = StatsTextSecondary, fontSize = 12.sp)
            Spacer(modifier = Modifier.height(10.dp))
            Box(contentAlignment = Alignment.Center) {
                Canvas(modifier = Modifier.size(78.dp)) {
                    drawArc(
                        color = StatsBorder,
                        startAngle = -90f,
                        sweepAngle = 360f,
                        useCenter = false,
                        style = Stroke(width = 12f, cap = StrokeCap.Round)
                    )
                    drawArc(
                        color = StatsPrimary,
                        startAngle = -90f,
                        sweepAngle = 360f * progress,
                        useCenter = false,
                        style = Stroke(width = 12f, cap = StrokeCap.Round)
                    )
                }
                Text(
                    text = "${value.toInt()}%",
                    fontWeight = FontWeight.Bold,
                    color = StatsTextPrimary
                )
            }
        }
    }
}

@Composable
private fun LineChartCard(
    title: String,
    points: List<AnalyticsTrendPoint>,
    lineColor: Color,
    valueFormatter: (Double) -> String
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = StatsCard),
        border = BorderStroke(1.dp, StatsBorder)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ShowChart,
                    contentDescription = null,
                    tint = lineColor,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = title,
                    fontWeight = FontWeight.SemiBold,
                    color = StatsTextPrimary
                )
            }
            Spacer(modifier = Modifier.height(10.dp))

            if (points.isEmpty()) {
                Text(
                    text = "Chưa có dữ liệu trong khoảng thời gian này",
                    color = StatsTextSecondary,
                    fontSize = 13.sp
                )
                return@Column
            }

            val maxValue = max(1.0, points.maxOf { it.value })
            val minValue = points.minOf { it.value }

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .background(StatsBackground, RoundedCornerShape(12.dp))
                    .border(1.dp, StatsBorder, RoundedCornerShape(12.dp))
                    .padding(horizontal = 10.dp, vertical = 12.dp)
            ) {
                val chartWidth = size.width
                val chartHeight = size.height

                if (points.size == 1) {
                    drawCircle(color = lineColor, radius = 6f, center = Offset(chartWidth / 2f, chartHeight / 2f))
                    return@Canvas
                }

                repeat(4) { gridIndex ->
                    val y = chartHeight * gridIndex / 3f
                    drawLine(
                        color = StatsBorder,
                        start = Offset(0f, y),
                        end = Offset(chartWidth, y),
                        strokeWidth = 1f
                    )
                }

                val spread = ((maxValue - minValue).takeIf { it > 0.0 } ?: 1.0)
                val xStep = chartWidth / (points.size - 1)
                val pointOffsets = points.mapIndexed { index, point ->
                    val ratio = ((point.value - minValue) / spread).toFloat()
                    val x = index * xStep
                    val y = chartHeight - (ratio * chartHeight)
                    Offset(x, y)
                }

                val path = Path().apply {
                    moveTo(pointOffsets.first().x, pointOffsets.first().y)
                    pointOffsets.drop(1).forEach { point ->
                        lineTo(point.x, point.y)
                    }
                }

                drawPath(
                    path = path,
                    color = lineColor,
                    style = Stroke(width = 4f, cap = StrokeCap.Round)
                )

                pointOffsets.forEach { point ->
                    drawCircle(color = Color.White, radius = 6f, center = point)
                    drawCircle(color = lineColor, radius = 4f, center = point)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                points.forEach { point ->
                    Text(
                        text = point.label,
                        color = StatsTextSecondary,
                        fontSize = 10.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            val lastPoint = points.last()
            Text(
                text = "Mốc mới nhất: ${valueFormatter(lastPoint.value)}",
                color = StatsTextPrimary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp
            )
            Text(
                text = "Thấp nhất: ${valueFormatter(minValue)} | Cao nhất: ${valueFormatter(maxValue)}",
                color = StatsTextSecondary,
                fontSize = 11.sp
            )
        }
    }
}

@Composable
private fun TimelineItem(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(StatsCard, RoundedCornerShape(12.dp))
            .border(1.dp, StatsBorder, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(StatsPrimary, CircleShape)
        )
        Spacer(modifier = Modifier.size(10.dp))
        Text(text = label, color = StatsTextSecondary, fontSize = 12.sp)
        Spacer(modifier = Modifier.size(10.dp))
        Text(text = "Lượt ghi danh", color = StatsTextPrimary, fontWeight = FontWeight.Medium, fontSize = 13.sp)
        Spacer(modifier = Modifier.weight(1f))
        Text(text = value, color = StatsTextPrimary, fontWeight = FontWeight.Bold)
    }
}
