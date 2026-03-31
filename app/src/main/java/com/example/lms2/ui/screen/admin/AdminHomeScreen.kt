package com.example.lms2.ui.screen.admin

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.ChecklistRtl
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.lms2.viewmodel.AdminManagementEvent
import com.example.lms2.viewmodel.AdminManagementViewModel

private val HomeBackground = Color(0xFFF8FAFC)
private val CardBackground = Color(0xFFFFFFFF)
private val BorderColor = Color(0xFFE2E8F0)
private val TextPrimary = Color(0xFF1E293B)
private val TextSecondary = Color(0xFF64748B)
private val Primary = Color(0xFF4B5CC4)

@Composable
fun AdminHomeRoute(
    viewModel: AdminManagementViewModel,
    onOpenApprovals: () -> Unit,
    onOpenUsers: () -> Unit,
    onOpenCourses: () -> Unit,
    onOpenCategories: () -> Unit
) {
    val uiState = viewModel.uiState.collectAsStateWithLifecycle().value
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.loadSummary()
    }

    LaunchedEffect(Unit) {
        viewModel.event.collect { event ->
            when (event) {
                is AdminManagementEvent.ShowError -> snackbarHostState.showSnackbar(event.message)
                is AdminManagementEvent.ShowSuccess -> snackbarHostState.showSnackbar(event.message)
            }
        }
    }

    AdminHomeScreen(
        isLoading = uiState.isLoadingSummary,
        totalUsers = uiState.summary.totalUsers,
        activeUsers = uiState.summary.activeUsers,
        pendingRequests = uiState.summary.pendingInstructorRequests,
        totalCourses = uiState.summary.totalCourses,
        unpublishedCourses = uiState.summary.unpublishedCourses,
        snackbarHostState = snackbarHostState,
        onRefresh = { viewModel.loadSummary() },
        onOpenApprovals = onOpenApprovals,
        onOpenUsers = onOpenUsers,
        onOpenCourses = onOpenCourses,
        onOpenCategories = onOpenCategories
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AdminHomeScreen(
    isLoading: Boolean,
    totalUsers: Int,
    activeUsers: Int,
    pendingRequests: Int,
    totalCourses: Int,
    unpublishedCourses: Int,
    snackbarHostState: SnackbarHostState,
    onRefresh: () -> Unit,
    onOpenApprovals: () -> Unit,
    onOpenUsers: () -> Unit,
    onOpenCourses: () -> Unit,
    onOpenCategories: () -> Unit
) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "Trang chủ quản trị",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                },
                actions = {
                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Default.Refresh, contentDescription = "Tải lại")
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
                    colors = CardDefaults.cardColors(containerColor = CardBackground),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, BorderColor, MaterialTheme.shapes.large)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.AdminPanelSettings, contentDescription = null, tint = Primary)
                            Text("Quản trị hệ thống", color = Primary, fontWeight = FontWeight.SemiBold)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Theo dõi người dùng, duyệt hồ sơ giảng viên và kiểm soát khóa học từ một nơi.",
                            color = TextSecondary,
                            fontSize = 13.sp
                        )
                    }
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SummaryCard(title = "Người dùng", value = totalUsers.toString(), subtitle = "$activeUsers đang hoạt động", modifier = Modifier.weight(1f))
                    SummaryCard(title = "Đơn chờ duyệt", value = pendingRequests.toString(), subtitle = "Đăng ký giảng viên", modifier = Modifier.weight(1f))
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SummaryCard(title = "Tổng khóa học", value = totalCourses.toString(), subtitle = "$unpublishedCourses chưa xuất bản", modifier = Modifier.weight(1f))
                    SummaryCard(title = "Mục tiêu", value = "Ổn định", subtitle = "Vận hành nền tảng", modifier = Modifier.weight(1f))
                }
            }

            item {
                FilledTonalButton(
                    onClick = onOpenApprovals,
                    modifier = Modifier.fillMaxWidth(),
                    colors = androidx.compose.material3.ButtonDefaults.filledTonalButtonColors(
                        containerColor = Color(0xFFEEF2FF),
                        contentColor = Primary
                    )
                ) {
                    Icon(Icons.Default.ChecklistRtl, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.size(8.dp))
                    Text("Mở trang duyệt giảng viên", fontWeight = FontWeight.SemiBold)
                }
            }

            item {
                FilledTonalButton(
                    onClick = onOpenUsers,
                    modifier = Modifier.fillMaxWidth(),
                    colors = androidx.compose.material3.ButtonDefaults.filledTonalButtonColors(
                        containerColor = Color(0xFFF1F5F9),
                        contentColor = TextPrimary
                    )
                ) {
                    Icon(Icons.Default.People, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.size(8.dp))
                    Text("Quản lý người dùng", fontWeight = FontWeight.SemiBold)
                }
            }

            item {
                FilledTonalButton(
                    onClick = onOpenCourses,
                    modifier = Modifier.fillMaxWidth(),
                    colors = androidx.compose.material3.ButtonDefaults.filledTonalButtonColors(
                        containerColor = Color(0xFFF1F5F9),
                        contentColor = TextPrimary
                    )
                ) {
                    Icon(Icons.Default.School, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.size(8.dp))
                    Text("Quản lý khóa học", fontWeight = FontWeight.SemiBold)
                }
            }

            item {
                FilledTonalButton(
                    onClick = onOpenCategories,
                    modifier = Modifier.fillMaxWidth(),
                    colors = androidx.compose.material3.ButtonDefaults.filledTonalButtonColors(
                        containerColor = Color(0xFFF1F5F9),
                        contentColor = TextPrimary
                    )
                ) {
                    Icon(Icons.Default.ViewList, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.size(8.dp))
                    Text("Quản lý danh mục", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun SummaryCard(
    title: String,
    value: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = CardBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(CardBackground)
                .padding(14.dp)
        ) {
            Text(text = title, color = TextSecondary, fontSize = 12.sp)
            Spacer(modifier = Modifier.height(6.dp))
            Text(text = value, color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = subtitle, color = TextSecondary, fontSize = 12.sp)
        }
    }
}
