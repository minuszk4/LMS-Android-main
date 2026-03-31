package com.example.lms.ui.screen.instructor.curriculum

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.lms.data.model.CurriculumItem
import com.example.lms.ui.component.TopBar
import com.example.lms.util.CurriculumEvent
import com.example.lms.util.CurriculumUiState
import com.example.lms.viewmodel.CourseViewModel
import com.example.lms.viewmodel.CurriculumViewModel
import kotlinx.coroutines.flow.collectLatest
import org.burnoutcrew.reorderable.*

private data class ItemUiProps(
    val icon: ImageVector,
    val label: String,
    val subtitle: String
)

private fun CurriculumItem.toUiProps(): ItemUiProps = when (this) {
    is CurriculumItem.LessonItem -> ItemUiProps(
        icon = Icons.Default.PlayArrow,
        label = lesson.title,
        subtitle = lesson.duration.ifBlank { "Video" }
    )
    is CurriculumItem.QuizItem -> ItemUiProps(
        icon = Icons.Default.Quiz,
        label = quiz.title,
        subtitle = "${quiz.questions.size} câu hỏi · ${quiz.durationMinutes} phút"
    )
}

@Composable
fun CurriculumScreen(
    courseViewModel: CourseViewModel,
    viewModel: CurriculumViewModel,
    onBackClick: () -> Unit,
    onAddLesson: (String) -> Unit,
    onAddQuiz: (String) -> Unit,
    onEditLesson: (CurriculumItem.LessonItem, String) -> Unit,
    onEditQuiz: (CurriculumItem.QuizItem, String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val courseUiState by courseViewModel.uiState.collectAsStateWithLifecycle()
    val currentCourse = courseUiState.currentCourse
    val courseId = currentCourse?.id ?: ""
    val courseTitle = currentCourse?.title ?: "Nội dung khóa học"

    val snackbarHostState = remember { SnackbarHostState() }
    var showAddMenu by remember { mutableStateOf(false) }
    var itemToDelete by remember { mutableStateOf<CurriculumItem?>(null) }

    val primaryIndigo = Color(0xFF4B5CC4)

    LaunchedEffect(courseId) {
        if (courseId.isNotEmpty()) {
            viewModel.setCourseId(courseId)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is CurriculumEvent.ShowSnackbar ->
                    snackbarHostState.showSnackbar(event.message)
            }
        }
    }

    Scaffold(
        topBar = {
            TopBar(title = courseTitle, onBackClick = onBackClick)
        },
        floatingActionButton = {
            AddContentFab(
                expanded = showAddMenu,
                onToggle = { showAddMenu = !showAddMenu },
                onAddLesson = {
                    showAddMenu = false
                    onAddLesson(courseId)
                },
                onAddQuiz = {
                    showAddMenu = false
                    onAddQuiz(courseId)
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = Color(0xFFF8FAFC)
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (val state = uiState) {
                is CurriculumUiState.Idle,
                is CurriculumUiState.Loading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = primaryIndigo)
                    }
                }
                is CurriculumUiState.Error -> {
                    ErrorContent(
                        message = state.message,
                        onRetry = { viewModel.loadCurriculum() }
                    )
                }
                is CurriculumUiState.Success -> {
                    if (state.items.isEmpty()) {
                        EmptyContent()
                    } else {
                        CurriculumList(
                            items = state.items,
                            onReorder = { viewModel.updateOrder(it) },
                            onEdit = { item ->
                                when (item) {
                                    is CurriculumItem.LessonItem -> onEditLesson(item, courseId)
                                    is CurriculumItem.QuizItem -> onEditQuiz(item, courseId)
                                }
                            },
                            onDeleteRequest = { itemToDelete = it }
                        )
                    }
                }
            }

            if (showAddMenu) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.1f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { showAddMenu = false }
                )
            }
        }
    }

    itemToDelete?.let { item ->
        DeleteConfirmDialog(
            item = item,
            onConfirm = {
                viewModel.deleteContent(item)
                itemToDelete = null
            },
            onDismiss = { itemToDelete = null }
        )
    }
}

@Composable
private fun CurriculumList(
    items: List<CurriculumItem>,
    onReorder: (List<CurriculumItem>) -> Unit,
    onEdit: (CurriculumItem) -> Unit,
    onDeleteRequest: (CurriculumItem) -> Unit
) {
    var localItems by remember { mutableStateOf(items) }

    val reorderableState = rememberReorderableLazyListState(
        onMove = { from, to ->
            val fromIndex = (from.index - 1).coerceIn(localItems.indices)
            val toIndex = (to.index - 1).coerceIn(localItems.indices)

            if (fromIndex != toIndex) {
                localItems = localItems.toMutableList().apply {
                    add(toIndex, removeAt(fromIndex))
                }
            }
        },
        onDragEnd = { _, _ -> onReorder(localItems) }
    )

    val isDraggingAny by remember { derivedStateOf { reorderableState.draggingItemKey != null } }
    LaunchedEffect(items) {
        if (!isDraggingAny) {
            localItems = items
        }
    }

    LazyColumn(
        state = reorderableState.listState,
        modifier = Modifier
            .fillMaxSize()
            .reorderable(reorderableState),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = "Nội dung khóa học (${localItems.size})",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1E293B),
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }
        itemsIndexed(localItems, key = { _, item -> item.id }) { index, item ->
            ReorderableItem(reorderableState, key = item.id) { isDragging ->
                CurriculumItemCard(
                    item = item,
                    index = index,
                    isDragging = isDragging,
                    reorderableState = reorderableState,
                    onEdit = { onEdit(item) },
                    onDeleteRequest = { onDeleteRequest(item) }
                )
            }
        }
        item { Spacer(modifier = Modifier.height(80.dp)) }
    }
}

