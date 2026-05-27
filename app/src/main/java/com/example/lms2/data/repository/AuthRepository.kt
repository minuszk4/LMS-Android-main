package com.example.lms2.data.repository

import com.example.lms2.BuildConfig
import com.example.lms2.data.cache.CacheTTL
import com.example.lms2.data.cache.RepositoryCache
import com.example.lms2.data.model.InstructorApplication
import com.example.lms2.data.model.InstructorApplicationStatus
import com.example.lms2.data.model.User
import com.example.lms2.data.model.UserRole
import com.example.lms2.data.paging.PageRequest
import com.example.lms2.data.paging.PageResult
import com.example.lms2.util.ResultState
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/**
 * Triển khai repository AuthRepository cho ứng dụng LMS Android.
 * File này chịu trách nhiệm làm việc với Firestore hoặc API ngoài, đồng thời chuyển đổi kết quả về dạng phù hợp cho ViewModel.
 * Repository là ranh giới chính giữa tầng giao diện và tầng dữ liệu nên được mô tả rõ để thuận tiện cho tài liệu kỹ thuật.
 */

class AuthRepository {

    companion object {
        private const val USERS_CACHE_PREFIX = "users:admin"
        private const val PENDING_INSTRUCTOR_CACHE_PREFIX = "users:instructor_pending"
    }

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
    private val httpClient = OkHttpClient()

    /**
     * Đọc hồ sơ người dùng từ collection `users`.
     *
     * Hàm này được dùng sau đăng nhập hoặc ở các màn hình hồ sơ khi cần tải đầy đủ
     * thông tin role, avatar, trạng thái xét duyệt giảng viên và các metadata khác.
     */
    suspend fun getUserDetails(uid: String): ResultState<User> {
        return try {
            val snapshot = firestore.collection("users").document(uid).get().await()
            val user = snapshot.toObject(User::class.java)
            if (user != null) {
                ResultState.Success(user)
            } else {
                ResultState.Error("Không tìm thấy thông tin người dùng")
            }
        } catch (e: Exception) {
            ResultState.Error(e.localizedMessage ?: "Lỗi khi lấy thông tin người dùng")
        }
    }

    /**
     * Đăng nhập bằng email và mật khẩu.
     *
     * Sau khi Firebase xác thực thành công, repository tiếp tục bảo đảm role admin cấu hình
     * sẵn được đồng bộ về Firestore và kiểm tra trạng thái hoạt động của tài khoản trước khi
     * trả `uid` cho tầng gọi phía trên.
     */
    suspend fun login(
        email: String,
        password: String
    ): ResultState<String> {
        return try {
            val result = auth
                .signInWithEmailAndPassword(email.trim(), password)
                .await()
            val user = result.user
            if (user != null) {
                ensureConfiguredAdminRole(user.uid)
                enforceUserIsActive(user.uid)
                ResultState.Success(user.uid)
            } else {
                ResultState.Error("Không tìm thấy tài khoản")
            }
        } catch (e: FirebaseAuthException) {
            ResultState.Error(mapFirebaseLoginError(e.errorCode))
        } catch (e: Exception) {
            ResultState.Error("Lỗi hệ thống: ${e.localizedMessage}")
        }
    }

    /**
     * Đăng ký tài khoản mới bằng email và mật khẩu.
     *
     * Sau khi Firebase Authentication tạo user thành công, repository tiếp tục tạo
     * document tương ứng trong Firestore để lưu role, họ tên và các trạng thái nghiệp vụ.
     */
    suspend fun register(
        email: String,
        password: String,
        fullName: String
    ): ResultState<String> {
        return try {
            val result = auth
                .createUserWithEmailAndPassword(email.trim(), password)
                .await()
            
            val firebaseUser = result.user
            
            if (firebaseUser != null) {
                val newUser = User(
                    uid = firebaseUser.uid,
                    fullName = fullName,
                    email = email.trim(),
                    role = getInitialRole(firebaseUser.uid),
                    instructorRequestStatus = InstructorApplicationStatus.NONE,
                    instructorRequestSubmittedAt = null,
                    createdAt = System.currentTimeMillis()
                )

                firestore.collection("users")
                    .document(firebaseUser.uid)
                    .set(newUser)
                    .await()

                ResultState.Success(firebaseUser.uid)
            } else {
                ResultState.Error("Đăng ký thất bại")
            }
        } catch (e: FirebaseAuthException) {
            ResultState.Error(mapFirebaseRegisterError(e.errorCode))
        } catch (e: Exception) {
            ResultState.Error("Lỗi khi lưu dữ liệu: ${e.localizedMessage}")
        }
    }

