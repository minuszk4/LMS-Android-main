package com.example.lms2.data.repository

import android.util.Log
import com.example.lms2.BuildConfig
import com.example.lms2.data.model.Cart
import com.example.lms2.data.model.CartItem
import com.example.lms2.data.model.CartStatus
import com.example.lms2.data.model.Course
import com.example.lms2.data.model.Enrollment
import com.example.lms2.data.model.Instructor
import com.example.lms2.data.model.NotificationItem
import com.example.lms2.data.model.NotificationType
import com.example.lms2.data.model.Order
import com.example.lms2.data.model.OrderItem
import com.example.lms2.data.model.PaymentMethod
import com.example.lms2.data.model.PaymentStatus
import com.example.lms2.util.ResultState
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Transaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Repository điều phối checkout và thanh toán khóa học.
 *
 * Luồng tổng quát của file:
 * - app tạo `Order` từ cart hoặc direct buy;
 * - nếu dùng MoMo thì gọi backend function để lấy link thanh toán;
 * - client chỉ poll trạng thái order, còn việc xác nhận thành công và cấp quyền học
 *   phải do backend/webhook quyết định.
 */
class PaymentRepository {

    data class MomoLaunchInfo(
        val openUrl: String,
        val qrCodeUrl: String
    )

    private companion object {
        private const val TAG = "MoMoPayment"
    }

    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val cartsCollection = firestore.collection("carts")
    private val cartItemsCollection = firestore.collection("cartItems")
    private val ordersCollection = firestore.collection("orders")
    private val orderItemsCollection = firestore.collection("orderItems")
    private val enrollmentsCollection = firestore.collection("enrollments")
    private val coursesCollection = firestore.collection("courses")
    private val instructorsCollection = firestore.collection("instructors")
    private val bankTransactionsCollection = firestore.collection("bankTransactions")
    private val notificationRepository = NotificationRepository()
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .callTimeout(45, TimeUnit.SECONDS)
        .build()
    private val momoFunctionBaseUrl = BuildConfig.MOMO_FUNCTION_BASE_URL.trim().trimEnd('/')
    private val adminUid = BuildConfig.ADMIN_UID.trim()

    /**
     * Dữ liệu course đã được resolve đầy đủ trước khi tạo order.
     *
     * Tách riêng cấu trúc này giúp transaction checkout đọc rõ hơn:
     * phần nào là dữ liệu hiển thị cho order item, phần nào là dữ liệu phục vụ
     * cập nhật cart/enrollment ở bước fulfillment.
     */
    private data class ResolvedCheckoutItem(
        val courseId: String,
        val instructorId: String,
        val instructorName: String,
        val courseTitle: String,
        val courseThumbnailUrl: String,
        val coursePrice: Double,
        val courseRefPath: String,
        val enrollmentCount: Long,
        val cartItemPriceToRemove: Double?
    )

    /**
     * Checkout từ các item đang nằm trong giỏ hàng.
     *
     * Nếu `selectedCourseIds` rỗng, hàm hiểu là thanh toán toàn bộ cart hiện tại.
     */
    suspend fun checkoutCart(
        userId: String,
        paymentMethod: PaymentMethod,
        selectedCourseIds: List<String> = emptyList()
    ): ResultState<Order> {
        if (userId.isBlank()) return ResultState.Error("Thiếu thông tin người dùng")
        if (auth.currentUser?.uid.isNullOrBlank()) {
            return ResultState.Error("Phiên đăng nhập đã hết hạn, vui lòng đăng nhập lại")
        }
        if (auth.currentUser?.uid != userId) {
            return ResultState.Error("Người dùng hiện tại không hợp lệ, vui lòng đăng nhập lại")
        }

        return try {
            val snapshot = cartItemsCollection
                .whereEqualTo("userId", userId)
                .get()
                .await()
            val cartItems = snapshot.documents
                .mapNotNull { it.toObject(CartItem::class.java) }

            if (cartItems.isEmpty()) {
                return ResultState.Error("Giỏ hàng đang trống")
            }

            val selectedItems = if (selectedCourseIds.isEmpty()) {
                cartItems
            } else {
                val selectedSet = selectedCourseIds.toSet()
                cartItems.filter { it.courseId in selectedSet }
            }

            if (selectedCourseIds.isNotEmpty()) {
                val requestedSet = selectedCourseIds.toSet()
                val cartCourseIds = cartItems.map { it.courseId }.toSet()
                val missingCourseIds = requestedSet - cartCourseIds
                if (missingCourseIds.isNotEmpty()) {
                    return ResultState.Error("Một số khóa học đã không còn trong giỏ hàng, vui lòng tải lại")
                }
            }

            if (selectedItems.isEmpty()) {
                return ResultState.Error("Không tìm thấy khóa học được chọn trong giỏ hàng")
            }

            val selectedCourseIdSet = selectedItems.map { it.courseId }.toSet()
            if (selectedCourseIdSet.size != selectedItems.size) {
                return ResultState.Error("Danh sách khóa học thanh toán không hợp lệ")
            }

            executeCheckout(
                userId = userId,
                paymentMethod = paymentMethod,
                selectedCourseIds = selectedCourseIdSet.toList(),
                fromCart = true
            )
        } catch (e: IllegalStateException) {
            ResultState.Error(e.message ?: "Thanh toán thất bại")
        } catch (e: Exception) {
            ResultState.Error(e.message ?: "Thanh toán thất bại")
        }
    }

