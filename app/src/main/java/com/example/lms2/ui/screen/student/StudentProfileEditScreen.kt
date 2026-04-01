package com.example.lms2.ui.screen.student

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBarDefaults
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
import com.example.lms2.util.AuthEvent
import com.example.lms2.viewmodel.AuthViewModel

private val EditBg = Color(0xFFF8FAFC)
private val EditSurface = Color.White
private val EditTextPrimary = Color(0xFF1E293B)
private val EditTextSecondary = Color(0xFF64748B)
private val EditPrimary = Color(0xFF4B5CC4)
private val EditBorder = Color(0xFFE2E8F0)
private val EditInputBg = Color.White
private val EditInputBgSoft = Color(0xFFF1F5F9)
private val EditAvatarBg = Color(0xFFE2E8F0)
private val EditMuted = Color(0xFF94A3B8)

@OptIn(ExperimentalMaterial3Api::class)
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
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "Cập nhật thông tin",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = EditTextPrimary
                    )
                },
                navigationIcon = {},
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = EditSurface
                )
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        containerColor = EditBg
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
                    .background(EditAvatarBg),
                contentAlignment = Alignment.Center
            ) {
                if (avatarPreviewModel == null) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = EditMuted,
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
                        color = EditPrimary
                    )
                }
            }

            TextButton(
                enabled = !uiState.isUpdatingProfile,
                onClick = { imagePickerLauncher.launch("image/*") }
            ) {
                Text(
                    text = if (uiState.isUpdatingProfile) "Đang tải ảnh..." else "Thay đổi ảnh đại diện",
                    color = EditTextSecondary,
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
                    color = EditTextPrimary
                )
                OutlinedTextField(
                    value = fullName,
                    onValueChange = { fullName = it },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = EditInputBg,
                        unfocusedContainerColor = EditInputBg,
                        focusedIndicatorColor = EditBorder,
                        unfocusedIndicatorColor = EditBorder,
                        focusedTextColor = EditTextPrimary,
                        unfocusedTextColor = EditTextPrimary,
                        focusedLabelColor = EditTextSecondary,
                        unfocusedLabelColor = EditTextSecondary
                    )
                )

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = "Email",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = EditTextPrimary
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
                            tint = EditMuted
                        )
                    },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = EditInputBgSoft,
                        unfocusedContainerColor = EditInputBgSoft,
                        disabledContainerColor = EditInputBgSoft,
                        focusedIndicatorColor = EditBorder,
                        unfocusedIndicatorColor = EditBorder,
                        disabledIndicatorColor = EditBorder,
                        focusedTextColor = EditTextSecondary,
                        unfocusedTextColor = EditTextSecondary,
                        disabledTextColor = EditTextSecondary
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
                    containerColor = EditPrimary,
                    disabledContainerColor = EditPrimary.copy(alpha = 0.45f)
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

