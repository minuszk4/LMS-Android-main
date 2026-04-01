package com.example.lms2.ui.screen.student

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.lms2.data.model.InstructorApplication
import com.example.lms2.data.model.InstructorApplicationStatus
import com.example.lms2.data.model.UserRole
import coil.compose.AsyncImage
import com.example.lms2.util.AuthEvent
import com.example.lms2.viewmodel.AuthViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val ProfileBackground = Color(0xFFF8FAFC)
private val ProfileSurface = Color.White
private val ProfileTextPrimary = Color(0xFF1E293B)
private val ProfileTextSecondary = Color(0xFF64748B)
private val ProfilePrimary = Color(0xFF4B5CC4)
private val ProfileBorder = Color(0xFFE2E8F0)
private val ProfileIconBg = Color(0xFFE4E7FF)
private val ProfileIconTint = Color(0xFF4B5CC4)
private val ProfileMuted = Color(0xFF94A3B8)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudentProfileScreen(
	authViewModel: AuthViewModel,
	onLogoutClick: () -> Unit,
	onAccountInfoClick: () -> Unit = {},
	onCartClick: () -> Unit = {},
	onChatbotClick: () -> Unit = {},
	onSettingsClick: () -> Unit = {}
) {
	val uiState by authViewModel.uiState.collectAsStateWithLifecycle()
	val snackbarHostState = remember { SnackbarHostState() }
	val coroutineScope = rememberCoroutineScope()
	val actionItems = remember {
		listOf(
			StudentProfileActionItem("Thông tin tài khoản", Icons.Default.Person),
			StudentProfileActionItem("Giỏ hàng", Icons.Default.ShoppingCart),
			StudentProfileActionItem("Chatbot hỗ trợ học tập", Icons.Default.SmartToy),
			StudentProfileActionItem("Cài đặt", Icons.Default.Settings)
		)
	}

	var showLogoutDialog by remember { mutableStateOf(false) }
	var isLoggingOut by remember { mutableStateOf(false) }
	var showInstructorDialog by remember { mutableStateOf(false) }

	var expertise by remember { mutableStateOf("") }
	var experienceYears by remember { mutableStateOf("") }
	var qualification by remember { mutableStateOf("") }
	var bio by remember { mutableStateOf("") }
	var portfolioUrl by remember { mutableStateOf("") }
	var bankAccountName by remember { mutableStateOf("") }
	var bankAccountNumber by remember { mutableStateOf("") }
	var bankName by remember { mutableStateOf("") }

	fun resetInstructorForm() {
		expertise = ""
		experienceYears = ""
		qualification = ""
		bio = ""
		portfolioUrl = ""
		bankAccountName = ""
		bankAccountNumber = ""
		bankName = ""
	}

	LaunchedEffect(isLoggingOut) {
		if (isLoggingOut) {
			delay(300)
			authViewModel.logout()
			onLogoutClick()
		}
	}

	LaunchedEffect(Unit) {
		authViewModel.event.collect { event ->
			when (event) {
				is AuthEvent.ShowError -> snackbarHostState.showSnackbar(event.message)
				AuthEvent.InstructorApplicationSubmitted -> {
					showInstructorDialog = false
					resetInstructorForm()
					snackbarHostState.showSnackbar("Đã gửi đơn đăng ký giảng viên")
				}
				else -> Unit
			}
		}
	}

	Box(modifier = Modifier.fillMaxSize()) {
		Scaffold(
			snackbarHost = { SnackbarHost(snackbarHostState) },
			topBar = {
				CenterAlignedTopAppBar(
					title = {
						Text(
							"Cá nhân",
							fontWeight = FontWeight.Bold,
							fontSize = 20.sp,
							color = ProfileTextPrimary
						)
					},
					colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
						containerColor = ProfileSurface
					)
				)
			},
			containerColor = ProfileBackground
		) { paddingValues ->
			Column(
				modifier = Modifier
					.fillMaxSize()
					.statusBarsPadding()
					.padding(paddingValues)
					.padding(24.dp),
				horizontalAlignment = Alignment.CenterHorizontally
			) {
				Box(
					modifier = Modifier
						.size(120.dp)
						.clip(CircleShape)
						.background(Color(0xFFE2E8F0)),
					contentAlignment = Alignment.Center
				) {
					if (uiState.currentUser?.avatarUrl.isNullOrEmpty()) {
						Icon(
							imageVector = Icons.Default.Person,
							contentDescription = null,
							modifier = Modifier.size(64.dp),
							tint = ProfileMuted
						)
					} else {
						AsyncImage(
							model = uiState.currentUser?.avatarUrl,
							contentDescription = "Profile Image",
							modifier = Modifier.fillMaxSize(),
							contentScale = ContentScale.Crop
						)
					}
				}

				Spacer(modifier = Modifier.height(16.dp))

				Text(
					text = uiState.currentUser?.fullName ?: "Student",
					fontSize = 22.sp,
					fontWeight = FontWeight.Bold,
					color = ProfileTextPrimary
				)

				Text(
					text = uiState.currentUser?.email ?: "",
					fontSize = 14.sp,
					color = ProfileTextSecondary
				)

				Spacer(modifier = Modifier.height(40.dp))

				Column(
					modifier = Modifier.fillMaxWidth(),
					verticalArrangement = Arrangement.spacedBy(12.dp)
				) {
					actionItems.forEachIndexed { index, item ->
						val clickAction = when (index) {
							0 -> onAccountInfoClick
							1 -> onCartClick
							2 -> onChatbotClick
							else -> onSettingsClick
						}
						ProfileActionRow(
							title = item.title,
							icon = item.icon,
							onClick = clickAction
						)
					}
				}

				Spacer(modifier = Modifier.weight(1f))

				val currentUser = uiState.currentUser
				if (currentUser != null && currentUser.role == UserRole.STUDENT) {
					val requestStatus = currentUser.instructorRequestStatus
					val buttonText = when (requestStatus) {
						InstructorApplicationStatus.PENDING -> "Đơn giảng viên đang chờ duyệt"
						InstructorApplicationStatus.REJECTED -> "Gửi lại đăng ký giảng viên"
						InstructorApplicationStatus.APPROVED -> "Đã được duyệt giảng viên"
						InstructorApplicationStatus.NONE -> "Đăng ký làm giảng viên"
					}

					Button(
						onClick = { showInstructorDialog = true },
						enabled = requestStatus != InstructorApplicationStatus.PENDING && requestStatus != InstructorApplicationStatus.APPROVED,
						modifier = Modifier
							.fillMaxWidth()
							.height(52.dp),
						shape = RoundedCornerShape(12.dp),
						colors = ButtonDefaults.buttonColors(containerColor = ProfilePrimary)
					) {
						Text(buttonText, color = Color.White, fontWeight = FontWeight.Bold)
					}

					currentUser.instructorRequestRejectReason
						?.takeIf { it.isNotBlank() && requestStatus == InstructorApplicationStatus.REJECTED }
						?.let { rejectReason ->
							Spacer(modifier = Modifier.height(8.dp))
							Text(
								text = "Lý do từ chối trước đó: $rejectReason",
								color = Color(0xFFB45309),
								fontSize = 13.sp,
								modifier = Modifier.fillMaxWidth()
							)
						}

					Spacer(modifier = Modifier.height(16.dp))
				}

				if (showInstructorDialog) {
					AlertDialog(
						onDismissRequest = { showInstructorDialog = false },
						title = { Text("Đăng ký làm giảng viên", fontWeight = FontWeight.Bold) },
						text = {
							Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
								OutlinedTextField(
									value = expertise,
									onValueChange = { expertise = it },
									label = { Text("Chuyên môn chính *") },
									singleLine = true,
									modifier = Modifier.fillMaxWidth()
								)

								OutlinedTextField(
									value = experienceYears,
									onValueChange = { experienceYears = it.filter { ch -> ch.isDigit() } },
									label = { Text("Số năm kinh nghiệm *") },
									singleLine = true,
									keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
									modifier = Modifier.fillMaxWidth()
								)

								OutlinedTextField(
									value = qualification,
									onValueChange = { qualification = it },
									label = { Text("Bằng cấp/chứng chỉ *") },
									singleLine = true,
									modifier = Modifier.fillMaxWidth()
								)

								OutlinedTextField(
									value = bio,
									onValueChange = { bio = it },
									label = { Text("Mô tả kinh nghiệm *") },
									modifier = Modifier
										.fillMaxWidth()
										.height(100.dp)
								)

								OutlinedTextField(
									value = portfolioUrl,
									onValueChange = { portfolioUrl = it },
									label = { Text("Portfolio/Website *") },
									singleLine = true,
									modifier = Modifier.fillMaxWidth()
								)

								OutlinedTextField(
									value = bankAccountName,
									onValueChange = { bankAccountName = it },
									label = { Text("Tên chủ tài khoản (tuỳ chọn)") },
									singleLine = true,
									modifier = Modifier.fillMaxWidth()
								)

								OutlinedTextField(
									value = bankAccountNumber,
									onValueChange = { bankAccountNumber = it.filter { ch -> ch.isDigit() } },
									label = { Text("Số tài khoản (tuỳ chọn)") },
									singleLine = true,
									modifier = Modifier.fillMaxWidth()
								)

								OutlinedTextField(
									value = bankName,
									onValueChange = { bankName = it },
									label = { Text("Ngân hàng (tuỳ chọn)") },
									singleLine = true,
									modifier = Modifier.fillMaxWidth()
								)
							}
						},
						confirmButton = {
							TextButton(onClick = {
								val expYears = experienceYears.toIntOrNull()
								if (expertise.isBlank() || qualification.isBlank() || bio.isBlank() || portfolioUrl.isBlank() || expYears == null || expYears <= 0) {
									coroutineScope.launch {
										snackbarHostState.showSnackbar("Vui lòng điền đầy đủ thông tin bắt buộc")
									}
									return@TextButton
								}

								authViewModel.submitInstructorApplication(
									InstructorApplication(
										expertise = expertise,
										experienceYears = expYears,
										qualification = qualification,
										bio = bio,
										portfolioUrl = portfolioUrl,
										bankAccountName = bankAccountName,
										bankAccountNumber = bankAccountNumber,
										bankName = bankName
									)
								)
							}) {
								Text("Gửi đơn")
							}
						},
						dismissButton = {
							TextButton(onClick = {
								showInstructorDialog = false
							}) {
								Text("Hủy")
							}
						}
					)
				}

				OutlinedButton(
					onClick = { showLogoutDialog = true },
					modifier = Modifier
						.fillMaxWidth()
						.height(56.dp),
					shape = RoundedCornerShape(12.dp),
					colors = ButtonDefaults.outlinedButtonColors(
						containerColor = Color(0xFFFFF1F0),
						contentColor = Color(0xFFEF4444)
					),
					border = BorderStroke(1.5.dp, Color(0xFFEF4444)),
					enabled = !isLoggingOut
				) {
					Row(
						verticalAlignment = Alignment.CenterVertically,
						horizontalArrangement = Arrangement.spacedBy(8.dp)
					) {
						Icon(
							Icons.AutoMirrored.Filled.ExitToApp,
							contentDescription = null,
							modifier = Modifier.size(20.dp)
						)
						Text(
							"Đăng xuất",
							fontWeight = FontWeight.Bold,
							fontSize = 16.sp
						)
					}
				}

				Spacer(modifier = Modifier.height(20.dp))
			}
		}

		if (isLoggingOut) {
			Box(
				modifier = Modifier
					.fillMaxSize()
							.background(Color.White.copy(alpha = 0.95f)),
				contentAlignment = Alignment.Center
			) {
				Column(
					horizontalAlignment = Alignment.CenterHorizontally,
					verticalArrangement = Arrangement.spacedBy(16.dp)
				) {
					CircularProgressIndicator(
								color = ProfilePrimary,
						modifier = Modifier.size(48.dp)
					)
					Text(
						text = "Đang đăng xuất...",
						fontSize = 16.sp,
						fontWeight = FontWeight.Medium,
								color = ProfileTextSecondary
					)
				}
			}
		}
	}

	if (showLogoutDialog) {
		AlertDialog(
			onDismissRequest = { showLogoutDialog = false },
			title = {
				Text(
					text = "Đăng xuất",
					fontWeight = FontWeight.Bold,
					color = ProfileTextPrimary
				)
			},
			text = {
				Text(
					text = "Bạn có chắc chắn muốn đăng xuất khỏi tài khoản không?",
						color = ProfileTextSecondary,
					lineHeight = 20.sp
				)
			},
			confirmButton = {
				Button(
					onClick = {
						showLogoutDialog = false
						isLoggingOut = true
					},
					colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
				) {
					Text("Đăng xuất", color = Color.White)
				}
			},
			dismissButton = {
				TextButton(
					onClick = { showLogoutDialog = false }
				) {
					Text("Hủy", color = ProfileTextSecondary)
				}
			},
			shape = RoundedCornerShape(16.dp),
			containerColor = Color.White
		)
	}
}

