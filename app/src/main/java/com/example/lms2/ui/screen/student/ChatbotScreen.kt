package com.example.lms2.ui.screen.student

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.lms2.data.model.ChatMessage
import com.example.lms2.data.model.ChatMessageType
import com.example.lms2.data.model.ChatSender
import com.example.lms2.data.model.ChatSession
import com.example.lms2.ui.component.TopBar
import com.example.lms2.util.ChatbotEvent
import com.example.lms2.viewmodel.ChatbotViewModel

private val ChatBg = Color(0xFFF8FAFC)
private val ChatUserBubble = Color(0xFF4B5CC4)
private val ChatBotBubble = Color.White
private val ChatBorder = Color(0xFFE2E8F0)
private val ChatTextPrimary = Color(0xFF1E293B)
private val ChatTextSecondary = Color(0xFF64748B)
private val ChatInputBg = Color(0xFFF1F3F8)

@Composable
fun ChatbotRoute(
    userId: String,
    userAvatarUrl: String?,
    viewModel: ChatbotViewModel,
    onNavigateToCourseDetail: (String) -> Unit,
    onNavigateToCart: () -> Unit,
    onNavigateToInstructor: (String) -> Unit,
    onNavigateToMyLearning: () -> Unit,
    onNavigateToPayment: (String) -> Unit,
    onBackClick: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var input by remember { mutableStateOf("") }

    LaunchedEffect(userId) {
        viewModel.init(userId)
    }

    LaunchedEffect(Unit) {
        viewModel.event.collect { event ->
            if (event is ChatbotEvent.ShowError) {
                snackbarHostState.showSnackbar(event.message)
            }
        }
    }

    ChatbotScreen(
        isLoading = uiState.isLoading,
        isSending = uiState.isSending,
        sessions = uiState.sessions,
        canCreateNewSession = uiState.canCreateNewSession,
        selectedSessionId = uiState.sessionId,
        messages = uiState.messages,
        userAvatarUrl = userAvatarUrl,
        input = input,
        snackbarHostState = snackbarHostState,
        onInputChange = { input = it },
        onSelectSession = viewModel::selectSession,
        onDeleteSession = viewModel::deleteSession,
        onCreateSession = { viewModel.createNewSession(userId) },
        onQuickAction = { action ->
            viewModel.sendMessage(action)
        },
        onNavigateToCourseDetail = onNavigateToCourseDetail,
        onNavigateToCart = onNavigateToCart,
        onNavigateToInstructor = onNavigateToInstructor,
        onNavigateToMyLearning = onNavigateToMyLearning,
        onNavigateToPayment = onNavigateToPayment,
        onSendClick = {
            if (input.isNotBlank()) {
                viewModel.sendMessage(input)
                input = ""
            }
        },
        onBackClick = onBackClick
    )
}