    /**
     * Đăng nhập bằng Google Sign-In.
     *
     * Nếu người dùng đăng nhập lần đầu, repository sẽ tạo hồ sơ mới trong Firestore
     * trước khi tiếp tục các bước kiểm tra quyền admin cấu hình sẵn và trạng thái hoạt động.
     */
    suspend fun signInWithGoogle(idToken: String): ResultState<String> {
        return try {
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            val result = auth.signInWithCredential(credential).await()
            val firebaseUser = result.user
            
            if (firebaseUser != null) {
                val userDoc = firestore.collection("users").document(firebaseUser.uid).get().await()
                
                if (!userDoc.exists()) {
                    val newUser = User(
                        uid = firebaseUser.uid,
                        fullName = firebaseUser.displayName ?: "Người dùng Google",
                        email = firebaseUser.email ?: "",
                        role = getInitialRole(firebaseUser.uid),
                        avatarUrl = firebaseUser.photoUrl?.toString(),
                        createdAt = System.currentTimeMillis()
                    )
                    firestore.collection("users").document(firebaseUser.uid).set(newUser).await()
                }

                ensureConfiguredAdminRole(firebaseUser.uid)
                enforceUserIsActive(firebaseUser.uid)
                
                ResultState.Success(firebaseUser.uid)
            } else {
                ResultState.Error("Đăng nhập Google thất bại")
            }
        } catch (e: Exception) {
            ResultState.Error("Lỗi đăng nhập Google: ${e.localizedMessage}")
        }
    }

    /**
     * Gửi email khôi phục mật khẩu thông qua Firebase Authentication.
     */
    suspend fun sendPasswordResetEmail(email: String): ResultState<Unit> {
        return try {
            auth.sendPasswordResetEmail(email.trim()).await()
            ResultState.Success(Unit)
        } catch (e: FirebaseAuthException) {
            ResultState.Error(mapFirebaseResetError(e.errorCode))
        } catch (e: Exception) {
            ResultState.Error("Gửi email khôi phục thất bại: ${e.localizedMessage}")
        }
    }

    /**
     * Cập nhật các trường hồ sơ cơ bản của người dùng.
     *
     * Repository dùng `SetOptions.merge()` để chỉ ghi đè các trường được phép sửa,
     * tránh làm mất dữ liệu khác đang tồn tại trong document người dùng.
     */
    suspend fun updateProfile(
        uid: String,
        fullName: String,
        avatarUrl: String?
    ): ResultState<Unit> {
        return try {
            val updates = mapOf(
                "fullName" to fullName.trim(),
                "avatarUrl" to avatarUrl?.trim().orEmpty().ifBlank { null }
            )

            firestore.collection("users")
                .document(uid)
                .set(updates, SetOptions.merge())
                .await()

            ResultState.Success(Unit)
        } catch (e: Exception) {
            ResultState.Error(e.localizedMessage ?: "Không thể cập nhật thông tin tài khoản")
        }
    }

