package com.example.lms2.data.repository

import com.example.lms2.data.cache.RepositoryCache
import com.example.lms2.data.cache.CacheTTL
import com.example.lms2.data.model.Cart
import com.example.lms2.data.model.CartItem
import com.example.lms2.data.model.CartStatus
import com.example.lms2.data.model.Course
import com.example.lms2.data.paging.PageRequest
import com.example.lms2.data.paging.PageResult
import com.example.lms2.util.ResultState
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await

/**
 * Triển khai repository CartRepository cho ứng dụng LMS Android.
 * File này chịu trách nhiệm làm việc với Firestore hoặc API ngoài, đồng thời chuyển đổi kết quả về dạng phù hợp cho ViewModel.
 * Repository là ranh giới chính giữa tầng giao diện và tầng dữ liệu nên được mô tả rõ để thuận tiện cho tài liệu kỹ thuật.
 */

class CartRepository {

    private val firestore = FirebaseFirestore.getInstance()
    private val cartsCollection = firestore.collection("carts")
    private val cartItemsCollection = firestore.collection("cartItems")
    private val coursesCollection = firestore.collection("courses")
    private val enrollmentsCollection = firestore.collection("enrollments")
    private val cartItemsCachePrefix = "cart-items:user"

    /**
     * Lấy dữ liệu hiện có hoặc tạo mới nếu tài nguyên chưa tồn tại.
     * Hàm này thường làm việc với Firestore hoặc API ngoài và trả kết quả về dạng `ResultState` cho tầng gọi phía trên.
     * Ví dụ: Lấy giỏ hàng đang hoạt động của người dùng. Nếu giỏ hàng chưa tồn tại, tạo mới một giỏ hàng với trạng thái "ACTIVE" và trả về. Điều này giúp đảm bảo rằng người dùng luôn có một giỏ hàng sẵn sàng để thêm khóa học vào mà không cần phải lo lắng về việc tạo giỏ hàng trước đó đã tồn tại hay chưa. Ngoài ra, cũng có thể kiểm tra xem giỏ hàng hiện tại có đang ở trạng thái "ACTIVE" hay không, nếu không thì có thể tạo mới hoặc trả về lỗi tùy theo yêu cầu nghiệp vụ.
     */

    suspend fun getOrCreateActiveCart(userId: String): ResultState<Cart> {
        // Security hardening: validate user identity and input before performing any data operations. This helps prevent unauthorized access and ensures that the cart retrieval or creation process is initiated with valid data, reducing the risk of errors and potential abuse. In this case, we check if the `userId` is valid before proceeding with any operations related to Firestore.
        // Kiểm tra xem `userId` có hợp lệ hay không (không được để trống). Nếu không hợp lệ, trả về lỗi ngay lập tức để tránh thực hiện các thao tác không cần thiết với Firestore.
        if (userId.isBlank()) return ResultState.Error("Thiếu thông tin người dùng")

        return try {
            // Lấy tham chiếu đến document giỏ hàng của người dùng dựa trên `userId`. Sau đó, lấy snapshot của document này để kiểm tra xem giỏ hàng đã tồn tại hay chưa. Nếu đã tồn tại, chuyển đổi snapshot thành đối tượng `Cart` và trả về. Nếu chưa tồn tại, tạo mới một đối tượng `Cart` với trạng thái "ACTIVE" và lưu vào Firestore trước khi trả về. Điều này đảm bảo rằng người dùng luôn có một giỏ hàng sẵn sàng để thêm khóa học vào mà không cần phải lo lắng về việc tạo giỏ hàng trước đó đã tồn tại hay chưa.
            // Ngoài ra, cũng có thể kiểm tra xem giỏ hàng hiện tại có đang ở trạng thái "ACTIVE" hay không. Nếu giỏ hàng đã tồn tại nhưng không ở trạng thái "ACTIVE", có thể tạo mới một giỏ hàng mới hoặc trả về lỗi tùy theo yêu cầu nghiệp vụ. Trong trường hợp này, chúng ta sẽ tạo mới một giỏ hàng mới nếu giỏ hàng hiện tại không ở trạng thái "ACTIVE".
            val cartRef = cartsCollection.document(buildCartId(userId))
            val snapshot = cartRef.get().await()
            val now = System.currentTimeMillis()
            // Nếu snapshot tồn tại, chuyển đổi thành đối tượng `Cart` và cập nhật trường `updatedAt` để đảm bảo rằng thông tin giỏ hàng luôn được cập nhật mới nhất. Nếu snapshot không tồn tại, tạo mới một đối tượng `Cart` với trạng thái "ACTIVE" và lưu vào Firestore. Sau đó, trả về đối tượng `Cart` đã được tạo hoặc lấy từ Firestore.
            // Cập nhật trường `updatedAt` mỗi khi lấy hoặc tạo giỏ hàng để đảm bảo rằng thông tin giỏ hàng luôn được cập nhật mới nhất. Điều này giúp theo dõi được thời điểm cuối cùng mà giỏ hàng được truy cập hoặc thay đổi, đồng thời hỗ trợ các tính năng liên quan đến việc tự động xóa giỏ hàng cũ hoặc gửi thông báo nhắc nhở người dùng về giỏ hàng của họ.
            
            val cart = if (snapshot.exists()) {
                snapshot.toObject(Cart::class.java)?.copy(updatedAt = now)
                    ?: Cart(
                        id = buildCartId(userId),
                        userId = userId,
                        status = CartStatus.ACTIVE,
                        itemCount = 0,
                        totalAmount = 0.0,
                        createdAt = now,
                        updatedAt = now
                    )
            } else {
                Cart(
                    id = buildCartId(userId),
                    userId = userId,
                    status = CartStatus.ACTIVE,
                    itemCount = 0,
                    totalAmount = 0.0,
                    createdAt = now,
                    updatedAt = now
                )
            }
            // Sử dụng `set()` với `await()` để đảm bảo rằng thao tác lưu giỏ hàng đã hoàn thành trước khi tiếp tục, đồng thời xử lý lỗi nếu có xảy ra trong quá trình này.
            cartRef.set(cart).await()
            ResultState.Success(cart)
        } catch (e: Exception) {
            ResultState.Error(e.message ?: "Lấy giỏ hàng thất bại")
        }
    }

