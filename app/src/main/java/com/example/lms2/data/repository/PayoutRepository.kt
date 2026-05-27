package com.example.lms2.data.repository

import com.example.lms2.data.model.Course
import com.example.lms2.data.model.Instructor
import com.example.lms2.data.model.InstructorPayout
import com.example.lms2.data.model.Order
import com.example.lms2.data.model.OrderItem
import com.example.lms2.data.model.PayoutStatus
import com.example.lms2.data.model.User
import com.example.lms2.util.ResultState
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await

/**
 * Triển khai repository PayoutRepository cho ứng dụng LMS Android.
 * File này chịu trách nhiệm làm việc với Firestore hoặc API ngoài, đồng thời chuyển đổi kết quả về dạng phù hợp cho ViewModel.
 * Repository là ranh giới chính giữa tầng giao diện và tầng dữ liệu nên được mô tả rõ để thuận tiện cho tài liệu kỹ thuật.
 */

class PayoutRepository {

    private val firestore = FirebaseFirestore.getInstance()
    private val payoutsCollection = firestore.collection("instructorPayouts")
    private val ordersCollection = firestore.collection("orders")
    private val orderItemsCollection = firestore.collection("orderItems")
    private val coursesCollection = firestore.collection("courses")
    private val instructorsCollection = firestore.collection("instructors")
    private val usersCollection = firestore.collection("users")

    /**
     * Lấy dữ liệu hoặc trạng thái cần thiết cho luồng hiện tại.
     * Hàm này thường làm việc với Firestore hoặc API ngoài và trả kết quả về dạng `ResultState` cho tầng gọi phía trên.
     * Ví dụ: Lấy danh sách các khoản thanh toán của giảng viên, có thể bao gồm việc đồng bộ dữ liệu bị thiếu từ các đơn hàng đã thành công trước đó để đảm bảo rằng dữ liệu hiển thị ở tầng giao diện luôn đầy đủ và cập nhật nhất.
     */
    suspend fun getInstructorPayouts(): ResultState<List<InstructorPayout>> {
        return try {
            // Đồng bộ các khoản thanh toán bị thiếu từ các đơn hàng đã thành công trước khi truy vấn danh sách chuyển tiền. Điều này đảm bảo rằng dữ liệu hiển thị ở tầng giao diện luôn đầy đủ và cập nhật nhất, đặc biệt trong trường hợp có các đơn hàng mới được xác nhận mà chưa được tạo bản ghi chuyển tiền tương ứng.
            syncMissingPayoutsFromSuccessfulOrders()
            // Truy vấn tất cả các khoản thanh toán của giảng viên từ Firestore, sắp xếp theo thời gian xác nhận đơn hàng để hiển thị mới nhất trước. Sau khi lấy dữ liệu, chuyển đổi mỗi document thành đối tượng InstructorPayout, đảm bảo rằng ID được lấy từ document nếu trường ID trong dữ liệu trống. Cuối cùng, sắp xếp lại danh sách kết quả theo thời gian xác nhận đơn hàng một lần nữa để đảm bảo thứ tự chính xác.
            val snapshot = payoutsCollection
                .orderBy("orderConfirmedAt")
                .get()
                .await()
            // Trong quá trình chuyển đổi, nếu có document nào không thể chuyển đổi thành InstructorPayout hợp lệ (ví dụ: thiếu trường quan trọng), sẽ bị loại bỏ khỏi kết quả bằng cách sử dụng `mapNotNull`. Điều này giúp đảm bảo rằng danh sách trả về chỉ chứa các khoản thanh toán hợp lệ và tránh lỗi khi hiển thị ở tầng giao diện.
            val payouts = snapshot.documents
                .mapNotNull { document ->
                    document.toObject(InstructorPayout::class.java)?.let { payout ->
                        if (payout.id.isBlank()) payout.copy(id = document.id) else payout
                    }
                }
                .sortedByDescending { it.orderConfirmedAt }
            // Sắp xếp lại danh sách kết quả theo thời gian xác nhận đơn hàng một lần nữa để đảm bảo thứ tự chính xác, đặc biệt nếu có các khoản thanh toán mới được thêm vào từ quá trình đồng bộ trước đó.
            ResultState.Success(payouts)
        } catch (e: Exception) {
            ResultState.Error(e.message ?: "Khong tai duoc danh sach chuyen tien")
        }
    }

