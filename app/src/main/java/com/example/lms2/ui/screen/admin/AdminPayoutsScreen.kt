package com.example.lms2.ui.screen.admin

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Paid
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.lms2.data.model.InstructorPayout
import com.example.lms2.data.model.PayoutStatus
import com.example.lms2.ui.component.SearchBar
import com.example.lms2.viewmodel.AdminPayoutEvent
import com.example.lms2.viewmodel.AdminPayoutViewModel
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Màn hình hoặc thành phần AdminPayoutsScreen phục vụ nghiệp vụ quản trị hệ thống.
 * File này hiển thị dữ liệu quản trị, trạng thái duyệt hoặc các thao tác vận hành do admin thực hiện.
 * Mô tả rõ vai trò màn hình giúp việc viết tài liệu kỹ thuật và phân tích use case dễ dàng hơn.
 */

private val PayoutBg = Color(0xFFF8FAFC)
private val PayoutCard = Color.White
private val PayoutBorder = Color(0xFFE2E8F0)
private val PayoutPrimary = Color(0xFF4B5CC4)
private val PayoutTextPrimary = Color(0xFF1E293B)
private val PayoutTextSecondary = Color(0xFF64748B)

private enum class PayoutFilter {
    ALL,
    PENDING,
    PAID
}

private data class MonthlyPayoutSummary(
    val key: String,
    val label: String,
    val pendingCount: Int,
    val pendingAmount: Double,
    val paidCount: Int,
    val paidAmount: Double
)

private data class InstructorMonthlyPayoutGroup(
    val key: String,
    val monthKey: String,
    val monthLabel: String,
    val instructorId: String,
    val instructorName: String,
    val bankName: String,
    val bankCode: String,
    val bankAccountNumber: String,
    val bankAccountHolder: String,
    val hasBankInfo: Boolean,
    val items: List<InstructorPayout>,
    val pendingItems: List<InstructorPayout>,
    val paidItems: List<InstructorPayout>
) {
    val pendingAmount: Double
        get() = pendingItems.sumOf { it.payoutAmount }

    val paidAmount: Double
        get() = paidItems.sumOf { it.payoutAmount }

    val totalAmount: Double
        get() = items.sumOf { it.payoutAmount }
}