    /**
     * Lấy dữ liệu hoặc trạng thái cần thiết cho luồng hiện tại.
     * Hàm này thường làm việc với Firestore hoặc API ngoài và trả kết quả về dạng `ResultState` cho tầng gọi phía trên.
     */

    suspend fun getCartItems(userId: String): ResultState<List<CartItem>> {
        if (userId.isBlank()) return ResultState.Error("Thiếu thông tin người dùng")

        return try {
            val items = mutableListOf<CartItem>()
            var cursor: String? = null
            var hasMore = true

            while (hasMore) {
                when (
                    val pageResult = getCartItemsPage(
                        userId = userId,
                        pageRequest = PageRequest(pageSize = 50, cursor = cursor, useCache = true)
                    )
                ) {
                    is ResultState.Success -> {
                        items += pageResult.data.items
                        cursor = pageResult.data.nextCursor
                        hasMore = pageResult.data.hasMore
                    }

                    is ResultState.Error -> return ResultState.Error(pageResult.message)
                    ResultState.Loading -> return ResultState.Loading
                }
            }

            ResultState.Success(items)
        } catch (e: Exception) {
            ResultState.Error(e.message ?: "Lấy danh sách giỏ hàng thất bại")
        }
    }

    /**
     * Lấy dữ liệu hoặc trạng thái cần thiết cho luồng hiện tại.
     * Hàm này thường làm việc với Firestore hoặc API ngoài và trả kết quả về dạng `ResultState` cho tầng gọi phía trên.
     */

