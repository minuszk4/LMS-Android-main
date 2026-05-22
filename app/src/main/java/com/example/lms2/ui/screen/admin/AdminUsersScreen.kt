package com.example.lms2.ui.screen.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.lms2.data.model.Course
import com.example.lms2.data.model.Enrollment
import com.example.lms2.data.model.InstructorApplicationStatus
import com.example.lms2.data.model.Progress
import com.example.lms2.data.model.User
import com.example.lms2.data.model.UserRole
import com.example.lms2.viewmodel.AdminManagementEvent
import com.example.lms2.viewmodel.AdminManagementViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class AdminRoleFilter(val label: String) {
    ALL("Tất cả"),
    STUDENT("Học viên"),
    INSTRUCTOR("Giảng viên"),
    ADMIN("Admin")
}

private enum class AdminStateFilter(val label: String) {
    ALL("Toàn bộ"),
    ACTIVE("Đang hoạt động"),
    LOCKED("Đã khóa"),
    PENDING_REQUEST("Chờ duyệt GV")
}

private data class StudentCourseInsight(
    val enrollment: Enrollment,
    val course: Course,
    val progress: Progress?,
    val progressFraction: Float,
    val statusLabel: String,
    val latestActivityAt: Long?
)

private data class InstructorCourseInsight(
    val course: Course,
    val studentCount: Int
)

