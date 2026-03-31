package com.example.lms.ui.screen.student

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Quiz
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.exoplayer.ExoPlayer
import androidx.navigation.NavController
import com.example.lms.data.model.*
import com.example.lms.ui.component.TopBar
import com.example.lms.util.CloudinaryManager
import com.example.lms.viewmodel.LessonPlayerViewModel

private val Indigo        = Color(0xFF4B5CC4)
private val SurfaceGray   = Color(0xFFF5F6FA)
private val TextPrimary   = Color(0xFF1A1D2E)
private val TextSecondary = Color(0xFF6B7280)
private val CardWhite     = Color(0xFFFFFFFF)
private val DarkBg        = Color(0xFF1A1D2E)
private val GreenCheck    = Color(0xFF10B981)

@Composable
fun LessonPlayerScreen(
    navController: NavController,
    viewModel: LessonPlayerViewModel,
    courseId: String,
    itemId: String,
    userId: String,
    onStartQuiz: (String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var isVideoFullscreen by rememberSaveable { mutableStateOf(false) }
    var isNavigatingBack by remember { mutableStateOf(false) }
    var lastPlaybackUrl by rememberSaveable { mutableStateOf("") }
    var playbackPositionMs by rememberSaveable { mutableLongStateOf(0L) }
    var playbackWhenReady by rememberSaveable { mutableStateOf(false) }
    val isPortrait = configuration.orientation == Configuration.ORIENTATION_PORTRAIT
    val showScaffoldChrome = !isVideoFullscreen && isPortrait

    val exoPlayer = remember {
        ExoPlayer.Builder(context).build()
    }
    val videoSectionStateHolder = rememberSaveableStateHolder()

    LessonPlayerFullscreenEffect(isFullscreen = isVideoFullscreen)

    BackHandler(enabled = true) {
        isNavigatingBack = true
        navController.popBackStack()
    }

    LaunchedEffect(courseId, itemId) {
        viewModel.loadData(userId, courseId, itemId)
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> viewModel.reloadProgress(userId, courseId)
                Lifecycle.Event.ON_PAUSE -> {
                    exoPlayer.pause()
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.release()
        }
    }

    val selectedItem = uiState.selectedItem
    val isQuizSelected = selectedItem is CurriculumItem.QuizItem
    val selectedLessonVideoUrl = (selectedItem as? CurriculumItem.LessonItem)?.lesson?.videoUrl.orEmpty()

    LaunchedEffect(selectedLessonVideoUrl) {
        if (selectedLessonVideoUrl.isNotBlank() && selectedLessonVideoUrl != lastPlaybackUrl) {
            lastPlaybackUrl = selectedLessonVideoUrl
            playbackPositionMs = 0L
            playbackWhenReady = false
        }
    }

    fun updatePlaybackSnapshot(positionMs: Long, whenReady: Boolean) {
        playbackPositionMs = positionMs
        playbackWhenReady = whenReady
    }

    Scaffold(
        topBar = {
            if (showScaffoldChrome) {
                TopBar(
                    title = uiState.course?.title ?: "",
                    onBackClick = {
                        isNavigatingBack = true
                        navController.popBackStack()
                    }
                )
            }
        },
        containerColor = SurfaceGray,
        bottomBar = {
            if (!uiState.isLoading && showScaffoldChrome) {
                LessonPlayerBottomBar(
                    isQuizSelected = isQuizSelected,
                    isCompleted = uiState.currentLessonProgress?.isCompleted ?: false,
                    isToggling = uiState.isTogglingLesson,
                    onToggleComplete = {
                        if (!isQuizSelected) {
                            viewModel.toggleLessonComplete(userId, courseId, uiState.selectedItemId)
                        }
                    },
                    onStartQuiz = {
                        if (isQuizSelected) onStartQuiz(uiState.selectedItemId)
                    }
                )
            }
        }
    ) { paddingValues ->
        if (isNavigatingBack) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(SurfaceGray)
            )
        } else if (uiState.isLoading) {
            Box(Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Indigo)
            }
        } else if ((isVideoFullscreen || !isPortrait) && selectedItem is CurriculumItem.LessonItem) {
            videoSectionStateHolder.SaveableStateProvider(key = "lesson_video_section") {
                LessonVideoSection(
                    videoUrl = selectedItem.lesson.videoUrl,
                    exoPlayer = exoPlayer,
                    isFullscreen = isVideoFullscreen,
                    isPortrait = isPortrait,
                    playbackPositionMs = playbackPositionMs,
                    playbackWhenReady = playbackWhenReady,
                    onPlaybackSnapshot = ::updatePlaybackSnapshot,
                    onEnterFullscreen = {
                        isVideoFullscreen = true
                    },
                    onExitFullscreen = {
                        isVideoFullscreen = false
                    }
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(paddingValues),
            ) {
                item {
                    when (selectedItem) {
                        is CurriculumItem.LessonItem -> videoSectionStateHolder.SaveableStateProvider(key = "lesson_video_section") {
                            LessonVideoSection(
                                videoUrl = selectedItem.lesson.videoUrl,
                                exoPlayer = exoPlayer,
                                isFullscreen = isVideoFullscreen,
                                isPortrait = isPortrait,
                                playbackPositionMs = playbackPositionMs,
                                playbackWhenReady = playbackWhenReady,
                                onPlaybackSnapshot = ::updatePlaybackSnapshot,
                                onEnterFullscreen = {
                                    isVideoFullscreen = true
                                },
                                onExitFullscreen = {
                                    isVideoFullscreen = false
                                }
                            )
                        }
                        is CurriculumItem.QuizItem -> QuizInfoSection(quiz = selectedItem.quiz, quizProgress = uiState.quizProgressMap[selectedItem.id])
                        null -> VideoPlaceholder()
                    }
                }

                if (selectedItem is CurriculumItem.LessonItem) {
                    item { LessonTabSection(lesson = selectedItem.lesson) }
                }

                item {
                    CurriculumListSection(
                        curriculum = uiState.curriculum,
                        selectedItemId = uiState.selectedItemId,
                        lessonProgressMap = uiState.lessonProgressMap,
                        quizProgressMap = uiState.quizProgressMap,
                        completedLessons = uiState.progress?.completedLessons ?: 0,
                        totalLessons = uiState.course?.lessonCount ?: 0,
                        onItemClick = { viewModel.selectItem(userId, courseId, it) }
                    )
                }
            }
        }
    }
}