@Composable
private fun CurriculumItemCard(
    item: CurriculumItem,
    index: Int,
    isDragging: Boolean,
    reorderableState: ReorderableState<*>,
    onEdit: () -> Unit,
    onDeleteRequest: () -> Unit
) {
    val props = item.toUiProps()
    val elevation by animateDpAsState(if (isDragging) 8.dp else 2.dp, label = "elevation")
    val bgColor by animateColorAsState(
        if (isDragging) Color.White.copy(alpha = 0.9f) else Color.White,
        label = "bg"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = bgColor),
        elevation = CardDefaults.cardElevation(defaultElevation = elevation)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.DragHandle,
                contentDescription = "Drag to reorder",
                tint = Color(0xFF94A3B8),
                modifier = Modifier
                    .size(48.dp)
                    .padding(14.dp)
                    .detectReorder(reorderableState)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(Color(0xFF4B5CC4).copy(alpha = 0.1f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = props.icon,
                    contentDescription = null,
                    tint = Color(0xFF4B5CC4),
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${index + 1}. ${props.label}",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF1E293B),
                    maxLines = 1,
                    modifier = Modifier.basicMarquee()
                )
                Text(
                    text = props.subtitle,
                    fontSize = 12.sp,
                    color = Color(0xFF64748B)
                )
            }
            Row {
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, null, tint = Color(0xFF64748B), modifier = Modifier.size(20.dp))
                }
                IconButton(onClick = onDeleteRequest) {
                    Icon(Icons.Default.Delete, null, tint = Color(0xFFEF4444), modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

@Composable
private fun AddContentFab(
    expanded: Boolean,
    onToggle: () -> Unit,
    onAddLesson: () -> Unit,
    onAddQuiz: () -> Unit
) {
    Column(horizontalAlignment = Alignment.End) {
        if (expanded) {
            Card(
                shape = RoundedCornerShape(12.dp),
                elevation = CardDefaults.cardElevation(4.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(modifier = Modifier.width(180.dp)) {
                    DropdownMenuItem(
                        text = { Text("Thêm bài học", fontSize = 14.sp) },
                        onClick = onAddLesson,
                        leadingIcon = { Icon(Icons.Default.PlayCircle, null, tint = Color(0xFF4B5CC4)) }
                    )
                    HorizontalDivider(color = Color(0xFFF8FAFC))
                    DropdownMenuItem(
                        text = { Text("Thêm bài kiểm tra", fontSize = 14.sp) },
                        onClick = onAddQuiz,
                        leadingIcon = { Icon(Icons.Default.Quiz, null, tint = Color(0xFF4B5CC4)) }
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
        ExtendedFloatingActionButton(
            onClick = onToggle,
            containerColor = Color(0xFF4B5CC4),
            contentColor = Color.White,
            shape = RoundedCornerShape(16.dp),
            icon = { Icon(if (expanded) Icons.Default.Close else Icons.Default.Add, null) },
            text = { Text(if (expanded) "Đóng" else "Thêm nội dung", fontWeight = FontWeight.Bold) }
        )
    }
}

@Composable
private fun EmptyContent() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.AutoMirrored.Filled.LibraryBooks, null, modifier = Modifier.size(64.dp), tint = Color.LightGray)
        Spacer(modifier = Modifier.height(16.dp))
        Text("Chưa có nội dung nào", color = Color(0xFF64748B))
    }
}

@Composable
private fun ErrorContent(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(message, color = Color(0xFFEF4444))
        TextButton(onClick = onRetry) { Text("Thử lại") }
    }
}

@Composable
private fun DeleteConfirmDialog(
    item: CurriculumItem,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val title = when (item) {
        is CurriculumItem.LessonItem -> item.lesson.title
        is CurriculumItem.QuizItem -> item.quiz.title
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Xác nhận xóa", fontWeight = FontWeight.Bold, color = Color(0xFF1E293B)) },
        text = { Text("Bạn có chắc muốn xóa \"$title\"? Hành động này không thể hoàn tác.") },
        confirmButton = {
            Button(onClick = onConfirm, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))) {
                Text("Xóa", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Hủy", color = Color(0xFF64748B)) }
        },
        containerColor = Color.White,
        shape = RoundedCornerShape(16.dp)
    )
}
