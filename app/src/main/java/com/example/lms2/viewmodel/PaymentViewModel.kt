package com.example.lms2.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.lms2.data.model.CartItem
import com.example.lms2.data.model.PaymentMethod
import com.example.lms2.data.repository.CartRepository
import com.example.lms2.data.repository.CourseRepository
import com.example.lms2.data.repository.PaymentRepository
import com.example.lms2.util.CheckoutSource
import com.example.lms2.util.PaymentEvent
import com.example.lms2.util.PaymentUiState
import com.example.lms2.util.ResultState
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class PaymentViewModel(
    private val cartRepository: CartRepository = CartRepository(),
    private val courseRepository: CourseRepository = CourseRepository(),
    private val paymentRepository: PaymentRepository = PaymentRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(PaymentUiState())
    val uiState: StateFlow<PaymentUiState> = _uiState.asStateFlow()

    private val _event = MutableSharedFlow<PaymentEvent>()
    val event = _event.asSharedFlow()
    private var pollingJob: Job? = null

    fun initCheckout(
        userId: String,
        selectedCourseIds: List<String>,
        source: CheckoutSource
    ) {
        if (userId.isBlank()) return

        val normalizedIds = selectedCourseIds.map { it.trim() }.filter { it.isNotBlank() }.distinct()
        if (normalizedIds.isEmpty()) {
            viewModelScope.launch {
                _event.emit(PaymentEvent.ShowError("Không có khóa học để thanh toán"))
            }
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    checkoutSource = source,
                    selectedCourseIds = normalizedIds,
                    externalPaymentUrl = "",
                    pendingOrder = null,
                    isCheckingPaymentStatus = false
                )
            }

            val loadResult = when (source) {
                CheckoutSource.CART -> loadItemsFromCart(userId, normalizedIds)
                CheckoutSource.DIRECT -> loadItemsDirect(userId, normalizedIds)
            }

            when (loadResult) {
                is ResultState.Success -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            selectedItems = loadResult.data
                        )
                    }
                }

                is ResultState.Error -> {
                    _uiState.update { it.copy(isLoading = false, selectedItems = emptyList()) }
                    _event.emit(PaymentEvent.ShowError(loadResult.message))
                }

                else -> _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    private suspend fun loadItemsFromCart(
        userId: String,
        selectedCourseIds: List<String>
    ): ResultState<List<CartItem>> {
        return when (val result = cartRepository.getCartItems(userId)) {
            is ResultState.Success -> {
                val selectedSet = selectedCourseIds.toSet()
                val selectedItems = result.data.filter { it.courseId in selectedSet }
                if (selectedItems.size != selectedSet.size) {
                    ResultState.Error("Một số khóa học đã không còn trong giỏ hàng, vui lòng quay lại")
                } else {
                    try {
                        val latestCoursesById = coroutineScope {
                            selectedItems.map { item ->
                                async {
                                    when (val courseResult = courseRepository.getCourseById(item.courseId)) {
                                        is ResultState.Success -> courseResult.data
                                        is ResultState.Error -> throw IllegalStateException(courseResult.message)
                                        else -> throw IllegalStateException("Không tải được thông tin khóa học")
                                    }
                                }
                            }.awaitAll().associateBy { it.id }
                        }

                        // Override cart snapshot values with latest course values.
                        val normalizedItems = selectedItems.map { item ->
                            val latestCourse = latestCoursesById[item.courseId]
                            if (latestCourse != null) {
                                item.copy(
                                    courseTitle = latestCourse.title,
                                    coursePrice = latestCourse.price,
                                    courseThumbnail = latestCourse.thumbnailUrl
                                )
                            } else {
                                item
                            }
                        }

                        ResultState.Success(normalizedItems)
                    } catch (e: IllegalStateException) {
                        ResultState.Error(e.message ?: "Tải dữ liệu thanh toán thất bại")
                    } catch (e: Exception) {
                        ResultState.Error(e.message ?: "Tải dữ liệu thanh toán thất bại")
                    }
                }
            }

            is ResultState.Error -> ResultState.Error(result.message)
            else -> ResultState.Error("Tải dữ liệu thanh toán thất bại")
        }
    }

    private suspend fun loadItemsDirect(
        userId: String,
        selectedCourseIds: List<String>
    ): ResultState<List<CartItem>> {
        return try {
            val items = coroutineScope {
                selectedCourseIds.map { courseId ->
                    async {
                        when (val courseResult = courseRepository.getCourseById(courseId)) {
                            is ResultState.Success -> {
                                val course = courseResult.data
                                CartItem(
                                    id = "${userId}_${course.id}",
                                    cartId = "",
                                    userId = userId,
                                    courseId = course.id,
                                    courseThumbnail = course.thumbnailUrl,
                                    courseTitle = course.title,
                                    coursePrice = course.price,
                                    addedAt = System.currentTimeMillis()
                                )
                            }

                            is ResultState.Error -> throw IllegalStateException(courseResult.message)
                            else -> throw IllegalStateException("Không tải được thông tin khóa học")
                        }
                    }
                }.awaitAll()
            }

            ResultState.Success(items)
        } catch (e: IllegalStateException) {
            ResultState.Error(e.message ?: "Tải dữ liệu thanh toán thất bại")
        } catch (e: Exception) {
            ResultState.Error(e.message ?: "Tải dữ liệu thanh toán thất bại")
        }
    }

    fun selectPaymentMethod(method: PaymentMethod) {
        _uiState.update { it.copy(paymentMethod = method) }
    }

    fun submitCheckout(userId: String) {
        if (userId.isBlank()) return
        val state = _uiState.value
        if (state.isSubmitting) return
        if (state.selectedCourseIds.isEmpty()) {
            viewModelScope.launch {
                _event.emit(PaymentEvent.ShowError("Không có khóa học để thanh toán"))
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true) }

            val result = when (state.checkoutSource) {
                CheckoutSource.CART -> paymentRepository.checkoutCart(
                    userId = userId,
                    paymentMethod = state.paymentMethod,
                    selectedCourseIds = state.selectedCourseIds
                )

                CheckoutSource.DIRECT -> paymentRepository.checkoutCoursesDirect(
                    userId = userId,
                    paymentMethod = state.paymentMethod,
                    courseIds = state.selectedCourseIds
                )
            }

            when (result) {
                is ResultState.Success -> {
                    val order = result.data
                    _uiState.update { it.copy(isSubmitting = false) }

                    if (order.paymentStatus == com.example.lms2.data.model.PaymentStatus.SUCCESS) {
                        _event.emit(PaymentEvent.CheckoutSuccess(order.id))
                    } else {
                        if (order.paymentMethod == PaymentMethod.E_WALLET) {
                            when (val momoResult = paymentRepository.createMomoPaymentForOrder(order.id)) {
                                is ResultState.Success -> {
                                    _uiState.update {
                                        it.copy(
                                            externalPaymentUrl = momoResult.data.openUrl,
                                            pendingOrder = order.copy(qrCodeUrl = momoResult.data.qrCodeUrl),
                                            isCheckingPaymentStatus = true
                                        )
                                    }
                                    _event.emit(PaymentEvent.ShowError("Đã tạo liên kết MoMo. Bấm 'Mở ứng dụng MoMo' để tiếp tục."))
                                    startPollingPaymentStatus(order.id)
                                }
                                is ResultState.Error -> {
                                    _uiState.update {
                                        it.copy(
                                            externalPaymentUrl = "",
                                            pendingOrder = null,
                                            isCheckingPaymentStatus = false
                                        )
                                    }
                                    _event.emit(PaymentEvent.ShowError(momoResult.message))
                                }
                                else -> Unit
                            }
                        } else {
                            _uiState.update {
                                it.copy(
                                    pendingOrder = order,
                                    isCheckingPaymentStatus = true
                                )
                            }
                            _event.emit(PaymentEvent.AwaitingTransferConfirmation(order.id))
                            startPollingPaymentStatus(order.id)
                        }
                    }
                }

                is ResultState.Error -> {
                    _uiState.update { it.copy(isSubmitting = false) }
                    _event.emit(PaymentEvent.ShowError(result.message))
                }

                else -> {
                    _uiState.update { it.copy(isSubmitting = false) }
                }
            }
        }
    }

    fun checkPendingPaymentNow() {
        val pendingOrderId = _uiState.value.pendingOrder?.id.orEmpty()
        if (pendingOrderId.isBlank()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isCheckingPaymentStatus = true) }

            when (val result = paymentRepository.tryAutoConfirmPendingOrder(pendingOrderId)) {
                is ResultState.Success -> {
                    val order = result.data
                    if (order.paymentStatus == com.example.lms2.data.model.PaymentStatus.SUCCESS) {
                        _uiState.update {
                            it.copy(
                                externalPaymentUrl = "",
                                isCheckingPaymentStatus = false,
                                pendingOrder = null
                            )
                        }
                        _event.emit(PaymentEvent.CheckoutSuccess(order.id))
                    } else {
                        _uiState.update { it.copy(isCheckingPaymentStatus = false, pendingOrder = order) }
                    }
                }

                is ResultState.Error -> {
                    _uiState.update { it.copy(isCheckingPaymentStatus = false) }
                    _event.emit(PaymentEvent.ShowError(result.message))
                }

                else -> {
                    _uiState.update { it.copy(isCheckingPaymentStatus = false) }
                }
            }
        }
    }

    private fun startPollingPaymentStatus(orderId: String) {
        pollingJob?.cancel()
        pollingJob = viewModelScope.launch {
            repeat(24) {
                if (!isActive) return@launch
                delay(5000)

                when (val result = paymentRepository.tryAutoConfirmPendingOrder(orderId)) {
                    is ResultState.Success -> {
                        val order = result.data
                        if (order.paymentStatus == com.example.lms2.data.model.PaymentStatus.SUCCESS) {
                            _uiState.update {
                                it.copy(
                                    externalPaymentUrl = "",
                                    pendingOrder = null,
                                    isCheckingPaymentStatus = false
                                )
                            }
                            _event.emit(PaymentEvent.CheckoutSuccess(order.id))
                            return@launch
                        }
                        _uiState.update { it.copy(pendingOrder = order, isCheckingPaymentStatus = true) }
                    }

                    is ResultState.Error -> {
                        _uiState.update { it.copy(isCheckingPaymentStatus = false) }
                        return@launch
                    }

                    else -> Unit
                }
            }

            _uiState.update { it.copy(isCheckingPaymentStatus = false) }
        }
    }

    override fun onCleared() {
        pollingJob?.cancel()
        super.onCleared()
    }
}