@Composable
private fun ChatbotScreen(
    isLoading: Boolean,
    isSending: Boolean,
    sessions: List<ChatSession>,
    canCreateNewSession: Boolean,
    selectedSessionId: String,
    messages: List<ChatMessage>,
    userAvatarUrl: String?,
    input: String,
    snackbarHostState: SnackbarHostState,
    onInputChange: (String) -> Unit,
    onSelectSession: (String) -> Unit,
    onDeleteSession: (String) -> Unit,
    onCreateSession: () -> Unit,
    onQuickAction: (String) -> Unit,
    onNavigateToCourseDetail: (String) -> Unit,
    onNavigateToCart: () -> Unit,
    onNavigateToInstructor: (String) -> Unit,
    onNavigateToMyLearning: () -> Unit,
    onNavigateToPayment: (String) -> Unit,
    onSendClick: () -> Unit,
    onBackClick: () -> Unit
) {
    val listState = rememberLazyListState()
    var activeQuickAction by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(isSending) {
        if (!isSending) {
            activeQuickAction = null
        }
    }

    val handleQuickAction: (String, String) -> Unit = { actionKey, actionText ->
        if (!isSending && !isLoading) {
            activeQuickAction = actionKey
            onQuickAction(actionText)
        }
    }

    LaunchedEffect(selectedSessionId, messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    Scaffold(
        topBar = {
            TopBar(
                title = "Trợ lý học tập",
                onBackClick = onBackClick
            )
        },
        containerColor = ChatBg,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ChatBg)
                    .padding(horizontal = 12.dp, vertical = 10.dp)
                    .navigationBarsPadding()
                    .imePadding(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextField(
                    modifier = Modifier
                        .weight(1f),
                    value = input,
                    onValueChange = onInputChange,
                    shape = RoundedCornerShape(14.dp),
                    placeholder = { Text("Hỏi trợ lý...", color = ChatTextSecondary) },
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = ChatInputBg,
                        unfocusedContainerColor = ChatInputBg,
                        disabledContainerColor = ChatInputBg,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent
                    )
                )

                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(ChatUserBubble)
                        .clickable(enabled = input.isNotBlank() && !isLoading && !isSending) {
                            onSendClick()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (isSending) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = Color.White
                        )
                    } else {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Gửi",
                            tint = Color.White
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            SessionSelectorRow(
                sessions = sessions,
                canCreateNewSession = canCreateNewSession,
                selectedSessionId = selectedSessionId,
                onSelectSession = onSelectSession,
                onDeleteSession = onDeleteSession,
                onCreateSession = onCreateSession
            )

            Spacer(modifier = Modifier.height(4.dp))

            if (isLoading && messages.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = ChatUserBubble)
                }
            } else if (messages.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Bắt đầu cuộc trò chuyện để nhận hỗ trợ học tập.",
                        color = ChatTextSecondary,
                        fontSize = 14.sp
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    state = listState,
                    contentPadding = PaddingValues(bottom = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(messages, key = { it.id }) { message ->
                        MessageBubble(
                            message = message,
                            userAvatarUrl = userAvatarUrl,
                            isSending = isSending,
                            activeQuickAction = activeQuickAction,
                            onQuickAction = handleQuickAction,
                            onNavigateToCourseDetail = onNavigateToCourseDetail,
                            onNavigateToCart = onNavigateToCart,
                            onNavigateToInstructor = onNavigateToInstructor,
                            onNavigateToMyLearning = onNavigateToMyLearning,
                            onNavigateToPayment = onNavigateToPayment
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SessionSelectorRow(
    sessions: List<ChatSession>,
    canCreateNewSession: Boolean,
    selectedSessionId: String,
    onSelectSession: (String) -> Unit,
    onDeleteSession: (String) -> Unit,
    onCreateSession: () -> Unit
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White, RoundedCornerShape(10.dp))
            .padding(horizontal = 6.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items(sessions, key = { it.id }) { session ->
            val selected = session.id == selectedSessionId
            Surface(
                shape = RoundedCornerShape(9.dp),
                color = if (selected) Color(0xFFE9EDFF) else Color.White,
                onClick = { onSelectSession(session.id) },
                border = androidx.compose.foundation.BorderStroke(
                    width = 1.dp,
                    color = if (selected) ChatUserBubble.copy(alpha = 0.4f) else ChatBorder
                ),
                modifier = Modifier.widthIn(min = 62.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier.padding(start = 6.dp, top = 4.dp, bottom = 4.dp, end = 4.dp)
                ) {
                    Text(
                        text = session.title.ifBlank { "Phiên trò chuyện" },
                        color = if (selected) ChatUserBubble else ChatTextPrimary,
                        fontSize = 10.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                        maxLines = 1
                    )

                    Surface(
                        shape = CircleShape,
                        color = if (selected) ChatUserBubble.copy(alpha = 0.15f) else Color(0xFFF1F5F9),
                        onClick = { onDeleteSession(session.id) },
                        modifier = Modifier.size(14.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Xóa phiên",
                                tint = if (selected) ChatUserBubble else ChatTextSecondary,
                                modifier = Modifier.size(9.dp)
                            )
                        }
                    }
                }
            }
        }

        item {
            Surface(
                shape = RoundedCornerShape(9.dp),
                color = if (canCreateNewSession) Color(0xFFE9EDFF) else Color(0xFFF1F3F8),
                onClick = onCreateSession,
                enabled = canCreateNewSession,
                border = androidx.compose.foundation.BorderStroke(
                    width = 1.dp,
                    color = if (canCreateNewSession) ChatUserBubble.copy(alpha = 0.35f) else ChatBorder
                )
            ) {
                Box(
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Tạo phiên mới",
                        tint = if (canCreateNewSession) ChatUserBubble else ChatTextSecondary,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(
    message: ChatMessage,
    userAvatarUrl: String?,
    isSending: Boolean,
    activeQuickAction: String?,
    onQuickAction: (String, String) -> Unit,
    onNavigateToCourseDetail: (String) -> Unit,
    onNavigateToCart: () -> Unit,
    onNavigateToInstructor: (String) -> Unit,
    onNavigateToMyLearning: () -> Unit,
    onNavigateToPayment: (String) -> Unit
) {
    val isUser = message.sender == ChatSender.USER

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Top
    ) {
        if (!isUser) {
            Surface(
                modifier = Modifier.size(30.dp),
                shape = CircleShape,
                color = Color(0xFFEDEBFF)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.SmartToy,
                        contentDescription = null,
                        tint = ChatUserBubble,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.size(8.dp))
        }

        // Render based on message type
        when (message.messageType) {
            ChatMessageType.COURSE_CARD -> {
                CourseCardMessage(
                    message = message,
                    isUser = isUser,
                    isSending = isSending,
                    activeQuickAction = activeQuickAction,
                    onQuickAction = onQuickAction,
                    onNavigateToCourseDetail = onNavigateToCourseDetail,
                    onNavigateToCart = onNavigateToCart,
                    onNavigateToInstructor = onNavigateToInstructor,
                    onNavigateToMyLearning = onNavigateToMyLearning,
                    onNavigateToPayment = onNavigateToPayment
                )
            }
            ChatMessageType.COURSE_LIST -> {
                CourseListMessage(
                    message = message,
                    isUser = isUser,
                    isSending = isSending,
                    activeQuickAction = activeQuickAction,
                    onQuickAction = onQuickAction,
                    onNavigateToCourseDetail = onNavigateToCourseDetail,
                    onNavigateToCart = onNavigateToCart,
                    onNavigateToInstructor = onNavigateToInstructor,
                    onNavigateToMyLearning = onNavigateToMyLearning,
                    onNavigateToPayment = onNavigateToPayment
                )
            }
            ChatMessageType.PROGRESS_CHART -> {
                ProgressChartMessage(
                    message = message,
                    isUser = isUser
                )
            }
            ChatMessageType.FUNCTION_CALL,
            ChatMessageType.TEXT -> {
                // Default TEXT message
                val cleanedText = message.content
                    .replace("**", "")
                    .replace("__", "")
                    .replace("`", "")
                    .replace(Regex("^\\s*[-*+]\\s+", RegexOption.MULTILINE), "")
                    .replace(Regex("^\\s{0,3}#{1,6}\\s+", RegexOption.MULTILINE), "")
                    .trim()

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        color = if (isUser) ChatUserBubble else ChatBotBubble,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .widthIn(max = 280.dp)
                            .border(
                                width = if (isUser) 0.dp else 1.dp,
                                color = if (isUser) Color.Transparent else ChatBorder,
                                shape = RoundedCornerShape(16.dp)
                            )
                    ) {
                        Text(
                            text = cleanedText,
                            color = if (isUser) Color.White else ChatTextPrimary,
                            fontSize = 16.sp,
                            lineHeight = 24.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp)
                        )
                    }

                    if (!isUser && shouldShowHelpQuickActions(cleanedText)) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            ActionChipRow {
                                ActionChip(
                                    label = "Tìm khóa",
                                    backgroundColor = Color(0xFFE9EDFF),
                                    textColor = ChatUserBubble,
                                    fontSize = 11,
                                    isActive = activeQuickAction == "help_search_${message.id}",
                                    onClick = { onQuickAction("help_search_${message.id}", "Tìm khóa học Kotlin") },
                                    enabled = !isSending
                                )
                                ActionChip(
                                    label = "Chi tiết",
                                    backgroundColor = Color(0xFFF0FDF4),
                                    textColor = Color(0xFF166534),
                                    fontSize = 11,
                                    isActive = activeQuickAction == "help_detail_${message.id}",
                                    onClick = { onQuickAction("help_detail_${message.id}", "Xem chi tiết khóa học Android") },
                                    enabled = !isSending
                                )
                                ActionChip(
                                    label = "Tiến độ",
                                    backgroundColor = Color(0xFFFFF7ED),
                                    textColor = Color(0xFF9A3412),
                                    fontSize = 11,
                                    isActive = activeQuickAction == "help_progress_${message.id}",
                                    onClick = { onQuickAction("help_progress_${message.id}", "Tôi đã học đến đâu rồi?") },
                                    enabled = !isSending
                                )
                            }

                            ActionChipRow {
                                ActionChip(
                                    label = "Thêm giỏ",
                                    backgroundColor = Color(0xFFE9EDFF),
                                    textColor = ChatUserBubble,
                                    fontSize = 11,
                                    isActive = activeQuickAction == "help_add_cart_${message.id}",
                                    onClick = { onQuickAction("help_add_cart_${message.id}", "Thêm khóa học Kotlin vào giỏ hàng") },
                                    enabled = !isSending
                                )
                                ActionChip(
                                    label = "Gợi ý 5",
                                    backgroundColor = Color(0xFFEEF2FF),
                                    textColor = Color(0xFF3730A3),
                                    fontSize = 11,
                                    isActive = activeQuickAction == "help_recommend_${message.id}",
                                    onClick = { onQuickAction("help_recommend_${message.id}", "Gợi ý cho tôi 5 khóa học phù hợp") },
                                    enabled = !isSending
                                )
                                ActionChip(
                                    label = "Mở giỏ",
                                    backgroundColor = Color(0xFFFFF1F2),
                                    textColor = Color(0xFF9F1239),
                                    fontSize = 11,
                                    onClick = onNavigateToCart,
                                    enabled = !isSending
                                )
                            }
                        }
                    }
                }
            }
        }

        if (isUser) {
            Spacer(modifier = Modifier.size(8.dp))
            Surface(
                modifier = Modifier.size(30.dp),
                shape = CircleShape,
                color = Color.White,
                border = androidx.compose.foundation.BorderStroke(1.dp, ChatBorder)
            ) {
                if (!userAvatarUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = userAvatarUrl,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = ChatTextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

private fun shouldShowHelpQuickActions(content: String): Boolean {
    val text = content.lowercase()
    return text.contains("bạn có thể yêu cầu") ||
        text.contains("hướng dẫn") ||
        text.contains("trợ giúp") ||
        text.contains("help")
}

@Composable
private fun ActionChipRow(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit
) {
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        content = content
    )
}

@Composable
private fun ActionChip(
    label: String,
    backgroundColor: Color,
    textColor: Color,
    fontSize: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isActive: Boolean = false,
    enabled: Boolean = true
) {
    val resolvedBackground = when {
        !enabled -> backgroundColor.copy(alpha = 0.45f)
        isActive -> backgroundColor.copy(alpha = 0.78f)
        else -> backgroundColor
    }
    val resolvedText = if (enabled) textColor else textColor.copy(alpha = 0.6f)

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = resolvedBackground,
        border = if (isActive) androidx.compose.foundation.BorderStroke(1.dp, resolvedText.copy(alpha = 0.45f)) else null,
        modifier = modifier.clickable(enabled = enabled) { onClick() }
    ) {
        Text(
            text = label,
            color = resolvedText,
            fontSize = fontSize.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(
                horizontal = if (fontSize >= 12) 10.dp else 8.dp,
                vertical = if (fontSize >= 12) 6.dp else 5.dp
            )
        )
    }
}

@Composable
private fun CourseCardMessage(
    message: ChatMessage,
    isUser: Boolean,
    isSending: Boolean,
    activeQuickAction: String?,
    onQuickAction: (String, String) -> Unit,
    onNavigateToCourseDetail: (String) -> Unit,
    onNavigateToCart: () -> Unit,
    onNavigateToInstructor: (String) -> Unit,
    onNavigateToMyLearning: () -> Unit,
    onNavigateToPayment: (String) -> Unit
) {
    val courseId = message.metadata["id"] as? String ?: ""
    val instructorId = message.metadata["instructorId"] as? String ?: ""
    val title = message.metadata["title"] as? String ?: "Course"
    val price = message.metadata["price"] as? String ?: "0"
    val rating = (message.metadata["rating"] as? Number)?.toDouble() ?: 0.0
    val instructor = message.metadata["instructor"] as? String ?: "Unknown"
    val enrollmentCount = (message.metadata["enrollmentCount"] as? Number)?.toInt() ?: 0
    val description = message.metadata["description"] as? String ?: message.content

    Surface(
        color = if (isUser) ChatUserBubble else ChatBotBubble,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .widthIn(max = 300.dp)
            .border(
                width = if (isUser) 0.dp else 1.dp,
                color = if (isUser) Color.Transparent else ChatBorder,
                shape = RoundedCornerShape(16.dp)
            )
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Title
            Text(
                text = title,
                color = if (isUser) Color.White else ChatTextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2
            )

            // Description
            if (description.isNotEmpty()) {
                Text(
                    text = description.take(100),
                    color = if (isUser) Color.White else ChatTextSecondary,
                    fontSize = 14.sp,
                    maxLines = 2
                )
            }

            // Instructor
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "👨‍🏫",
                    fontSize = 14.sp
                )
                Text(
                    text = instructor,
                    color = if (isUser) Color.White else ChatTextSecondary,
                    fontSize = 13.sp,
                    maxLines = 1
                )
            }

            // Stats Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Rating
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text("⭐", fontSize = 12.sp)
                    Text(
                        text = String.format("%.1f", rating),
                        color = if (isUser) Color.White else ChatTextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                // Enrollment Count
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text("👥", fontSize = 12.sp)
                    Text(
                        text = "$enrollmentCount học viên",
                        color = if (isUser) Color.White else ChatTextSecondary,
                        fontSize = 12.sp
                    )
                }
            }

            // Price
            if (price.isNotEmpty() && price != "0") {
                Text(
                    text = "$price đ",
                    color = if (isUser) Color.White else Color(0xFF4B5CC4),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            if (!isUser && title.isNotBlank()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ActionChipRow {
                        ActionChip(
                            label = "Đi tới trang",
                            backgroundColor = Color(0xFFF0FDF4),
                            textColor = Color(0xFF166534),
                            fontSize = 12,
                            onClick = {
                                if (courseId.isNotBlank()) {
                                    onNavigateToCourseDetail(courseId)
                                } else {
                                    onQuickAction("course_card_fallback_detail_${message.id}", "Cho tôi xem chi tiết khóa học \"$title\"")
                                }
                            }
                        )

                        ActionChip(
                            label = "Xem chi tiết",
                            backgroundColor = Color(0xFFE9EDFF),
                            textColor = ChatUserBubble,
                            fontSize = 12,
                            isActive = activeQuickAction == "course_card_detail_${message.id}",
                            onClick = { onQuickAction("course_card_detail_${message.id}", "Cho tôi xem chi tiết khóa học \"$title\"") },
                            enabled = !isSending
                        )

                        ActionChip(
                            label = "Thêm giỏ",
                            backgroundColor = Color(0xFFE9EDFF),
                            textColor = ChatUserBubble,
                            fontSize = 12,
                            isActive = activeQuickAction == "course_card_add_cart_${message.id}",
                            onClick = { onQuickAction("course_card_add_cart_${message.id}", "Thêm khóa học \"$title\" vào giỏ hàng") },
                            enabled = !isSending
                        )
                    }

                    ActionChipRow {
                        ActionChip(
                            label = "Mở giỏ",
                            backgroundColor = Color(0xFFFFF7ED),
                            textColor = Color(0xFF9A3412),
                            fontSize = 12,
                            onClick = onNavigateToCart,
                            enabled = !isSending
                        )

                        ActionChip(
                            label = "Giảng viên",
                            backgroundColor = Color(0xFFEEF2FF),
                            textColor = Color(0xFF3730A3),
                            fontSize = 12,
                            onClick = { if (instructorId.isNotBlank()) onNavigateToInstructor(instructorId) },
                            enabled = !isSending && instructorId.isNotBlank()
                        )

                        ActionChip(
                            label = "Học của tôi",
                            backgroundColor = Color(0xFFF0FDF4),
                            textColor = Color(0xFF166534),
                            fontSize = 12,
                            onClick = onNavigateToMyLearning,
                            enabled = !isSending
                        )

                        ActionChip(
                            label = "Thanh toán",
                            backgroundColor = Color(0xFFFFF1F2),
                            textColor = Color(0xFF9F1239),
                            fontSize = 12,
                            onClick = { if (courseId.isNotBlank()) onNavigateToPayment(courseId) },
                            enabled = !isSending && courseId.isNotBlank()
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CourseListMessage(
    message: ChatMessage,
    isUser: Boolean,
    isSending: Boolean,
    activeQuickAction: String?,
    onQuickAction: (String, String) -> Unit,
    onNavigateToCourseDetail: (String) -> Unit,
    onNavigateToCart: () -> Unit,
    onNavigateToInstructor: (String) -> Unit,
    onNavigateToMyLearning: () -> Unit,
    onNavigateToPayment: (String) -> Unit
) {
    @Suppress("UNCHECKED_CAST")
    val courses = message.metadata["courses"] as? List<Map<String, Any>> ?: emptyList()

    Surface(
        color = if (isUser) ChatUserBubble else ChatBotBubble,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .widthIn(max = 320.dp)
            .border(
                width = if (isUser) 0.dp else 1.dp,
                color = if (isUser) Color.Transparent else ChatBorder,
                shape = RoundedCornerShape(16.dp)
            )
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header
            if (message.content.isNotEmpty()) {
                Text(
                    text = message.content,
                    color = if (isUser) Color.White else ChatTextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            // Course List
            courses.forEach { courseData ->
                val courseId = courseData["id"] as? String ?: ""
                val instructorId = courseData["instructorId"] as? String ?: ""
                val courseTitle = courseData["title"] as? String ?: "Course"
                val coursePrice = courseData["price"] as? String ?: ""
                val courseRating = (courseData["rating"] as? Number)?.toDouble() ?: 0.0
                val enrollCount = (courseData["enrollmentCount"] as? Number)?.toInt() ?: 0

                Surface(
                    color = Color.White.copy(alpha = if (isUser) 0.1f else 1f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            width = 0.5.dp,
                            color = ChatBorder,
                            shape = RoundedCornerShape(10.dp)
                        )
                        .padding(10.dp)
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = courseTitle,
                            color = if (isUser) Color.White else ChatTextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("⭐", fontSize = 11.sp)
                                Text(
                                    text = String.format("%.1f", courseRating),
                                    color = if (isUser) Color.White else ChatTextPrimary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }

                            if (enrollCount > 0) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("👥", fontSize = 11.sp)
                                    Text(
                                        text = "$enrollCount",
                                        color = if (isUser) Color.White else ChatTextSecondary,
                                        fontSize = 11.sp
                                    )
                                }
                            }

                            if (coursePrice.isNotEmpty()) {
                                Text(
                                    text = coursePrice,
                                    color = if (isUser) Color.White else Color(0xFF4B5CC4),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        if (!isUser && courseTitle.isNotBlank()) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                ActionChipRow {
                                    ActionChip(
                                        label = "Đi tới",
                                        backgroundColor = Color(0xFFF0FDF4),
                                        textColor = Color(0xFF166534),
                                        fontSize = 11,
                                        onClick = {
                                            if (courseId.isNotBlank()) {
                                                onNavigateToCourseDetail(courseId)
                                            } else {
                                                onQuickAction("course_list_fallback_detail_${message.id}_$courseTitle", "Cho tôi xem chi tiết khóa học \"$courseTitle\"")
                                            }
                                        }
                                    )

                                    ActionChip(
                                        label = "Chi tiết",
                                        backgroundColor = Color(0xFFE9EDFF),
                                        textColor = ChatUserBubble,
                                        fontSize = 11,
                                        isActive = activeQuickAction == "course_list_detail_${message.id}_$courseId",
                                        onClick = { onQuickAction("course_list_detail_${message.id}_$courseId", "Cho tôi xem chi tiết khóa học \"$courseTitle\"") },
                                        enabled = !isSending
                                    )

                                    ActionChip(
                                        label = "Thêm giỏ",
                                        backgroundColor = Color(0xFFE9EDFF),
                                        textColor = ChatUserBubble,
                                        fontSize = 11,
                                        isActive = activeQuickAction == "course_list_add_cart_${message.id}_$courseId",
                                        onClick = { onQuickAction("course_list_add_cart_${message.id}_$courseId", "Thêm khóa học \"$courseTitle\" vào giỏ hàng") },
                                        enabled = !isSending
                                    )
                                }

                                ActionChipRow {
                                    ActionChip(
                                        label = "Giỏ",
                                        backgroundColor = Color(0xFFFFF7ED),
                                        textColor = Color(0xFF9A3412),
                                        fontSize = 11,
                                        onClick = onNavigateToCart,
                                        enabled = !isSending
                                    )

                                    ActionChip(
                                        label = "GV",
                                        backgroundColor = Color(0xFFEEF2FF),
                                        textColor = Color(0xFF3730A3),
                                        fontSize = 11,
                                        onClick = { if (instructorId.isNotBlank()) onNavigateToInstructor(instructorId) },
                                        enabled = !isSending && instructorId.isNotBlank()
                                    )

                                    ActionChip(
                                        label = "Học",
                                        backgroundColor = Color(0xFFF0FDF4),
                                        textColor = Color(0xFF166534),
                                        fontSize = 11,
                                        onClick = onNavigateToMyLearning,
                                        enabled = !isSending
                                    )

                                    ActionChip(
                                        label = "TT",
                                        backgroundColor = Color(0xFFFFF1F2),
                                        textColor = Color(0xFF9F1239),
                                        fontSize = 11,
                                        onClick = { if (courseId.isNotBlank()) onNavigateToPayment(courseId) },
                                        enabled = !isSending && courseId.isNotBlank()
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProgressChartMessage(
    message: ChatMessage,
    isUser: Boolean
) {
    @Suppress("UNCHECKED_CAST")
    val courses = message.metadata["courses"] as? List<Map<String, Any>> ?: emptyList()

    Surface(
        color = if (isUser) ChatUserBubble else ChatBotBubble,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .widthIn(max = 300.dp)
            .border(
                width = if (isUser) 0.dp else 1.dp,
                color = if (isUser) Color.Transparent else ChatBorder,
                shape = RoundedCornerShape(16.dp)
            )
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header
            Text(
                text = message.content.ifEmpty { "Tiến độ học tập của bạn" },
                color = if (isUser) Color.White else ChatTextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )

            // Progress Charts
            courses.forEach { courseData ->
                val courseTitle = courseData["title"] as? String ?: "Course"
                val progress = (courseData["progress"] as? Number)?.toInt() ?: 0

                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = courseTitle,
                            color = if (isUser) Color.White else ChatTextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f),
                            maxLines = 1
                        )
                        Text(
                            text = "$progress%",
                            color = if (isUser) Color.White else Color(0xFF4B5CC4),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Progress Bar
                    Surface(
                        color = Color.White.copy(alpha = if (isUser) 0.2f else 0.1f),
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(progress / 100f)
                                .background(
                                    color = when {
                                        progress >= 80 -> Color(0xFF4CAF50)
                                        progress >= 50 -> Color(0xFF2196F3)
                                        progress >= 25 -> Color(0xFFFFC107)
                                        else -> Color(0xFFEF5350)
                                    },
                                    shape = RoundedCornerShape(4.dp)
                                )
                        )
                    }
                }
            }
        }
    }
}