private data class StudentProfileActionItem(
	val title: String,
	val icon: ImageVector
)

@Composable
private fun ProfileActionRow(
	title: String,
	icon: ImageVector,
	onClick: () -> Unit
) {
	Card(
        onClick = onClick,
		modifier = Modifier
			.fillMaxWidth()
			.height(58.dp),
		shape = RoundedCornerShape(12.dp),
		colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
		border = BorderStroke(0.5.dp, Color(0xFFE2E8F0))
	) {
		Row(
			modifier = Modifier
				.fillMaxSize()
				.padding(horizontal = 12.dp),
			verticalAlignment = Alignment.CenterVertically,
			horizontalArrangement = Arrangement.spacedBy(12.dp)
		) {
			Box(
				modifier = Modifier
					.size(32.dp)
					.clip(RoundedCornerShape(8.dp))
					.background(ProfileIconBg),
				contentAlignment = Alignment.Center
			) {
				Icon(
					imageVector = icon,
					contentDescription = null,
					tint = ProfileIconTint,
					modifier = Modifier.size(18.dp)
				)
			}

			Text(
				text = title,
				modifier = Modifier.weight(1f),
				fontSize = 16.sp,
				fontWeight = FontWeight.Medium,
				color = Color(0xFF111827)
			)

			Icon(
				imageVector = Icons.Default.ChevronRight,
				contentDescription = null,
				tint = Color(0xFF94A3B8),
				modifier = Modifier.size(20.dp)
			)
		}
	}
}
