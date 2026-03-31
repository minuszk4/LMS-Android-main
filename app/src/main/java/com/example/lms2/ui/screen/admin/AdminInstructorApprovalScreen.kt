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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.lms2.data.model.User
import com.example.lms2.viewmodel.AdminApprovalEvent
import com.example.lms2.viewmodel.AdminApprovalViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminInstructorApprovalScreen(
    adminUid: String,
    viewModel: AdminApprovalViewModel,
    onLogoutClick: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var rejectingUser by remember { mutableStateOf<User?>(null) }
    var rejectReason by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        viewModel.loadPendingRequests()
    }

    LaunchedEffect(Unit) {
        viewModel.event.collect { event ->
            when (event) {
                is AdminApprovalEvent.ShowError -> snackbarHostState.showSnackbar(event.message)
                is AdminApprovalEvent.ShowSuccess -> snackbarHostState.showSnackbar(event.message)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Duyệt đăng ký giảng viên", fontWeight = FontWeight.Bold) },
                actions = {
                    TextButton(onClick = { viewModel.loadPendingRequests() }, enabled = !uiState.isLoading) {
                        Text("Làm mới")
                    }
                    TextButton(onClick = onLogoutClick) {
                        Text("Đăng xuất")
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
                uiState.isLoading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                uiState.pendingUsers.isEmpty() -> {
                    Text(
                        text = "Hiện không có đơn đăng ký giảng viên nào đang chờ duyệt.",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                else -> {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(uiState.pendingUsers, key = { it.uid }) { pendingUser ->
                            PendingInstructorCard(
                                user = pendingUser,
                                onApprove = {
                                    viewModel.approveInstructor(pendingUser, adminUid)
                                },
                                onReject = {
                                    rejectingUser = pendingUser
                                    rejectReason = ""
                                },
                                isProcessing = uiState.isProcessing
                            )
                        }
                    }
                }
            }
        }
    }

    if (rejectingUser != null) {
        AlertDialog(
            onDismissRequest = {
                rejectingUser = null
                rejectReason = ""
            },
            title = { Text("Từ chối đơn đăng ký") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Nhập lý do từ chối để học viên biết cần bổ sung gì.")
                    OutlinedTextField(
                        value = rejectReason,
                        onValueChange = { rejectReason = it },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        maxLines = 4,
                        placeholder = { Text("Ví dụ: Cần bổ sung thông tin hồ sơ") }
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val user = rejectingUser ?: return@TextButton
                        viewModel.rejectInstructor(user, adminUid, rejectReason)
                        rejectingUser = null
                        rejectReason = ""
                    }
                ) {
                    Text("Xác nhận")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    rejectingUser = null
                    rejectReason = ""
                }) {
                    Text("Hủy")
                }
            }
        )
    }
}

@Composable
private fun PendingInstructorCard(
    user: User,
    onApprove: () -> Unit,
    onReject: () -> Unit,
    isProcessing: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(user.fullName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            Text(user.email, style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Gửi lúc: ${user.instructorRequestSubmittedAt ?: user.createdAt}",
                style = MaterialTheme.typography.bodySmall
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onApprove,
                    enabled = !isProcessing,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Phê duyệt")
                }
                Button(
                    onClick = onReject,
                    enabled = !isProcessing,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Từ chối")
                }
            }
        }
    }
}
