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
 * Repository phụ trách bài toán payout cho giảng viên.
 *
 * Trách nhiệm chính của lớp này là:
 * - tổng hợp danh sách các khoản cần chi trả từ `orders` và `orderItems`,
 * - bảo đảm collection `instructorPayouts` luôn được đồng bộ nếu có đơn hàng thành công mới,
 * - cho phép admin đánh dấu một hoặc nhiều khoản payout là đã chuyển khoản,
 * - chuẩn hóa dữ liệu ngân hàng và thông tin học viên/giảng viên để màn hình admin hiển thị đầy đủ.
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
     * Tải toàn bộ danh sách payout cho admin.
     *
     * Trước khi đọc dữ liệu, repository chủ động gọi bước đồng bộ để tạo bổ sung
     * các payout còn thiếu từ những đơn hàng đã `SUCCESS`. Nhờ đó màn hình admin
     * luôn nhìn thấy bức tranh mới nhất ngay cả khi trước đó webhook/payment flow
     * chưa kịp sinh bản ghi payout tương ứng.
     */
    suspend fun getInstructorPayouts(): ResultState<List<InstructorPayout>> {
        return try {
            syncMissingPayoutsFromSuccessfulOrders()

            val snapshot = payoutsCollection
                .orderBy("orderConfirmedAt")
                .get()
                .await()

            val payouts = snapshot.documents
                .mapNotNull { document ->
                    document.toObject(InstructorPayout::class.java)?.let { payout ->
                        if (payout.id.isBlank()) payout.copy(id = document.id) else payout
                    }
                }
                .sortedByDescending { it.orderConfirmedAt }

            ResultState.Success(payouts)
        } catch (e: Exception) {
            ResultState.Error(e.message ?: "Khong tai duoc danh sach chuyen tien")
        }
    }

    /**
     * Đánh dấu một payout đơn lẻ là đã thanh toán.
     *
     * Hàm này chỉ là wrapper mỏng để tái sử dụng luồng cập nhật hàng loạt,
     * giúp mọi quy tắc cập nhật trạng thái đều đi qua cùng một nơi.
     */
    suspend fun markPayoutAsPaid(
        payoutId: String,
        adminUid: String,
        manualTransferReference: String
    ): ResultState<Unit> {
        return markPayoutsAsPaid(
            payoutIds = listOf(payoutId),
            adminUid = adminUid,
            manualTransferReference = manualTransferReference
        )
    }

    /**
     * Đánh dấu nhiều payout là đã chuyển khoản.
     *
     * Dùng batch update để tất cả khoản payout trong cùng một thao tác admin
     * được cập nhật đồng thời về:
     * - `payoutStatus = PAID`
     * - thời điểm thực chi `paidAt`
     * - admin thực hiện
     * - mã tham chiếu chuyển khoản thủ công nếu có
     */
    suspend fun markPayoutsAsPaid(
        payoutIds: List<String>,
        adminUid: String,
        manualTransferReference: String
    ): ResultState<Unit> {
        val normalizedIds = payoutIds.map { it.trim() }.filter { it.isNotBlank() }.distinct()
        if (normalizedIds.isEmpty()) return ResultState.Error("Thieu danh sach chuyen tien")
        if (adminUid.isBlank()) return ResultState.Error("Thieu tai khoan admin")

        return try {
            val paidAt = System.currentTimeMillis()
            val batch = firestore.batch()

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

            batch.commit().await()
            ResultState.Success(Unit)
        } catch (e: Exception) {
            ResultState.Error(e.message ?: "Khong cap nhat duoc trang thai chuyen tien")
        }
    }

    /**
     * Quét các đơn hàng thanh toán thành công để bù lại những payout còn thiếu.
     *
     * Mỗi `orderItem` về bản chất tương ứng với một khoản doanh thu của một giảng viên,
     * nên repository dùng `orderItem.id` làm khóa payout để tránh trùng lặp.
     * Khi tạo payout mới, hàm cố gắng điền thêm:
     * - thông tin khóa học,
     * - thông tin giảng viên,
     * - thông tin học viên,
     * - dữ liệu tài khoản ngân hàng để admin có thể chuyển tiền trực tiếp.
     */
    private suspend fun syncMissingPayoutsFromSuccessfulOrders() {
        val existingPayoutIds = payoutsCollection.get().await().documents.map { it.id }.toHashSet()
        val successfulOrders = ordersCollection
            .whereEqualTo("paymentStatus", "SUCCESS")
            .get()
            .await()
            .documents

        if (successfulOrders.isEmpty()) return

        val courseCache = mutableMapOf<String, Course?>()
        val instructorCache = mutableMapOf<String, Instructor?>()
        val userCache = mutableMapOf<String, User?>()

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
                        instructorsCollection.document(instructorId).get().await()
                            .toObject(Instructor::class.java)
                    }
                }

                val student = if (order.userId.isBlank()) {
                    null
                } else {
                    userCache.getOrPut(order.userId) {
                        usersCollection.document(order.userId).get().await().toObject(User::class.java)
                    }
                }

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
     * Kiểm tra nhanh xem hồ sơ giảng viên đã đủ dữ liệu ngân hàng để chuyển khoản chưa.
     *
     * Cờ này được lưu sẵn vào payout để màn hình admin có thể highlight những khoản
     * còn thiếu thông tin thanh toán mà không cần suy luận lại ở tầng UI.
     */
    private fun hasBankInfo(instructor: Instructor?): Boolean {
        if (instructor == null) return false
        return instructor.bankName.isNotBlank() &&
            instructor.bankAccountNumber.isNotBlank() &&
            instructor.bankAccountHolder.isNotBlank()
    }
}