    /**
     * Gửi yêu cầu đăng ký trở thành giảng viên.
     *
     * Trước khi ghi dữ liệu, repository chuẩn hóa nội dung nhập vào và kiểm tra những
     * điều kiện tối thiểu như kinh nghiệm, thông tin chuyên môn và trạng thái role hiện tại.
     */
    suspend fun submitInstructorApplication(
        uid: String,
        application: InstructorApplication
    ): ResultState<Unit> {
        val sanitized = application.copy(
            expertise = application.expertise.trim(),
            qualification = application.qualification.trim(),
            bio = application.bio.trim(),
            portfolioUrl = application.portfolioUrl.trim(),
            bankAccountName = application.bankAccountName.trim(),
            bankAccountNumber = application.bankAccountNumber.trim(),
            bankName = application.bankName.trim(),
            experienceYears = application.experienceYears.coerceAtLeast(0)
        )

        if (sanitized.expertise.isBlank() || sanitized.qualification.isBlank() || sanitized.bio.isBlank()) {
            return ResultState.Error("Vui lòng điền đầy đủ chuyên môn, bằng cấp và mô tả kinh nghiệm")
        }

        if (sanitized.experienceYears <= 0) {
            return ResultState.Error("Số năm kinh nghiệm phải lớn hơn 0")
        }

        return try {
            val userRef = firestore.collection("users").document(uid)

            /**
             * Transaction được dùng để đảm bảo việc kiểm tra vai trò hiện tại và cập nhật
             * trạng thái đăng ký giảng viên diễn ra trên cùng một ảnh chụp dữ liệu.
             */
            firestore.runTransaction { transaction ->
                val userSnapshot = transaction.get(userRef)
                val user = userSnapshot.toObject(User::class.java)
                    ?: throw IllegalStateException("Không tìm thấy thông tin người dùng")

                if (user.role == UserRole.ADMIN || user.role == UserRole.INSTRUCTOR) {
                    throw IllegalStateException("Tài khoản hiện tại không cần gửi đăng ký giảng viên")
                }

                if (user.instructorRequestStatus == InstructorApplicationStatus.PENDING) {
                    throw IllegalStateException("Đơn đăng ký đang chờ admin phê duyệt")
                }

                transaction.set(
                    userRef,
                    mapOf(
                        "instructorRequestStatus" to InstructorApplicationStatus.PENDING.name,
                        "instructorRequestSubmittedAt" to System.currentTimeMillis(),
                        "instructorRequestReviewedAt" to null,
                        "instructorRequestReviewedBy" to null,
                        "instructorRequestRejectReason" to null,
                        "instructorApplication" to sanitized
                    ),
                    SetOptions.merge()
                )
            }.await()

            ResultState.Success(Unit)
        } catch (e: Exception) {
            ResultState.Error(e.localizedMessage ?: "Không thể gửi đăng ký giảng viên")
        }
    }

    /**
     * Tải toàn bộ danh sách tài khoản đang chờ duyệt giảng viên.
     *
     * Hàm lặp qua nhiều trang dữ liệu nhỏ để tránh một truy vấn quá lớn và vẫn trả về
     * kết quả gộp cho màn hình quản trị.
     */
    suspend fun getPendingInstructorApplications(): ResultState<List<User>> {
        return try {
            val users = mutableListOf<User>()
            var cursor: String? = null
            var hasMore = true

            while (hasMore) {
                when (
                    val pageResult = getPendingInstructorApplicationsPage(
                        PageRequest(pageSize = 100, cursor = cursor, useCache = true)
                    )
                ) {
                    is ResultState.Success -> {
                        users += pageResult.data.items
                        cursor = pageResult.data.nextCursor
                        hasMore = pageResult.data.hasMore
                    }

                    is ResultState.Error -> return ResultState.Error(pageResult.message)
                    ResultState.Loading -> return ResultState.Loading
                }
            }

            ResultState.Success(users)
        } catch (e: Exception) {
            ResultState.Error(e.localizedMessage ?: "Không tải được danh sách chờ duyệt")
        }
    }

    /**
     * Lấy dữ liệu hoặc trạng thái cần thiết cho luồng hiện tại.
     * Hàm này thường làm việc với Firestore hoặc API ngoài và trả kết quả về dạng `ResultState` cho tầng gọi phía trên.
     */

    suspend fun getPendingInstructorApplicationsPage(
        pageRequest: PageRequest = PageRequest()
    ): ResultState<PageResult<User>> {
        return try {
            val cacheKey = buildPendingInstructorCacheKey(pageRequest)
            if (pageRequest.useCache && !pageRequest.refresh) {
                RepositoryCache.get<PageResult<User>>(cacheKey)?.let {
                    return ResultState.Success(it.copy(fromCache = true))
                }
            }

            var query = firestore.collection("users")
                .whereEqualTo("instructorRequestStatus", InstructorApplicationStatus.PENDING.name)
                .orderBy("instructorRequestSubmittedAt", Query.Direction.DESCENDING)
                .limit((pageRequest.normalizedPageSize + 1).toLong())

            val cursorId = pageRequest.cursor
            if (!cursorId.isNullOrBlank()) {
                val cursorSnapshot = firestore.collection("users").document(cursorId).get().await()
                if (cursorSnapshot.exists()) {
                    query = query.startAfter(cursorSnapshot)
                }
            }

            val snapshot = query.get().await()
            val rawItems = snapshot.documents.mapNotNull { doc ->
                doc.toObject(User::class.java)?.let { user ->
                    if (user.uid.isBlank()) user.copy(uid = doc.id) else user
                }
            }

            val hasMore = rawItems.size > pageRequest.normalizedPageSize
            val pageItems = if (hasMore) rawItems.take(pageRequest.normalizedPageSize) else rawItems
            val nextCursor = if (hasMore) pageItems.lastOrNull()?.uid else null
            val pageResult = PageResult(
                items = pageItems,
                nextCursor = nextCursor,
                hasMore = hasMore,
                fromCache = false
            )

            RepositoryCache.put(cacheKey, pageResult, CacheTTL.SHORT)
            ResultState.Success(pageResult)
        } catch (e: Exception) {
            ResultState.Error(e.localizedMessage ?: "Không tải được danh sách chờ duyệt")
        }
    }

