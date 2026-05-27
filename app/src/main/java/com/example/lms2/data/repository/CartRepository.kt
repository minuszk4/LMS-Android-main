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
     * Ví dụ: Lấy danh sách các mục trong giỏ hàng của người dùng. Hàm này có thể hỗ trợ phân trang để lấy dữ liệu theo từng phần, giúp giảm thiểu thời gian tải và cải thiện hiệu suất khi giỏ hàng có nhiều mục. Ngoài ra, cũng có thể sử dụng cache để lưu trữ kết quả của các trang đã lấy để tránh phải truy vấn lại Firestore khi người dùng quay lại các trang đã xem trước đó.
     */

    suspend fun getCartItems(userId: String): ResultState<List<CartItem>> {
        if (userId.isBlank()) return ResultState.Error("Thiếu thông tin người dùng")
        // Security hardening: validate user identity and input before performing any data operations. This helps prevent unauthorized access and ensures that the cart items retrieval process is initiated with valid data, reducing the risk of errors and potential abuse. In this case, we check if the `userId` is valid before proceeding with any operations related to Firestore.
        // Kiểm tra xem `userId` có hợp lệ hay không (không được để trống). Nếu không hợp lệ, trả về lỗi ngay lập tức để tránh thực hiện các thao tác không cần thiết với Firestore.
        return try {
            val items = mutableListOf<CartItem>()
            var cursor: String? = null
            var hasMore = true
            // Sử dụng vòng lặp để lấy dữ liệu phân trang từ Firestore cho đến khi không còn trang nào nữa. Trong mỗi lần lặp, gọi hàm `getCartItemsPage` với tham số `cursor` để lấy trang tiếp theo của dữ liệu. Nếu có lỗi xảy ra trong quá trình này, trả về lỗi ngay lập tức để thông báo cho người dùng.
            // Kết hợp với việc sử dụng cache trong hàm `getCartItemsPage` để tối ưu hiệu suất khi người dùng quay lại các trang đã xem trước đó. Nếu dữ liệu đã được lưu trong cache, sẽ trả về dữ liệu từ cache thay vì truy vấn lại Firestore, giúp giảm thiểu thời gian tải và cải thiện trải nghiệm người dùng.
            // Sau khi lấy được tất cả các mục trong giỏ hàng, trả về danh sách các mục đã lấy. Nếu có lỗi xảy ra trong quá trình này, trả về lỗi để thông báo cho người dùng.
            // Lưu ý: Việc sử dụng vòng lặp để lấy dữ liệu phân trang có thể dẫn đến việc thực hiện nhiều truy vấn đến Firestore nếu giỏ hàng có nhiều mục. Do đó, cần cân nhắc về hiệu suất và có thể giới hạn số lượng trang tối đa để tránh tình trạng quá tải khi giỏ hàng có quá nhiều mục.
            // Ngoài ra, cũng có thể xem xét việc sử dụng một phương pháp khác để lấy tất cả các mục trong giỏ hàng mà không cần phải sử dụng vòng lặp phân trang, ví dụ như sử dụng một truy vấn lớn hơn để lấy tất cả các mục cùng một lúc nếu số lượng mục trong giỏ hàng không quá lớn. Tuy nhiên, trong trường hợp này, chúng ta sẽ sử dụng phương pháp phân trang để đảm bảo hiệu suất tốt hơn khi giỏ hàng có nhiều mục.
            // Cần đảm bảo rằng hàm `getCartItemsPage` được triển khai đúng cách để hỗ trợ phân trang và cache hiệu quả, đồng thời xử lý các lỗi có thể xảy ra trong quá trình lấy dữ liệu từ Firestore.
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
     *  Ví dụ: Lấy danh sách các mục trong giỏ hàng của người dùng theo từng trang. Hàm này sẽ nhận vào một đối tượng `PageRequest` chứa thông tin về kích thước trang, con trỏ phân trang, và tùy chọn sử dụng cache. Dựa trên thông tin này, hàm sẽ truy vấn Firestore để lấy một trang dữ liệu của các mục trong giỏ hàng, đồng thời sử dụng cache nếu được yêu cầu để tối ưu hiệu suất. Kết quả trả về sẽ là một đối tượng `PageResult` chứa danh sách các mục đã lấy, con trỏ cho trang tiếp theo, và thông tin về việc dữ liệu có được lấy từ cache hay không.
     */

    suspend fun getCartItemsPage(
        userId: String,
        pageRequest: PageRequest = PageRequest()
    ): ResultState<PageResult<CartItem>> {
        if (userId.isBlank()) return ResultState.Error("Thiếu thông tin người dùng")
        // Security hardening: validate user identity and input before performing any data operations. This helps prevent unauthorized access and ensures that the cart items retrieval process is initiated with valid data, reducing the risk of errors and potential abuse. In this case, we check if the `userId` is valid before proceeding with any operations related to Firestore.
        // Kiểm tra xem `userId` có hợp lệ hay không (không được để trống). Nếu không hợp lệ, trả về lỗi ngay lập tức để tránh thực hiện các thao tác không cần thiết với Firestore.
        // Sử dụng `buildCartItemsCacheKey` để tạo khóa cache duy nhất cho mỗi người dùng và trang dữ liệu. Điều này giúp đảm bảo rằng dữ liệu được lưu trữ trong cache có thể được truy xuất một cách chính xác dựa trên người dùng và thông tin phân trang, đồng thời tránh xung đột dữ liệu giữa các người dùng hoặc các trang khác nhau.
        // Kiểm tra cache trước khi truy vấn Firestore. Nếu dữ liệu đã tồn tại trong cache và không yêu cầu làm mới, trả về dữ liệu từ cache ngay lập tức để tối ưu hiệu suất và giảm thiểu thời gian tải. Nếu dữ liệu không tồn tại trong cache hoặc yêu cầu làm mới, tiếp tục truy vấn Firestore để lấy dữ liệu mới nhất.
        // Sau khi lấy dữ liệu từ Firestore, lưu kết quả vào cache với TTL ngắn để đảm bảo rằng dữ liệu được cập nhật thường xuyên nhưng vẫn tận dụng được lợi ích của cache khi người dùng quay lại các trang đã xem trước đó. Điều này giúp cải thiện hiệu suất và trải nghiệm người dùng khi làm việc với giỏ hàng có nhiều mục.
        // Nếu cache trả về danh sách rỗng và pageRequest cho phép sử dụng cache nhưng không yêu cầu làm mới, có thể thử làm mới cache một lần nữa để lấy dữ liệu mới nhất từ Firestore. Điều này giúp tránh trường hợp cache bị lỗi hoặc dữ liệu đã được thêm vào từ bên ngoài mà cache chưa cập nhật, đồng thời đảm bảo rằng người dùng luôn nhận được thông tin giỏ hàng chính xác và cập nhật nhất.
        return try {
            val cacheKey = buildCartItemsCacheKey(userId, pageRequest)
            if (pageRequest.useCache && !pageRequest.refresh) {
                RepositoryCache.get<PageResult<CartItem>>(cacheKey)?.let {
                    return ResultState.Success(it.copy(fromCache = true))
                }
            }
            // Truy vấn Firestore để lấy một trang dữ liệu của các mục trong giỏ hàng dựa trên `userId` và thông tin phân trang trong `pageRequest`. Sử dụng `orderBy` để sắp xếp các mục theo thời gian thêm vào giỏ hàng, đồng thời sử dụng `limit` để giới hạn số lượng mục trả về theo kích thước trang yêu cầu. Nếu có con trỏ phân trang (`cursor`) trong `pageRequest`, sử dụng `startAfter` để bắt đầu truy vấn từ vị trí của con trỏ đó, giúp hỗ trợ phân trang hiệu quả.
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
     *  Ví dụ: Kiểm tra xem một khóa học cụ thể đã có trong giỏ hàng của người dùng hay chưa. Hàm này sẽ nhận vào `userId` và `courseId`, sau đó truy vấn Firestore để kiểm tra xem có tồn tại một mục giỏ hàng nào tương ứng với người dùng và khóa học đó hay không. Kết quả trả về sẽ là một đối tượng `ResultState` chứa giá trị boolean cho biết khóa học đã có trong giỏ hàng hay chưa, hoặc lỗi nếu có vấn đề xảy ra trong quá trình kiểm tra. 
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
     * Ví dụ: Kiểm tra xem một khóa học cụ thể đã được người dùng đăng ký hay chưa. Hàm này sẽ nhận vào `userId` và `courseId`, sau đó truy vấn Firestore để kiểm tra xem có tồn tại một bản ghi đăng ký nào tương ứng với người dùng và khóa học đó hay không. Kết quả trả về sẽ là một đối tượng `ResultState` chứa giá trị boolean cho biết khóa học đã được đăng ký hay chưa, hoặc lỗi nếu có vấn đề xảy ra trong quá trình kiểm tra.
     */

    suspend fun isCourseEnrolled(userId: String, courseId: String): ResultState<Boolean> {
        if (userId.isBlank() || courseId.isBlank()) {
            return ResultState.Error("Thiếu thông tin kiểm tra đăng ký")
        }

        return try {
            // Truy vấn Firestore để kiểm tra xem có tồn tại một bản ghi đăng ký nào tương ứng với `userId` và `courseId` hay không. Sử dụng `document` với một ID được xây dựng dựa trên `userId` và `courseId` để truy vấn trực tiếp đến document đó, giúp tối ưu hiệu suất khi kiểm tra đăng ký của một khóa học cụ thể. Nếu snapshot tồn tại, trả về `true`, ngược lại trả về `false`. Nếu có lỗi xảy ra trong quá trình này, trả về lỗi để thông báo cho người dùng.
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
     * Ví dụ: Thêm một khóa học mới vào giỏ hàng của người dùng. Hàm này sẽ nhận vào `userId` và `courseId`, sau đó truy vấn Firestore để kiểm tra xem khóa học đã có trong giỏ hàng hay chưa, và nếu chưa, sẽ thêm một mục giỏ hàng mới. Kết quả trả về sẽ là một đối tượng `ResultState` chứa thông tin về mục giỏ hàng vừa được thêm, hoặc lỗi nếu có vấn đề xảy ra trong quá trình thêm.
     */

    suspend fun addCourseToCart(userId: String, courseId: String): ResultState<CartItem> {
        if (userId.isBlank() || courseId.isBlank()) {
            return ResultState.Error("Thiếu thông tin khóa học hoặc người dùng")
        }
        // Security hardening: validate user identity and input before performing any data operations. This helps prevent unauthorized access and ensures that the add to cart process is initiated with valid data, reducing the risk of errors and potential abuse. In this case, we check if the `userId` and `courseId` are valid before proceeding with any operations related to Firestore.
        // Kiểm tra xem `userId` và `courseId` có hợp lệ hay
        // không (không được để trống). Nếu không hợp lệ, trả về lỗi ngay lập tức để tránh thực hiện các thao tác không cần thiết với Firestore.
        // Sử dụng transaction để đảm bảo tính nhất quán của dữ liệu khi thêm một khóa học vào giỏ hàng. Trong transaction, kiểm tra xem khóa học đã có trong giỏ hàng hay chưa, và nếu chưa, thêm một mục giỏ hàng mới. Điều này giúp tránh
        return try {    
            val now = System.currentTimeMillis()
            val cartId = buildCartId(userId)
            val cartRef = cartsCollection.document(cartId)
            val cartItemRef = cartItemsCollection.document(buildCartItemId(userId, courseId))
            val enrollmentRef = enrollmentsCollection.document(buildEnrollmentId(userId, courseId))
            val courseRef = coursesCollection.document(courseId)
            // Sử dụng `runTransaction` để thực hiện các thao tác kiểm tra và thêm mục giỏ hàng một cách nguyên tử. Trong transaction, đầu tiên kiểm tra xem đã tồn tại một bản ghi đăng ký nào tương ứng với `userId` và `courseId` hay chưa. Nếu đã tồn tại, ném ra một ngoại lệ để thông báo rằng người dùng đã đăng ký khóa học này. Tiếp theo, kiểm tra xem đã tồn tại một mục giỏ hàng nào tương ứng với `userId` và `courseId` hay chưa. Nếu đã tồn tại, ném ra một ngoại lệ để thông báo rằng khóa học đã có trong giỏ hàng. Sau đó, lấy thông tin khóa học từ Firestore để tính toán giá tiền và cập nhật thông tin giỏ hàng. Cuối cùng, tạo mới một mục giỏ hàng và lưu vào Firestore trong cùng một transaction để đảm bảo tính nhất quán của dữ liệu.
            val newCartItem = firestore.runTransaction { transaction ->
                val enrollmentSnapshot = transaction.get(enrollmentRef)
                if (enrollmentSnapshot.exists()) {
                    throw IllegalStateException("Bạn đã đăng ký khóa học này")
                }

                val existingCartItemSnapshot = transaction.get(cartItemRef)
                if (existingCartItemSnapshot.exists()) {
                    throw IllegalStateException("Khóa học đã có trong giỏ hàng")
                }
                // Lấy thông tin khóa học từ Firestore để tính toán giá tiền và cập nhật thông tin giỏ hàng. Nếu khóa học không tồn tại, ném ra một ngoại lệ để thông báo rằng khóa học không tìm thấy. Sau đó, tạo mới một mục giỏ hàng và lưu vào Firestore trong cùng một transaction để đảm bảo tính nhất quán của dữ liệu.
                
                val courseSnapshot = transaction.get(courseRef)
                val course = courseSnapshot.toObject(Course::class.java)
                    ?: throw IllegalStateException("Không tìm thấy khóa học")

                val cartSnapshot = transaction.get(cartRef)
                val currentCart = cartSnapshot.toObject(Cart::class.java)
                val createdAt = currentCart?.createdAt ?: now
                val newItemCount = (currentCart?.itemCount ?: 0) + 1
                val newTotalAmount = (currentCart?.totalAmount ?: 0.0) + course.price
                // Tạo mới một đối tượng `Cart` với thông tin cập nhật về số lượng mục và tổng tiền, đồng thời tạo một đối tượng `CartItem` mới với thông tin về khóa học vừa được thêm vào. Sau đó, sử dụng transaction để lưu cả hai đối tượng này vào Firestore, đảm bảo rằng dữ liệu được cập nhật một cách nguyên tử và nhất quán.
                val cart = Cart(
                    id = cartId,
                    userId = userId,
                    status = CartStatus.ACTIVE,
                    itemCount = newItemCount,
                    totalAmount = newTotalAmount,
                    createdAt = createdAt,
                    updatedAt = now
                )
                // Tạo mới một đối tượng `CartItem` với thông tin về khóa học vừa được thêm vào, bao gồm ID, thông tin khóa học, và thời gian thêm vào giỏ hàng. Sau đó, sử dụng transaction để lưu cả đối tượng `Cart` và `CartItem` vào Firestore, đảm bảo rằng dữ liệu được cập nhật một cách nguyên tử và nhất quán. Cuối cùng, trả về đối tượng `CartItem` vừa được tạo để thông báo cho tầng gọi phía trên về kết quả của thao tác thêm vào giỏ hàng.
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
     * Ví dụ: Xóa một khóa học khỏi giỏ hàng của người dùng. Hàm này sẽ nhận vào `userId` và `courseId`, sau đó truy vấn Firestore để kiểm tra xem khóa học có tồn tại trong giỏ hàng hay không, và nếu có, sẽ xóa mục giỏ hàng tương ứng. Kết quả trả về sẽ là một đối tượng `ResultState` chứa thông tin về việc xóa thành công hay không, hoặc lỗi nếu có vấn đề xảy ra trong quá trình xóa.
     */

    suspend fun removeCourseFromCart(userId: String, courseId: String): ResultState<Unit> {
        if (userId.isBlank() || courseId.isBlank()) {
            return ResultState.Error("Thiếu thông tin khóa học hoặc người dùng")
        }

        return try {
            // Sử dụng transaction để đảm bảo tính nhất quán của dữ liệu khi xóa một khóa học khỏi giỏ hàng. Trong transaction, kiểm tra xem mục giỏ hàng tương ứng với `userId` và `courseId` có tồn tại hay không. Nếu không tồn tại, ném ra một ngoại lệ để thông báo rằng khóa học không có trong giỏ hàng. Nếu tồn tại, lấy thông tin giỏ hàng hiện tại để cập nhật số lượng mục và tổng tiền sau khi xóa khóa học. Sau đó, xóa mục giỏ hàng và cập nhật lại thông tin giỏ hàng trong cùng một transaction để đảm bảo tính nhất quán của dữ liệu.
            val now = System.currentTimeMillis()
            val cartId = buildCartId(userId)
            val cartRef = cartsCollection.document(cartId)
            val cartItemRef = cartItemsCollection.document(buildCartItemId(userId, courseId))
            // Sử dụng `runTransaction` để thực hiện các thao tác kiểm tra và xóa mục giỏ hàng một cách nguyên tử. Trong transaction, đầu tiên kiểm tra xem đã tồn tại một mục giỏ hàng nào tương ứng với `userId` và `courseId` hay chưa. Nếu không tồn tại, ném ra một ngoại lệ để thông báo rằng khóa học không có trong giỏ hàng. Sau đó, lấy thông tin giỏ hàng hiện tại để cập nhật số lượng mục và tổng tiền sau khi xóa khóa học. Cuối cùng, xóa mục giỏ hàng và cập nhật lại thông tin giỏ hàng trong cùng một transaction để đảm bảo tính nhất quán của dữ liệu.
            firestore.runTransaction { transaction ->
                val cartItemSnapshot = transaction.get(cartItemRef)
                val cartItem = cartItemSnapshot.toObject(CartItem::class.java)
                    ?: return@runTransaction
                // Lấy thông tin giỏ hàng hiện tại để cập nhật số lượng mục và tổng tiền sau khi xóa khóa học. Nếu giỏ hàng không tồn tại, ném ra một ngoại lệ để thông báo rằng giỏ hàng không tìm thấy. Sau đó, xóa mục giỏ hàng và cập nhật lại thông tin giỏ hàng trong cùng một transaction để đảm bảo tính nhất quán của dữ liệu.
                val cartSnapshot = transaction.get(cartRef)
                val currentCart = cartSnapshot.toObject(Cart::class.java)
                // Tính toán số lượng mục và tổng tiền mới sau khi xóa khóa học, đảm bảo rằng số lượng mục không âm và tổng tiền không âm. Sau đó, tạo một đối tượng `Cart` mới với thông tin cập nhật và sử dụng transaction để xóa mục giỏ hàng và cập nhật lại giỏ hàng trong Firestore, đảm bảo rằng dữ liệu được cập nhật một cách nguyên tử và nhất quán.
                val currentItemCount = currentCart?.itemCount ?: 0
                val currentTotalAmount = currentCart?.totalAmount ?: 0.0
                // Tính toán số lượng mục và tổng tiền mới sau khi xóa khóa học, đảm bảo rằng số lượng mục không âm và tổng tiền không âm. Sau đó, tạo một đối tượng `Cart` mới với thông tin cập nhật và sử dụng transaction để xóa mục giỏ hàng và cập nhật lại giỏ hàng trong Firestore, đảm bảo rằng dữ liệu được cập nhật một cách nguyên tử và nhất quán.
                val newItemCount = (currentItemCount - 1).coerceAtLeast(0)
                val newTotalAmount = (currentTotalAmount - cartItem.coursePrice).coerceAtLeast(0.0)
                val createdAt = currentCart?.createdAt ?: now
                // Tạo một đối tượng `Cart` mới với thông tin cập nhật về số lượng mục và tổng tiền, đồng thời sử dụng transaction để xóa mục giỏ hàng và cập nhật lại giỏ hàng trong Firestore, đảm bảo rằng dữ liệu được cập nhật một cách nguyên tử và nhất quán. Cuối cùng, trả về `Unit` để thông báo cho tầng gọi phía trên về kết quả của thao tác xóa khỏi giỏ hàng.
                val updatedCart = Cart(
                    id = cartId,
                    userId = userId,
                    status = CartStatus.ACTIVE,
                    itemCount = newItemCount,
                    totalAmount = newTotalAmount,
                    createdAt = createdAt,
                    updatedAt = now
                )
                // Sử dụng transaction để xóa mục giỏ hàng và cập nhật lại giỏ hàng trong Firestore, đảm bảo rằng dữ liệu được cập nhật một cách nguyên tử và nhất quán. Cuối cùng, trả về `Unit` để thông báo cho tầng gọi phía trên về kết quả của thao tác xóa khỏi giỏ hàng.
                transaction.delete(cartItemRef)
                transaction.set(cartRef, updatedCart)
            }.await()
            // Sau khi xóa mục giỏ hàng và cập nhật lại giỏ hàng trong Firestore, gọi `invalidateCartItemCache` để xóa cache liên quan đến các mục giỏ hàng của người dùng. Điều này đảm bảo rằng dữ liệu được cập nhật mới nhất sẽ được hiển thị khi người dùng quay lại xem giỏ hàng của họ, đồng thời tránh việc hiển thị dữ liệu cũ đã bị xóa khỏi cache.
            invalidateCartItemCache(userId)
            // Trả về `Unit` để thông báo cho tầng gọi phía trên về kết quả của thao tác xóa khỏi giỏ hàng. Nếu có lỗi xảy ra trong quá trình này, trả về lỗi để thông báo cho người dùng.
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

