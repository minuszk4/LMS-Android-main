package com.example.lms.ui.screen.instructor.course

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.MonetizationOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.lms.data.model.Course
import com.example.lms.data.model.CourseLevel
import com.example.lms.ui.component.FormFieldWithValidation
import com.example.lms.ui.component.SectionCard
import com.example.lms.ui.component.TopBar
import com.example.lms.util.CourseEvent
import com.example.lms.viewmodel.AuthViewModel
import com.example.lms.viewmodel.CourseViewModel

@Composable
fun CourseFormScreen(
    instructorId: String,
    authViewModel: AuthViewModel,
    viewModel: CourseViewModel,
    onBackClick: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val authUiState by authViewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val isEditMode = uiState.currentCourse != null

    var title by remember(uiState.currentCourse) { mutableStateOf(uiState.currentCourse?.title ?: "") }
    var description by remember(uiState.currentCourse) { mutableStateOf(uiState.currentCourse?.description ?: "") }
    var price by remember(uiState.currentCourse) { mutableStateOf(uiState.currentCourse?.price?.toInt()?.toString() ?: "") }
    var categoryId by remember(uiState.currentCourse) { mutableStateOf(uiState.currentCourse?.categoryId ?: "") }
    var level by remember(uiState.currentCourse) { mutableStateOf(uiState.currentCourse?.level ?: CourseLevel.BEGINNER) }
    var duration by remember(uiState.currentCourse) { mutableStateOf(uiState.currentCourse?.duration ?: "") }
    var isPublished by remember(uiState.currentCourse) { mutableStateOf(uiState.currentCourse?.isPublished ?: false) }
    var thumbnailUrl by remember(uiState.currentCourse) { mutableStateOf(uiState.currentCourse?.thumbnailUrl ?: "") }
    var isFree by remember(uiState.currentCourse) { mutableStateOf(uiState.currentCourse?.price == 0.0 && uiState.currentCourse != null) }

    var showLevelPicker by remember { mutableStateOf(false) }
    var showCategoryPicker by remember { mutableStateOf(false) }

    val primaryIndigo = Color(0xFF4B5CC4)

    LaunchedEffect(Unit) {
        viewModel.event.collect { event ->
            when (event) {
                is CourseEvent.ShowError -> Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
                CourseEvent.SaveSuccess -> {
                    Toast.makeText(context, "Lưu khóa học thành công", Toast.LENGTH_SHORT).show()
                    onBackClick()
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopBar(
                title = if (isEditMode) "Chỉnh sửa khóa học" else "Tạo khóa học mới",
                onBackClick = onBackClick
            )
        },
        containerColor = Color(0xFFF8FAFC)
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            SectionCard(title = "Ảnh đại diện", icon = Icons.Default.Image) {
                ThumbnailUploadSection(
                    thumbnailUrl = thumbnailUrl,
                    onThumbnailSelected = { 
                        thumbnailUrl = it 
                        viewModel.onThumbnailSelected()
                    },
                    error = uiState.thumbnailUrlError
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            SectionCard(title = "Thông tin cơ bản", icon = Icons.Outlined.Description) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    FormFieldWithValidation(
                        label = "Tên khóa học",
                        value = title,
                        onValueChange = { title = it; viewModel.onTitleChange() },
                        placeholder = "Ví dụ: Lập trình Kotlin chuyên sâu",
                        error = uiState.titleError
                    )
                    FormFieldWithValidation(
                        label = "Mô tả",
                        value = description,
                        onValueChange = { description = it; viewModel.onDescriptionChange() },
                        placeholder = "Mô tả ngắn gọn về khóa học...",
                        minLines = 3,
                        error = uiState.descriptionError
                    )
                    
                    val selectedCategory = uiState.categories.find { it.id == categoryId }
                    SelectableFieldWithValidation(
                        label = "Danh mục", 
                        value = selectedCategory?.name ?: categoryId.ifEmpty { "Chọn danh mục" }, 
                        onClick = { showCategoryPicker = true },
                        error = uiState.categoryError
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        SelectableField(
                            label = "Trình độ", 
                            value = when(level) {
                                CourseLevel.BEGINNER -> "Cơ bản"
                                CourseLevel.INTERMEDIATE -> "Trung cấp"
                                CourseLevel.ADVANCED -> "Nâng cao"
                            }, 
                            onClick = { showLevelPicker = true }, 
                            modifier = Modifier.weight(1f)
                        )
                        FormFieldWithValidation(
                            label = "Thời lượng",
                            value = duration,
                            onValueChange = { duration = it; viewModel.onDurationChange() },
                            placeholder = "Ví dụ: 15h 45m",
                            modifier = Modifier.weight(1f),
                            error = uiState.durationError
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            SectionCard(title = "Học phí & Trạng thái", icon = Icons.Outlined.MonetizationOn) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Khóa học miễn phí", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF1E293B))
                            Text("Học viên không cần thanh toán", fontSize = 12.sp, color = Color(0xFF64748B))
                        }
                        Switch(
                            checked = isFree, 
                            onCheckedChange = { 
                                isFree = it 
                                if(it) price = "0"
                                viewModel.onPriceChange()
                            },
                            thumbContent = {
                                Box(modifier = Modifier.size(SwitchDefaults.IconSize))
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = primaryIndigo,
                                uncheckedThumbColor = Color.White,
                                uncheckedTrackColor = Color(0xFFE2E8F0),
                                uncheckedBorderColor = Color.Transparent
                            )
                        )
                    }
                    
                    FormFieldWithValidation(
                        label = "Giá bán (VNĐ)", 
                        value = if(isFree) "0" else price, 
                        onValueChange = { 
                            if(!isFree) {
                                price = it 
                                viewModel.onPriceChange()
                            }
                        }, 
                        placeholder = "Nhập giá...", 
                        enabled = !isFree, 
                        keyboardType = KeyboardType.Number,
                        error = uiState.priceError
                    )
                    
                    HorizontalDivider(Modifier.padding(vertical = 12.dp), color = Color(0xFFE2E8F0))
                    
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Công khai khóa học", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF1E293B))
                            Text("Cho phép học viên tìm thấy khóa học này", fontSize = 12.sp, color = Color(0xFF64748B))
                        }
                        Switch(
                            checked = isPublished, 
                            onCheckedChange = { isPublished = it },
                            thumbContent = {
                                Box(modifier = Modifier.size(SwitchDefaults.IconSize))
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = primaryIndigo,
                                uncheckedThumbColor = Color.White,
                                uncheckedTrackColor = Color(0xFFE2E8F0),
                                uncheckedBorderColor = Color.Transparent
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = {
                    val instructorName = authUiState.currentUser?.fullName ?: "Instructor"
                    val course = (uiState.currentCourse ?: Course()).copy(
                        title = title, description = description, 
                        price = if(isFree) 0.0 else (price.toDoubleOrNull() ?: 0.0),
                        duration = duration, level = level, categoryId = categoryId, 
                        isPublished = isPublished, thumbnailUrl = thumbnailUrl, 
                        instructorId = instructorId, instructorName = instructorName
                    )
                    if (isEditMode) viewModel.updateCourse(course, isFree, price) else viewModel.createCourse(course, isFree, price)
                },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(56.dp),
                shape = RoundedCornerShape(12.dp),
                enabled = !uiState.isLoading,
                colors = ButtonDefaults.buttonColors(containerColor = primaryIndigo)
            ) {
                if (uiState.isLoading) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                else Text(if (isEditMode) "Cập nhật khóa học" else "Tạo khóa học", fontWeight = FontWeight.Bold, color = Color.White)
            }
            Spacer(modifier = Modifier.height(40.dp))
        }
    }

    if (showLevelPicker) {
        AlertDialog(
            onDismissRequest = { showLevelPicker = false },
            title = { Text("Chọn trình độ", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    CourseLevel.entries.forEach { l ->
                        TextButton(onClick = { level = l; showLevelPicker = false }, modifier = Modifier.fillMaxWidth()) {
                            Text(when(l) {
                                CourseLevel.BEGINNER -> "Cơ bản"
                                CourseLevel.INTERMEDIATE -> "Trung cấp"
                                CourseLevel.ADVANCED -> "Nâng cao"
                            }, color = Color(0xFF1E293B), modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            },
            confirmButton = {}, containerColor = Color.White, shape = RoundedCornerShape(16.dp)
        )
    }

    if (showCategoryPicker) {
        AlertDialog(
            onDismissRequest = { showCategoryPicker = false },
            title = { Text("Chọn danh mục", fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    if (uiState.categories.isEmpty()) {
                        Text("Đang tải danh mục...", modifier = Modifier.padding(16.dp), color = Color.Gray)
                    }
                    uiState.categories.forEach { cat ->
                        TextButton(
                            onClick = {
                                categoryId = cat.id
                                viewModel.onCategorySelected()
                                showCategoryPicker = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(cat.name, color = Color(0xFF1E293B), modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            },
            confirmButton = {}, containerColor = Color.White, shape = RoundedCornerShape(16.dp)
        )
    }
}

@Composable
fun ThumbnailUploadSection(
    thumbnailUrl: String, 
    onThumbnailSelected: (String) -> Unit,
    error: String? = null
) {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> uri?.let { onThumbnailSelected(it.toString()) } }
    Column {
        Box(
            Modifier
                .fillMaxWidth()
                .height(180.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFFF0F0F4))
                .border(
                    width = 1.dp,
                    color = if (error != null) Color(0xFFEF4444).copy(alpha = 0.5f) else Color.Transparent,
                    shape = RoundedCornerShape(12.dp)
                )
                .clickable { launcher.launch("image/*") }, 
            Alignment.Center
        ) {
            if (thumbnailUrl.isEmpty()) Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.CloudUpload, null, tint = Color(0xFF4B5CC4), modifier = Modifier.size(40.dp))
                Text("Nhấn để tải lên ảnh đại diện", fontSize = 14.sp, color = Color(0xFF64748B))
            } else AsyncImage(thumbnailUrl, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
        AnimatedVisibility(
            visible = error != null,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            error?.let {
                Text(
                    text = it,
                    color = Color(0xFFEF4444),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 4.dp, start = 4.dp)
                )
            }
        }
    }
}

@Composable
fun SelectableField(
    label: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Column(modifier) {
        Text(
            text = label,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = Color(0xFF64748B)
        )
        Spacer(Modifier.height(8.dp))

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .clip(RoundedCornerShape(12.dp)),
            shape = RoundedCornerShape(12.dp),
            onClick = onClick,
            enabled = enabled,
            color = Color(0xFFFAFAFC),
            border = BorderStroke(
                width = 1.dp,
                color = Color(0xFFE2E8F0)
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = value,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Normal,
                    lineHeight = 22.sp,
                    color = if (value.contains("Chọn"))
                        Color(0xFFA0AEC0)
                    else
                        Color(0xFF2D3748)
                )

                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = Color(0xFF94A3B8),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
fun SelectableFieldWithValidation(
    label: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    error: String? = null
) {
    Column(modifier) {
        SelectableField(
            label = label,
            value = value,
            onClick = onClick,
            enabled = enabled
        )
        AnimatedVisibility(
            visible = error != null,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            error?.let {
                Text(
                    text = it,
                    color = Color(0xFFEF4444),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 4.dp, start = 4.dp)
                )
            }
        }
    }
}