    /**
     * Thực hiện phần xử lý chính của luồng nghiệp vụ hoặc giao diện tương ứng.
     * Hàm này thường làm việc với Firestore hoặc API ngoài và trả kết quả về dạng `ResultState` cho tầng gọi phía trên.
     */
    suspend fun approveInstructorApplication(targetUid: String, adminUid: String): ResultState<Unit> {
        return try {
            val userRef = firestore.collection("users").document(targetUid)
            val instructorRef = firestore.collection("instructors").document(targetUid)

            firestore.runTransaction { transaction ->
                val userSnapshot = transaction.get(userRef)
                val user = userSnapshot.toObject(User::class.java)
                    ?: throw IllegalStateException("Không tìm thấy người dùng cần duyệt")

                if (user.instructorRequestStatus != InstructorApplicationStatus.PENDING) {
                    throw IllegalStateException("Đơn đăng ký không còn ở trạng thái chờ duyệt")
                }
                // Chuẩn bị dữ liệu cho document giảng viên mới dựa trên thông tin từ đơn đăng ký
                // Sử dụng `SetOptions.merge()` khi tạo document giảng viên để tránh ghi đè lên các trường khác nếu document đã tồn tại (trường hợp admin duyệt lại đơn của giảng viên cũ)
                // Dữ liệu này sẽ được dùng để tạo hoặc cập nhật document trong collection `instructors` tương ứng với `uid` của người dùng
                val instructorData = mutableMapOf<String, Any>("uid" to targetUid)
                user.instructorApplication?.let { app ->
                    if (app.expertise.isNotBlank()) instructorData["expertise"] = app.expertise
                    if (app.experienceYears > 0) instructorData["experienceYears"] = app.experienceYears
                    if (app.qualification.isNotBlank()) instructorData["qualification"] = app.qualification
                    if (app.bankAccountName.isNotBlank()) instructorData["bankAccountHolder"] = app.bankAccountName
                    if (app.bankAccountNumber.isNotBlank()) instructorData["bankAccountNumber"] = app.bankAccountNumber
                    if (app.bankName.isNotBlank()) instructorData["bankName"] = app.bankName
                    if (app.portfolioUrl.isNotBlank()) instructorData["portfolioUrl"] = app.portfolioUrl
                    if (app.bio.isNotBlank()) instructorData["bio"] = app.bio
                }
                // Cập nhật đồng thời role của người dùng thành INSTRUCTOR và tạo document giảng viên mới trong collection `instructors`
                transaction.set(
                    userRef,
                    mapOf(
                        "role" to UserRole.INSTRUCTOR.name,
                        "instructorRequestStatus" to InstructorApplicationStatus.APPROVED.name,
                        "instructorRequestReviewedAt" to System.currentTimeMillis(),
                        "instructorRequestReviewedBy" to adminUid,
                        "instructorRequestRejectReason" to null
                    ),
                    SetOptions.merge()
                )

                transaction.set(
                    instructorRef,
                    instructorData,
                    SetOptions.merge()
                )
            }.await()
            // Sau khi duyệt xong, cần làm mới cache liên quan đến danh sách chờ duyệt và danh sách người dùng để đảm bảo dữ liệu hiển thị ở tầng giao diện là mới nhất
            RepositoryCache.invalidateByPrefix(PENDING_INSTRUCTOR_CACHE_PREFIX)
            RepositoryCache.invalidateByPrefix(USERS_CACHE_PREFIX)

            ResultState.Success(Unit)
        } catch (e: Exception) {
            ResultState.Error(e.localizedMessage ?: "Không thể phê duyệt giảng viên")
        }
    }

