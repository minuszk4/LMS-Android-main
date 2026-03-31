package com.example.lms2.ui.screen.student

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.lms2.data.model.CartItem
import com.example.lms2.ui.component.TopBar
import com.example.lms2.util.CartEvent
import com.example.lms2.util.CartUiState
import com.example.lms2.util.formatPrice
import com.example.lms2.viewmodel.CartViewModel
import kotlinx.coroutines.flow.collectLatest
import java.text.NumberFormat
import java.util.Locale

private val Indigo = Color(0xFF4B5CC4)
private val SurfaceGray = Color(0xFFF5F6FA)
private val CardWhite = Color(0xFFFFFFFF)
private val TextPrimary = Color(0xFF1A1D2E)
private val TextSecondary = Color(0xFF6B7280)
private val BorderColor = Color(0xFFE2E4ED)

@Composable
fun CartRoute(
    userId: String,
    viewModel: CartViewModel,
    onBackClick: () -> Unit,
    onNavigateToPayment: (selectedCourseIds: List<String>) -> Unit,
    onExploreCoursesClick: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(userId) {
        viewModel.loadCart(userId)
    }

    LaunchedEffect(viewModel) {
        viewModel.event.collectLatest { event ->
            when (event) {
                is CartEvent.ShowError -> snackbarHostState.showSnackbar(event.message)
                is CartEvent.ItemRemoved -> {
                    snackbarHostState.showSnackbar("Đã xóa ${event.courseTitle}")
                }
                is CartEvent.NavigateToPayment -> onNavigateToPayment(event.selectedCourseIds)
            }
        }
    }

    CartScreen(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onBackClick = onBackClick,
        onToggleItem = viewModel::toggleCourseSelection,
        onRemoveItem = { courseId -> viewModel.removeCourseFromCart(userId, courseId) },
        onSelectAll = viewModel::selectAllCourses,
        onClearSelection = viewModel::clearSelection,
        onCheckout = viewModel::proceedToPayment,
        onLoadMore = { viewModel.loadMoreItems(userId) },
        onExploreCoursesClick = onExploreCoursesClick
    )
}

@Composable
fun CartScreen(
    uiState: CartUiState,
    snackbarHostState: SnackbarHostState,
    onBackClick: () -> Unit,
    onToggleItem: (courseId: String) -> Unit,
    onRemoveItem: (courseId: String) -> Unit,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    onCheckout: () -> Unit,
    onLoadMore: () -> Unit,
    onExploreCoursesClick: () -> Unit
) {
    Scaffold(
        topBar = {
            TopBar(
                title = "Giỏ hàng",
                onBackClick = onBackClick
            )
        },
        containerColor = SurfaceGray,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        bottomBar = {
            if (uiState.items.isNotEmpty()) {
                CartBottomBar(
                    totalAmount = uiState.selectedTotalAmount,
                    selectedItemCount = uiState.selectedItemCount,
                    enabled = uiState.selectedItemCount > 0,
                    onCheckout = onCheckout
                )
            }
        }
    ) { paddingValues ->
        when {
            uiState.isLoading && uiState.items.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Indigo)
                }
            }

            uiState.items.isEmpty() -> {
                EmptyCartState(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    onExploreCoursesClick = onExploreCoursesClick
                )
            }

            else -> {
                CartContent(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    uiState = uiState,
                    onToggleItem = onToggleItem,
                    onRemoveItem = onRemoveItem,
                    onSelectAll = onSelectAll,
                    onClearSelection = onClearSelection,
                    onLoadMore = onLoadMore
                )
            }
        }
    }
}