private data class UserAdminInsight(
    val user: User,
    val studentCourses: List<StudentCourseInsight>,
    val instructorCourses: List<InstructorCourseInsight>,
    val enrolledCourseCount: Int,
    val completedCourseCount: Int,
    val inProgressCourseCount: Int,
    val publishedCourseCount: Int,
    val totalInstructorStudents: Int,
    val latestActivityAt: Long?
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminUsersScreen(
    adminUid: String,
    viewModel: AdminManagementViewModel
) {
    val uiState = viewModel.uiState.collectAsStateWithLifecycle().value
    val snackbarHostState = remember { SnackbarHostState() }
    var instructorFullName by rememberSaveable { mutableStateOf("") }
    var instructorEmail by rememberSaveable { mutableStateOf("") }
    var instructorPassword by rememberSaveable { mutableStateOf("") }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var roleFilter by rememberSaveable { mutableStateOf(AdminRoleFilter.ALL) }
    var stateFilter by rememberSaveable { mutableStateOf(AdminStateFilter.ALL) }
    var selectedUser by remember { mutableStateOf<UserAdminInsight?>(null) }

    val insights by remember(
        uiState.users,
        uiState.courses,
        uiState.enrollments,
        uiState.progresses
    ) {
        derivedStateOf {
            buildUserInsights(
                users = uiState.users,
                courses = uiState.courses,
                enrollments = uiState.enrollments,
                progresses = uiState.progresses
            )
        }
    }

    val filteredInsights by remember(insights, searchQuery, roleFilter, stateFilter) {
        derivedStateOf {
            insights
                .filter { insight ->
                    val matchesQuery = searchQuery.isBlank() ||
                        insight.user.fullName.contains(searchQuery, ignoreCase = true) ||
                        insight.user.email.contains(searchQuery, ignoreCase = true)
                    val matchesRole = when (roleFilter) {
                        AdminRoleFilter.ALL -> true
                        AdminRoleFilter.STUDENT -> insight.user.role == UserRole.STUDENT
                        AdminRoleFilter.INSTRUCTOR -> insight.user.role == UserRole.INSTRUCTOR
                        AdminRoleFilter.ADMIN -> insight.user.role == UserRole.ADMIN
                    }
                    val matchesState = when (stateFilter) {
                        AdminStateFilter.ALL -> true
                        AdminStateFilter.ACTIVE -> insight.user.isActive
                        AdminStateFilter.LOCKED -> !insight.user.isActive
                        AdminStateFilter.PENDING_REQUEST ->
                            insight.user.instructorRequestStatus == InstructorApplicationStatus.PENDING
                    }
                    matchesQuery && matchesRole && matchesState
                }
                .sortedWith(
                    compareByDescending<UserAdminInsight> {
                        it.user.instructorRequestStatus == InstructorApplicationStatus.PENDING
                    }.thenByDescending {
                        it.latestActivityAt ?: it.user.createdAt
                    }
                )
        }
    }

    val isRefreshing = uiState.isLoadingUsers || uiState.isLoadingCourses || uiState.isLoadingLearningData
    val initialLoading = uiState.users.isEmpty() && isRefreshing

    LaunchedEffect(Unit) {
        viewModel.loadUsers()
        viewModel.loadCourses()
        viewModel.loadLearningData()
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
                title = { Text("Quản lý người dùng", fontWeight = FontWeight.Bold) },
                actions = {
                    TextButton(
                        onClick = {
                            viewModel.loadUsers()
                            viewModel.loadCourses()
                            viewModel.loadLearningData()
                        },
                        enabled = !isRefreshing
                    ) {
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
            if (initialLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    item {
                        CreateInstructorCard(
                            fullName = instructorFullName,
                            email = instructorEmail,
                            password = instructorPassword,
                            isProcessing = uiState.isProcessing,
                            onFullNameChange = { instructorFullName = it },
                            onEmailChange = { instructorEmail = it },
                            onPasswordChange = { instructorPassword = it },
                            onCreateInstructor = {
                                viewModel.createInstructorAccount(
                                    adminUid = adminUid,
                                    fullName = instructorFullName,
                                    email = instructorEmail,
                                    password = instructorPassword
                                )
                                instructorPassword = ""
                            }
                        )
                    }

                    item {
                        SummarySection(insights = filteredInsights)
                    }

                    item {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            label = { Text("Tìm theo tên hoặc email") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }

                    item {
                        FilterSection(
                            title = "Vai trò",
                            options = AdminRoleFilter.values().toList(),
                            selectedOption = roleFilter,
                            onOptionSelected = { roleFilter = it }
                        )
                    }

                    item {
                        FilterSection(
                            title = "Trạng thái",
                            options = AdminStateFilter.values().toList(),
                            selectedOption = stateFilter,
                            onOptionSelected = { stateFilter = it }
                        )
                    }

                    if (uiState.isLoadingLearningData) {
                        item {
                            Text(
                                "Đang đồng bộ tiến độ học tập và khóa học đã mua...",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (filteredInsights.isEmpty()) {
                        item {
                            EmptyStateCard(
                                message = "Không có người dùng phù hợp với bộ lọc hiện tại"
                            )
                        }
                    }

                    items(filteredInsights, key = { it.user.uid }) { insight ->
                        UserManagementCard(
                            insight = insight,
                            disableToggle = uiState.isProcessing || insight.user.uid == adminUid,
                            onToggleActive = { viewModel.toggleUserActive(insight.user) },
                            onOpenDetail = { selectedUser = insight }
                        )
                    }
                }
            }
        }

        selectedUser?.let { insight ->
            UserDetailDialog(
                insight = insight,
                onDismiss = { selectedUser = null }
            )
        }
    }
}

@Composable
private fun CreateInstructorCard(
    fullName: String,
    email: String,
    password: String,
    isProcessing: Boolean,
    onFullNameChange: (String) -> Unit,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onCreateInstructor: () -> Unit
) {
    val canSubmit = fullName.isNotBlank() && email.isNotBlank() && password.length >= 6

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "Tạo tài khoản giảng viên",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Admin có thể tạo nhanh tài khoản giảng viên để khởi tạo khóa học và theo dõi thanh toán sau này.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OutlinedTextField(
                value = fullName,
                onValueChange = onFullNameChange,
                label = { Text("Họ và tên") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = email,
                onValueChange = onEmailChange,
                label = { Text("Email") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = password,
                onValueChange = onPasswordChange,
                label = { Text("Mật khẩu tạm") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Button(
                onClick = onCreateInstructor,
                enabled = canSubmit && !isProcessing,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Tạo tài khoản instructor")
            }
        }
    }
}

@Composable
private fun SummarySection(insights: List<UserAdminInsight>) {
    val totalUsers = insights.size
    val studentCount = insights.count { it.user.role == UserRole.STUDENT }
    val instructorCount = insights.count { it.user.role == UserRole.INSTRUCTOR }
    val attentionCount = insights.count {
        !it.user.isActive || it.user.instructorRequestStatus == InstructorApplicationStatus.PENDING
    }

    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            SummaryCard(title = "Đang hiển thị", value = totalUsers.toString(), subtitle = "Người dùng")
        }
        item {
            SummaryCard(title = "Học viên", value = studentCount.toString(), subtitle = "Tài khoản")
        }
        item {
            SummaryCard(title = "Giảng viên", value = instructorCount.toString(), subtitle = "Tài khoản")
        }
        item {
            SummaryCard(title = "Cần chú ý", value = attentionCount.toString(), subtitle = "Khóa hoặc chờ duyệt")
        }
    }
}

@Composable
private fun SummaryCard(
    title: String,
    value: String,
    subtitle: String
) {
    Card(
        modifier = Modifier.width(152.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                title,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun <T> FilterSection(
    title: String,
    options: List<T>,
    selectedOption: T,
    onOptionSelected: (T) -> Unit
) where T : Enum<T> {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(options) { option ->
                val label = when (option) {
                    is AdminRoleFilter -> option.label
                    is AdminStateFilter -> option.label
                    else -> option.name
                }
                AssistChip(
                    onClick = { onOptionSelected(option) },
                    label = {
                        Text(if (option == selectedOption) "• $label" else label)
                    }
                )
            }
        }
    }
}

@Composable
private fun EmptyStateCard(message: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Text(
            text = message,
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun UserManagementCard(
    insight: UserAdminInsight,
    disableToggle: Boolean,
    onToggleActive: () -> Unit,
    onOpenDetail: () -> Unit
) {
    val user = insight.user

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AsyncImage(
                model = user.avatarUrl?.ifBlank { null } ?: "https://i.pravatar.cc/256?u=${user.uid}",
                contentDescription = user.fullName,
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(12.dp)),
                contentScale = ContentScale.Crop
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    user.fullName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(user.email, style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(10.dp))

                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        AssistChip(onClick = {}, label = { Text(roleLabel(user.role)) })
                    }
                    item {
                        AssistChip(
                            onClick = {},
                            label = { Text(if (user.isActive) "Đang hoạt động" else "Đã khóa") }
                        )
                    }
                    if (user.instructorRequestStatus == InstructorApplicationStatus.PENDING) {
                        item {
                            AssistChip(onClick = {}, label = { Text("Chờ duyệt giảng viên") })
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    summaryLine(insight),
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    buildMetaLine(insight),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onOpenDetail) {
                        Text("Xem chi tiết")
                    }
                    Button(
                        onClick = onToggleActive,
                        enabled = !disableToggle
                    ) {
                        Text(if (user.isActive) "Khóa tài khoản" else "Mở khóa")
                    }
                }
            }
        }
    }
}

@Composable
private fun UserDetailDialog(
    insight: UserAdminInsight,
    onDismiss: () -> Unit
) {
    val user = insight.user
    val scrollState = rememberScrollState()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(user.fullName) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 560.dp)
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                DetailHeader(insight = insight)
                OverviewSection(insight = insight)

                if (user.instructorApplication != null || user.instructorRequestStatus != InstructorApplicationStatus.NONE) {
                    InstructorApplicationSection(user = user)
                }

                if (insight.studentCourses.isNotEmpty()) {
                    StudentCoursesSection(courses = insight.studentCourses)
                } else if (user.role == UserRole.STUDENT) {
                    EmptyStateCard(message = "Học viên này chưa ghi danh khóa học nào.")
                }

                if (user.role == UserRole.INSTRUCTOR) {
                    if (insight.instructorCourses.isNotEmpty()) {
                        InstructorCoursesSection(courses = insight.instructorCourses)
                    } else {
                        EmptyStateCard(message = "Giảng viên này chưa có khóa học nào.")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Đóng")
            }
        }
    )
}

@Composable
private fun DetailHeader(insight: UserAdminInsight) {
    val user = insight.user

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AsyncImage(
                model = user.avatarUrl?.ifBlank { null } ?: "https://i.pravatar.cc/256?u=${user.uid}",
                contentDescription = user.fullName,
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(14.dp)),
                contentScale = ContentScale.Crop
            )

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(user.email, style = MaterialTheme.typography.bodyMedium)
                Text("Vai trò: ${roleLabel(user.role)}", style = MaterialTheme.typography.bodySmall)
                Text(
                    "Trạng thái: ${if (user.isActive) "Đang hoạt động" else "Đã khóa"}",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    "Tạo lúc: ${formatDateTime(user.createdAt)}",
                    style = MaterialTheme.typography.bodySmall
                )
                insight.latestActivityAt?.let {
                    Text(
                        "Hoạt động gần nhất: ${formatDateTime(it)}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

@Composable
private fun OverviewSection(insight: UserAdminInsight) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle("Tổng quan")
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                when (insight.user.role) {
                    UserRole.STUDENT -> {
                        DetailLine("Khóa đã ghi danh", insight.enrolledCourseCount.toString())
                        DetailLine("Khóa hoàn thành", insight.completedCourseCount.toString())
                        DetailLine("Khóa đang học", insight.inProgressCourseCount.toString())
                    }

                    UserRole.INSTRUCTOR -> {
                        DetailLine("Tổng khóa học", insight.instructorCourses.size.toString())
                        DetailLine("Đã xuất bản", insight.publishedCourseCount.toString())
                        DetailLine("Tổng học viên", insight.totalInstructorStudents.toString())
                    }

                    UserRole.ADMIN -> {
                        DetailLine("Phạm vi", "Quản trị hệ thống")
                        DetailLine("Mô tả", "Tài khoản có quyền quản lý người dùng, khóa học và thanh toán")
                    }
                }
            }
        }
    }
}

