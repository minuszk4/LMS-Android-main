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

/**
 * Điều phối trạng thái giao diện trong PaymentViewModel.
 * File này kết nối màn hình Compose với repository, cập nhật `uiState` và phát event một lần cho các thao tác điều hướng hoặc thông báo.
 * Đây là nơi tập trung phần lớn logic trình bày và điều phối nghiệp vụ ở phía ứng dụng Android.
 */

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

    /**
     * Thực hiện phần xử lý chính của luồng nghiệp vụ hoặc giao diện tương ứng.
     * Hàm này chủ yếu cập nhật `uiState`, gọi repository và phát event cho giao diện khi cần.
     */

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
        // Cập nhật `uiState` để hiển thị trạng thái đang tải và lưu lại nguồn checkout cùng với danh sách ID khóa học đã được chuẩn hóa. Điều này giúp giao diện có thể hiển thị thông tin phù hợp dựa trên nguồn checkout (ví dụ: nếu đến từ giỏ hàng thì có thể hiển thị thông tin giỏ hàng, nếu đến từ trang khóa học thì có thể hiển thị thông tin khóa học trực tiếp) và cũng đảm bảo rằng danh sách ID khóa học đã được làm sạch và chuẩn hóa trước khi sử dụng cho các thao tác tiếp theo.
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
            // Dựa trên kết quả tải dữ liệu, cập nhật `uiState` để hiển thị thông tin khóa học đã chọn hoặc hiển thị lỗi nếu có vấn đề xảy ra trong quá trình tải. Nếu tải thành công, `selectedItems` sẽ được cập nhật với danh sách các mục đã chọn để hiển thị ở giao diện thanh toán. Nếu có lỗi, `selectedItems` sẽ được đặt lại thành danh sách trống và phát một event để hiển thị thông báo lỗi cho người dùng.
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
                        // Tải thông tin khóa học mới nhất cho tất cả các mục đã chọn để đảm bảo rằng người dùng sẽ thanh toán đúng số tiền dựa trên thông tin khóa học mới nhất, đồng thời tránh các vấn đề liên quan đến dữ liệu lỗi thời trong giỏ hàng.
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

    /**
     * Thực hiện phần xử lý chính của luồng nghiệp vụ hoặc giao diện tương ứng.
     * Hàm này chủ yếu cập nhật `uiState`, gọi repository và phát event cho giao diện khi cần.
     */

    fun selectPaymentMethod(method: PaymentMethod) {
        _uiState.update { it.copy(paymentMethod = method) }
    }

    /**
     * Gửi dữ liệu biểu mẫu hoặc yêu cầu nghiệp vụ để hệ thống tiếp nhận.
     * Hàm này chủ yếu cập nhật `uiState`, gọi repository và phát event cho giao diện khi cần.
     */

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
            // Dựa trên kết quả checkout, cập nhật `uiState` để phản ánh trạng thái thanh toán hiện tại và phát event tương ứng để giao diện có thể điều hướng hoặc hiển thị thông báo cho người dùng. Nếu checkout thành công và phương thức thanh toán là ví điện tử, sẽ tiếp tục tạo liên kết thanh toán MoMo và bắt đầu quá trình polling để kiểm tra trạng thái thanh toán. Nếu có lỗi xảy ra trong quá trình checkout, sẽ cập nhật `uiState` để kết thúc trạng thái đang gửi và phát event để hiển thị lỗi cho người dùng.
            // Quan trọng là phải xử lý tất cả các trường hợp kết quả (thành công, lỗi, và các trạng thái khác) để đảm bảo rằng giao diện luôn phản ánh đúng trạng thái của quá trình thanh toán và cung cấp trải nghiệm người dùng mượt mà và rõ ràng.
            // Ngoài ra, cần đảm bảo rằng tất cả các thao tác cập nhật `uiState` và phát event đều được thực hiện trong phạm vi của `viewModelScope` để đảm bảo rằng chúng sẽ được hủy bỏ đúng cách khi ViewModel bị hủy, tránh các vấn đề liên quan đến memory leak hoặc cập nhật giao diện sau khi ViewModel đã bị hủy.
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

    /**
     * Thực hiện phần xử lý chính của luồng nghiệp vụ hoặc giao diện tương ứng.
     * Hàm này chủ yếu cập nhật `uiState`, gọi repository và phát event cho giao diện khi cần.
     * Hàm này có thể được gọi khi người dùng muốn kiểm tra trạng thái thanh toán của đơn hàng đang chờ xử lý, đặc biệt là trong trường hợp thanh toán qua chuyển khoản ngân hàng hoặc ví điện tử, nơi mà quá trình thanh toán có thể mất một khoảng thời gian trước khi được xác nhận. Khi gọi hàm này, sẽ kiểm tra trạng thái thanh toán hiện tại của đơn hàng và cập nhật `uiState` cũng như phát event tương ứng để giao diện có thể phản ánh đúng trạng thái thanh toán cho người dùng.
     */

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
    /*
        * Thực hiện phần xử lý chính của luồng nghiệp vụ hoặc giao diện tương ứng.
        * Hàm này chủ yếu cập nhật `uiState`, gọi repository và phát event cho giao diện khi cần.
        * Hàm này được gọi để bắt đầu quá trình polling kiểm tra trạng thái thanh toán
        * của đơn hàng đang chờ xử lý. Quá trình này sẽ lặp lại trong một khoảng thời gian nhất định (ví dụ: 2 phút) với khoảng delay giữa các lần kiểm tra (ví dụ: 5 giây) để liên tục cập nhật trạng thái thanh toán cho người dùng mà không cần họ phải tự tay bấm nút kiểm tra. Nếu trong quá trình polling phát hiện rằng đơn hàng đã được thanh toán thành công, sẽ cập nhật `uiState` và phát event để thông báo cho giao diện về kết quả này. Nếu có lỗi xảy ra hoặc sau khi hết thời gian polling mà đơn hàng vẫn chưa được thanh toán, sẽ cập nhật `uiState` để kết thúc trạng thái đang kiểm tra và có thể phát event để thông báo cho người dùng nếu cần.
        * Quan trọng là phải đảm bảo rằng quá trình polling được hủy bỏ đúng cách khi ViewModel bị hủy để tránh các vấn đề liên quan đến memory leak hoặc cập nhật giao diện sau khi ViewModel đã bị hủy.
     */
    private fun startPollingPaymentStatus(orderId: String) {
        pollingJob?.cancel()
        pollingJob = viewModelScope.launch {
            repeat(24) {
                if (!isActive) return@launch
                delay(5000)
                // Tự động kiểm tra trạng thái thanh toán của đơn hàng đang chờ xử lý mà không cần người dùng phải bấm nút kiểm tra. Điều này giúp cải thiện trải nghiệm người dùng bằng cách cung cấp thông tin cập nhật về trạng thái thanh toán một cách liên tục và tự động.
                // Dựa trên kết quả kiểm tra trạng thái thanh toán, cập nhật `uiState` và phát event tương ứng để giao diện có thể phản ánh đúng trạng thái thanh toán cho người dùng. Nếu đơn hàng đã được thanh toán thành công, sẽ cập nhật `uiState` để kết thúc trạng thái đang kiểm tra và phát event để thông báo cho giao diện về kết quả này. Nếu có lỗi xảy ra trong quá trình kiểm tra, sẽ cập nhật `uiState` để kết thúc trạng thái đang kiểm tra và có thể phát event để thông báo cho người dùng nếu cần.
                // Quan trọng là phải xử lý tất cả các trường hợp kết quả (thành công, lỗi, và các trạng thái khác) để đảm bảo rằng giao diện luôn phản ánh đúng trạng thái của quá trình thanh toán và cung cấp trải nghiệm người dùng mượt mà và rõ ràng.

                when (val result = paymentRepository.tryAutoConfirmPendingOrder(orderId)) {
                    is ResultState.Success -> {
                        val order = result.data
                        // Nếu đơn hàng đã được thanh toán thành công, cập nhật `uiState` để kết thúc trạng thái đang kiểm tra và phát event để thông báo cho giao diện về kết quả này. Nếu đơn hàng vẫn chưa được thanh toán, chỉ cần cập nhật `uiState` với thông tin đơn hàng mới nhất mà không cần phát event nào cả, vì người dùng
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

    /**
     * Xử lý một sự kiện giao diện và cập nhật state hoặc event liên quan.
     * Hàm này chủ yếu cập nhật `uiState`, gọi repository và phát event cho giao diện khi cần.
     */

    override fun onCleared() {
        pollingJob?.cancel()
        super.onCleared()
    }
}