@Composable
fun AdminPayoutsRoute(
    adminUid: String,
    viewModel: AdminPayoutViewModel,
    onBackClick: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var searchQuery by rememberSaveable { mutableStateOf("") }
    var selectedFilter by rememberSaveable { mutableStateOf(PayoutFilter.PENDING) }
    var selectedMonthKey by rememberSaveable { mutableStateOf("") }
    var selectedGroupKey by rememberSaveable { mutableStateOf("") }
    var transferReference by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(Unit) {
        viewModel.loadPayouts()
    }

    LaunchedEffect(Unit) {
        viewModel.event.collect { event ->
            when (event) {
                is AdminPayoutEvent.ShowError -> snackbarHostState.showSnackbar(event.message)
                is AdminPayoutEvent.ShowSuccess -> {
                    selectedGroupKey = ""
                    transferReference = ""
                    snackbarHostState.showSnackbar(event.message)
                }
            }
        }
    }

    val monthlySummaries = remember(uiState.payouts) {
        buildMonthlySummaries(uiState.payouts)
    }

    LaunchedEffect(monthlySummaries) {
        val validKeys = buildList {
            add(ALL_MONTHS_KEY)
            addAll(monthlySummaries.map { it.key })
        }
        if (selectedMonthKey !in validKeys || selectedMonthKey.isBlank()) {
            selectedMonthKey = monthlySummaries.firstOrNull()?.key ?: ALL_MONTHS_KEY
        }
    }

    val visibleMonthPayouts = remember(uiState.payouts, selectedMonthKey) {
        uiState.payouts.filter { payout ->
            selectedMonthKey == ALL_MONTHS_KEY || monthKeyFromTimestamp(payout.orderConfirmedAt) == selectedMonthKey
        }
    }

    val groupedPayouts = remember(visibleMonthPayouts, searchQuery) {
        buildInstructorGroups(visibleMonthPayouts, searchQuery)
    }

    val visibleGroups = remember(groupedPayouts, selectedFilter) {
        groupedPayouts.filter { group ->
            when (selectedFilter) {
                PayoutFilter.ALL -> group.items.isNotEmpty()
                PayoutFilter.PENDING -> group.pendingItems.isNotEmpty()
                PayoutFilter.PAID -> group.paidItems.isNotEmpty()
            }
        }
    }

    val selectedSummary = remember(selectedMonthKey, monthlySummaries, uiState.payouts) {
        if (selectedMonthKey == ALL_MONTHS_KEY) {
            MonthlyPayoutSummary(
                key = ALL_MONTHS_KEY,
                label = "Tat ca",
                pendingCount = uiState.payouts.count { it.payoutStatus == PayoutStatus.PENDING },
                pendingAmount = uiState.payouts.filter { it.payoutStatus == PayoutStatus.PENDING }.sumOf { it.payoutAmount },
                paidCount = uiState.payouts.count { it.payoutStatus == PayoutStatus.PAID },
                paidAmount = uiState.payouts.filter { it.payoutStatus == PayoutStatus.PAID }.sumOf { it.payoutAmount }
            )
        } else {
            monthlySummaries.firstOrNull { it.key == selectedMonthKey }
        }
    }

    val selectedGroup = visibleGroups.firstOrNull { it.key == selectedGroupKey }

    LaunchedEffect(selectedGroupKey, visibleGroups) {
        if (selectedGroupKey.isNotBlank() && visibleGroups.none { it.key == selectedGroupKey }) {
            selectedGroupKey = ""
            transferReference = ""
        }
    }

    AdminPayoutsScreen(
        isLoading = uiState.isLoading,
        isProcessing = uiState.isProcessing,
        searchQuery = searchQuery,
        onSearchQueryChange = { searchQuery = it },
        selectedFilter = selectedFilter,
        onFilterSelected = { selectedFilter = it },
        selectedMonthKey = selectedMonthKey,
        onMonthSelected = { selectedMonthKey = it },
        monthlySummaries = monthlySummaries,
        selectedSummary = selectedSummary,
        groups = visibleGroups,
        snackbarHostState = snackbarHostState,
        onRefresh = viewModel::loadPayouts,
        onBackClick = onBackClick,
        onOpenGroup = { group ->
            selectedGroupKey = group.key
            transferReference = group.paidItems.firstOrNull()?.manualTransferReference.orEmpty()
        }
    )

    selectedGroup?.let { group ->
        InstructorPayoutDetailDialog(
            group = group,
            selectedFilter = selectedFilter,
            transferReference = transferReference,
            isSubmitting = uiState.isProcessing,
            onTransferReferenceChange = { transferReference = it },
            onDismiss = {
                if (!uiState.isProcessing) {
                    selectedGroupKey = ""
                    transferReference = ""
                }
            },
            onMarkPaid = {
                viewModel.markPayoutGroupAsPaid(
                    payoutIds = group.pendingItems.map { it.id },
                    adminUid = adminUid,
                    manualTransferReference = transferReference
                )
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AdminPayoutsScreen(
    isLoading: Boolean,
    isProcessing: Boolean,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    selectedFilter: PayoutFilter,
    onFilterSelected: (PayoutFilter) -> Unit,
    selectedMonthKey: String,
    onMonthSelected: (String) -> Unit,
    monthlySummaries: List<MonthlyPayoutSummary>,
    selectedSummary: MonthlyPayoutSummary?,
    groups: List<InstructorMonthlyPayoutGroup>,
    snackbarHostState: SnackbarHostState,
    onRefresh: () -> Unit,
    onBackClick: () -> Unit,
    onOpenGroup: (InstructorMonthlyPayoutGroup) -> Unit
) {
    val currencyFormatter = remember { NumberFormat.getCurrencyInstance(Locale("vi", "VN")) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "Chuyen tien giang vien",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = PayoutTextPrimary
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Quay lai",
                            tint = PayoutPrimary
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onRefresh, enabled = !isLoading && !isProcessing) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Tai lai",
                            tint = PayoutTextPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.White)
            )
        },
        containerColor = PayoutBg,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { paddingValues ->
        if (isLoading && groups.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = PayoutPrimary)
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = PayoutCard),
                    border = BorderStroke(1.dp, PayoutBorder)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Tong hop payout theo giang vien tung thang",
                            color = PayoutPrimary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        )
                        Text(
                            text = "Man hinh mac dinh chi hien ten giang vien va tong tien can thanh toan theo thang. Bam vao tung dong de xem chi tiet cac don.",
                            color = PayoutTextSecondary,
                            fontSize = 13.sp,
                            lineHeight = 18.sp
                        )
                    }
                }
            }

            item {
                SearchBar(
                    query = searchQuery,
                    onQueryChange = onSearchQueryChange,
                    placeholder = "Tim theo giang vien, khoa hoc hoac ma don"
                )
            }

            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        FilterChip(
                            selected = selectedMonthKey == ALL_MONTHS_KEY,
                            onClick = { onMonthSelected(ALL_MONTHS_KEY) },
                            label = { Text("Tat ca") }
                        )
                    }
                    items(monthlySummaries, key = { it.key }) { summary ->
                        FilterChip(
                            selected = selectedMonthKey == summary.key,
                            onClick = { onMonthSelected(summary.key) },
                            label = { Text(summary.label) }
                        )
                    }
                }
            }

            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(PayoutFilter.entries) { filter ->
                        FilterChip(
                            selected = selectedFilter == filter,
                            onClick = { onFilterSelected(filter) },
                            label = {
                                Text(
                                    when (filter) {
                                        PayoutFilter.ALL -> "Tat ca"
                                        PayoutFilter.PENDING -> "Cho chuyen"
                                        PayoutFilter.PAID -> "Da chuyen"
                                    }
                                )
                            }
                        )
                    }
                }
            }

            selectedSummary?.let { summary ->
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        SummaryCard(
                            modifier = Modifier.weight(1f),
                            title = "Giang vien cho chuyen",
                            value = groups.count { it.pendingItems.isNotEmpty() }.toString(),
                            subtitle = currencyFormatter.format(summary.pendingAmount),
                            accent = Color(0xFFF59E0B)
                        )
                        SummaryCard(
                            modifier = Modifier.weight(1f),
                            title = "Giang vien da chuyen",
                            value = groups.count { it.paidItems.isNotEmpty() }.toString(),
                            subtitle = currencyFormatter.format(summary.paidAmount),
                            accent = Color(0xFF10B981)
                        )
                    }
                }
            }

            if (groups.isEmpty()) {
                item {
                    EmptyPayoutState()
                }
            } else {
                items(groups, key = { it.key }) { group ->
                    InstructorPayoutGroupCard(
                        group = group,
                        selectedFilter = selectedFilter,
                        currencyFormatter = currencyFormatter,
                        onClick = { onOpenGroup(group) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SummaryCard(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    subtitle: String,
    accent: Color
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = PayoutCard),
        border = BorderStroke(1.dp, PayoutBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(title, color = PayoutTextSecondary, fontSize = 12.sp)
            Text(value, color = PayoutTextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, color = accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun InstructorPayoutGroupCard(
    group: InstructorMonthlyPayoutGroup,
    selectedFilter: PayoutFilter,
    currencyFormatter: NumberFormat,
    onClick: () -> Unit
) {
    val displayAmount = when (selectedFilter) {
        PayoutFilter.ALL -> group.totalAmount
        PayoutFilter.PENDING -> group.pendingAmount
        PayoutFilter.PAID -> group.paidAmount
    }
    val displayLabel = when (selectedFilter) {
        PayoutFilter.ALL -> "Tong payout"
        PayoutFilter.PENDING -> "Can thanh toan"
        PayoutFilter.PAID -> "Da thanh toan"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = PayoutCard),
        border = BorderStroke(1.dp, PayoutBorder),
        onClick = onClick
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = group.instructorName.ifBlank { "Giang vien" },
                        color = PayoutTextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = group.monthLabel,
                        color = PayoutTextSecondary,
                        fontSize = 12.sp
                    )
                }

                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "Xem chi tiet",
                    tint = PayoutTextSecondary
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(displayLabel, color = PayoutTextSecondary, fontSize = 13.sp)
                Text(
                    text = currencyFormatter.format(displayAmount),
                    color = PayoutPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${group.items.size} don trong thang",
                    color = PayoutTextSecondary,
                    fontSize = 12.sp
                )
                Text(
                    text = "${group.pendingItems.size} cho • ${group.paidItems.size} da chuyen",
                    color = PayoutTextSecondary,
                    fontSize = 12.sp
                )
            }

            DetailLine(
                label = "STK",
                value = if (group.bankAccountNumber.isNotBlank()) group.bankAccountNumber else "Chua cap nhat"
            )
            DetailLine(
                label = "Chu tai khoan",
                value = if (group.bankAccountHolder.isNotBlank()) group.bankAccountHolder else "Chua cap nhat"
            )

            if (!group.hasBankInfo) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFFFFFBEB))
                        .border(1.dp, Color(0xFFFDE68A), RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.WarningAmber,
                        contentDescription = null,
                        tint = Color(0xFFD97706),
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Giang vien chua cap nhat du thong tin ngan hang.",
                        color = Color(0xFF92400E),
                        fontSize = 12.sp
                    )
                }
            }

            Text(
                text = "Bam de xem chi tiet cac don",
                color = PayoutPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun InstructorPayoutDetailDialog(
    group: InstructorMonthlyPayoutGroup,
    selectedFilter: PayoutFilter,
    transferReference: String,
    isSubmitting: Boolean,
    onTransferReferenceChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onMarkPaid: () -> Unit
) {
    val currencyFormatter = remember { NumberFormat.getCurrencyInstance(Locale("vi", "VN")) }
    val visibleItems = when (selectedFilter) {
        PayoutFilter.ALL -> group.items
        PayoutFilter.PENDING -> group.pendingItems
        PayoutFilter.PAID -> group.paidItems
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            color = Color.White
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = group.instructorName.ifBlank { "Giang vien" },
                    color = PayoutTextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                )
                Text(
                    text = "Chi tiet don payout thang ${group.monthLabel}",
                    color = PayoutTextSecondary,
                    fontSize = 13.sp
                )

                SummaryPanel(
                    title = "Tong can thanh toan",
                    value = currencyFormatter.format(group.pendingAmount),
                    subtitle = "${group.pendingItems.size} don cho chuyen"
                )
                SummaryPanel(
                    title = "Tong da thanh toan",
                    value = currencyFormatter.format(group.paidAmount),
                    subtitle = "${group.paidItems.size} don da chuyen"
                )

                DetailLine(
                    label = "Ngan hang",
                    value = buildBankLine(group.bankName, group.bankCode)
                )
                DetailLine(
                    label = "So tai khoan",
                    value = if (group.bankAccountNumber.isNotBlank()) group.bankAccountNumber else "Chua cap nhat"
                )
                DetailLine(
                    label = "Chu tai khoan",
                    value = if (group.bankAccountHolder.isNotBlank()) group.bankAccountHolder else "Chua cap nhat"
                )

                Text(
                    text = "Danh sach don",
                    color = PayoutTextPrimary,
                    fontWeight = FontWeight.Bold
                )

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(visibleItems, key = { it.id }) { payout ->
                        PayoutOrderRow(
                            payout = payout,
                            currencyFormatter = currencyFormatter
                        )
                    }
                }

                if (group.pendingItems.isNotEmpty()) {
                    OutlinedTextField(
                        value = transferReference,
                        onValueChange = onTransferReferenceChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Ma giao dich / ghi chu") },
                        placeholder = { Text("Vi du: VCB 20/05 09:30") },
                        enabled = !isSubmitting,
                        maxLines = 3
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    TextButton(
                        onClick = onDismiss,
                        enabled = !isSubmitting,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Dong", color = PayoutTextSecondary)
                    }

                    if (group.pendingItems.isNotEmpty()) {
                        Button(
                            onClick = onMarkPaid,
                            enabled = !isSubmitting,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = PayoutPrimary)
                        ) {
                            if (isSubmitting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Paid,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.size(8.dp))
                                Text("Da chuyen")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryPanel(
    title: String,
    value: String,
    subtitle: String
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
        border = BorderStroke(1.dp, PayoutBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(title, color = PayoutTextSecondary, fontSize = 12.sp)
            Text(value, color = PayoutTextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text(subtitle, color = PayoutTextSecondary, fontSize = 12.sp)
        }
    }
}

@Composable
private fun PayoutOrderRow(
    payout: InstructorPayout,
    currencyFormatter: NumberFormat
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
        border = BorderStroke(1.dp, PayoutBorder)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = payout.courseTitle,
                        color = PayoutTextPrimary,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Hoc vien: ${payout.studentName.ifBlank { payout.studentId }}",
                        color = PayoutTextSecondary,
                        fontSize = 12.sp
                    )
                }
                PayoutStatusChip(status = payout.payoutStatus)
            }

            DetailLine(label = "Ma don", value = payout.orderId)
            DetailLine(label = "So tien", value = currencyFormatter.format(payout.payoutAmount))
            if (payout.payoutStatus == PayoutStatus.PAID) {
                DetailLine(label = "Da chuyen luc", value = formatDateTime(payout.paidAt))
                if (payout.manualTransferReference.isNotBlank()) {
                    DetailLine(label = "Ghi chu", value = payout.manualTransferReference)
                }
            }
        }
    }
}