    /**
     * Thực hiện phần xử lý chính của luồng nghiệp vụ hoặc giao diện tương ứng.
     * Hàm này thường làm việc với Firestore hoặc API ngoài và trả kết quả về dạng `ResultState` cho tầng gọi phía trên.
     */

    suspend fun rejectInstructorApplication(targetUid: String, adminUid: String, reason: String): ResultState<Unit> {
        return try {
            val userRef = firestore.collection("users").document(targetUid)
            val rejectReason = reason.trim().ifBlank { "Không đáp ứng điều kiện hiện tại" }
            // Dùng transaction để đảm bảo việc kiểm tra trạng thái đơn và cập nhật đồng thời các trường liên quan diễn ra trên cùng một snapshot dữ liệu, tránh tình
            firestore.runTransaction { transaction ->
                val userSnapshot = transaction.get(userRef)
                val user = userSnapshot.toObject(User::class.java)
                    ?: throw IllegalStateException("Không tìm thấy người dùng cần duyệt")

                if (user.instructorRequestStatus != InstructorApplicationStatus.PENDING) {
                    throw IllegalStateException("Đơn đăng ký không còn ở trạng thái chờ duyệt")
                }
                // Cập nhật trạng thái đơn đăng ký thành REJECTED và lưu lại lý do từ chối cùng thông tin người admin đã duyệt
                // Sử dụng `SetOptions.merge()` để chỉ cập nhật các trường liên quan đến xét duyệt giảng viên mà không làm ảnh hưởng đến các dữ liệu khác của người dùng
                transaction.set(
                    userRef,
                    mapOf(
                        "instructorRequestStatus" to InstructorApplicationStatus.REJECTED.name,
                        "instructorRequestReviewedAt" to System.currentTimeMillis(),
                        "instructorRequestReviewedBy" to adminUid,
                        "instructorRequestRejectReason" to rejectReason
                    ),
                    SetOptions.merge()
                )
            }.await()
            // Sau khi từ chối xong, cần làm mới cache liên quan đến danh sách chờ duyệt và danh sách người dùng để đảm bảo dữ liệu hiển thị ở tầng giao diện là mới nhất
            RepositoryCache.invalidateByPrefix(PENDING_INSTRUCTOR_CACHE_PREFIX)
            RepositoryCache.invalidateByPrefix(USERS_CACHE_PREFIX)

            ResultState.Success(Unit)
        } catch (e: Exception) {
            ResultState.Error(e.localizedMessage ?: "Không thể từ chối đăng ký")
        }
    }

    /**
     * Lấy dữ liệu hoặc trạng thái cần thiết cho luồng hiện tại.
     * Hàm này thường làm việc với Firestore hoặc API ngoài và trả kết quả về dạng `ResultState` cho tầng gọi phía trên.
     */

    suspend fun getAllUsers(): ResultState<List<User>> {
        return try {
            val users = mutableListOf<User>()
            var cursor: String? = null
            var hasMore = true

            while (hasMore) {
                when (
                    val pageResult = getAllUsersPage(
                        PageRequest(pageSize = 100, cursor = cursor, useCache = true)
                    )
                ) {
                    is ResultState.Success -> {
                        users += pageResult.data.items
                        cursor = pageResult.data.nextCursor
                        hasMore = pageResult.data.hasMore
                    }

                    is ResultState.Error -> return ResultState.Error(pageResult.message)
                    ResultState.Loading -> return ResultState.Loading
                }
            }

            ResultState.Success(users)
        } catch (e: Exception) {
            ResultState.Error(e.localizedMessage ?: "Không tải được danh sách người dùng")
        }
    }

    /**
     * Lấy dữ liệu hoặc trạng thái cần thiết cho luồng hiện tại.
     * Hàm này thường làm việc với Firestore hoặc API ngoài và trả kết quả về dạng `ResultState` cho tầng gọi phía trên.
     */