@Composable
private fun InstructorApplicationSection(user: User) {
    val application = user.instructorApplication

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle("Hồ sơ giảng viên")
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DetailLine("Trạng thái duyệt", instructorRequestLabel(user.instructorRequestStatus))
                user.instructorRequestSubmittedAt?.let {
                    DetailLine("Gửi yêu cầu lúc", formatDateTime(it))
                }
                user.instructorRequestReviewedAt?.let {
                    DetailLine("Duyệt lúc", formatDateTime(it))
                }
                if (!user.instructorRequestRejectReason.isNullOrBlank()) {
                    DetailLine("Lý do từ chối", user.instructorRequestRejectReason)
                }

                application?.let {
                    if (it.expertise.isNotBlank()) DetailLine("Chuyên môn", it.expertise)
                    if (it.qualification.isNotBlank()) DetailLine("Bằng cấp", it.qualification)
                    if (it.bio.isNotBlank()) DetailLine("Giới thiệu", it.bio)
                    if (it.experienceYears > 0) DetailLine("Kinh nghiệm", "${it.experienceYears} năm")
                    if (it.portfolioUrl.isNotBlank()) DetailLine("Portfolio", it.portfolioUrl)
                    if (it.bankName.isNotBlank()) DetailLine("Ngân hàng", it.bankName)
                    if (it.bankAccountName.isNotBlank()) DetailLine("Chủ tài khoản", it.bankAccountName)
                    if (it.bankAccountNumber.isNotBlank()) DetailLine("Số tài khoản", it.bankAccountNumber)
                }
            }
        }
    }
}