@Composable
private fun PayoutStatusChip(status: PayoutStatus) {
    val background = if (status == PayoutStatus.PAID) Color(0xFFDCFCE7) else Color(0xFFFEF3C7)
    val textColor = if (status == PayoutStatus.PAID) Color(0xFF166534) else Color(0xFF92400E)
    val label = if (status == PayoutStatus.PAID) "Da chuyen" else "Cho chuyen"

    Surface(
        color = background,
        shape = RoundedCornerShape(999.dp)
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            color = textColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(label, color = PayoutTextSecondary, fontSize = 12.sp)
        Text(
            text = value.ifBlank { "-" },
            color = PayoutTextPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun EmptyPayoutState() {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = PayoutCard),
        border = BorderStroke(1.dp, PayoutBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Chua co giang vien phu hop",
                color = PayoutTextPrimary,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Thu doi thang hoac bo loc trang thai de xem them du lieu.",
                color = PayoutTextSecondary,
                fontSize = 13.sp
            )
        }
    }
}

private fun buildMonthlySummaries(payouts: List<InstructorPayout>): List<MonthlyPayoutSummary> {
    return payouts
        .filter { it.orderConfirmedAt > 0L }
        .groupBy { monthKeyFromTimestamp(it.orderConfirmedAt) }
        .mapNotNull { (monthKey, items) ->
            if (monthKey.isBlank()) return@mapNotNull null
            MonthlyPayoutSummary(
                key = monthKey,
                label = monthLabelFromMonthKey(monthKey),
                pendingCount = items.count { it.payoutStatus == PayoutStatus.PENDING },
                pendingAmount = items.filter { it.payoutStatus == PayoutStatus.PENDING }.sumOf { it.payoutAmount },
                paidCount = items.count { it.payoutStatus == PayoutStatus.PAID },
                paidAmount = items.filter { it.payoutStatus == PayoutStatus.PAID }.sumOf { it.payoutAmount }
            )
        }
        .sortedByDescending { it.key }
}