    /**
     * Checkout theo luồng mua trực tiếp, không phụ thuộc course có nằm trong cart hay không.
     */
    suspend fun checkoutCoursesDirect(
        userId: String,
        paymentMethod: PaymentMethod,
        courseIds: List<String>
    ): ResultState<Order> {
        if (userId.isBlank()) return ResultState.Error("Thiếu thông tin người dùng")
        if (auth.currentUser?.uid.isNullOrBlank()) {
            return ResultState.Error("Phiên đăng nhập đã hết hạn, vui lòng đăng nhập lại")
        }
        if (auth.currentUser?.uid != userId) {
            return ResultState.Error("Người dùng hiện tại không hợp lệ, vui lòng đăng nhập lại")
        }

        val normalizedCourseIds = courseIds
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()

        if (normalizedCourseIds.isEmpty()) {
            return ResultState.Error("Không có khóa học để thanh toán")
        }

        return executeCheckout(
            userId = userId,
            paymentMethod = paymentMethod,
            selectedCourseIds = normalizedCourseIds,
            fromCart = false
        )
    }

    /**
     * Hàm lõi tạo order và order items.
     *
     * Transaction giữ mọi kiểm tra và mọi ghi dữ liệu nằm trong cùng một snapshot nhất quán:
     * course hiện tại, enrollment hiện có, cart items và order mới.
     */
    private suspend fun executeCheckout(
        userId: String,
        paymentMethod: PaymentMethod,
        selectedCourseIds: List<String>,
        fromCart: Boolean
    ): ResultState<Order> {
        return try {
            val now = System.currentTimeMillis()
            val orderRef = ordersCollection.document()
            val orderId = orderRef.id
            val cartRef = cartsCollection.document(userId)

            val createdOrder = firestore.runTransaction { transaction ->
                val currentCart = transaction.get(cartRef).toObject(Cart::class.java)
                val resolvedItems = mutableListOf<ResolvedCheckoutItem>()
                var totalAmount = 0.0

                selectedCourseIds.forEach { courseId ->
                    val courseRef = coursesCollection.document(courseId)
                    val courseSnapshot = transaction.get(courseRef)
                    val course = courseSnapshot.toObject(Course::class.java)
                        ?: throw IllegalStateException("Không tìm thấy khóa học, vui lòng tải lại")

                    val enrollmentRef = enrollmentsCollection.document(buildEnrollmentId(userId, courseId))
                    if (transaction.get(enrollmentRef).exists()) {
                        throw IllegalStateException("Có khóa học bạn đã đăng ký, vui lòng tải lại")
                    }

                    val itemData = if (fromCart) {
                        val cartItemRef = cartItemsCollection.document(buildCartItemId(userId, courseId))
                        val cartItem = transaction.get(cartItemRef)
                            .toObject(CartItem::class.java)
                            ?: throw IllegalStateException("Có khóa học không còn trong giỏ hàng")

                        ResolvedCheckoutItem(
                            courseId = courseId,
                            instructorId = course.instructorId,
                            instructorName = course.instructorName,
                            // Luôn tính tiền theo snapshot course mới nhất để tránh giá cũ trong cart.
                            courseTitle = course.title,
                            courseThumbnailUrl = course.thumbnailUrl,
                            coursePrice = course.price,
                            courseRefPath = courseRef.path,
                            enrollmentCount = course.enrollmentCount.toLong(),
                            // Chỉ giữ giá cart cũ để trừ total cart đúng ở bước fulfillment.
                            cartItemPriceToRemove = cartItem.coursePrice
                        )
                    } else {
                        val cartItemRef = cartItemsCollection.document(buildCartItemId(userId, courseId))
                        val cartItem = transaction.get(cartItemRef).toObject(CartItem::class.java)
                        ResolvedCheckoutItem(
                            courseId = courseId,
                            instructorId = course.instructorId,
                            instructorName = course.instructorName,
                            courseTitle = course.title,
                            courseThumbnailUrl = course.thumbnailUrl,
                            coursePrice = course.price,
                            courseRefPath = courseRef.path,
                            enrollmentCount = course.enrollmentCount.toLong(),
                            cartItemPriceToRemove = cartItem?.coursePrice
                        )
                    }

                    totalAmount += itemData.coursePrice
                    resolvedItems.add(itemData)
                }

                val transferContent = "LMS${orderId.take(8).uppercase(Locale.US)}"
                val transferContentNormalized = if (transferContent.isBlank()) {
                    ""
                } else {
                    normalizeTransferContent(transferContent)
                }
                val initialStatus = PaymentStatus.PENDING
                val order = Order(
                    id = orderId,
                    userId = userId,
                    itemCount = resolvedItems.size,
                    paymentMethod = paymentMethod,
                    paymentStatus = initialStatus,
                    totalAmount = totalAmount,
                    createdAt = now,
                    payeeInstructorId = adminUid,
                    bankName = "MoMo",
                    bankCode = "MOMO",
                    bankAccountNumber = "",
                    bankAccountHolder = "Tài khoản quản trị LMS",
                    transferContent = transferContent,
                    transferContentNormalized = transferContentNormalized,
                    qrCodeUrl = "",
                    confirmedAt = 0L
                )
                transaction.set(orderRef, order)

                resolvedItems.forEach { item ->
                    val orderItem = OrderItem(
                        id = buildOrderItemId(orderId, item.courseId),
                        orderId = orderId,
                        userId = userId,
                        courseId = item.courseId,
                        courseTitle = item.courseTitle,
                        courseThumbnailUrl = item.courseThumbnailUrl,
                        coursePrice = item.coursePrice,
                        instructorId = item.instructorId,
                        instructorName = item.instructorName,
                        createdAt = now
                    )
                    transaction.set(orderItemsCollection.document(orderItem.id), orderItem)
                }

                order
            }.await()

            ResultState.Success(createdOrder)
        } catch (e: IllegalStateException) {
            ResultState.Error(e.message ?: "Thanh toán thất bại")
        } catch (e: Exception) {
            ResultState.Error(e.message ?: "Thanh toán thất bại")
        }
    }

