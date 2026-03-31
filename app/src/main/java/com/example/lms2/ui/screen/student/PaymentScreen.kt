package com.example.lms2.ui.screen.student

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.RadioButtonUnchecked
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.lms2.data.model.CartItem
import com.example.lms2.data.model.Order
import com.example.lms2.data.model.PaymentMethod
import com.example.lms2.data.model.PaymentStatus
import com.example.lms2.ui.component.TopBar
import com.example.lms2.util.CheckoutSource
import com.example.lms2.util.PaymentEvent
import com.example.lms2.util.PaymentUiState
import com.example.lms2.util.formatPrice
import com.example.lms2.viewmodel.PaymentViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

private val Indigo = Color(0xFF4B5CC4)
private val SurfaceGray = Color(0xFFF5F6FA)
private val CardWhite = Color(0xFFFFFFFF)
private val TextPrimary = Color(0xFF1A1D2E)
private val TextSecondary = Color(0xFF6B7280)
private val BorderColor = Color(0xFFE2E4ED)

@Composable
fun PaymentRoute(
    userId: String,
    selectedCourseIds: List<String>,
    checkoutSource: CheckoutSource,
    viewModel: PaymentViewModel,
    onBackClick: () -> Unit,
    onCheckoutSuccess: (orderId: String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val uriHandler = LocalUriHandler.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(userId, selectedCourseIds, checkoutSource) {
        viewModel.initCheckout(userId, selectedCourseIds, checkoutSource)
    }

    LaunchedEffect(viewModel) {
        viewModel.event.collectLatest { event ->
            when (event) {
                is PaymentEvent.ShowError -> snackbarHostState.showSnackbar(event.message)
                is PaymentEvent.CheckoutSuccess -> onCheckoutSuccess(event.orderId)
                is PaymentEvent.AwaitingTransferConfirmation -> {
                    snackbarHostState.showSnackbar("Đã tạo QR chuyển khoản. Hệ thống đang tự động xác nhận giao dịch")
                }
                is PaymentEvent.OpenExternalPayment -> Unit
            }
        }
    }

    PaymentScreen(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onBackClick = onBackClick,
        onSelectPaymentMethod = viewModel::selectPaymentMethod,
        onSubmitCheckout = { viewModel.submitCheckout(userId) },
        onCheckPendingPayment = viewModel::checkPendingPaymentNow,
        onOpenExternalPayment = {
            val url = uiState.externalPaymentUrl
            if (url.isBlank()) {
                scope.launch { snackbarHostState.showSnackbar("Không tìm thấy liên kết thanh toán") }
            } else {
                runCatching { uriHandler.openUri(url) }
                    .onFailure { scope.launch { snackbarHostState.showSnackbar("Không mở được ứng dụng thanh toán. Vui lòng thử lại") } }
            }
        }
    )
}

@Composable
fun PaymentScreen(
    uiState: PaymentUiState,
    snackbarHostState: SnackbarHostState,
    onBackClick: () -> Unit,
    onSelectPaymentMethod: (PaymentMethod) -> Unit,
    onSubmitCheckout: () -> Unit,
    onCheckPendingPayment: () -> Unit,
    onOpenExternalPayment: () -> Unit
) {
    Scaffold(
        topBar = {
            TopBar(
                title = "Thanh toán",
                onBackClick = onBackClick
            )
        },
        containerColor = SurfaceGray,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        bottomBar = {
            if (uiState.selectedItems.isNotEmpty() && uiState.pendingOrder == null) {
                PaymentBottomBar(
                    totalAmount = uiState.totalAmount,
                    isSubmitting = uiState.isSubmitting,
                    onCheckout = onSubmitCheckout,
                    checkoutLabel = "Thanh toán qua MoMo"
                )
            }
        }
    ) { paddingValues ->
        when {
            uiState.isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Indigo)
                }
            }

            uiState.selectedItems.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Không có khóa học hợp lệ để thanh toán",
                        color = TextSecondary,
                        fontSize = 15.sp
                    )
                }
            }

            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Text(
                            text = "${uiState.itemCount} khóa học được chọn",
                            color = TextSecondary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    items(uiState.selectedItems, key = { it.id }) { item ->
                        PaymentItemCard(item = item)
                    }

                    item {
                        PaymentMethodSection(
                            selectedMethod = uiState.paymentMethod,
                            onSelectPaymentMethod = onSelectPaymentMethod
                        )
                    }

                    item {
                        PaymentDetailsSection(
                            selectedMethod = uiState.paymentMethod,
                            totalAmount = uiState.totalAmount,
                            pendingOrder = uiState.pendingOrder
                        )
                    }

                    if (uiState.pendingOrder?.paymentStatus == PaymentStatus.PENDING) {
                        item {
                            PendingTransferSection(
                                order = uiState.pendingOrder ?: return@item,
                                isCheckingPaymentStatus = uiState.isCheckingPaymentStatus,
                                onCheckPendingPayment = onCheckPendingPayment,
                                externalPaymentUrl = uiState.externalPaymentUrl,
                                onOpenExternalPayment = onOpenExternalPayment
                            )
                        }
                    }

                    item {
                        Spacer(modifier = Modifier.height(86.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun PaymentItemCard(item: CartItem) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, BorderColor, RoundedCornerShape(14.dp)),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = CardWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = item.courseThumbnail,
                contentDescription = item.courseTitle,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(84.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFFE8ECF6))
            )

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = item.courseTitle,
                    color = TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = formatPrice(item.coursePrice),
                    color = Indigo,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun PaymentMethodSection(
    selectedMethod: PaymentMethod,
    onSelectPaymentMethod: (PaymentMethod) -> Unit
) {
    data class PaymentMethodItem(
        val method: PaymentMethod,
        val title: String,
        val subtitle: String,
        val icon: androidx.compose.ui.graphics.vector.ImageVector
    )

    val methods = listOf(
        PaymentMethodItem(
            method = PaymentMethod.E_WALLET,
            title = "Ví điện tử",
            subtitle = "Momo, ZaloPay, ShopeePay",
            icon = Icons.Default.AccountBalanceWallet
        )
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = CardWhite)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Phương thức thanh toán",
                color = TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )

            methods.forEach { methodItem ->
                val isSelected = selectedMethod == methodItem.method
                Surface(
                    onClick = { onSelectPaymentMethod(methodItem.method) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = if (isSelected) Color(0xFFE9EDFF) else Color(0xFFF8FAFC),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (isSelected) Indigo.copy(alpha = 0.5f) else BorderColor
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 11.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = methodItem.icon,
                            contentDescription = null,
                            tint = if (isSelected) Indigo else Color(0xFF64748B),
                            modifier = Modifier.size(20.dp)
                        )

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = methodItem.title,
                                color = TextPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = methodItem.subtitle,
                                color = TextSecondary,
                                fontSize = 13.sp
                            )
                        }

                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = Indigo,
                                modifier = Modifier.size(18.dp)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.RadioButtonUnchecked,
                                contentDescription = null,
                                tint = Color(0xFFB8C2D1),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PaymentDetailsSection(
    selectedMethod: PaymentMethod,
    totalAmount: Double,
    pendingOrder: Order?
) {
    val detailRows = listOf(
        "Đơn vị hỗ trợ" to "Momo / ZaloPay / ShopeePay",
        "Phí giao dịch" to "Miễn phí",
        "Thời gian xác nhận" to "Ngay lập tức"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = CardWhite)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Chi tiết thanh toán",
                color = TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )

            detailRows.forEach { (label, value) ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        text = label,
                        color = TextSecondary,
                        fontSize = 13.sp
                    )
                    Text(
                        text = value,
                        color = TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            HorizontalDivider(thickness = 1.dp, color = BorderColor)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Tổng cần thanh toán",
                    color = TextSecondary,
                    fontSize = 13.sp
                )
                Text(
                    text = formatPrice(totalAmount),
                    color = Indigo,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun PendingTransferSection(
    order: Order,
    isCheckingPaymentStatus: Boolean,
    onCheckPendingPayment: () -> Unit,
    externalPaymentUrl: String,
    onOpenExternalPayment: () -> Unit
) {
    val qrDisplayUrl = remember(order.qrCodeUrl) {
        val raw = order.qrCodeUrl.trim()
        if (raw.isBlank()) {
            ""
        } else if (raw.startsWith("http://") || raw.startsWith("https://")) {
            raw
        } else {
            "https://api.qrserver.com/v1/create-qr-code/?size=700x700&data=${Uri.encode(raw)}"
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = CardWhite)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.HourglassTop,
                    contentDescription = null,
                    tint = Indigo
                )
                Text(
                    text = if (order.paymentMethod == PaymentMethod.E_WALLET) {
                        "Đang chờ xác nhận thanh toán MoMo"
                    } else {
                        "Đang chờ xác nhận chuyển khoản"
                    },
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }

            if (qrDisplayUrl.isNotBlank()) {
                AsyncImage(
                    model = qrDisplayUrl,
                    contentDescription = "QR thanh toán",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(230.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFFF8FAFC))
                )
            }

            Text(
                text = if (order.paymentMethod == PaymentMethod.E_WALLET) {
                    "Mã đối soát: ${order.transferContent}"
                } else {
                    "Nội dung chuyển khoản: ${order.transferContent}"
                },
                color = TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = if (order.paymentMethod == PaymentMethod.E_WALLET) {
                    "Sau khi bạn thanh toán trong ứng dụng MoMo, hệ thống sẽ tự xác nhận giao dịch."
                } else {
                    "Hệ thống sẽ tự xác nhận khi nhận đúng giao dịch. Nếu đã chuyển khoản, bạn có thể bấm kiểm tra ngay."
                },
                color = TextSecondary,
                fontSize = 12.sp
            )

            if (order.paymentMethod == PaymentMethod.E_WALLET && externalPaymentUrl.isNotBlank()) {
                Button(
                    onClick = onOpenExternalPayment,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0EA5E9)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(text = "Mở ứng dụng MoMo", color = Color.White)
                }
            }

            Button(
                onClick = onCheckPendingPayment,
                enabled = !isCheckingPaymentStatus,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Indigo),
                shape = RoundedCornerShape(12.dp)
            ) {
                if (isCheckingPaymentStatus) {
                    CircularProgressIndicator(
                        color = Color.White,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(16.dp)
                    )
                } else {
                    Text(text = "Kiểm tra trạng thái thanh toán", color = Color.White)
                }
            }
        }
    }
}

@Composable
private fun PaymentBottomBar(
    totalAmount: Double,
    isSubmitting: Boolean,
    onCheckout: () -> Unit,
    checkoutLabel: String
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
                    text = "Tổng thanh toán",
                    color = TextSecondary,
                    fontSize = 14.sp
                )
                Text(
                    text = formatPrice(totalAmount),
                    color = TextPrimary,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
            }

            Button(
                onClick = onCheckout,
                modifier = Modifier
                    .weight(0.92f)
                    .height(54.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Indigo),
                enabled = !isSubmitting
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(
                        color = Color.White,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(18.dp)
                    )
                } else {
                    Text(
                        text = checkoutLabel,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