private fun buildInstructorGroups(
    payouts: List<InstructorPayout>,
    searchQuery: String
): List<InstructorMonthlyPayoutGroup> {
    val keyword = searchQuery.trim()

    return payouts
        .groupBy { payout ->
            val monthKey = monthKeyFromTimestamp(payout.orderConfirmedAt)
            val instructorKey = payout.instructorId.ifBlank { payout.instructorName.ifBlank { "unknown" } }
            "$monthKey::$instructorKey"
        }
        .mapNotNull { (_, items) ->
            val first = items.firstOrNull() ?: return@mapNotNull null
            val matchesKeyword = keyword.isBlank() ||
                first.instructorName.contains(keyword, ignoreCase = true) ||
                items.any {
                    it.courseTitle.contains(keyword, ignoreCase = true) ||
                        it.orderId.contains(keyword, ignoreCase = true) ||
                        it.studentName.contains(keyword, ignoreCase = true)
                }

            if (!matchesKeyword) return@mapNotNull null

            InstructorMonthlyPayoutGroup(
                key = "${monthKeyFromTimestamp(first.orderConfirmedAt)}::${first.instructorId.ifBlank { first.instructorName }}",
                monthKey = monthKeyFromTimestamp(first.orderConfirmedAt),
                monthLabel = monthLabelFromTimestamp(first.orderConfirmedAt),
                instructorId = first.instructorId,
                instructorName = first.instructorName,
                bankName = items.firstNotBlankOfOrNull { it.bankName }.orEmpty(),
                bankCode = items.firstNotBlankOfOrNull { it.bankCode }.orEmpty(),
                bankAccountNumber = items.firstNotBlankOfOrNull { it.bankAccountNumber }.orEmpty(),
                bankAccountHolder = items.firstNotBlankOfOrNull { it.bankAccountHolder }.orEmpty(),
                hasBankInfo = items.any { it.hasBankInfo },
                items = items.sortedByDescending { it.orderConfirmedAt },
                pendingItems = items.filter { it.payoutStatus == PayoutStatus.PENDING }.sortedByDescending { it.orderConfirmedAt },
                paidItems = items.filter { it.payoutStatus == PayoutStatus.PAID }.sortedByDescending { it.orderConfirmedAt }
            )
        }
        .sortedWith(
            compareByDescending<InstructorMonthlyPayoutGroup> { it.pendingAmount }
                .thenByDescending { it.paidAmount }
                .thenBy { it.instructorName.lowercase() }
        )
}