    suspend fun getAllUsersPage(
        pageRequest: PageRequest = PageRequest()
    ): ResultState<PageResult<User>> {
        return try {
            val cacheKey = buildUsersCacheKey(pageRequest)
            if (pageRequest.useCache && !pageRequest.refresh) {
                RepositoryCache.get<PageResult<User>>(cacheKey)?.let {
                    return ResultState.Success(it.copy(fromCache = true))
                }
            }

            var query = firestore.collection("users")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit((pageRequest.normalizedPageSize + 1).toLong())

            val cursorId = pageRequest.cursor
            if (!cursorId.isNullOrBlank()) {
                val cursorSnapshot = firestore.collection("users").document(cursorId).get().await()
                if (cursorSnapshot.exists()) {
                    query = query.startAfter(cursorSnapshot)
                }
            }

            val snapshot = query.get().await()
            val rawItems = snapshot.documents.mapNotNull { doc ->
                doc.toObject(User::class.java)?.let { user ->
                    if (user.uid.isBlank()) user.copy(uid = doc.id) else user
                }
            }

            val hasMore = rawItems.size > pageRequest.normalizedPageSize
            val pageItems = if (hasMore) rawItems.take(pageRequest.normalizedPageSize) else rawItems
            val nextCursor = if (hasMore) pageItems.lastOrNull()?.uid else null
            val pageResult = PageResult(
                items = pageItems,
                nextCursor = nextCursor,
                hasMore = hasMore,
                fromCache = false
            )

            RepositoryCache.put(cacheKey, pageResult, CacheTTL.SHORT)
            ResultState.Success(pageResult)
        } catch (e: Exception) {
            ResultState.Error(e.localizedMessage ?: "Không tải được danh sách người dùng")
        }
    }

    /**
     * Thực hiện phần xử lý chính của luồng nghiệp vụ hoặc giao diện tương ứng.
     * Hàm này thường làm việc với Firestore hoặc API ngoài và trả kết quả về dạng `ResultState` cho tầng gọi phía trên.
     */

    suspend fun setUserActive(uid: String, isActive: Boolean): ResultState<Unit> {
        return try {
            // Cập nhật trường `isActive` của người dùng trong Firestore để kích hoạt hoặc tạm khóa tài khoản
            firestore.collection("users")
                .document(uid)
                .set(mapOf("isActive" to isActive), SetOptions.merge())
                .await()
            // Sau khi cập nhật xong, cần làm mới cache liên quan đến danh sách người dùng để đảm bảo dữ liệu hiển thị ở tầng giao diện là mới nhất
            RepositoryCache.invalidateByPrefix(USERS_CACHE_PREFIX)

            ResultState.Success(Unit)
        } catch (e: Exception) {
            ResultState.Error(e.localizedMessage ?: "Không thể cập nhật trạng thái người dùng")
        }
    }

    /**
     * Tạo mới dữ liệu nghiệp vụ dựa trên đầu vào hiện tại.
     * Hàm này thường làm việc với Firestore hoặc API ngoài và trả kết quả về dạng `ResultState` cho tầng gọi phía trên.
     */