    /**
     * Tạo phiên thanh toán MoMo cho một order đã được tạo trước đó.
     *
     * Hàm này chỉ hợp lệ với order `E_WALLET` còn đang `PENDING`.
     */
    suspend fun createMomoPaymentForOrder(orderId: String): ResultState<MomoLaunchInfo> {
        Log.d(TAG, "createMomoPaymentForOrder called orderId=$orderId")
        if (orderId.isBlank()) return ResultState.Error("Thiếu mã đơn hàng")
        if (momoFunctionBaseUrl.isBlank()) {
            Log.e(TAG, "MOMO_FUNCTION_BASE_URL is blank")
            return ResultState.Error("Thiếu cấu hình MOMO_FUNCTION_BASE_URL")
        }

        return try {
            val order = ordersCollection.document(orderId).get().await().toObject(Order::class.java)
                ?: return ResultState.Error("Không tìm thấy đơn hàng")

            Log.d(
                TAG,
                "Loaded order id=${order.id}, method=${order.paymentMethod}, status=${order.paymentStatus}, amount=${order.totalAmount}"
            )

            if (order.paymentMethod != PaymentMethod.E_WALLET) {
                Log.e(TAG, "Order method is not E_WALLET: ${order.paymentMethod}")
                return ResultState.Error("Đơn hàng không dùng ví điện tử")
            }

            if (order.paymentStatus != PaymentStatus.PENDING) {
                Log.e(TAG, "Order status is not PENDING: ${order.paymentStatus}")
                return ResultState.Error("Đơn hàng không còn ở trạng thái chờ thanh toán")
            }

            val payload = JSONObject().apply {
                put("orderId", order.id)
                put("requestType", "captureWallet")
            }

            val idToken = auth.currentUser?.getIdToken(false)?.await()?.token.orEmpty()
            if (idToken.isBlank()) {
                return ResultState.Error("Phiên đăng nhập đã hết hạn, vui lòng đăng nhập lại")
            }

            val request = Request.Builder()
                .url("$momoFunctionBaseUrl/createMomoPayment")
                .addHeader("Authorization", "Bearer $idToken")
                .post(payload.toString().toRequestBody("application/json".toMediaType()))
                .build()

            Log.d(TAG, "POST ${request.url}")
            Log.d(TAG, "payload=$payload")

            val response = executeRequestWithRetry(request)
            response.use { res ->
                val bodyText = res.body?.string().orEmpty()
                val json = runCatching { JSONObject(bodyText.ifBlank { "{}" }) }.getOrElse { JSONObject() }

                Log.d(TAG, "responseCode=${res.code}")
                Log.d(TAG, "responseBody=${bodyText.take(500)}")

                if (!res.isSuccessful || json.optInt("resultCode", -1) != 0) {
                    val backendMessage = json.optString("message", "").trim()
                    val rawMessage = bodyText.take(200).trim()
                    val message = when {
                        backendMessage.isNotBlank() -> backendMessage
                        rawMessage.isNotBlank() -> "MoMo lỗi HTTP ${res.code}: $rawMessage"
                        else -> "MoMo lỗi HTTP ${res.code}"
                    }
                    Log.e(TAG, "createMomoPayment failed: $message")
                    return ResultState.Error(message)
                }

                val payUrl = json.optString("payUrl")
                val deepLink = json.optString("deeplink")
                val backendQrCodeUrl = json.optString("qrCodeUrl")
                val openUrl = when {
                    deepLink.isNotBlank() -> deepLink
                    payUrl.isNotBlank() -> payUrl
                    else -> ""
                }

                if (openUrl.isBlank()) {
                    Log.e(TAG, "MoMo response missing payUrl/deeplink")
                    return ResultState.Error("MoMo không trả về đường dẫn thanh toán")
                }

                val qrCodeUrl = backendQrCodeUrl

                // Ghi lại QR code để người dùng mở lại màn hình vẫn thấy dữ liệu mới nhất.
                runCatching {
                    ordersCollection.document(order.id).update(
                        mapOf(
                            "qrCodeUrl" to qrCodeUrl
                        )
                    ).await()
                }

                Log.d(TAG, "MoMo openUrl=$openUrl")
                return ResultState.Success(MomoLaunchInfo(openUrl = openUrl, qrCodeUrl = qrCodeUrl))
            }
        } catch (e: Exception) {
            Log.e(TAG, "createMomoPayment exception", e)
            ResultState.Error(e.message ?: "Không tạo được thanh toán MoMo")
        }
    }