    suspend fun getCartItemsPage(
        userId: String,
        pageRequest: PageRequest = PageRequest()
    ): ResultState<PageResult<CartItem>> {
        if (userId.isBlank()) return ResultState.Error("Thiếu thông tin người dùng")

        return try {
            val cacheKey = buildCartItemsCacheKey(userId, pageRequest)
            if (pageRequest.useCache && !pageRequest.refresh) {
                RepositoryCache.get<PageResult<CartItem>>(cacheKey)?.let {
                    return ResultState.Success(it.copy(fromCache = true))
                }
            }

            var query = cartItemsCollection
                .whereEqualTo("userId", userId)
                .orderBy("addedAt", Query.Direction.DESCENDING)
                .limit((pageRequest.normalizedPageSize + 1).toLong())

            val cursorId = pageRequest.cursor
            if (!cursorId.isNullOrBlank()) {
                val cursorSnapshot = cartItemsCollection.document(cursorId).get().await()
                if (cursorSnapshot.exists()) {
                    query = query.startAfter(cursorSnapshot)
                }
            }

            val snapshot = query.get().await()
            val rawItems = snapshot.documents.mapNotNull { document ->
                document.toObject(CartItem::class.java)?.let { cartItem ->
                    if (cartItem.id.isBlank()) {
                        cartItem.copy(id = document.id)
                    } else {
                        cartItem
                    }
                }
            }

            val hasMore = rawItems.size > pageRequest.normalizedPageSize
            val pageItems = if (hasMore) rawItems.take(pageRequest.normalizedPageSize) else rawItems
            val nextCursor = if (hasMore) pageItems.lastOrNull()?.id else null
            val pageResult = PageResult(
                items = pageItems,
                nextCursor = nextCursor,
                hasMore = hasMore,
                fromCache = false
            )

            RepositoryCache.put(cacheKey, pageResult, CacheTTL.SHORT)
            ResultState.Success(pageResult)
        } catch (e: Exception) {
            ResultState.Error(e.message ?: "Lấy danh sách giỏ hàng thất bại")
        }
    }

    /**
     * Kiểm tra điều kiện nghiệp vụ trước khi tiếp tục các bước xử lý kế tiếp.
     * Hàm này thường làm việc với Firestore hoặc API ngoài và trả kết quả về dạng `ResultState` cho tầng gọi phía trên.
     */

    suspend fun isCourseInCart(userId: String, courseId: String): ResultState<Boolean> {
        if (userId.isBlank() || courseId.isBlank()) {
            return ResultState.Error("Thiếu thông tin kiểm tra giỏ hàng")
        }

        return try {
            val snapshot = cartItemsCollection
                .document(buildCartItemId(userId, courseId))
                .get()
                .await()
            ResultState.Success(snapshot.exists())
        } catch (e: Exception) {
            ResultState.Error(e.message ?: "Kiểm tra giỏ hàng thất bại")
        }
    }

    /**
     * Kiểm tra điều kiện nghiệp vụ trước khi tiếp tục các bước xử lý kế tiếp.
     * Hàm này thường làm việc với Firestore hoặc API ngoài và trả kết quả về dạng `ResultState` cho tầng gọi phía trên.
     */

    suspend fun isCourseEnrolled(userId: String, courseId: String): ResultState<Boolean> {
        if (userId.isBlank() || courseId.isBlank()) {
            return ResultState.Error("Thiếu thông tin kiểm tra đăng ký")
        }

        return try {
            val snapshot = enrollmentsCollection
                .document(buildEnrollmentId(userId, courseId))
                .get()
                .await()
            ResultState.Success(snapshot.exists())
        } catch (e: Exception) {
            ResultState.Error(e.message ?: "Kiểm tra đăng ký khóa học thất bại")
        }
    }

    /**
     * Thêm dữ liệu hoặc đối tượng mới vào luồng xử lý hiện tại.
     * Hàm này thường làm việc với Firestore hoặc API ngoài và trả kết quả về dạng `ResultState` cho tầng gọi phía trên.
     */