    suspend fun createInstructorAccountByAdmin(
        adminUid: String,
        email: String,
        password: String,
        fullName: String
    ): ResultState<Unit> {
        return try {
            // Chuẩn hóa dữ liệu đầu vào để tránh lỗi do khoảng trắng thừa hoặc dữ liệu không hợp lệ
            val sanitizedEmail = email.trim()
            val sanitizedName = fullName.trim()

            if (sanitizedName.isBlank()) {
                return ResultState.Error("Vui lòng nhập họ tên giảng viên")
            }

            if (sanitizedEmail.isBlank()) {
                return ResultState.Error("Vui lòng nhập email")
            }

            if (password.length < 6) {
                return ResultState.Error("Mật khẩu phải có ít nhất 6 ký tự")
            }
            // Kiểm tra quyền admin của người thực hiện thao tác trước khi tiếp tục tạo tài khoản giảng viên mới
            val adminRole = getUserRole(adminUid)
            if (adminRole != UserRole.ADMIN) {
                return ResultState.Error("Chỉ admin mới có quyền tạo tài khoản giảng viên")
            }
            // Sử dụng Firebase Authentication REST API để tạo tài khoản người dùng mới với email và mật khẩu, sau đó lấy `uid` của tài khoản vừa tạo để tiếp tục các bước xử lý tiếp theo
            val apiKey = auth.app.options.apiKey.orEmpty().trim()
            if (apiKey.isBlank()) {
                return ResultState.Error("Thiếu Firebase API key, không thể tạo tài khoản")
            }
            // Endpoint này là URL chuẩn của Firebase Authentication REST API để tạo user mới, trong đó `key` là tham số bắt buộc để xác thực yêu cầu với Firebase
            val endpoint = "https://identitytoolkit.googleapis.com/v1/accounts:signUp?key=$apiKey"
            val payload = JSONObject()
                .put("email", sanitizedEmail)
                .put("password", password)
                .put("returnSecureToken", false)
            // Tạo request POST với payload JSON để gửi đến Firebase Authentication REST API và nhận về phản hồi chứa `uid` của tài khoản mới tạo
            val request = Request.Builder()
                .url(endpoint)
                .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()
            // Thực hiện yêu cầu HTTP trong context IO để tránh block thread chính, sau đó xử lý phản hồi để lấy `uid` hoặc lỗi nếu có
            val userUid = withContext(Dispatchers.IO) {
                httpClient.newCall(request).execute().use { response ->
                    val responseBody = response.body?.string().orEmpty()

                    if (!response.isSuccessful) {
                        val firebaseCode = runCatching {
                            JSONObject(responseBody).optJSONObject("error")?.optString("message").orEmpty()
                        }.getOrDefault("")
                        val errorMessage = mapIdentityToolkitError(firebaseCode)
                        throw IllegalStateException(errorMessage)
                    }

                    val json = JSONObject(responseBody)
                    val localId = json.optString("localId")
                    if (localId.isBlank()) {
                        throw IllegalStateException("Không thể tạo tài khoản giảng viên")
                    }
                    localId
                }
            }
            // Sau khi có `uid` của tài khoản mới, tiếp tục tạo document trong Firestore để lưu thông tin role, họ tên và trạng thái xét duyệt giảng viên. Sử dụng batch để đảm bảo tính nguyên tử giữa việc tạo user và document giảng viên
            firestore.runBatch { batch ->
                val userRef = firestore.collection("users").document(userUid)
                val instructorRef = firestore.collection("instructors").document(userUid)
                // Dữ liệu này sẽ được dùng để tạo document người dùng mới với role INSTRUCTOR và trạng thái xét duyệt đã được phê duyệt, đồng thời tạo document giảng viên mới trong collection `instructors` với thông tin cơ bản
                batch.set(
                    userRef,
                    User(
                        uid = userUid,
                        fullName = sanitizedName,
                        email = sanitizedEmail,
                        role = UserRole.INSTRUCTOR,
                        instructorRequestStatus = InstructorApplicationStatus.APPROVED,
                        instructorRequestReviewedAt = System.currentTimeMillis(),
                        instructorRequestReviewedBy = adminUid,
                        createdAt = System.currentTimeMillis()
                    ),
                    SetOptions.merge()
                )
                // Sử dụng `SetOptions.merge()` khi tạo document giảng viên để tránh ghi đè lên các trường khác nếu document đã tồn tại (trường hợp admin tạo lại tài khoản cho giảng viên cũ)
                batch.set(
                    instructorRef,
                    mapOf("uid" to userUid),
                    SetOptions.merge()
                )
            }.await()
            // Sau khi tạo xong, cần làm mới cache liên quan đến danh sách người dùng và danh sách chờ duyệt để đảm bảo dữ liệu hiển thị ở tầng giao diện là mới nhất
            RepositoryCache.invalidateByPrefix(USERS_CACHE_PREFIX)
            RepositoryCache.invalidateByPrefix(PENDING_INSTRUCTOR_CACHE_PREFIX)

            ResultState.Success(Unit)
        } catch (e: Exception) {
            ResultState.Error(e.localizedMessage ?: "Không thể tạo tài khoản giảng viên")
        }
    }

    /**
     * Đăng xuất người dùng khỏi phiên làm việc hiện tại.
     * Hàm này thường làm việc với Firestore hoặc API ngoài và trả kết quả về dạng `ResultState` cho tầng gọi phía trên.
     */

    fun logout() {
        auth.signOut()
    }

    /**
     * Kiểm tra điều kiện nghiệp vụ trước khi tiếp tục các bước xử lý kế tiếp.
     * Hàm này thường làm việc với Firestore hoặc API ngoài và trả kết quả về dạng `ResultState` cho tầng gọi phía trên.
     */