    /**
     * Thực hiện phần xử lý chính của luồng nghiệp vụ hoặc giao diện tương ứng.
     * Hàm này thường làm việc với Firestore hoặc API ngoài và trả kết quả về dạng `ResultState` cho tầng gọi phía trên.

     Ví dụ: Đánh dấu một khoản thanh toán cụ thể là đã được trả tiền, có thể bao gồm việc cập nhật trạng thái của khoản thanh toán trong Firestore và ghi lại thông tin về thời gian thanh toán, người thực hiện thao tác, và tham chiếu chuyển tiền thủ công nếu có. Ngoài ra, cũng có thể cung cấp một hàm để đánh dấu nhiều khoản thanh toán cùng lúc để thuận tiện cho việc xử lý hàng loạt.
     */
    suspend fun markPayoutAsPaid(
        payoutId: String,
        adminUid: String,
        manualTransferReference: String
    ): ResultState<Unit> {
        // Sử dụng hàm `markPayoutsAsPaid` để đánh dấu khoản thanh toán cụ thể là đã được trả tiền. Hàm này có thể được tái sử dụng để đánh dấu nhiều khoản thanh toán cùng lúc nếu cần thiết, giúp giảm thiểu sự trùng lặp mã và đảm bảo tính nhất quán trong cách xử lý trạng thái chuyển tiền.
        return markPayoutsAsPaid(
            payoutIds = listOf(payoutId),
            adminUid = adminUid,
            manualTransferReference = manualTransferReference
        )
    }

    /**
     * Thực hiện phần xử lý chính của luồng nghiệp vụ hoặc giao diện tương ứng.
     * Hàm này thường làm việc với Firestore hoặc API ngoài và trả kết quả về dạng `ResultState` cho tầng gọi phía trên.
     */