    /**
     * Gửi HTTP request và retry một lần nếu gặp lỗi tạm thời hoặc lỗi 5xx.
     */
    private suspend fun executeRequestWithRetry(request: Request): Response {
        return withContext(Dispatchers.IO) {
            var lastError: Exception? = null
            repeat(2) { attempt ->
                try {
                    Log.d(TAG, "HTTP attempt=${attempt + 1} url=${request.url}")
                    val response = httpClient.newCall(request).execute()
                    if (response.isSuccessful || response.code !in 500..599 || attempt == 1) {
                        Log.d(TAG, "HTTP attempt=${attempt + 1} finished code=${response.code}")
                        return@withContext response
                    }

                    // Retry once for transient server errors such as Render cold start.
                    Log.w(TAG, "Retrying due to server error code=${response.code}")
                    response.close()
                } catch (e: Exception) {
                    lastError = e
                    Log.e(TAG, "HTTP attempt=${attempt + 1} exception", e)
                    if (attempt == 1) throw e
                }

                delay(1500)
            }

            throw lastError ?: IllegalStateException("Không thể kết nối dịch vụ thanh toán")
        }
    }

    /**
     * Lấy lại trạng thái mới nhất của một order đang chờ.
     *
     * Client không có quyền “xác nhận thành công”; hàm này chỉ poll trạng thái hiện tại
     * để xem backend/webhook đã cập nhật order hay chưa.
     */
    suspend fun tryAutoConfirmPendingOrder(orderId: String): ResultState<Order> {
        if (orderId.isBlank()) return ResultState.Error("Thiếu mã đơn hàng")

        return try {
            val orderRef = ordersCollection.document(orderId)
            val orderSnapshot = orderRef.get().await()
            if (!orderSnapshot.exists()) {
                return ResultState.Error("Không tìm thấy đơn hàng")
            }
            val order = orderSnapshot.toObject(Order::class.java)
                ?: return ResultState.Error("Không đọc được thông tin đơn hàng")

            if (order.paymentStatus != PaymentStatus.PENDING) {
                return ResultState.Success(order)
            }

            // Chỉ server-side webhook mới được finalize thanh toán và cấp quyền học.
            val refreshedOrder = orderRef.get().await().toObject(Order::class.java) ?: order
            ResultState.Success(refreshedOrder)
        } catch (e: IllegalStateException) {
            ResultState.Error(e.message ?: "Xác nhận thanh toán thất bại")
        } catch (e: Exception) {
            ResultState.Error(e.message ?: "Xác nhận thanh toán thất bại")
        }
    }