@Composable
private fun StudentCoursesSection(courses: List<StudentCourseInsight>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle("Khóa học của học viên")
        courses.forEach { item ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        AsyncImage(
                            model = item.course.thumbnailUrl.ifBlank { "https://picsum.photos/seed/${item.course.id}/640/360" },
                            contentDescription = item.course.title,
                            modifier = Modifier
                                .size(64.dp)
                                .clip(RoundedCornerShape(12.dp)),
                            contentScale = ContentScale.Crop
                        )

                        Column(modifier = Modifier.weight(1f)) {
                            Text(item.course.title, fontWeight = FontWeight.SemiBold)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "Đăng ký: ${formatDateTime(item.enrollment.enrolledAt)}",
                                style = MaterialTheme.typography.bodySmall
                            )
                            item.latestActivityAt?.let {
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    "Hoạt động gần nhất: ${formatDateTime(it)}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }

                    AssistChip(onClick = {}, label = { Text(item.statusLabel) })

                    if (item.course.lessonCount > 0) {
                        LinearProgressIndicator(
                            progress = item.progressFraction,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            "${item.progress?.completedLessons ?: 0}/${item.course.lessonCount} bài học hoàn thành",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InstructorCoursesSection(courses: List<InstructorCourseInsight>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle("Khóa học giảng dạy")
        courses.forEach { item ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    AsyncImage(
                        model = item.course.thumbnailUrl.ifBlank { "https://picsum.photos/seed/${item.course.id}/640/360" },
                        contentDescription = item.course.title,
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Crop
                    )

                    Column(modifier = Modifier.weight(1f)) {
                        Text(item.course.title, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(6.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            item {
                                AssistChip(
                                    onClick = {},
                                    label = { Text(if (item.course.isPublished) "Đã xuất bản" else "Bản nháp") }
                                )
                            }
                            item {
                                AssistChip(
                                    onClick = {},
                                    label = { Text("${item.studentCount} học viên") }
                                )
                            }
                            item {
                                AssistChip(
                                    onClick = {},
                                    label = { Text("${item.course.lessonCount} bài học") }
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            "Tạo lúc: ${formatDateTime(item.course.createdAt)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold
    )
}

@Composable
private fun DetailLine(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

private fun buildUserInsights(
    users: List<User>,
    courses: List<Course>,
    enrollments: List<Enrollment>,
    progresses: List<Progress>
): List<UserAdminInsight> {
    val courseById = courses.associateBy { it.id }
    val coursesByInstructor = courses.groupBy { it.instructorId }
    val enrollmentsByUser = enrollments.groupBy { it.userId }
    val enrollmentCountByCourse = enrollments.groupingBy { it.courseId }.eachCount()
    val progressesByUser = progresses.groupBy { it.userId }

    return users.map { user ->
        val userProgressByCourse = progressesByUser[user.uid].orEmpty().associateBy { it.courseId }
        val studentCourses = enrollmentsByUser[user.uid].orEmpty()
            .mapNotNull { enrollment ->
                val course = courseById[enrollment.courseId] ?: return@mapNotNull null
                val progress = userProgressByCourse[course.id]
                val latestActivityAt = progress?.lastAccessedAt?.takeIf { it > 0L }
                val progressFraction = when {
                    course.lessonCount > 0 -> {
                        ((progress?.completedLessons ?: 0).toFloat() / course.lessonCount.toFloat())
                            .coerceIn(0f, 1f)
                    }

                    progress?.isCompleted == true -> 1f
                    else -> 0f
                }
                val statusLabel = when {
                    progress?.isCompleted == true -> "Hoàn thành"
                    progress != null && (progress.completedLessons > 0 || (progress.lastAccessedAt > 0L)) -> "Đang học"
                    else -> "Chưa học"
                }

                StudentCourseInsight(
                    enrollment = enrollment,
                    course = course,
                    progress = progress,
                    progressFraction = progressFraction,
                    statusLabel = statusLabel,
                    latestActivityAt = latestActivityAt
                )
            }
            .sortedByDescending { (it.latestActivityAt ?: 0L).coerceAtLeast(it.enrollment.enrolledAt) }

        val instructorCourses = coursesByInstructor[user.uid].orEmpty()
            .map { course ->
                InstructorCourseInsight(
                    course = course,
                    studentCount = enrollmentCountByCourse[course.id] ?: course.enrollmentCount
                )
            }
            .sortedByDescending { it.studentCount }

        val completedCourseCount = studentCourses.count { it.progress?.isCompleted == true }
        val inProgressCourseCount = studentCourses.count {
            it.progress != null && it.progress.isCompleted.not() &&
                (it.progress.completedLessons > 0 || (it.progress.lastAccessedAt > 0L))
        }
        val latestActivityAt = listOfNotNull(
            studentCourses.maxOfOrNull { it.latestActivityAt ?: 0L }?.takeIf { it > 0L },
            studentCourses.maxOfOrNull { it.enrollment.enrolledAt }
        ).maxOrNull()

        UserAdminInsight(
            user = user,
            studentCourses = studentCourses,
            instructorCourses = instructorCourses,
            enrolledCourseCount = studentCourses.size,
            completedCourseCount = completedCourseCount,
            inProgressCourseCount = inProgressCourseCount,
            publishedCourseCount = instructorCourses.count { it.course.isPublished },
            totalInstructorStudents = instructorCourses.sumOf { it.studentCount },
            latestActivityAt = latestActivityAt
        )
    }
}

private fun roleLabel(role: UserRole): String {
    return when (role) {
        UserRole.STUDENT -> "Học viên"
        UserRole.INSTRUCTOR -> "Giảng viên"
        UserRole.ADMIN -> "Admin"
    }
}

private fun instructorRequestLabel(status: InstructorApplicationStatus): String {
    return when (status) {
        InstructorApplicationStatus.NONE -> "Chưa gửi"
        InstructorApplicationStatus.PENDING -> "Chờ duyệt"
        InstructorApplicationStatus.APPROVED -> "Đã duyệt"
        InstructorApplicationStatus.REJECTED -> "Đã từ chối"
    }
}

private fun summaryLine(insight: UserAdminInsight): String {
    return when (insight.user.role) {
        UserRole.STUDENT -> "Đã ghi danh ${insight.enrolledCourseCount} khóa, hoàn thành ${insight.completedCourseCount}, đang học ${insight.inProgressCourseCount}"
        UserRole.INSTRUCTOR -> "Đang quản lý ${insight.instructorCourses.size} khóa học, ${insight.publishedCourseCount} khóa đã xuất bản, ${insight.totalInstructorStudents} học viên"
        UserRole.ADMIN -> "Tài khoản quản trị hệ thống"
    }
}

private fun buildMetaLine(insight: UserAdminInsight): String {
    val joined = "Tạo lúc ${formatDateTime(insight.user.createdAt)}"
    val latest = insight.latestActivityAt?.let { "Hoạt động gần nhất ${formatDateTime(it)}" }
    return listOfNotNull(joined, latest).joinToString(" • ")
}

private fun formatDateTime(timestamp: Long): String {
    return SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("vi", "VN")).format(Date(timestamp))
}
