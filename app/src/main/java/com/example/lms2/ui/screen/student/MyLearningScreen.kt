package com.example.lms2.ui.screen.student

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import com.example.lms2.data.model.MyLearningItem
import com.example.lms2.ui.component.TopBar
import com.example.lms2.util.MyLearningEvent
import com.example.lms2.util.MyLearningTab
import com.example.lms2.util.MyLearningUiState
import com.example.lms2.viewmodel.MyLearningViewModel
import kotlinx.coroutines.flow.collectLatest

private val Indigo = Color(0xFF4B5CC4)
private val SurfaceGray = Color(0xFFF5F6FA)
private val CardWhite = Color(0xFFFFFFFF)
private val TextPrimary = Color(0xFF1A1D2E)
private val TextSecondary = Color(0xFF6B7280)

@Composable
fun MyLearningRoute(
	userId: String,
	viewModel: MyLearningViewModel,
	onCourseClick: (courseId: String) -> Unit,
	onInstructorClick: (String, String) -> Unit,
	onExploreCoursesClick: () -> Unit = {}
) {
	val uiState by viewModel.uiState.collectAsStateWithLifecycle()
	val snackbarHostState = remember { SnackbarHostState() }

	LaunchedEffect(userId) {
		viewModel.loadMyLearning(userId)
	}

	LaunchedEffect(viewModel) {
		viewModel.event.collectLatest { event ->
			when (event) {
				is MyLearningEvent.ShowError -> snackbarHostState.showSnackbar(event.message)
			}
		}
	}

	Box(modifier = Modifier.fillMaxSize()) {
		MyLearningScreen(
			uiState = uiState,
			onTabSelected = viewModel::selectTab,
			onLoadMoreInProgress = { viewModel.loadMoreInProgress(userId) },
			onLoadMoreCompleted = { viewModel.loadMoreCompleted(userId) },
			onCourseClick = { item -> onCourseClick(item.course.id) },
			onInstructorClick = onInstructorClick,
			onExploreCoursesClick = onExploreCoursesClick
		)

		SnackbarHost(
			hostState = snackbarHostState,
			modifier = Modifier
				.align(Alignment.BottomCenter)
				.padding(16.dp)
		)
	}
}