    /**
     * Hoàn tất quyền lợi sau khi thanh toán đã được xác nhận ở phía server.
     *
     * Hàm này ghi enrollments, tăng `courses.enrollmentCount`, xóa cart items đã thanh toán
     * và cập nhật lại trạng thái cart còn lại.
     */
    private fun applyFulfillment(
        transaction: Transaction,
        userId: String,
        items: List<ResolvedCheckoutItem>,
        currentCart: Cart?,
        now: Long,
        cartRefPath: String
    ) {
        items.forEach { item ->
            val enrollment = Enrollment(
                id = buildEnrollmentId(userId, item.courseId),
                userId = userId,
                courseId = item.courseId,
                enrolledAt = now
            )
            transaction.set(enrollmentsCollection.document(enrollment.id), enrollment)

            val courseRef = firestore.document(item.courseRefPath)
            transaction.update(courseRef, "enrollmentCount", item.enrollmentCount + 1)

            if (item.cartItemPriceToRemove != null) {
                transaction.delete(cartItemsCollection.document(buildCartItemId(userId, item.courseId)))
            }
        }

        val removedCartItemCount = items.count { it.cartItemPriceToRemove != null }
        val removedCartAmount = items.sumOf { it.cartItemPriceToRemove ?: 0.0 }

        if (removedCartItemCount > 0) {
            val remainingItemCount = ((currentCart?.itemCount ?: removedCartItemCount) - removedCartItemCount)
                .coerceAtLeast(0)
            val remainingTotalAmount = ((currentCart?.totalAmount ?: removedCartAmount) - removedCartAmount)
                .coerceAtLeast(0.0)
            val updatedCart = Cart(
                id = userId,
                userId = userId,
                status = if (remainingItemCount == 0) CartStatus.CHECKED_OUT else CartStatus.ACTIVE,
                itemCount = remainingItemCount,
                totalAmount = remainingTotalAmount,
                createdAt = currentCart?.createdAt ?: now,
                updatedAt = now
            )
            transaction.set(firestore.document(cartRefPath), updatedCart)
        }
    }

    /**
     * Chuẩn hóa nội dung chuyển khoản để backend có thể đối chiếu dễ hơn với sao kê.
     */
    private fun normalizeTransferContent(value: String): String {
        return value
            .trim()
            .uppercase(Locale.US)
            .replace("\\s+".toRegex(), "")
    }

    private fun Long.ifBlankIfZero(fallback: Long): Long = if (this == 0L) fallback else this

    // Các ID dạng ghép cho phép truy vấn trực tiếp từng document mà không cần thêm index phức tạp.
    private fun buildOrderItemId(orderId: String, courseId: String): String = "${orderId}_${courseId}"

    private fun buildEnrollmentId(userId: String, courseId: String): String = "${userId}_${courseId}"

    private fun buildCartItemId(userId: String, courseId: String): String = "${userId}_${courseId}"
}
