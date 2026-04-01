package com.example.lms2.ui.screen.student

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.core.view.WindowInsetsControllerCompat
import coil.compose.AsyncImage
import com.example.lms2.data.model.Course
import com.example.lms2.data.model.MyLearningItem
import com.example.lms2.ui.component.ProgressBar
import com.example.lms2.util.formatPrice
import com.example.lms2.viewmodel.AuthViewModel
import com.example.lms2.viewmodel.CourseViewModel
import com.example.lms2.viewmodel.MyLearningViewModel

private val Indigo = Color(0xFF4B5CC4)
private val StarYellow = Color(0xFFFFC107)
private val SurfaceGray = Color(0xFFF5F6FA)
private val TextPrimary = Color(0xFF1A1D2E)
private val TextSecondary = Color(0xFF6B7280)
private val CardWhite = Color(0xFFFFFFFF)

@Composable
fun StudentHomeRoute(
    authViewModel: AuthViewModel,
    courseViewModel: CourseViewModel = viewModel(),
    myLearningViewModel: MyLearningViewModel = viewModel(),
    onSearchClick: () -> Unit,
    onCartClick: () -> Unit,
    onSeeAllClick: () -> Unit,
    onContinueClick: (MyLearningItem) -> Unit,
    onMyCourseClick: (MyLearningItem) -> Unit,
    onSuggestedCourseClick: (Course) -> Unit,
    onInstructorClick: (String, String) -> Unit
) {
    val authUiState by authViewModel.uiState.collectAsStateWithLifecycle()
    val courseUiState by courseViewModel.uiState.collectAsStateWithLifecycle()
    val myLearningUiState by myLearningViewModel.uiState.collectAsStateWithLifecycle()
    val user = authUiState.currentUser

    LaunchedEffect(user?.uid) {
        courseViewModel.getSuggestedCourses(user?.uid.orEmpty())
    }

    LaunchedEffect(user?.uid) {
        val uid = user?.uid.orEmpty()
        if (uid.isNotBlank()) {
            myLearningViewModel.loadMyLearning(uid)
        }
    }

    val continueCourse = remember(myLearningUiState.inProgressCourses) {
        myLearningUiState.inProgressCourses.firstOrNull()
    }
    val myCourses = remember(myLearningUiState.inProgressCourses) {
        myLearningUiState.inProgressCourses.take(3)
    }

    StudentHomeScreen(
        userName = user?.fullName ?: "Học viên",
        avatarUrl = user?.avatarUrl ?: "",
        isSuggestedLoading = courseUiState.isLoading,
        isMyLearningLoading = myLearningUiState.isLoading,
        hasMyLearningLoaded = myLearningUiState.hasLoadedOnce,
        suggestedCourses = courseUiState.suggestedCourses,
        continueCourse = continueCourse,
        myCourses = myCourses,
        onSearchClick = onSearchClick,
        onCartClick = onCartClick,
        onSeeAllClick = onSeeAllClick,
        onContinueClick = onContinueClick,
        onMyCourseClick = onMyCourseClick,
        onSuggestedCourseClick = onSuggestedCourseClick,
        onInstructorClick = onInstructorClick
    )
}