private fun monthKeyFromTimestamp(timestamp: Long): String {
    if (timestamp <= 0L) return ""
    return SimpleDateFormat("yyyy-MM", Locale.US).format(Date(timestamp))
}

private fun monthLabelFromTimestamp(timestamp: Long): String {
    if (timestamp <= 0L) return "-"
    return SimpleDateFormat("MM/yyyy", Locale("vi", "VN")).format(Date(timestamp))
}

private fun monthLabelFromMonthKey(monthKey: String): String {
    return runCatching {
        val parsed = SimpleDateFormat("yyyy-MM", Locale.US).parse(monthKey) ?: return monthKey
        SimpleDateFormat("MM/yyyy", Locale("vi", "VN")).format(parsed)
    }.getOrDefault(monthKey)
}

private fun formatDateTime(timestamp: Long): String {
    if (timestamp <= 0L) return "-"
    return SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("vi", "VN")).format(Date(timestamp))
}

private fun buildBankLine(bankName: String, bankCode: String): String {
    val pieces = listOf(bankName, bankCode).filter { it.isNotBlank() }
    return if (pieces.isEmpty()) "Chua cap nhat" else pieces.joinToString(" • ")
}

private inline fun <T> List<T>.firstNotBlankOfOrNull(selector: (T) -> String): String? {
    return firstNotNullOfOrNull { selector(it).trim().ifBlank { null } }
}

private const val ALL_MONTHS_KEY = "ALL"