    suspend fun markPayoutsAsPaid(
        payoutIds: List<String>,
        adminUid: String,
        manualTransferReference: String
    ): ResultState<Unit> {
        // Chuẩn hóa danh sách ID chuyển tiền bằng cách loại bỏ khoảng trắng thừa, lọc bỏ các ID trống, và loại bỏ trùng lặp để đảm bảo rằng chỉ có các ID hợp lệ được xử lý. Nếu sau khi chuẩn hóa danh sách rỗng, trả về lỗi ngay lập tức để tránh thực hiện các thao tác không cần thiết với Firestore.
        val normalizedIds = payoutIds.map { it.trim() }.filter { it.isNotBlank() }.distinct()
        if (normalizedIds.isEmpty()) return ResultState.Error("Thieu danh sach chuyen tien")
        if (adminUid.isBlank()) return ResultState.Error("Thieu tai khoan admin")

        return try {
            val paidAt = System.currentTimeMillis()
            val batch = firestore.batch()
            // Cập nhật trạng thái của tất cả các khoản thanh toán có ID trong danh sách đã chuẩn hóa thành "PAID", đồng thời ghi lại thời gian thanh toán, UID của admin thực hiện thao tác, và tham chiếu chuyển tiền thủ công. Sử dụng batch để đảm bảo rằng tất cả các cập nhật được thực hiện đồng thời và có thể rollback nếu có lỗi xảy ra trong quá trình này.
            normalizedIds.forEach { payoutId ->
                batch.update(
                    payoutsCollection.document(payoutId),
                    mapOf(
                        "payoutStatus" to PayoutStatus.PAID.name,
                        "paidAt" to paidAt,
                        "paidByAdminUid" to adminUid,
                        "manualTransferReference" to manualTransferReference.trim()
                    )
                )
            }
            // Sử dụng `await()` để đảm bảo rằng thao tác commit batch đã hoàn thành trước khi trả về kết quả, đồng thời xử lý lỗi nếu có xảy ra trong quá trình này.
            batch.commit().await()
            ResultState.Success(Unit)
        } catch (e: Exception) {
            ResultState.Error(e.message ?: "Khong cap nhat duoc trang thai chuyen tien")
        }
    }
    /**
     * Đồng bộ các khoản thanh toán bị thiếu từ các đơn hàng đã thành công.
     */
    private suspend fun syncMissingPayoutsFromSuccessfulOrders() {
        // Lấy tất cả ID của các khoản thanh toán hiện có để tránh tạo trùng lặp khi đồng bộ từ các đơn hàng đã thành công. Điều này giúp đảm bảo rằng chỉ có các khoản thanh toán mới được tạo ra cho các đơn hàng chưa có bản ghi chuyển tiền tương ứng.
        val existingPayoutIds = payoutsCollection.get().await().documents.map { it.id }.toHashSet()
        // Truy vấn tất cả các đơn hàng có trạng thái thanh toán là "SUCCESS" để tìm kiếm các đơn hàng đã được xác nhận nhưng chưa có bản ghi chuyển tiền tương ứng. Nếu không có đơn hàng nào thỏa
        val successfulOrders = ordersCollection
            .whereEqualTo("paymentStatus", "SUCCESS")
            .get()
            .await()
            .documents
        // Nếu không có đơn hàng nào thỏa mãn, trả về ngay để tránh thực hiện các thao tác không cần thiết với Firestore.
        if (successfulOrders.isEmpty()) return
        // Sử dụng cache tạm thời để lưu trữ thông tin về khóa học, giảng viên, và người dùng đã truy vấn trong quá trình đồng bộ. Điều này giúp giảm số lượng truy vấn đến Firestore và cải thiện hiệu suất, đặc biệt khi có nhiều đơn hàng và mục liên quan cần xử lý.
        val courseCache = mutableMapOf<String, Course?>()
        val instructorCache = mutableMapOf<String, Instructor?>()
        val userCache = mutableMapOf<String, User?>()
        // Duyệt qua từng đơn hàng đã thành công, sau đó lấy các mục đơn hàng liên quan để tạo bản ghi chuyển tiền tương ứng nếu chưa tồn tại. Trong quá trình này, cố gắng lấy thông tin về khóa học, giảng viên, và người dùng từ cache trước khi truy vấn Firestore để tối ưu hiệu suất. Nếu thông tin không có trong cache, truy vấn Firestore và lưu vào cache để sử dụng cho các mục tiếp theo.
        successfulOrders.forEach { orderDocument ->
            val order = orderDocument.toObject(Order::class.java)
                ?.let { if (it.id.isBlank()) it.copy(id = orderDocument.id) else it }
                ?: return@forEach

            val orderItems = orderItemsCollection
                .whereEqualTo("orderId", order.id)
                .get()
                .await()
                .documents
                .mapNotNull { document ->
                    document.toObject(OrderItem::class.java)?.let { item ->
                        if (item.id.isBlank()) item.copy(id = document.id) else item
                    }
                }
        // Nếu đơn hàng không có mục nào, bỏ qua để tránh thực hiện các thao tác không cần thiết với Firestore.
            orderItems.forEach { item ->
                if (item.id.isBlank() || existingPayoutIds.contains(item.id)) return@forEach

                val course = courseCache.getOrPut(item.courseId) {
                    coursesCollection.document(item.courseId).get().await().toObject(Course::class.java)
                }
                val instructorId = item.instructorId.ifBlank { course?.instructorId.orEmpty() }
                val instructor = if (instructorId.isBlank()) {
                    null
                } else {
                    instructorCache.getOrPut(instructorId) {
                        instructorsCollection.document(instructorId).get().await().toObject(Instructor::class.java)
                    }
                }
                val student = if (order.userId.isBlank()) {
                    null
                } else {
                    userCache.getOrPut(order.userId) {
                        usersCollection.document(order.userId).get().await().toObject(User::class.java)
                    }
                }
                // Tạo bản ghi chuyển tiền cho mục đơn hàng nếu chưa tồn tại, sử dụng thông tin đã lấy về từ khóa học, giảng viên, và người dùng để điền vào các trường tương ứng. Sử dụng `set()` với `SetOptions.merge()` để đảm bảo rằng nếu có bản ghi nào đó đã tồn tại với ID trùng khớp nhưng thiếu một số trường, các trường mới sẽ được thêm vào mà không ghi đè toàn bộ bản ghi cũ. Sau khi tạo xong bản ghi chuyển tiền, thêm ID của mục đơn hàng vào tập hợp `existingPayoutIds` để tránh tạo trùng lặp nếu có nhiều mục đơn hàng liên quan đến cùng một đơn hàng.
                payoutsCollection
                    .document(item.id)
                    .set(
                        mapOf(
                            "id" to item.id,
                            "orderId" to order.id,
                            "orderItemId" to item.id,
                            "courseId" to item.courseId,
                            "courseTitle" to item.courseTitle.ifBlank { course?.title.orEmpty() },
                            "courseThumbnailUrl" to item.courseThumbnailUrl.ifBlank { course?.thumbnailUrl.orEmpty() },
                            "studentId" to order.userId,
                            "studentName" to student?.fullName.orEmpty(),
                            "instructorId" to instructorId,
                            "instructorName" to item.instructorName.ifBlank { course?.instructorName.orEmpty() },
                            "grossAmount" to item.coursePrice,
                            "payoutAmount" to item.coursePrice,
                            "payoutStatus" to PayoutStatus.PENDING.name,
                            "orderConfirmedAt" to order.confirmedAt,
                            "paidAt" to 0L,
                            "paidByAdminUid" to "",
                            "manualTransferReference" to "",
                            "bankName" to instructor?.bankName.orEmpty(),
                            "bankCode" to instructor?.bankCode.orEmpty(),
                            "bankAccountNumber" to instructor?.bankAccountNumber.orEmpty(),
                            "bankAccountHolder" to instructor?.bankAccountHolder.orEmpty(),
                            "hasBankInfo" to hasBankInfo(instructor)
                        ),
                        SetOptions.merge()
                    )
                    .await()

                existingPayoutIds += item.id
            }
        }
    }
    /**
     * Thực hiện phần xử lý chính của luồng nghiệp vụ hoặc giao diện tương ứng.
        * Hàm này thường làm việc với Firestore hoặc API ngoài và trả kết quả về dạng `ResultState` cho tầng gọi phía trên.
    */
    private fun hasBankInfo(instructor: Instructor?): Boolean {
        if (instructor == null) return false
        return instructor.bankName.isNotBlank() &&
            instructor.bankAccountNumber.isNotBlank() &&
            instructor.bankAccountHolder.isNotBlank()
    }
}