@Composable
private fun VideoPlaceholder() {
    Box(Modifier.fillMaxWidth().height(220.dp).background(DarkBg), contentAlignment = Alignment.Center) {
        Icon(Icons.Default.PlayCircle, null, tint = Color.White.copy(alpha = 0.5f), modifier = Modifier.size(56.dp))
    }
}

@Composable
private fun QuizInfoSection(quiz: Quiz, quizProgress: QuizProgress?) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(CardWhite)
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            quiz.title,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(16.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            QuizInfoCard(
                icon = Icons.Default.Timer,
                value = "${quiz.durationMinutes} phút",
                label = "Thời gian",
                modifier = Modifier.weight(1f)
            )
            QuizInfoCard(
                icon = Icons.AutoMirrored.Filled.List,
                value = "${quiz.questions.size}",
                label = "Số câu hỏi",
                modifier = Modifier.weight(1f)
            )
        }

        if (quiz.description.isNotBlank()) {
            Spacer(Modifier.height(16.dp))
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(SurfaceGray)
                    .padding(14.dp)
            ) {
                Text(
                    "Về bài kiểm tra này",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary,
                    textAlign = TextAlign.Center,
                    modifier =  Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    quiz.description,
                    fontSize = 13.sp,
                    color = TextSecondary,
                    lineHeight = 19.sp
                )
            }
        }

        if (quizProgress != null && quizProgress.attempts > 0) {
            Spacer(Modifier.height(16.dp))
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (quizProgress.isPassed) GreenCheck.copy(0.08f) else Color(0xFFFFEBEE))
                    .padding(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            "Đã hoàn thành",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (quizProgress.isPassed) GreenCheck else Color(0xFFC41C3B)
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Số lần làm: ${quizProgress.attempts} • Điểm cao nhất: ${quizProgress.bestScore}%",
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (quizProgress.isPassed)
                                        GreenCheck.copy(0.15f)
                                    else
                                        Color(0xFFC41C3B).copy(0.15f)
                                )
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = if (quizProgress.isPassed) "Đạt" else "Chưa đạt",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (quizProgress.isPassed) GreenCheck else Color(0xFFC41C3B)
                            )
                        }

                        Icon(
                            if (quizProgress.isPassed) Icons.Default.CheckCircle else Icons.Default.Error,
                            null,
                            tint = if (quizProgress.isPassed) GreenCheck else Color(0xFFC41C3B),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QuizInfoCard(
    icon: ImageVector,
    value: String,
    label: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceGray)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(Indigo.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                null,
                tint = Indigo,
                modifier = Modifier.size(18.dp)
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                value,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                lineHeight = 20.sp
            )
            Text(
                label,
                fontSize = 11.sp,
                color = TextSecondary,
                lineHeight = 14.sp
            )
        }
    }
}