@Composable
fun MyLearningScreen(
	uiState: MyLearningUiState,
	onTabSelected: (MyLearningTab) -> Unit,
	onLoadMoreInProgress: () -> Unit,
	onLoadMoreCompleted: () -> Unit,
	onCourseClick: (MyLearningItem) -> Unit,
	onInstructorClick: (String, String) -> Unit,
	onExploreCoursesClick: () -> Unit = {},
	onBackClick: () -> Unit = {}
) {
	val listState = rememberLazyListState()
	val shouldLoadMore by remember {
		derivedStateOf {
			val layoutInfo = listState.layoutInfo
			val totalItems = layoutInfo.totalItemsCount
			val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
			totalItems > 0 && lastVisible >= totalItems - 3
		}
	}

	val activeHasMore = when (uiState.selectedTab) {
		MyLearningTab.IN_PROGRESS -> uiState.hasMoreInProgress
		MyLearningTab.COMPLETED -> uiState.hasMoreCompleted
	}

	LaunchedEffect(shouldLoadMore, uiState.selectedTab, uiState.isLoading, uiState.isLoadingMore, activeHasMore) {
		if (shouldLoadMore && !uiState.isLoading && !uiState.isLoadingMore && activeHasMore) {
			when (uiState.selectedTab) {
				MyLearningTab.IN_PROGRESS -> onLoadMoreInProgress()
				MyLearningTab.COMPLETED -> onLoadMoreCompleted()
			}
		}
	}

	Scaffold(
		topBar = {
			TopBar(
				title = "Khóa học của tôi",
				onBackClick = onBackClick,
				showBackButton = false
			)
		},
		containerColor = SurfaceGray
	) { paddingValues ->
		Column(
			modifier = Modifier
				.fillMaxSize()
				.padding(paddingValues)
		) {
			MyLearningTabs(
				selectedTab = uiState.selectedTab,
				inProgressCount = uiState.inProgressCourses.size,
				completedCount = uiState.completedCourses.size,
				onTabSelected = onTabSelected
			)

			when {
				uiState.isLoading -> {
					Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
						CircularProgressIndicator(color = Indigo)
					}
				}

				uiState.visibleCourses.isEmpty() -> {
					EmptyMyLearningState(
						tab = uiState.selectedTab,
						hasAnyEnrollment = uiState.inProgressCourses.isNotEmpty() || uiState.completedCourses.isNotEmpty(),
						onExploreCoursesClick = onExploreCoursesClick
					)
				}

				else -> {
					LazyColumn(
						modifier = Modifier.fillMaxSize(),
						state = listState,
						contentPadding = PaddingValues(16.dp),
						verticalArrangement = Arrangement.spacedBy(14.dp)
					) {
						items(uiState.visibleCourses, key = { it.course.id }) { item ->
							MyLearningCourseCard(
								item = item,
								onClick = { onCourseClick(item) },
								onInstructorClick = { instructorId, instructorName ->
									onInstructorClick(instructorId, instructorName)
								}
							)
						}

						if (uiState.isLoadingMore) {
							item {
								Box(
									modifier = Modifier
										.fillMaxWidth()
										.padding(vertical = 8.dp),
									contentAlignment = Alignment.Center
								) {
									CircularProgressIndicator(
										color = Indigo,
										modifier = Modifier.size(22.dp),
										strokeWidth = 2.dp
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
private fun MyLearningTabs(
	selectedTab: MyLearningTab,
	inProgressCount: Int,
	completedCount: Int,
	onTabSelected: (MyLearningTab) -> Unit
) {
	val tabs = listOf(
		MyLearningTab.IN_PROGRESS to "Đang học",
		MyLearningTab.COMPLETED to "Đã hoàn thành"
	)
	val selectedIndex = if (selectedTab == MyLearningTab.IN_PROGRESS) 0 else 1

	TabRow(
		selectedTabIndex = selectedIndex,
		containerColor = CardWhite,
		contentColor = Indigo,
		indicator = { tabPositions ->
			TabRowDefaults.SecondaryIndicator(
				modifier = Modifier.tabIndicatorOffset(tabPositions[selectedIndex]),
				color = Indigo
			)
		}
	) {
		tabs.forEachIndexed { index, (tab, title) ->
			val count = if (index == 0) inProgressCount else completedCount
			Tab(
				selected = selectedTab == tab,
				onClick = { onTabSelected(tab) },
				text = {
					Text(
						text = "$title ($count)",
						fontSize = 15.sp,
						fontWeight = if (selectedTab == tab) FontWeight.SemiBold else FontWeight.Normal
					)
				},
				selectedContentColor = Indigo,
				unselectedContentColor = TextSecondary
			)
		}
	}
	HorizontalDivider(thickness = 1.dp, color = Color(0xFFE5E7EB))
}

@Composable
private fun MyLearningCourseCard(
	item: MyLearningItem,
	onClick: () -> Unit,
	onInstructorClick: (String, String) -> Unit
) {
	Card(
		onClick = onClick,
		modifier = Modifier.fillMaxWidth(),
		shape = RoundedCornerShape(14.dp),
		colors = CardDefaults.cardColors(containerColor = CardWhite),
		elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
	) {
		Column(modifier = Modifier.padding(12.dp)) {
			Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
				Column(
					modifier = Modifier.weight(1f),
					verticalArrangement = Arrangement.spacedBy(2.dp)
				) {
					Text(
						text = item.categoryName,
						color = Indigo,
						fontSize = 12.sp,
						fontWeight = FontWeight.SemiBold
					)
					Text(
						text = item.course.title,
						color = TextPrimary,
						fontSize = 15.sp,
						fontWeight = FontWeight.Bold,
						lineHeight = 21.sp,
						maxLines = 2,
						overflow = TextOverflow.Ellipsis
					)

					Text(
						text = item.course.instructorName,
						color = Indigo,
						fontSize = 12.sp,
						maxLines = 1,
						overflow = TextOverflow.Ellipsis,
						modifier = Modifier.clickable(enabled = item.course.instructorId.isNotBlank()) {
							onInstructorClick(item.course.instructorId, item.course.instructorName)
						}
					)
				}

				if (item.course.thumbnailUrl.isNotBlank()) {
					AsyncImage(
						model = item.course.thumbnailUrl,
						contentDescription = null,
						modifier = Modifier
							.size(96.dp)
							.clip(RoundedCornerShape(10.dp)),
						contentScale = ContentScale.Crop
					)
				} else {
					Box(
						modifier = Modifier
							.size(96.dp)
							.clip(RoundedCornerShape(10.dp))
							.background(Color(0xFFE8ECF6)),
						contentAlignment = Alignment.Center
					) {
						Icon(
							imageVector = Icons.Default.PlayArrow,
							contentDescription = null,
							tint = Indigo.copy(alpha = 0.5f),
							modifier = Modifier
								.size(34.dp)
								.clip(CircleShape)
								.background(Indigo.copy(alpha = 0.14f))
								.padding(6.dp)
						)
					}
				}
			}

			Spacer(modifier = Modifier.height(8.dp))

			Row(
				modifier = Modifier.fillMaxWidth(),
				horizontalArrangement = Arrangement.SpaceBetween,
				verticalAlignment = Alignment.CenterVertically
			) {
				Text(
					text = "Tiến trình",
					fontSize = 14.sp,
					fontWeight = FontWeight.Medium,
					color = TextPrimary
				)
				Text(
					text = "${item.progressPercent}%",
					fontSize = 14.sp,
					fontWeight = FontWeight.SemiBold,
					color = Indigo
				)
			}

			Spacer(modifier = Modifier.height(4.dp))

			val animatedProgress by animateFloatAsState(
				targetValue = item.progressPercent / 100f,
				animationSpec = tween(durationMillis = 500),
				label = "learning_progress"
			)

			Box(
				modifier = Modifier
					.fillMaxWidth()
					.height(7.dp)
					.clip(RoundedCornerShape(999.dp))
					.background(Color(0xFFD7DBE6))
			) {
				Box(
					modifier = Modifier
						.fillMaxWidth(animatedProgress.coerceIn(0f, 1f))
						.height(7.dp)
						.clip(RoundedCornerShape(999.dp))
						.background(Indigo)
				)
			}
		}
	}
}

@Composable
private fun EmptyMyLearningState(
	tab: MyLearningTab,
	hasAnyEnrollment: Boolean,
	onExploreCoursesClick: () -> Unit
) {
	val message = when (tab) {
		MyLearningTab.IN_PROGRESS -> {
			if (hasAnyEnrollment) "Bạn đã hoàn thành hết khóa học" else "Bạn chưa tham gia khóa học nào"
		}
		MyLearningTab.COMPLETED -> "Bạn chưa hoàn thành khóa học nào"
	}

	Box(
		modifier = Modifier
			.fillMaxSize()
			.padding(24.dp),
		contentAlignment = Alignment.Center
	) {
		Column(horizontalAlignment = Alignment.CenterHorizontally) {
			Text(
				text = message,
				color = TextSecondary,
				fontSize = 16.sp
			)

			Spacer(modifier = Modifier.height(24.dp))

			if (tab == MyLearningTab.IN_PROGRESS) {
				Button(
					onClick = onExploreCoursesClick,
					modifier = Modifier.fillMaxWidth().height(52.dp),
					colors = ButtonDefaults.buttonColors(containerColor = Indigo),
					shape = RoundedCornerShape(12.dp)
				) {
					Text("Khám phá khóa học", fontWeight = FontWeight.Bold)
					Spacer(Modifier.width(8.dp))
					Icon(Icons.AutoMirrored.Filled.ArrowForward, null, Modifier.size(18.dp))
                }
			}
		}
	}
}
