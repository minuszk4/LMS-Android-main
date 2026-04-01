package com.example.lms2.ui.screen.instructor.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.lms2.data.model.Instructor
import com.example.lms2.ui.component.TopBar
import com.example.lms2.util.InstructorPersonalInfoEvent
import com.example.lms2.viewmodel.InstructorPersonalInfoViewModel

@Composable
fun InstructorPersonalInfoRoute(
    instructorId: String,
    viewModel: InstructorPersonalInfoViewModel,
    onBackClick: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(instructorId) {
        viewModel.init(instructorId)
    }

    LaunchedEffect(Unit) {
        viewModel.event.collect { event ->
            when (event) {
                is InstructorPersonalInfoEvent.ShowError -> {
                    snackbarHostState.showSnackbar(event.message)
                }

                is InstructorPersonalInfoEvent.ShowSuccess -> {
                    snackbarHostState.showSnackbar(event.message)
                }
            }
        }
    }

    InstructorPersonalInfoScreen(
        instructorId = instructorId,
        isLoading = uiState.isLoading,
        isSaving = uiState.isSaving,
        instructor = uiState.instructor,
        snackbarHostState = snackbarHostState,
        onSaveBankInfo = viewModel::saveBankInfo,
        onSaveInstructorProfile = viewModel::saveInstructorProfile,
        onBackClick = onBackClick
    )
}

@Composable
private fun InstructorPersonalInfoScreen(
    instructorId: String,
    isLoading: Boolean,
    isSaving: Boolean,
    instructor: Instructor?,
    snackbarHostState: SnackbarHostState,
    onSaveBankInfo: (String, String, String, String, String) -> Unit,
    onSaveInstructorProfile: (String, String, String, Int) -> Unit,
    onBackClick: () -> Unit
) {
    var bankName by remember(instructor?.uid, instructor?.bankName) { mutableStateOf(instructor?.bankName.orEmpty()) }
    var bankCode by remember(instructor?.uid, instructor?.bankCode) { mutableStateOf(instructor?.bankCode.orEmpty()) }
    var bankAccountNumber by remember(instructor?.uid, instructor?.bankAccountNumber) {
        mutableStateOf(instructor?.bankAccountNumber.orEmpty())
    }
    var bankAccountHolder by remember(instructor?.uid, instructor?.bankAccountHolder) {
        mutableStateOf(instructor?.bankAccountHolder.orEmpty())
    }

    var expertise by remember(instructor?.uid, instructor?.expertise) { mutableStateOf(instructor?.expertise.orEmpty()) }
    var qualification by remember(instructor?.uid, instructor?.qualification) { mutableStateOf(instructor?.qualification.orEmpty()) }
    var experienceYears by remember(instructor?.uid, instructor?.experienceYears) { mutableStateOf(instructor?.experienceYears?.toString().orEmpty()) }

    Scaffold(
        topBar = {
            TopBar(
                title = "Thông tin cá nhân",
                onBackClick = onBackClick
            )
        },
        containerColor = Color(0xFFF8FAFC),
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Color(0xFF4B5CC4))
            }
            return@Scaffold
        }

        if (instructor == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Chưa có thông tin cá nhân",
                    color = Color(0xFF64748B),
                    fontSize = 14.sp
                )
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Editable Instructor Profile Section
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(14.dp)),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Thông tin chuyên môn",
                        color = Color(0xFF1E293B),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )

                    OutlinedTextField(
                        value = expertise,
                        onValueChange = { expertise = it },
                        label = { Text("Chuyên môn") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = qualification,
                        onValueChange = { qualification = it },
                        label = { Text("Trình độ/Bằng cấp") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = experienceYears,
                        onValueChange = { experienceYears = it.filter(Char::isDigit) },
                        label = { Text("Số năm kinh nghiệm") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number)
                    )

                    Button(
                        onClick = {
                            val yearsInt = experienceYears.toIntOrNull() ?: 0
                            onSaveInstructorProfile(
                                instructorId,
                                expertise,
                                qualification,
                                yearsInt
                            )
                        },
                        enabled = !isSaving && expertise.isNotBlank() && qualification.isNotBlank() && experienceYears.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4B5CC4))
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.height(18.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(text = "Lưu thông tin chuyên môn", color = Color.White)
                        }
                    }
                }
            }

            // Bank Account Section
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(14.dp)),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Cập nhật tài khoản nhận tiền",
                        color = Color(0xFF1E293B),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )

                    OutlinedTextField(
                        value = bankName,
                        onValueChange = { bankName = it },
                        label = { Text("Tên ngân hàng") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = bankCode,
                        onValueChange = { bankCode = it.filter(Char::isDigit) },
                        label = { Text("Mã ngân hàng (BIN)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number)
                    )

                    OutlinedTextField(
                        value = bankAccountNumber,
                        onValueChange = { bankAccountNumber = it.filter(Char::isDigit) },
                        label = { Text("Số tài khoản") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number)
                    )

                    OutlinedTextField(
                        value = bankAccountHolder,
                        onValueChange = { bankAccountHolder = it },
                        label = { Text("Tên chủ tài khoản") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Button(
                        onClick = {
                            onSaveBankInfo(
                                instructorId,
                                bankName,
                                bankCode,
                                bankAccountNumber,
                                bankAccountHolder
                            )
                        },
                        enabled = !isSaving && bankName.isNotBlank() && bankCode.isNotBlank() && bankAccountNumber.isNotBlank() && bankAccountHolder.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4B5CC4))
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.height(18.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(text = "Lưu thông tin ngân hàng", color = Color.White)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
private fun InfoCard(
    title: String,
    value: String
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(14.dp)),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White)
                .padding(14.dp)
        ) {
            Text(
                text = title,
                color = Color(0xFF64748B),
                fontSize = 13.sp
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = value,
                color = Color(0xFF1E293B),
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