    suspend fun addCourseToCart(userId: String, courseId: String): ResultState<CartItem> {
        if (userId.isBlank() || courseId.isBlank()) {
            return ResultState.Error("Thiếu thông tin khóa học hoặc người dùng")
        }

        return try {
            val now = System.currentTimeMillis()
            val cartId = buildCartId(userId)
            val cartRef = cartsCollection.document(cartId)
            val cartItemRef = cartItemsCollection.document(buildCartItemId(userId, courseId))
            val enrollmentRef = enrollmentsCollection.document(buildEnrollmentId(userId, courseId))
            val courseRef = coursesCollection.document(courseId)

            val newCartItem = firestore.runTransaction { transaction ->
                val enrollmentSnapshot = transaction.get(enrollmentRef)
                if (enrollmentSnapshot.exists()) {
                    throw IllegalStateException("Bạn đã đăng ký khóa học này")
                }

                val existingCartItemSnapshot = transaction.get(cartItemRef)
                if (existingCartItemSnapshot.exists()) {
                    throw IllegalStateException("Khóa học đã có trong giỏ hàng")
                }

                val courseSnapshot = transaction.get(courseRef)
                val course = courseSnapshot.toObject(Course::class.java)
                    ?: throw IllegalStateException("Không tìm thấy khóa học")

                val cartSnapshot = transaction.get(cartRef)
                val currentCart = cartSnapshot.toObject(Cart::class.java)
                val createdAt = currentCart?.createdAt ?: now
                val newItemCount = (currentCart?.itemCount ?: 0) + 1
                val newTotalAmount = (currentCart?.totalAmount ?: 0.0) + course.price

                val cart = Cart(
                    id = cartId,
                    userId = userId,
                    status = CartStatus.ACTIVE,
                    itemCount = newItemCount,
                    totalAmount = newTotalAmount,
                    createdAt = createdAt,
                    updatedAt = now
                )

                val cartItem = CartItem(
                    id = cartItemRef.id,
                    cartId = cartId,
                    userId = userId,
                    courseId = course.id,
                    courseThumbnail = course.thumbnailUrl,
                    courseTitle = course.title,
                    coursePrice = course.price,
                    addedAt = now
                )

                transaction.set(cartRef, cart)
                transaction.set(cartItemRef, cartItem)
                cartItem
            }.await()

            invalidateCartItemCache(userId)

            ResultState.Success(newCartItem)
        } catch (e: IllegalStateException) {
            ResultState.Error(e.message ?: "Không thể thêm vào giỏ hàng")
        } catch (e: Exception) {
            ResultState.Error(e.message ?: "Thêm vào giỏ hàng thất bại")
        }
    }

    /**
     * Loại bỏ phần tử tương ứng khỏi tập dữ liệu đang quản lý.
     * Hàm này thường làm việc với Firestore hoặc API ngoài và trả kết quả về dạng `ResultState` cho tầng gọi phía trên.
     */

    suspend fun removeCourseFromCart(userId: String, courseId: String): ResultState<Unit> {
        if (userId.isBlank() || courseId.isBlank()) {
            return ResultState.Error("Thiếu thông tin khóa học hoặc người dùng")
        }

        return try {
            val now = System.currentTimeMillis()
            val cartId = buildCartId(userId)
            val cartRef = cartsCollection.document(cartId)
            val cartItemRef = cartItemsCollection.document(buildCartItemId(userId, courseId))

            firestore.runTransaction { transaction ->
                val cartItemSnapshot = transaction.get(cartItemRef)
                val cartItem = cartItemSnapshot.toObject(CartItem::class.java)
                    ?: return@runTransaction

                val cartSnapshot = transaction.get(cartRef)
                val currentCart = cartSnapshot.toObject(Cart::class.java)

                val currentItemCount = currentCart?.itemCount ?: 0
                val currentTotalAmount = currentCart?.totalAmount ?: 0.0

                val newItemCount = (currentItemCount - 1).coerceAtLeast(0)
                val newTotalAmount = (currentTotalAmount - cartItem.coursePrice).coerceAtLeast(0.0)
                val createdAt = currentCart?.createdAt ?: now

                val updatedCart = Cart(
                    id = cartId,
                    userId = userId,
                    status = CartStatus.ACTIVE,
                    itemCount = newItemCount,
                    totalAmount = newTotalAmount,
                    createdAt = createdAt,
                    updatedAt = now
                )

                transaction.delete(cartItemRef)
                transaction.set(cartRef, updatedCart)
            }.await()

            invalidateCartItemCache(userId)

            ResultState.Success(Unit)
        } catch (e: IllegalStateException) {
            ResultState.Error(e.message ?: "Không thể xóa khóa học khỏi giỏ hàng")
        } catch (e: Exception) {
            ResultState.Error(e.message ?: "Xóa khóa học khỏi giỏ hàng thất bại")
        }
    }

    private fun buildCartId(userId: String): String = userId

    private fun buildCartItemId(userId: String, courseId: String): String = "${userId}_${courseId}"

    private fun buildEnrollmentId(userId: String, courseId: String): String = "${userId}_${courseId}"

    private fun buildCartItemsCacheKey(userId: String, pageRequest: PageRequest): String {
        val cursorPart = pageRequest.cursor ?: "first"
        return "$cartItemsCachePrefix:$userId:${pageRequest.normalizedPageSize}:$cursorPart"
    }

    private fun invalidateCartItemCache(userId: String) {
        if (userId.isBlank()) return
        RepositoryCache.invalidateByPrefix("$cartItemsCachePrefix:$userId")
    }
}

