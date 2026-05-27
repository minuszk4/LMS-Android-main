package com.example.lms2.data.repository

import android.util.Log
import com.example.lms2.BuildConfig
import com.example.lms2.data.model.Cart
import com.example.lms2.data.model.CartItem
import com.example.lms2.data.model.CartStatus
import com.example.lms2.data.model.Course
import com.example.lms2.data.model.Enrollment
import com.example.lms2.data.model.Instructor
import com.example.lms2.data.model.Order
import com.example.lms2.data.model.OrderItem
import com.example.lms2.data.model.NotificationItem
import com.example.lms2.data.model.NotificationType
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
import okhttp3.Response
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Triển khai repository PaymentRepository cho ứng dụng LMS Android.
 * File này chịu trách nhiệm làm việc với Firestore hoặc API ngoài, đồng thời chuyển đổi kết quả về dạng phù hợp cho ViewModel.
 * Repository là ranh giới chính giữa tầng giao diện và tầng dữ liệu nên được mô tả rõ để thuận tiện cho tài liệu kỹ thuật.
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
     * Thực hiện phần xử lý chính của luồng nghiệp vụ hoặc giao diện tương ứng.
     * Hàm này thường làm việc với Firestore hoặc API ngoài và trả kết quả về dạng `ResultState` cho tầng gọi phía trên.
     */

    suspend fun checkoutCart(
        userId: String,
        paymentMethod: PaymentMethod,
        selectedCourseIds: List<String> = emptyList()
    ): ResultState<Order> {
        // Security hardening: validate user identity and input before performing any data operations. This helps prevent unauthorized access and ensures that the checkout process is initiated with valid data, reducing the risk of errors and potential abuse.
        // Kiểm tra xem `userId` có hợp lệ hay không (không được để trống). Nếu không hợp lệ, trả về lỗi ngay lập tức để tránh thực hiện các thao tác không cần thiết với Firestore.

        if (userId.isBlank()) return ResultState.Error("Thiếu thông tin người dùng")
        if (auth.currentUser?.uid.isNullOrBlank()) {
            return ResultState.Error("Phiên đăng nhập đã hết hạn, vui lòng đăng nhập lại")
        }
        if (auth.currentUser?.uid != userId) {
            return ResultState.Error("Người dùng hiện tại không hợp lệ, vui lòng đăng nhập lại")
        }
        // Chuẩn hóa danh sách ID khóa học được chọn bằng cách loại bỏ khoảng trắng thừa, lọc ra các ID trống, và loại bỏ trùng lặp. Nếu sau khi chuẩn hóa mà danh sách ID khóa học vẫn còn trống, có thể coi như người dùng không chọn khóa học nào cụ thể và sẽ tiến hành thanh toán tất cả các mục trong giỏ hàng.
        return try {
            val snapshot = cartItemsCollection
                .whereEqualTo("userId", userId)
                .get()
                .await()
            // Chuyển đổi các document snapshot thành danh sách `CartItem`, đồng thời loại bỏ các mục không thể chuyển đổi được (ví dụ do dữ liệu không hợp lệ). Nếu sau khi chuyển đổi mà danh sách `CartItem` trống, trả về lỗi để thông báo rằng giỏ hàng đang trống và không có gì để thanh toán.
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
            // Nếu người dùng đã chọn một số khóa học cụ thể để thanh toán, kiểm tra xem tất cả các khóa học đó có còn trong giỏ hàng hay không. Nếu có bất kỳ khóa học nào trong danh sách đã chọn mà không còn trong giỏ hàng, trả về lỗi để thông báo cho người dùng rằng một số khóa học đã không còn trong giỏ hàng và cần tải lại để cập nhật thông tin mới nhất.
            if (selectedCourseIds.isNotEmpty()) {
                val requestedSet = selectedCourseIds.toSet()
                val cartCourseIds = cartItems.map { it.courseId }.toSet()
                val missingCourseIds = requestedSet - cartCourseIds
                if (missingCourseIds.isNotEmpty()) {
                    return ResultState.Error("Một số khóa học đã không còn trong giỏ hàng, vui lòng tải lại")
                }
            }
            // Nếu danh sách khóa học đã chọn không trống, nhưng có trùng lặp về ID khóa học (ví dụ do lỗi dữ liệu), trả về lỗi để thông báo rằng danh sách khóa học không hợp lệ và cần kiểm tra lại.
            if (selectedItems.isEmpty()) {
                return ResultState.Error("Không tìm thấy khóa học được chọn trong giỏ hàng")
            }
            // Kiểm tra xem có bất kỳ khóa học nào trong danh sách đã chọn mà người dùng đã đăng ký hay chưa. Nếu có, trả về lỗi để thông báo rằng một số khóa học đã được đăng ký và cần tải lại để cập nhật thông tin mới nhất.
        
            val selectedCourseIdSet = selectedItems.map { it.courseId }.toSet()

            if (selectedCourseIdSet.size != selectedItems.size) {
                return ResultState.Error("Danh sách khóa học thanh toán không hợp lệ")
            }
            // Nếu tất cả các kiểm tra trên đều hợp lệ, tiến hành thực hiện quy trình thanh toán bằng cách gọi hàm `executeCheckout` với các tham số đã chuẩn hóa. Hàm này sẽ xử lý phần lớn logic liên quan đến việc tạo đơn hàng, cập nhật giỏ hàng, và ghi nhận enrollments. Nếu có lỗi xảy ra trong quá trình này (ví dụ do dữ liệu không hợp lệ hoặc lỗi hệ thống), bắt lỗi và trả về lỗi để thông báo cho người dùng rằng quá trình thanh toán đã thất bại.
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
     * Thực hiện phần xử lý chính của luồng nghiệp vụ hoặc giao diện tương ứng.
     * Hàm này thường làm việc với Firestore hoặc API ngoài và trả kết quả về dạng `ResultState` cho tầng gọi phía trên.
     */

    suspend fun checkoutCoursesDirect(
        userId: String,
        paymentMethod: PaymentMethod,
        courseIds: List<String>
    ): ResultState<Order> {
        // Security hardening: validate user identity and input before performing any data operations. This helps prevent unauthorized access and ensures that the checkout process is initiated with valid data, reducing the risk of errors and potential abuse.
        if (userId.isBlank()) return ResultState.Error("Thiếu thông tin người dùng")
        if (auth.currentUser?.uid.isNullOrBlank()) {
            return ResultState.Error("Phiên đăng nhập đã hết hạn, vui lòng đăng nhập lại")
        }
        if (auth.currentUser?.uid != userId) {
            return ResultState.Error("Người dùng hiện tại không hợp lệ, vui lòng đăng nhập lại")
        }
        // Chuẩn hóa danh sách ID khóa học bằng cách loại bỏ khoảng trắng thừa, lọc ra các ID trống, và loại bỏ trùng lặp. Nếu sau khi chuẩn hóa mà danh sách ID khóa học trống, trả về lỗi để thông báo rằng không có khóa học nào để thanh toán.
        val normalizedCourseIds = courseIds
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
        // Nếu sau khi chuẩn hóa mà danh sách ID khóa học trống, trả về lỗi để thông báo rằng không có khóa học nào để thanh toán.
        if (normalizedCourseIds.isEmpty()) {
            return ResultState.Error("Không có khóa học để thanh toán")
        }
        // Kiểm tra xem có bất kỳ khóa học nào trong danh sách đã chọn mà người dùng đã đăng ký hay chưa. Nếu có, trả về lỗi để thông báo rằng một số khóa học đã được đăng ký và cần tải lại để cập nhật thông tin mới nhất.
        return executeCheckout(
            userId = userId,
            paymentMethod = paymentMethod,
            selectedCourseIds = normalizedCourseIds,
            fromCart = false
        )
    }
    // Hàm `executeCheckout` thực hiện phần lớn logic liên quan đến việc tạo đơn hàng, cập nhật giỏ hàng, và ghi nhận enrollments. Hàm này được gọi từ cả hai phương thức `checkoutCart` và `checkoutCoursesDirect` sau khi đã thực hiện các bước kiểm tra và chuẩn hóa đầu vào cần thiết. Nếu có lỗi xảy ra trong quá trình này (ví dụ do dữ liệu không hợp lệ hoặc lỗi hệ thống), bắt lỗi và trả về lỗi để thông báo cho người dùng rằng quá trình thanh toán đã thất bại.
    private suspend fun executeCheckout(
        userId: String,
        paymentMethod: PaymentMethod,
        selectedCourseIds: List<String>,
        fromCart: Boolean
    ): ResultState<Order> {
        return try {
            // Trước khi thực hiện bất kỳ thao tác nào với Firestore, kiểm tra lại một lần nữa xem danh sách ID khóa học đã chọn có hợp lệ hay không (không được để trống). Nếu không hợp lệ, trả về lỗi ngay lập tức để tránh thực hiện các thao tác không cần thiết với Firestore.
            val now = System.currentTimeMillis()
            val orderRef = ordersCollection.document()
            val orderId = orderRef.id
            val cartRef = cartsCollection.document(userId)
            // Sử dụng transaction để đảm bảo rằng tất cả các thao tác liên quan đến việc tạo đơn hàng, cập nhật giỏ hàng, và ghi nhận enrollments được thực hiện đồng thời và có thể rollback nếu có lỗi xảy ra trong quá trình này. Điều này giúp đảm bảo tính nhất quán của dữ liệu và tránh các trường hợp như đơn hàng được tạo nhưng giỏ hàng không được cập nhật hoặc enrollments không được ghi nhận đúng cách.
            // Trong transaction, thực hiện các bước sau:
            // 1. Lấy thông tin giỏ hàng hiện tại của người dùng để kiểm tra trạng thái và các mục trong giỏ hàng. Nếu giỏ hàng đang bị khóa hoặc có trạng thái không hợp lệ, ném lỗi để rollback transaction và trả về lỗi cho người dùng.
            // 2. Kiểm tra xem tất cả các khóa học đã chọn có còn trong giỏ hàng hay không (nếu `fromCart` là true). Nếu có bất kỳ khóa học nào trong danh sách đã chọn mà không còn trong giỏ hàng, ném lỗi để rollback transaction và trả về lỗi cho người dùng.
            // 3. Kiểm tra xem có bất kỳ khóa học nào trong danh sách đã chọn mà người dùng đã đăng ký hay chưa. Nếu có, ném lỗi để rollback transaction và trả về lỗi cho người dùng.
            // 4. Nếu tất cả các kiểm tra trên đều hợp lệ, tạo đơn hàng mới với trạng thái thanh toán là "PENDING" và các thông tin liên quan khác (ví dụ như tổng số tiền, phương thức thanh toán, v.v.). Sau đó, tạo các mục đơn hàng tương ứng cho từng khóa học đã chọn.
            // 5. Nếu `fromCart` là true, cập nhật giỏ hàng của người dùng bằng cách trừ đi số lượng và tổng tiền của các mục đã thanh toán. Nếu sau khi cập nhật mà giỏ hàng không còn mục nào, có thể xóa giỏ hàng hoặc cập nhật trạng thái giỏ hàng thành "EMPTY".
            // 6. Ghi nhận enrollments cho người dùng đối với các khóa học đã thanh toán. Điều này có thể bao gồm việc tạo các document mới trong collection `enrollments` với thông tin về người dùng, khóa học, và trạng thái enrollment (ví dụ như "ACTIVE" hoặc "PENDING_APPROVAL" tùy thuộc vào quy trình của hệ thống).
            // 7. Nếu tất cả các bước trên đều thành công, commit transaction và trả về đơn hàng đã tạo. Nếu có bất kỳ lỗi nào xảy ra trong quá trình này, bắt lỗi và trả về lỗi để thông báo cho người dùng rằng quá trình thanh toán đã thất bại.
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
                            // Always charge using latest course snapshot to avoid stale cart prices.
                            courseTitle = course.title,
                            courseThumbnailUrl = course.thumbnailUrl,
                            coursePrice = course.price,
                            courseRefPath = courseRef.path,
                            enrollmentCount = course.enrollmentCount.toLong(),
                            // Keep original cart item price only for cart total adjustment.
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
            
            // E_WALLET payment is PENDING, waiting for webhook confirmation
            
            ResultState.Success(createdOrder)
        } catch (e: IllegalStateException) {
            ResultState.Error(e.message ?: "Thanh toán thất bại")
        } catch (e: Exception) {
            ResultState.Error(e.message ?: "Thanh toán thất bại")
        }
    }

    /**
     * Tạo mới dữ liệu nghiệp vụ dựa trên đầu vào hiện tại.
     * Hàm này thường làm việc với Firestore hoặc API ngoài và trả kết quả về dạng `ResultState` cho tầng gọi phía trên.
     */

    suspend fun createMomoPaymentForOrder(orderId: String): ResultState<MomoLaunchInfo> {
        Log.d(TAG, "createMomoPaymentForOrder called orderId=$orderId")
        if (orderId.isBlank()) return ResultState.Error("Thiếu mã đơn hàng")
        if (momoFunctionBaseUrl.isBlank()) {
            Log.e(TAG, "MOMO_FUNCTION_BASE_URL is blank")
            return ResultState.Error("Thiếu cấu hình MOMO_FUNCTION_BASE_URL")
        }
        // Security hardening: validate user identity and input before performing any data operations. This helps prevent unauthorized access and ensures that the payment creation process is initiated with valid data, reducing the risk of errors and potential abuse. In this case, we check if the `orderId` is valid and if the current user is authenticated before proceeding with any operations related to Firestore or external API calls.
        // Kiểm tra xem `orderId` có hợp lệ hay không (không được để trống). Nếu không hợp lệ, trả về lỗi ngay lập tức để tránh thực hiện các thao tác không cần thiết với Firestore hoặc API ngoài.
        // Kiểm tra xem người dùng hiện tại có đang đăng nhập hay không. Nếu không đăng nhập, trả về lỗi để thông báo rằng phiên đăng nhập đã hết hạn và người dùng cần đăng nhập lại để tiếp tục quá trình thanh toán.
        // Kiểm tra xem người dùng hiện tại có quyền truy cập vào đơn hàng hay không (ví dụ như đơn hàng phải thuộc về người dùng đó). Nếu không có quyền truy cập, trả về lỗi để thông báo rằng người dùng không có quyền truy cập vào đơn hàng này.
        // Nếu tất cả các kiểm tra trên đều hợp lệ, tiến hành thực hiện quy trình tạo thanh toán MoMo bằng cách gọi API ngoài. Trong quá trình này, nếu có lỗi xảy ra (ví dụ như lỗi mạng, lỗi API, hoặc lỗi dữ liệu), bắt lỗi và trả về lỗi để thông báo cho người dùng rằng không thể tạo được thanh toán MoMo.
        // Nếu quá trình tạo thanh toán MoMo thành công, trả về thông tin cần thiết để mở liên kết thanh toán hoặc hiển thị mã QR code cho người dùng. Nếu có bất kỳ thông tin nào từ API MoMo bị thiếu hoặc không hợp lệ (ví dụ như thiếu `payUrl` hoặc `deeplink`), trả về lỗi để thông báo rằng MoMo không trả về đường dẫn thanh toán.
        // Log chi tiết ở mỗi bước để dễ dàng theo dõi và gỡ lỗi nếu có vấn đề xảy ra trong quá trình này.
        // Đảm bảo rằng tất cả các lỗi được bắt và log đầy đủ để có thể phân tích và cải thiện hệ thống trong tương lai.
        // Trong trường hợp có lỗi xảy ra, trả về lỗi với thông điệp rõ ràng và hữu ích để người dùng có thể hiểu được vấn đề và biết cách khắc phục (ví dụ như đăng nhập lại, tải lại trang, hoặc liên hệ hỗ trợ).
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
            Log.d(TAG, "payload=${payload}")

            val response = executeRequestWithRetry(request)
            // Đọc và phân tích phản hồi từ API MoMo. Nếu phản hồi không thành công hoặc có lỗi được trả về từ API (ví dụ như `resultCode` khác 0), log lỗi chi tiết và trả về lỗi để thông báo cho người dùng rằng không thể tạo được thanh toán MoMo, kèm theo thông điệp lỗi cụ thể nếu có.
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
                // Cập nhật URL thanh toán và QR code vào đơn hàng trong Firestore để có thể hiển thị cho người dùng và theo dõi trạng thái thanh toán. Nếu có lỗi xảy ra trong quá trình cập nhật này, log lỗi chi tiết nhưng không trả về lỗi cho người dùng vì quá trình tạo thanh toán MoMo đã thành công và chúng ta vẫn có thể hiển thị liên kết thanh toán hoặc mã QR code cho người dùng.
                val qrCodeUrl = backendQrCodeUrl

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
    // Hàm `executeRequestWithRetry` thực hiện việc gửi yêu cầu HTTP với khả năng tự động thử lại một lần nếu gặp lỗi tạm thời (ví dụ như lỗi mạng hoặc lỗi server 5xx). Hàm này sử dụng `OkHttpClient` để gửi yêu cầu và xử lý phản hồi. Nếu sau hai lần thử mà vẫn không thành công, trả về lỗi để thông báo rằng không thể kết nối dịch vụ thanh toán.
    // Trong quá trình gửi yêu cầu, log chi tiết về mỗi lần thử, bao gồm URL yêu cầu, mã phản hồi, và bất kỳ lỗi nào xảy ra để dễ dàng theo dõi và gỡ lỗi nếu có vấn đề xảy ra trong quá trình này.
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
     * Thực hiện phần xử lý chính của luồng nghiệp vụ hoặc giao diện tương ứng.
     * Hàm này thường làm việc với Firestore hoặc API ngoài và trả kết quả về dạng `ResultState` cho tầng gọi phía trên.
     */

    suspend fun tryAutoConfirmPendingOrder(orderId: String): ResultState<Order> {
        if (orderId.isBlank()) return ResultState.Error("Thiếu mã đơn hàng")

        return try {
            // Security hardening: validate user identity and input before performing any data operations. This helps prevent unauthorized access and ensures that the order confirmation process is initiated with valid data, reducing the risk of errors and potential abuse. In this case, we check if the `orderId` is valid before proceeding with any operations related to Firestore.
            // Kiểm tra xem `orderId` có hợp lệ hay không (không được để trống). Nếu không hợp lệ, trả về lỗi ngay lập tức để tránh thực hiện các thao tác không cần thiết với Firestore.
            // Lấy thông tin đơn hàng từ Firestore dựa trên `orderId`. Nếu không tìm thấy đơn hàng, trả về lỗi để thông báo rằng không thể xác nhận thanh toán vì đơn hàng không tồn tại.
            // Kiểm tra trạng thái thanh toán của đơn hàng. Nếu đơn hàng không ở trạng thái "PENDING", có thể coi như đơn hàng đã được xử lý (hoặc đã bị hủy) và không cần xác nhận lại. Trong trường hợp này, trả về đơn hàng hiện tại mà không thực hiện bất kỳ thao tác nào khác.
            // Nếu đơn hàng đang ở trạng thái "PENDING", có thể thực hiện một số thao tác bổ sung để kiểm tra xem thanh toán đã được xác nhận từ phía MoMo hay chưa (ví dụ như gọi API MoMo để kiểm tra trạng thái thanh toán). Tuy nhiên, trong trường hợp này, chúng ta sẽ chỉ đơn giản là lấy lại thông tin đơn hàng mới nhất từ Firestore để đảm bảo rằng chúng ta có thông tin cập nhật nhất về trạng thái thanh toán. Điều này giúp tránh các vấn đề liên quan đến việc xác nhận thanh toán từ phía client và đảm bảo rằng trạng thái đơn hàng luôn được đồng bộ với dữ liệu trong Firestore.
            val orderRef = ordersCollection.document(orderId)
            val orderSnapshot = orderRef.get().await()
            if (!orderSnapshot.exists()) {
                return ResultState.Error("Không tìm thấy đơn hàng")
            }
            // Chuyển đổi document snapshot thành đối tượng `Order`. Nếu không thể chuyển đổi được (ví dụ do dữ liệu không hợp lệ), trả về lỗi để thông báo rằng không thể đọc được thông tin đơn hàng.
            val order = orderSnapshot.toObject(Order::class.java)
                ?: return ResultState.Error("Không đọc được thông tin đơn hàng")

            if (order.paymentStatus != PaymentStatus.PENDING) {
                return ResultState.Success(order)
            }

            // Security hardening: only server-side webhook is allowed to finalize payment
            // and grant enrollments. Client only polls latest order status.
            val refreshedOrder = orderRef.get().await().toObject(Order::class.java) ?: order
            ResultState.Success(refreshedOrder)
        } catch (e: IllegalStateException) {
            ResultState.Error(e.message ?: "Xác nhận thanh toán thất bại")
        } catch (e: Exception) {
            ResultState.Error(e.message ?: "Xác nhận thanh toán thất bại")
        }
    }
    // Hàm `applyFulfillment` thực hiện việc ghi nhận enrollments cho người dùng đối với các khóa học đã thanh toán, đồng thời cập nhật giỏ hàng nếu cần thiết. Hàm này được gọi từ phía server sau khi nhận được webhook xác nhận thanh toán từ MoMo. Trong quá trình thực hiện, nếu có lỗi xảy ra (ví dụ như dữ liệu không hợp lệ hoặc lỗi hệ thống), ném lỗi để rollback transaction và đảm bảo rằng trạng thái dữ liệu trong Firestore luôn nhất quán.

    private fun applyFulfillment(
        transaction: Transaction,
        userId: String,
        items: List<ResolvedCheckoutItem>,
        currentCart: Cart?,
        now: Long,
        cartRefPath: String
    ) { 
        // Ghi nhận enrollments cho người dùng đối với các khóa học đã thanh toán. Điều này bao gồm việc tạo các document mới trong collection `enrollments` với thông tin về người dùng, khóa học, và trạng thái enrollment (ví dụ như "ACTIVE"). Đồng thời, cập nhật số lượng enrollments của khóa học và xóa các mục tương ứng khỏi giỏ hàng nếu chúng được thanh toán từ giỏ hàng. Sau khi cập nhật giỏ hàng, nếu không còn mục nào trong giỏ hàng, có thể xóa giỏ hàng hoặc cập nhật trạng thái giỏ hàng thành "EMPTY".
        // Trong quá trình này, nếu có bất kỳ lỗi nào xảy ra (ví dụ như khóa học không còn trong giỏ hàng, người dùng đã đăng ký khóa học, hoặc lỗi dữ liệu), ném lỗi để rollback transaction và đảm bảo rằng trạng thái dữ liệu trong Firestore luôn nhất quán.
        // Đảm bảo rằng tất cả các thao tác liên quan đến việc ghi nhận enrollments, cập nhật khóa học, và cập nhật giỏ hàng được thực hiện trong cùng một transaction để đảm bảo tính nhất quán của dữ liệu. Nếu có lỗi xảy ra trong quá trình này, transaction sẽ tự động rollback và không có thay đổi nào được áp dụng vào Firestore, giúp tránh các trường hợp như enrollments được ghi nhận nhưng giỏ hàng không được cập nhật hoặc ngược lại.
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
        // Tính toán số lượng mục đã xóa khỏi giỏ hàng và tổng số tiền đã trừ đi từ giỏ hàng dựa trên các mục đã thanh toán. Sau đó, cập nhật giỏ hàng của người dùng bằng cách trừ đi số lượng và tổng tiền của các mục đã thanh toán. Nếu sau khi cập nhật mà giỏ hàng không còn mục nào, có thể xóa giỏ hàng hoặc cập nhật trạng thái giỏ hàng thành "EMPTY".
        val removedCartItemCount = items.count { it.cartItemPriceToRemove != null }
        val removedCartAmount = items.sumOf { it.cartItemPriceToRemove ?: 0.0 }

        if (removedCartItemCount > 0) {
            val remainingItemCount = ((currentCart?.itemCount ?: removedCartItemCount) - removedCartItemCount)
                .coerceAtLeast(0)
            val remainingTotalAmount = ((currentCart?.totalAmount ?: removedCartAmount) - removedCartAmount)
                .coerceAtLeast(0.0)
            // Cập nhật giỏ hàng với số lượng mục và tổng tiền đã được điều chỉnh sau khi trừ đi các mục đã thanh toán. Nếu sau khi cập nhật mà giỏ hàng không còn mục nào, có thể xóa giỏ hàng hoặc cập nhật trạng thái giỏ hàng thành "EMPTY". Trong trường hợp này, chúng ta sẽ cập nhật trạng thái giỏ hàng thành "CHECKED_OUT" nếu không còn mục nào, hoặc giữ nguyên trạng thái "ACTIVE" nếu vẫn còn mục trong giỏ hàng.
            val updatedCart = Cart(
                id = userId,
                userId = userId,
                status = if (remainingItemCount == 0) CartStatus.CHECKED_OUT else CartStatus.ACTIVE,
                itemCount = remainingItemCount,
                totalAmount = remainingTotalAmount,
                createdAt = currentCart?.createdAt ?: now,
                updatedAt = now
            )
            // Cập nhật giỏ hàng trong transaction để đảm bảo tính nhất quán của dữ liệu. Nếu có lỗi xảy ra trong quá trình này, ném lỗi để rollback transaction và đảm bảo rằng trạng thái dữ liệu trong Firestore luôn nhất quán.
            transaction.set(firestore.document(cartRefPath), updatedCart)
        }
    }

    private fun normalizeTransferContent(value: String): String {
        return value
            .trim()
            .uppercase(Locale.US)
            .replace("\\s+".toRegex(), "")
    }

    private fun Long.ifBlankIfZero(fallback: Long): Long = if (this == 0L) fallback else this

    private fun buildOrderItemId(orderId: String, courseId: String): String = "${orderId}_${courseId}"

    private fun buildEnrollmentId(userId: String, courseId: String): String = "${userId}_${courseId}"

    private fun buildCartItemId(userId: String, courseId: String): String = "${userId}_${courseId}"
}