    fun isUserLoggedIn(): Boolean {
        return auth.currentUser != null
    }

    /**
     * Lấy dữ liệu hoặc trạng thái cần thiết cho luồng hiện tại.
     * Hàm này thường làm việc với Firestore hoặc API ngoài và trả kết quả về dạng `ResultState` cho tầng gọi phía trên.
     */

    fun getCurrentUserId(): String? {
        return auth.currentUser?.uid
    }

    private fun getInitialRole(uid: String): UserRole {
        return if (isConfiguredAdmin(uid)) UserRole.ADMIN else UserRole.STUDENT
    }

    private fun isConfiguredAdmin(uid: String): Boolean {
        val configuredAdminUid = BuildConfig.ADMIN_UID.trim()
        return configuredAdminUid.isNotBlank() && uid == configuredAdminUid
    }

    private suspend fun ensureConfiguredAdminRole(uid: String) {
        if (!isConfiguredAdmin(uid)) return

        firestore.collection("users")
            .document(uid)
            .set(mapOf("role" to UserRole.ADMIN.name), SetOptions.merge())
            .await()
    }

    private suspend fun enforceUserIsActive(uid: String) {
        val userSnapshot = firestore.collection("users").document(uid).get().await()
        val user = userSnapshot.toObject(User::class.java) ?: return

        if (!user.isActive) {
            auth.signOut()
            throw IllegalStateException("Tài khoản của bạn đã bị tạm khóa. Vui lòng liên hệ quản trị viên.")
        }
    }

    private suspend fun getUserRole(uid: String): UserRole? {
        val userSnapshot = firestore.collection("users").document(uid).get().await()
        return userSnapshot.toObject(User::class.java)?.role
    }

    private fun buildUsersCacheKey(pageRequest: PageRequest): String {
        return "$USERS_CACHE_PREFIX:${pageRequest.normalizedPageSize}:${pageRequest.cursor ?: "first"}"
    }

    private fun buildPendingInstructorCacheKey(pageRequest: PageRequest): String {
        return "$PENDING_INSTRUCTOR_CACHE_PREFIX:${pageRequest.normalizedPageSize}:${pageRequest.cursor ?: "first"}"
    }

    /* ========================
       ERROR MAPPING
       ======================== */

    private fun mapFirebaseLoginError(code: String): String {
        return when (code) {
            "ERROR_INVALID_EMAIL" -> "Email không hợp lệ"
            "ERROR_USER_NOT_FOUND" -> "Tài khoản không tồn tại"
            "ERROR_WRONG_PASSWORD" -> "Mật khẩu không chính xác"
            "ERROR_INVALID_CREDENTIAL" -> "Email hoặc mật khẩu không chính xác"
            "ERROR_USER_DISABLED" -> "Tài khoản đã bị vô hiệu hóa"
            "ERROR_TOO_MANY_REQUESTS" -> "Quá nhiều yêu cầu. Vui lòng thử lại sau."
            else -> "Đăng nhập thất bại ($code)"
        }
    }

    private fun mapFirebaseRegisterError(code: String): String {
        return when (code) {
            "ERROR_INVALID_EMAIL" -> "Email không hợp lệ"
            "ERROR_EMAIL_ALREADY_IN_USE" -> "Email đã được sử dụng"
            "ERROR_WEAK_PASSWORD" -> "Mật khẩu quá yếu (tối thiểu 6 ký tự)"
            else -> "Đăng ký thất bại ($code)"
        }
    }

    private fun mapFirebaseResetError(code: String): String {
        return when (code) {
            "ERROR_INVALID_EMAIL" -> "Email không hợp lệ"
            "ERROR_USER_NOT_FOUND" -> "Email này chưa được đăng ký tài khoản"
            else -> "Gửi email thất bại ($code)"
        }
    }

    private fun mapIdentityToolkitError(code: String): String {
        return when (code) {
            "EMAIL_EXISTS" -> "Email đã được sử dụng"
            "INVALID_EMAIL" -> "Email không hợp lệ"
            "WEAK_PASSWORD : Password should be at least 6 characters" -> "Mật khẩu quá yếu (tối thiểu 6 ký tự)"
            else -> if (code.isBlank()) {
                "Không thể tạo tài khoản giảng viên"
            } else {
                "Không thể tạo tài khoản giảng viên ($code)"
            }
        }
    }
}