@Composable
private fun LessonTabSection(lesson: Lesson) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Mô tả", "Tài liệu")

    Column(Modifier.fillMaxWidth().background(CardWhite)) {
        TabRow(selectedTabIndex = selectedTab, containerColor = CardWhite, contentColor = Indigo, indicator = { tabPositions ->
            TabRowDefaults.SecondaryIndicator(Modifier.tabIndicatorOffset(tabPositions[selectedTab]), color = Indigo)
        }) {
            tabs.forEachIndexed { index, title ->
                Tab(selected = selectedTab == index, onClick = { selectedTab = index },
                    text = { Text(title, fontSize = 14.sp, fontWeight = if (selectedTab == index) FontWeight.SemiBold else FontWeight.Normal) },
                    selectedContentColor = Indigo, unselectedContentColor = TextSecondary
                )
            }
        }
        when (selectedTab) {
            0 -> DescriptionTab(title = lesson.title, description = lesson.description)
            1 -> AttachmentTab(attachments = lesson.attachments)
        }
    }
}

@Composable
private fun DescriptionTab(title: String, description: String) {
    Column(Modifier.padding(16.dp)) {
        Text(
            text = title,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(description.ifBlank { "Không có mô tả" }, fontSize = 14.sp, color = TextSecondary, lineHeight = 22.sp)
    }
}

@Composable
private fun AttachmentTab(attachments: List<Attachment>) {
    Column(Modifier.padding(16.dp)) {
        if (attachments.isEmpty()) {
            Text("Không có tài liệu", fontSize = 14.sp, color = TextSecondary)
        } else {
            Text("Tài liệu bài học", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            Spacer(Modifier.height(10.dp))
            attachments.forEach { AttachmentRow(attachment = it); Spacer(Modifier.height(8.dp)) }
        }
    }
}

@Composable
private fun AttachmentRow(attachment: Attachment) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .border(
                width = 1.dp,
                color = Color(0xFFE5E7EB),
                shape = RoundedCornerShape(10.dp)
            )
            .background(SurfaceGray)
            .clickable {
                CloudinaryManager.downloadFile(context, attachment.url, attachment.name)
            }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Indigo.copy(alpha = 0.1f)),
            Alignment.Center
        ) {
            Icon(
                Icons.Default.Description,
                null,
                tint = Indigo,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(
            Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy((-4).dp)
        ) {
            Text(
                attachment.name,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "${attachment.type.uppercase()} • ${attachment.size}",
                fontSize = 11.sp,
                color = TextSecondary
            )
        }
        Icon(
            Icons.Default.Download,
            "Tải xuống",
            tint = Indigo,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun CurriculumListSection(
    curriculum: List<CurriculumItem>,
    selectedItemId: String,
    lessonProgressMap: Map<String, LessonProgress>,
    quizProgressMap: Map<String, QuizProgress>,
    completedLessons: Int,
    totalLessons: Int,
    onItemClick: (String) -> Unit
) {
    Column(Modifier.fillMaxWidth()) {

        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Danh sách bài học",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )

            Text(
                "$completedLessons / $totalLessons Hoàn thành",
                fontSize = 12.sp,
                color = TextSecondary
            )
        }

        HorizontalDivider(
            thickness = 1.dp,
            color = Color(0xFFDBDBE6)
        )

        Column(Modifier.fillMaxWidth()) {
            curriculum.forEachIndexed { _, item ->
                val isSelected = item.id == selectedItemId

                when (item) {
                    is CurriculumItem.LessonItem -> {
                        LessonCurriculumRow(
                            lesson = item.lesson,
                            isSelected = isSelected,
                            isCompleted = lessonProgressMap[item.id]?.isCompleted ?: false
                        ) { onItemClick(item.id) }
                    }

                    is CurriculumItem.QuizItem -> {
                        QuizCurriculumRow(
                            quiz = item.quiz,
                            isSelected = isSelected,
                            isCompleted = (quizProgressMap[item.id]?.attempts ?: 0) > 0
                        ) { onItemClick(item.id) }
                    }
                }

                HorizontalDivider(
                    thickness = 1.dp,
                    color = Color(0xFFDBDBE6)
                )
            }
        }
    }
}

@Composable
private fun LessonCurriculumRow(
    lesson: Lesson,
    isSelected: Boolean,
    isCompleted: Boolean,
    onClick: () -> Unit
) {
    val backgroundColor = when {
        isSelected -> Indigo.copy(alpha = 0.08f)
        else -> Color.White
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(
                    when {
                        isCompleted -> GreenCheck.copy(0.15f)
                        isSelected -> Indigo.copy(0.15f)
                        else -> SurfaceGray
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = when {
                    isCompleted -> Icons.Default.CheckCircle
                    else -> Icons.Default.PlayArrow
                },
                contentDescription = null,
                tint = when {
                    isCompleted -> GreenCheck
                    isSelected -> Indigo
                    else -> TextSecondary
                },
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(Modifier.width(14.dp))

        Column(
            modifier = Modifier
                .weight(1f)
                .widthIn(max = 220.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                lesson.title,
                fontSize = 14.sp,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                color = if (isSelected) Indigo else TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 18.sp
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {

                Text(
                    lesson.duration,
                    fontSize = 12.sp,
                    color = TextSecondary,
                    lineHeight = 16.sp
                )

                if (isSelected || isCompleted) {
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(50))
                            .background(
                                if (isCompleted) GreenCheck.copy(0.1f)
                                else Indigo.copy(0.1f)
                            )
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            if (isCompleted) "Đã hoàn thành" else "Đang học",
                            fontSize = 11.sp,
                            color = if (isCompleted) GreenCheck else Indigo,
                            fontWeight = FontWeight.Medium,
                            lineHeight = 14.sp
                        )
                    }
                }
            }
        }

        Spacer(Modifier.width(8.dp))

        Box(
            modifier = Modifier.width(28.dp),
            contentAlignment = Alignment.Center
        ) {
            if (isSelected && !isCompleted) {
                Icon(
                    Icons.Default.Equalizer,
                    contentDescription = null,
                    tint = Indigo,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun QuizCurriculumRow(
    quiz: Quiz,
    isSelected: Boolean,
    isCompleted: Boolean = false,
    onClick: () -> Unit
) {
    val backgroundColor = when {
        isSelected -> Indigo.copy(alpha = 0.08f)
        else -> Color.White
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {

        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(
                    when {
                        isCompleted -> GreenCheck.copy(0.15f)
                        isSelected -> Indigo.copy(0.15f)
                        else -> SurfaceGray
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = when {
                    isCompleted -> Icons.Default.CheckCircle
                    else -> Icons.Outlined.Quiz
                },
                contentDescription = null,
                tint = when {
                    isCompleted -> GreenCheck
                    isSelected -> Indigo
                    else -> TextSecondary
                },
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(Modifier.width(14.dp))

        Text(
            quiz.title,
            fontSize = 14.sp,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
            color = if (isSelected) Indigo else TextPrimary,
            modifier = Modifier
                .weight(1f)
                .widthIn(max = 220.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 18.sp
        )

        if (isSelected || isCompleted) {
            Box(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(
                        if (isCompleted) GreenCheck.copy(0.1f)
                        else Indigo.copy(0.1f)
                    )
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(
                    if (isCompleted) "Đã hoàn thành" else "Chưa làm",
                    fontSize = 11.sp,
                    color = if (isCompleted) GreenCheck else Indigo,
                    fontWeight = FontWeight.Medium,
                    lineHeight = 14.sp
                )
            }
        }
        Spacer(Modifier.width(8.dp))
    }
}

@Composable
private fun LessonPlayerBottomBar(isQuizSelected: Boolean, isCompleted: Boolean, isToggling: Boolean, onToggleComplete: () -> Unit, onStartQuiz: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shadowElevation = 0.dp,
        color = CardWhite
    ) {
        HorizontalDivider(thickness = 1.dp, color = Color(0xFFE2E4ED))
        Box(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            if (isQuizSelected) {
                Button(onStartQuiz, Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(Indigo)) {
                    Text("Bắt đầu", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
            } else {
                Button(onToggleComplete, Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = if (isCompleted) GreenCheck else Indigo), enabled = !isToggling) {
                    if (isToggling) CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                    else Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(if (isCompleted) "Đã hoàn thành" else "Đánh dấu đã hoàn thành", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Icon(if (isCompleted) Icons.Default.CheckCircle else Icons.Default.CheckCircleOutline, null, Modifier.size(20.dp))
                    }
                }
            }
        }
    }
}
