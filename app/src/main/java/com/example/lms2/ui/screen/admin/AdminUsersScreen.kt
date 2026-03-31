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
import androidx.compose.material3.AssistChip
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.lms2.data.model.User
import com.example.lms2.viewmodel.AdminManagementEvent
import com.example.lms2.viewmodel.AdminManagementViewModel

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

    LaunchedEffect(Unit) {
        viewModel.loadUsers()
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
                    TextButton(onClick = { viewModel.loadUsers() }, enabled = !uiState.isLoadingUsers) {
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
                uiState.isLoadingUsers -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                else -> {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
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

                        if (uiState.users.isEmpty()) {
                            item {
                                Text("Chưa có dữ liệu người dùng")
                            }
                        }

                        items(uiState.users, key = { it.uid }) { user ->
                            UserManagementCard(
                                user = user,
                                disableToggle = uiState.isProcessing || user.uid == adminUid,
                                onToggleActive = { viewModel.toggleUserActive(user) }
                            )
                        }
                    }
                }
            }
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
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Tạo tài khoản giảng viên", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

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
private fun UserManagementCard(
    user: User,
    disableToggle: Boolean,
    onToggleActive: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(user.fullName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            Text(user.email, style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.height(10.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(onClick = {}, label = { Text("Vai trò: ${user.role.name}") })
                AssistChip(onClick = {}, label = { Text(if (user.isActive) "Đang hoạt động" else "Đang bị khóa") })
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = onToggleActive,
                enabled = !disableToggle,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (user.isActive) "Khóa tài khoản" else "Mở khóa tài khoản")
            }
        }
    }
}