@Composable
fun StudentHomeScreen(
    userName: String,
    avatarUrl: String,
    isSuggestedLoading: Boolean,
    isMyLearningLoading: Boolean,
    hasMyLearningLoaded: Boolean,
    suggestedCourses: List<Course>,
    continueCourse: MyLearningItem?,
    myCourses: List<MyLearningItem>,
    onSearchClick: () -> Unit,
    onCartClick: () -> Unit,
    onSeeAllClick: () -> Unit,
    onContinueClick: (MyLearningItem) -> Unit,
    onMyCourseClick: (MyLearningItem) -> Unit,
    onSuggestedCourseClick: (Course) -> Unit,
    onInstructorClick: (String, String) -> Unit
) {
    HomeStatusBar(color = CardWhite, darkIcons = true)

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(SurfaceGray),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item {
            StudentHeader(
                userName = userName,
                avatarUrl = avatarUrl,
                onSearchClick = onSearchClick,
                onCartClick = onCartClick
            )
        }
        
        item {
            SectionTitle("Tiếp tục học", Modifier.padding(16.dp, 8.dp))
            when {
                continueCourse != null -> {
                    ContinueLearningCard(
                        item = continueCourse,
                        onContinueClick = {
                            onContinueClick(continueCourse)
                        },
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }

                isMyLearningLoading || !hasMyLearningLoaded -> {
                    ContinueLearningCardSkeleton(modifier = Modifier.padding(horizontal = 16.dp))
                }

                else -> {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(CardWhite)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Bạn chưa có khóa học để tiếp tục", color = TextSecondary, fontSize = 13.sp)
                        }
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp, 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SectionTitle("Các khóa học đang học")
                Text(
                    text = "Xem tất cả", color = Indigo, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable(onClick = onSeeAllClick)
                )
            }
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(myCourses, key = { it.course.id }) {
                    MyCourseCard(
                        item = it,
                        onClick = {
                            onMyCourseClick(it)
                        },
                        onInstructorClick = { instructorId, instructorName ->
                            onInstructorClick(instructorId, instructorName)
                        }
                    )
                }
            }
        }

        item { SectionTitle("Gợi ý cho bạn", Modifier.padding(16.dp, 12.dp)) }
        
        if (isSuggestedLoading) {
            item {
                Box(Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Indigo, modifier = Modifier.size(30.dp))
                }
            }
        } else {
            items(suggestedCourses) { course ->
                SuggestedCourseCard(
                    course = course,
                    onClick = { onSuggestedCourseClick(course) },
                    onInstructorClick = { instructorId, instructorName ->
                        onInstructorClick(instructorId, instructorName)
                    },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun HomeStatusBar(color: Color, darkIcons: Boolean) {
    val view = LocalView.current
    DisposableEffect(view, color, darkIcons) {
        val activity = view.context as? Activity
        val window = activity?.window
        if (window != null) {
            val previousColor = window.statusBarColor
            val previousIcons = WindowInsetsControllerCompat(window, view).isAppearanceLightStatusBars

            window.statusBarColor = color.toArgb()
            WindowInsetsControllerCompat(window, view).isAppearanceLightStatusBars = darkIcons

            onDispose {
                window.statusBarColor = previousColor
                WindowInsetsControllerCompat(window, view).isAppearanceLightStatusBars = previousIcons
            }
        } else {
            onDispose { }
        }
    }
}

@Composable
private fun ContinueLearningCardSkeleton(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "continue_learning_skeleton")
    val shimmerTranslate by transition.animateFloat(
        initialValue = -300f,
        targetValue = 900f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "skeleton_translate"
    )
    val shimmerBrush = Brush.linearGradient(
        colors = listOf(
            Color(0xFFE8ECF6),
            Color(0xFFF6F8FC),
            Color(0xFFE8ECF6)
        ),
        start = Offset(shimmerTranslate - 280f, 0f),
        end = Offset(shimmerTranslate, 280f)
    )

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(CardWhite),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .background(shimmerBrush)
            )

            Column(Modifier.padding(16.dp)) {
                Box(
                    Modifier
                        .fillMaxWidth(0.72f)
                        .height(18.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(shimmerBrush)
                )
                Spacer(Modifier.height(8.dp))
                Box(
                    Modifier
                        .fillMaxWidth(0.5f)
                        .height(14.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(shimmerBrush)
                )
                Spacer(Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier
                            .width(120.dp)
                            .height(14.dp)
                            .clip(RoundedCornerShape(7.dp))
                            .background(shimmerBrush)
                    )
                    Box(
                        Modifier
                            .width(86.dp)
                            .height(14.dp)
                            .clip(RoundedCornerShape(7.dp))
                            .background(shimmerBrush)
                    )
                }

                Spacer(Modifier.height(10.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(shimmerBrush)
                )

                Spacer(Modifier.height(16.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(shimmerBrush)
                )
            }
        }
    }
}

@Composable
private fun StudentHeader(userName: String, avatarUrl: String, onSearchClick: () -> Unit, onCartClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .background(CardWhite)
            .padding(16.dp, 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(44.dp).clip(CircleShape).background(Indigo.copy(0.15f)), contentAlignment = Alignment.Center) {
            if (avatarUrl.isNotBlank()) {
                AsyncImage(avatarUrl, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } else Icon(Icons.Default.Person, null, tint = Indigo)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy((-4).dp)) {
            Text("Xin chào", fontSize = 12.sp, color = TextSecondary)
            Text(userName, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        }
        IconButton(onSearchClick) { Icon(Icons.Default.Search, null, tint = TextPrimary) }
        IconButton(onCartClick) { Icon(Icons.Default.ShoppingCart, null, tint = TextPrimary) }
    }
}

@Composable
private fun ContinueLearningCard(item: MyLearningItem, onContinueClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(modifier = modifier, shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(CardWhite), elevation = CardDefaults.cardElevation(2.dp)) {
        Column {
            Box(Modifier.fillMaxWidth().height(160.dp).background(Brush.linearGradient(listOf(Color(0xFF2C3E8C), Color(0xFF6B79D4), Color(0xFFE8A838))))) {
                if (item.course.thumbnailUrl.isNotBlank()) AsyncImage(item.course.thumbnailUrl, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            }
            Column(Modifier.padding(16.dp)) {
                Text(item.course.title, fontWeight = FontWeight.Bold, fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val lessonLabel = if (item.lastLessonOrderIndex >= 0 && item.lastLessonTitle.isNotBlank()) {
                    "Bài học ${item.lastLessonOrderIndex + 1}: ${item.lastLessonTitle}"
                } else {
                    "Bắt đầu ngay nào!"
                }
                Text(lessonLabel, fontSize = 13.sp, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(10.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("${item.progressPercent}% Hoàn thành", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Indigo)
                    val total = item.totalLessons.coerceAtLeast(0)
                    Text("${item.completedLessons}/$total Bài học", fontSize = 12.sp, color = TextSecondary)
                }
                Spacer(Modifier.height(6.dp))
                ProgressBar(progress = (item.progressPercent / 100f).coerceIn(0f, 1f))
                Spacer(Modifier.height(16.dp))
                Button(onContinueClick, Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(Indigo)) {
                    Icon(Icons.Default.PlayArrow, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Tiếp tục", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun MyCourseCard(
    item: MyLearningItem,
    onClick: () -> Unit,
    onInstructorClick: (String, String) -> Unit
) {
    Card(
        modifier = Modifier.width(155.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(CardWhite),
        elevation = CardDefaults.cardElevation(2.dp),
        onClick = onClick
    ) {
        Column {
            Box(Modifier.fillMaxWidth().height(100.dp).background(Brush.linearGradient(listOf(Color(0xFFE8926A), Color(0xFFF5C49A))))) {
                if (item.course.thumbnailUrl.isNotBlank()) {
                    AsyncImage(item.course.thumbnailUrl, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                }
            }
            Column(Modifier.padding(10.dp)) {
                Text(item.course.title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 18.sp)
                Text(
                    text = item.course.instructorName,
                    fontSize = 11.sp,
                    color = Indigo,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.clickable(enabled = item.course.instructorId.isNotBlank()) {
                        onInstructorClick(item.course.instructorId, item.course.instructorName)
                    }
                )
                val total = item.totalLessons.coerceAtLeast(0)
                Text("${item.completedLessons}/$total Bài học", fontSize = 11.sp, color = TextSecondary)
                Spacer(Modifier.height(6.dp))
                ProgressBar(progress = (item.progressPercent / 100f).coerceIn(0f, 1f), height = 5.dp)
            }
        }
    }
}

@Composable
private fun SuggestedCourseCard(
    course: Course,
    onClick: () -> Unit,
    onInstructorClick: (String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CardWhite),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {

            Box(
                modifier = Modifier
                    .size(90.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(SurfaceGray)
            ) {
                if (course.thumbnailUrl.isNotBlank()) {
                    AsyncImage(
                        model = course.thumbnailUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = course.title,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 16.sp
                    )

                    Text(
                        text = course.instructorName,
                        fontSize = 12.sp,
                        color = Indigo,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.clickable(enabled = course.instructorId.isNotBlank()) {
                            onInstructorClick(course.instructorId, course.instructorName)
                        }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Star,
                            contentDescription = null,
                            tint = StarYellow,
                            modifier = Modifier.size(14.dp)
                        )

                        Spacer(modifier = Modifier.width(2.dp))

                        Text(
                            text = "${course.rating}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )

                        Text(
                            text = " (${course.reviewCount} đánh giá)",
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    Text(
                        text = formatPrice(course.price),
                        color = Indigo,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String, modifier: Modifier = Modifier) {
    Text(title, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = TextPrimary, modifier = modifier)
}