@Composable
private fun CartContent(
    modifier: Modifier,
    uiState: CartUiState,
    onToggleItem: (courseId: String) -> Unit,
    onRemoveItem: (courseId: String) -> Unit,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    onLoadMore: () -> Unit
) {
    val allSelected = uiState.items.isNotEmpty() && uiState.selectedItemCount == uiState.items.size
    var revealedItemId by rememberSaveable { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()
    val shouldLoadMore by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val totalItems = layoutInfo.totalItemsCount
            val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            totalItems > 0 && lastVisible >= totalItems - 3
        }
    }

    LaunchedEffect(shouldLoadMore, uiState.isLoadingMore, uiState.hasMoreItems) {
        if (shouldLoadMore && !uiState.isLoadingMore && uiState.hasMoreItems) {
            onLoadMore()
        }
    }

    LazyColumn(
        modifier = modifier,
        state = listState,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${uiState.items.size} khóa học trong giỏ",
                    color = TextSecondary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )

                Text(
                    text = if (allSelected) "Bỏ chọn tất cả" else "Chọn tất cả",
                    color = Indigo,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable {
                        if (allSelected) onClearSelection() else onSelectAll()
                    }
                )
            }
        }

        items(uiState.items, key = { it.id }) { item ->
            SwipeToDeleteCartItem(
                item = item,
                isSelected = item.courseId in uiState.selectedCourseIds,
                isDeleteRevealed = revealedItemId == item.id,
                onReveal = { revealedItemId = item.id },
                onClose = {
                    if (revealedItemId == item.id) {
                        revealedItemId = null
                    }
                },
                onToggle = { onToggleItem(item.courseId) },
                onRemove = {
                    onRemoveItem(item.courseId)
                    if (revealedItemId == item.id) {
                        revealedItemId = null
                    }
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

        item {
            Spacer(modifier = Modifier.height(86.dp))
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun SwipeToDeleteCartItem(
    item: CartItem,
    isSelected: Boolean,
    isDeleteRevealed: Boolean,
    onReveal: () -> Unit,
    onClose: () -> Unit,
    onToggle: () -> Unit,
    onRemove: () -> Unit
) {
    val deleteActionSize = 44.dp
    val deleteActionTrailingPadding = 10.dp
    val deleteActionLeadingGap = 10.dp

    val offsetX by androidx.compose.animation.core.animateDpAsState(
        targetValue = if (isDeleteRevealed) {
            -(deleteActionSize + deleteActionTrailingPadding + deleteActionLeadingGap)
        } else {
            0.dp
        },
        label = "cart_item_reveal_offset"
    )

    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                onReveal()
                false
            } else {
                false
            }
        },
        positionalThreshold = { it * 0.35f }
    )

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = true,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color(0xFFFFEBEE))
                    .padding(end = deleteActionTrailingPadding),
                contentAlignment = Alignment.CenterEnd
            ) {
                Surface(
                    onClick = onRemove,
                    color = Color(0xFFE35D5D),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .padding(end = 2.dp)
                        .size(deleteActionSize)
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Xóa khỏi giỏ",
                            modifier = Modifier.size(16.dp),
                            tint = Color.White
                        )
                    }
                }
            }
        }
    ) {
        Box(
            modifier = Modifier.offset {
                IntOffset(x = offsetX.roundToPx(), y = 0)
            }
        ) {
            CartItemCard(
                item = item,
                isSelected = isSelected,
                onToggle = {
                    if (isDeleteRevealed) {
                        onClose()
                    } else {
                        onToggle()
                    }
                }
            )
        }
    }
}

@Composable
private fun CartItemCard(
    item: CartItem,
    isSelected: Boolean,
    onToggle: () -> Unit
) {
    val containerColor = if (isSelected) Color(0xFFE7ECFF) else CardWhite

    Card(
        onClick = onToggle,
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = if (isSelected) Indigo.copy(alpha = 0.62f) else BorderColor,
                shape = RoundedCornerShape(18.dp)
            ),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 4.dp else 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CartSelectIndicator(
                isSelected = isSelected,
                modifier = Modifier.padding(top = 2.dp)
            )

            AsyncImage(
                model = item.courseThumbnail,
                contentDescription = item.courseTitle,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(96.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFFE8ECF6))
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        text = item.courseTitle,
                        color = TextPrimary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }

                Text(
                    text = formatPrice(item.coursePrice),
                    color = Indigo,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }
    }
}

@Composable
private fun CartSelectIndicator(
    isSelected: Boolean,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(22.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(if (isSelected) Indigo else CardWhite)
            .border(
                width = 1.5.dp,
                color = if (isSelected) Indigo else Color(0xFFB7C0D4),
                shape = RoundedCornerShape(999.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        if (isSelected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(13.dp)
            )
        }
    }
}

@Composable
private fun EmptyCartState(
    modifier: Modifier = Modifier,
    onExploreCoursesClick: () -> Unit
) {
    Column(
        modifier = modifier.padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Giỏ hàng của bạn đang trống",
            color = TextPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Hãy thêm khóa học để bắt đầu thanh toán.",
            color = TextSecondary,
            fontSize = 14.sp
        )
        Spacer(modifier = Modifier.height(48.dp))

        Button(
            onClick = onExploreCoursesClick,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Indigo),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
        ) {
            Text("Khám phá khóa học", fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun CartBottomBar(
    totalAmount: Double,
    selectedItemCount: Int,
    enabled: Boolean,
    onCheckout: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shadowElevation = 0.dp,
        color = CardWhite
    ) {
        HorizontalDivider(thickness = 1.dp, color = BorderColor)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Tổng đã chọn",
                    color = TextSecondary,
                    fontSize = 14.sp
                )
                Text(
                    text = NumberFormat.getInstance(Locale("vi", "VN"))
                        .format(totalAmount.toLong()) + "đ",
                    color = TextPrimary,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                Text(
                    text = "$selectedItemCount khóa học được chọn",
                    color = TextSecondary,
                    fontSize = 12.sp
                )
            }

            Button(
                onClick = onCheckout,
                modifier = Modifier
                    .weight(0.92f)
                    .height(54.dp)
                    .fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Indigo),
                enabled = enabled
            ) {
                Text(
                    text = "Thanh toán",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}


