package com.example.lms.ui.screen.student

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.lms.ui.component.TopBar
import com.example.lms.util.AuthEvent
import com.example.lms.viewmodel.AuthViewModel

@Composable
fun StudentProfileEditScreen(
    authViewModel: AuthViewModel,
    onBackClick: () -> Unit
) {
    val uiState by authViewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var fullName by remember { mutableStateOf("") }
    var avatarUrl by remember { mutableStateOf("") }
    var selectedAvatarUri by remember { mutableStateOf<Uri?>(null) }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        selectedAvatarUri = uri
    }

    LaunchedEffect(uiState.currentUser?.uid) {
        val user = uiState.currentUser ?: return@LaunchedEffect
        fullName = user.fullName
        avatarUrl = user.avatarUrl.orEmpty()
        selectedAvatarUri = null
    }

    LaunchedEffect(Unit) {
        authViewModel.event.collect { event ->
            when (event) {
                is AuthEvent.ShowError -> snackbarHostState.showSnackbar(event.message)
                AuthEvent.ProfileUpdated -> {
                    val updatedUser = uiState.currentUser
                    if (updatedUser != null) {
                        fullName = updatedUser.fullName
                        avatarUrl = updatedUser.avatarUrl.orEmpty()
                    }
                    selectedAvatarUri = null
                    snackbarHostState.showSnackbar("Đã cập nhật thông tin")
                }
                else -> Unit
            }
        }
    }

    val email = uiState.currentUser?.email.orEmpty()
    val originalFullName = uiState.currentUser?.fullName.orEmpty()
    val hasNameChanged = fullName.trim() != originalFullName.trim()
    val hasAvatarChanged = selectedAvatarUri != null
    val hasChanges = hasNameChanged || hasAvatarChanged
    val canSave = fullName.isNotBlank() && !uiState.isUpdatingProfile && hasChanges
    val avatarPreviewModel: Any? = selectedAvatarUri ?: avatarUrl.takeIf { it.isNotBlank() }

    Scaffold(
        topBar = {
            TopBar(
                title = "Cập nhật thông tin",
                onBackClick = onBackClick
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        containerColor = Color(0xFFF8FAFC)
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(paddingValues)
                .padding(horizontal = 24.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFE2E8F0)),
                contentAlignment = Alignment.Center
            ) {
                if (avatarPreviewModel == null) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = Color(0xFF94A3B8),
                        modifier = Modifier.size(64.dp)
                    )
                } else {
                    AsyncImage(
                        model = avatarPreviewModel,
                        contentDescription = "Avatar",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }

                if (uiState.isUpdatingProfile) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(36.dp),
                        strokeWidth = 3.dp,
                        color = Color(0xFF4B5CC4)
                    )
                }
            }

            TextButton(
                enabled = !uiState.isUpdatingProfile,
                onClick = { imagePickerLauncher.launch("image/*") }
            ) {
                Text(
                    text = if (uiState.isUpdatingProfile) "Đang tải ảnh..." else "Thay đổi ảnh đại diện",
                    color = Color(0xFF64748B),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "Họ và tên",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF1F2937)
                )
                OutlinedTextField(
                    value = fullName,
                    onValueChange = { fullName = it },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White,
                        focusedIndicatorColor = Color(0xFFCBD5E1),
                        unfocusedIndicatorColor = Color(0xFFCBD5E1)
                    )
                )

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = "Email",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF1F2937)
                )
                OutlinedTextField(
                    value = email,
                    onValueChange = {},
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    readOnly = true,
                    singleLine = true,
                    trailingIcon = {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = Color(0xFF9CA3AF)
                        )
                    },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color(0xFFF1F5F9),
                        unfocusedContainerColor = Color(0xFFF1F5F9),
                        disabledContainerColor = Color(0xFFF1F5F9),
                        focusedIndicatorColor = Color(0xFFE2E8F0),
                        unfocusedIndicatorColor = Color(0xFFE2E8F0),
                        disabledIndicatorColor = Color(0xFFE2E8F0),
                        focusedTextColor = Color(0xFF6B7280),
                        unfocusedTextColor = Color(0xFF6B7280)
                    )
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = {
                    authViewModel.saveStudentProfile(
                        fullName = fullName,
                        currentAvatarUrl = avatarUrl,
                        selectedAvatarUri = selectedAvatarUri
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(12.dp),
                enabled = canSave,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF4B5CC4),
                    disabledContainerColor = Color(0xFFBFC7F5)
                )
            ) {
                if (uiState.isUpdatingProfile) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = Color.White
                    )
                } else {
                    Text(
                        text = "Lưu thông tin",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

