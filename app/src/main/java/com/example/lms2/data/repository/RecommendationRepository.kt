package com.example.lms2.data.repository

import com.example.lms2.BuildConfig
import com.example.lms2.data.model.Course
import com.example.lms2.data.model.CourseLevel
import com.example.lms2.util.ResultState
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.QuerySnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.exp

/**
 * Triển khai repository RecommendationRepository cho ứng dụng LMS Android.
 * File này chịu trách nhiệm làm việc với Firestore hoặc API ngoài, đồng thời chuyển đổi kết quả về dạng phù hợp cho ViewModel.
 * Repository là ranh giới chính giữa tầng giao diện và tầng dữ liệu nên được mô tả rõ để thuận tiện cho tài liệu kỹ thuật.
 */

class RecommendationRepository {

    private companion object {
        const val HEURISTIC_WEIGHT = 0.25
        const val BACKEND_WEIGHT = 0.75
        const val BACKEND_TIMEOUT_SECONDS = 12L
        const val BACKEND_RECOMMENDATION_PATH = "/recommendations"
        const val BACKEND_FEEDBACK_PATH = "/recommendation-feedback"
    }

    private val firestore = FirebaseFirestore.getInstance()
    private val coursesCollection = firestore.collection("courses")
    private val enrollmentsCollection = firestore.collection("enrollments")
    private val progressCollection = firestore.collection("progress")

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(BACKEND_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(BACKEND_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(BACKEND_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .callTimeout(BACKEND_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    private val recommendationBackendUrl: String
        get() = BuildConfig.RECOMMENDATION_API_URL.trim().trimEnd('/')

    /**
     * Lấy dữ liệu hoặc trạng thái cần thiết cho luồng hiện tại.
     * Hàm này thường làm việc với Firestore hoặc API ngoài và trả kết quả về dạng `ResultState` cho tầng gọi phía trên.
     * Hàm này được thiết kế để cung cấp danh sách các khóa học được đề xuất cho người dùng dựa trên lịch sử học tập, sở thích và các tín hiệu khác. Quá trình đề xuất có thể bao gồm việc lấy dữ liệu từ Firestore về các khóa học đã đăng ký, tiến trình học tập của người dùng, sau đó xây dựng một hồ sơ người dùng để gửi đến backend recommendation engine nhằm nhận được điểm số đề xuất cho từng khóa học. Kết quả cuối cùng sẽ là một danh sách các khóa học được sắp xếp theo điểm số đề xuất, kết hợp giữa tín hiệu từ backend và heuristic local để đảm bảo rằng ngay cả khi backend không trả về điểm số thì vẫn có thể cung cấp các đề xuất phù hợp dựa trên hồ sơ người dùng.
     */

    suspend fun getRecommendedCourses(
        userId: String,
        limit: Int = 10
    ): ResultState<List<Course>> {
        if (userId.isBlank()) return ResultState.Error("Thiếu thông tin người dùng")

        return try {
            // Recommendation chi co y nghia khi bo qua cac course ma user da enroll.
            val enrolledCourseIds = loadEnrolledCourseIds(userId)
            val allCourses = loadPublishedCourses()
            val availableCourses = allCourses.filter { it.id !in enrolledCourseIds }
            if (availableCourses.isEmpty()) {
                return ResultState.Success(emptyList())
            }

            // Cold-start fallback: chua co lich su thi xep hang bang quality/popularity signal.
            if (enrolledCourseIds.isEmpty()) {
                return ResultState.Success(rankFallbackCourses(availableCourses).take(limit))
            }
            // Neu da co lich su hoc tap, thi xay dung profile user de xin backend score cho tung course,
            // sau do tron backend score voi heuristic de co duoc danh sach recommendation hop profile hon.
            val progressSnapshot = progressCollection
                .whereEqualTo("userId", userId)
                .get()
                .await()
            // UserProfile la phan tom tat "so thich hoc tap" ma app tu xay
            val userProfile = buildUserProfile(
                userId = userId,
                enrolledCourses = allCourses.filter { it.id in enrolledCourseIds },
                progressSnapshot = progressSnapshot,
                courseById = allCourses.associateBy { it.id }
            )
            // Backend recommendation duoc thiet ke de chi tra ve score cho mot tap hop nho courseId ma user co kha nang quan tam nhat,
            val backendScores = fetchBackendRecommendations(
                userId = userId,
                limit = limit,
                candidateCourseIds = availableCourses.map { it.id },
                source = "app_home"
            )
            // Neu backend tra ve score, thi tron score do voi heuristic de uu tien cho nhung course vua hop profile user, vua co quality signal tot. Neu backend khong tra ve duoc score (do loi hoac cold-start), thi chi danh gia bang heuristic van co the dua ra danh sach recommendation kha hop profile.
            ResultState.Success(rankCourses(availableCourses, userProfile, backendScores).take(limit))
        } catch (_: Exception) {
            fallbackRecommendedCourses(limit = limit)
        }
    }

    /**
     * Lấy dữ liệu hoặc trạng thái cần thiết cho luồng hiện tại.
     * Hàm này thường làm việc với Firestore hoặc API ngoài và trả kết quả về dạng `ResultState` cho tầng gọi phía trên.
     */

    suspend fun getRecommendedCoursesByCategory(
        userId: String,
        categoryId: String,
        limit: Int = 5
    ): ResultState<List<Course>> {
        if (userId.isBlank()) return ResultState.Error("Thiếu thông tin người dùng")

        return try {
            // Nhanh hon va dung nghiep vu hon neu app tu loc theo category truoc,
            // sau do moi xin backend score cho dung tap con lai.
            val enrolledCourseIds = loadEnrolledCourseIds(userId)
            val allPublishedCourses = loadPublishedCourses()
            val categoryCourses = allPublishedCourses
                .filter { it.categoryId == categoryId && it.id !in enrolledCourseIds }

            if (categoryCourses.isEmpty()) {
                return ResultState.Success(emptyList())
            }

            val progressSnapshot = progressCollection
                .whereEqualTo("userId", userId)
                .get()
                .await()

            val userProfile = buildUserProfile(
                userId = userId,
                enrolledCourses = allPublishedCourses.filter { it.id in enrolledCourseIds },
                progressSnapshot = progressSnapshot,
                courseById = allPublishedCourses.associateBy { it.id }
            )

            val backendScores = fetchBackendRecommendations(
                userId = userId,
                limit = limit,
                candidateCourseIds = categoryCourses.map { it.id },
                categoryId = categoryId,
                source = "app_category"
            )

            ResultState.Success(rankCourses(categoryCourses, userProfile, backendScores).take(limit))
        } catch (_: Exception) {
            fallbackRecommendedCourses(limit = limit, categoryId = categoryId)
        }
    }

    private suspend fun fetchBackendRecommendations(
        userId: String,
        limit: Int,
        candidateCourseIds: List<String>,
        categoryId: String? = null,
        source: String = "app"
    ): Map<String, Double> {
        if (recommendationBackendUrl.isBlank()) return emptyMap()

        return withContext(Dispatchers.IO) {
            try {
                // Payload backend chi mang thong tin toi thieu can cho suy luan.
                val payload = JSONObject().apply {
                    put("userId", userId)
                    put("limit", limit)
                    put("candidateCourseIds", JSONArray(candidateCourseIds))
                    categoryId?.takeIf { it.isNotBlank() }?.let { put("categoryId", it) }
                    put("source", source)
                }

                val request = Request.Builder()
                    .url("$recommendationBackendUrl$BACKEND_RECOMMENDATION_PATH")
                    .post(
                        payload.toString().toRequestBody(
                            "application/json; charset=utf-8".toMediaType()
                        )
                    )
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    // Loi backend khong duoc lam vo home screen; khi do app se tu fallback.
                    if (!response.isSuccessful) return@withContext emptyMap()
                    val body = response.body?.string().orEmpty()
                    if (body.isBlank()) return@withContext emptyMap()
                    parseBackendScores(JSONObject(body), candidateCourseIds)
                }
            } catch (_: Exception) {
                emptyMap()
            }
        }
    }

    /**
     * Ghi nhận dữ liệu theo dõi hoặc phản hồi để phục vụ thống kê và tối ưu hệ thống.
     * Hàm này thường làm việc với Firestore hoặc API ngoài và trả kết quả về dạng `ResultState` cho tầng gọi phía trên.
     */

    suspend fun logRecommendationFeedback(
        userId: String,
        courseId: String,
        eventType: String,
        source: String,
        modelVersion: String? = null
    ): ResultState<Unit> {
        // Logging feedback la best-effort telemetry; khong co backend URL thi bo qua im lang.
        if (recommendationBackendUrl.isBlank()) return ResultState.Success(Unit)
        if (userId.isBlank() || courseId.isBlank() || eventType.isBlank()) return ResultState.Success(Unit)

        return withContext(Dispatchers.IO) {
            try {
                val payload = JSONObject().apply {
                    put("userId", userId)
                    put("courseId", courseId)
                    put("eventType", eventType)
                    put("source", source)
                    modelVersion?.takeIf { it.isNotBlank() }?.let { put("modelVersion", it) }
                }

                val request = Request.Builder()
                    .url("$recommendationBackendUrl$BACKEND_FEEDBACK_PATH")
                    .post(
                        payload.toString().toRequestBody(
                            "application/json; charset=utf-8".toMediaType()
                        )
                    )
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        ResultState.Success(Unit)
                    } else {
                        ResultState.Error("Feedback logging failed with code ${response.code}")
                    }
                }
            } catch (e: Exception) {
                ResultState.Error(e.message ?: "Feedback logging failed")
            }
        }
    }

    /**
     * Ghi nhận dữ liệu theo dõi hoặc phản hồi để phục vụ thống kê và tối ưu hệ thống.
     * Hàm này thường làm việc với Firestore hoặc API ngoài và trả kết quả về dạng `ResultState` cho tầng gọi phía trên.
     */

    suspend fun logRecommendationImpressions(
        userId: String,
        courseIds: List<String>,
        source: String,
        modelVersion: String? = null
    ) {
        if (userId.isBlank()) return
        // Impression duoc tach thanh tung event/course de backend co the thong ke CTR
        // va hoc online tu moi item da duoc show cho user.
        courseIds.distinct().forEach { courseId ->
            logRecommendationFeedback(
                userId = userId,
                courseId = courseId,
                eventType = "IMPRESSION",
                source = source,
                modelVersion = modelVersion
            )
        }
    }

    private fun parseBackendScores(
        json: JSONObject,
        candidateCourseIds: List<String>
    ): Map<String, Double> {
        // Backend co the tra nhieu hinh payload khac nhau qua cac version:
        // recommendations / courses / data, item la string hoac object co score.
        val array = when {
            json.has("recommendations") -> json.optJSONArray("recommendations")
            json.has("courses") -> json.optJSONArray("courses")
            json.has("data") -> json.optJSONArray("data")
            else -> null
        } ?: return emptyMap()

        val scores = mutableMapOf<String, Double>()

        for (index in 0 until array.length()) {
            when (val item = array.opt(index)) {
                is String -> {
                    val courseId = item.trim()
                    if (courseId.isNotBlank() && courseId in candidateCourseIds) {
                        scores[courseId] = maxOf(
                            scores[courseId] ?: 0.0,
                            normalizedRankScore(index, array.length(), Double.NaN)
                        )
                    }
                }

                is JSONObject -> {
                    val courseId = item.optString("courseId").ifBlank { item.optString("id") }
                    if (courseId.isBlank() || courseId !in candidateCourseIds) continue

                    val rawScore = when {
                        item.has("score") -> item.optDouble("score", Double.NaN)
                        item.has("rankScore") -> item.optDouble("rankScore", Double.NaN)
                        else -> Double.NaN
                    }

                    scores[courseId] = maxOf(
                        scores[courseId] ?: 0.0,
                        normalizedRankScore(index, array.length(), rawScore)
                    )
                }
            }
        }

        return scores
    }

    private fun normalizedRankScore(
        index: Int,
        total: Int,
        rawScore: Double
    ): Double {
        // Neu backend da tra score thi dung score; neu chi tra rank thi bien rank thanh
        // score giam dan trong khoang [0, 1] de app van co the tron voi heuristic.
        if (!rawScore.isNaN()) {
            return if (rawScore in 0.0..1.0) {
                rawScore.coerceIn(0.0, 1.0)
            } else {
                1.0 / (1.0 + exp(-rawScore.coerceIn(-20.0, 20.0)))
            }
        }

        if (total <= 1) return 1.0
        return 1.0 - (index.toDouble() / (total - 1).toDouble())
    }

    private fun buildUserProfile(
        userId: String,
        enrolledCourses: List<Course>,
        progressSnapshot: QuerySnapshot,
        courseById: Map<String, Course>
    ): UserProfile {
        // UserProfile la phan tom tat "so thich hoc tap" ma app tu xay:
        // category, level, instructor va khoang gia.
        val categoryWeights = mutableMapOf<String, Double>()
        val levelWeights = mutableMapOf<CourseLevel, Double>()
        val instructorWeights = mutableMapOf<String, Double>()
        val weightedPrices = mutableListOf<Double>()

        val progressWeights = buildCourseInteractionWeights(
            userId = userId,
            progressSnapshot = progressSnapshot,
            enrolledCourseIds = enrolledCourses.map { it.id }.toSet(),
            courseById = courseById
        )

        enrolledCourses.forEach { course ->
            // Trong so cua mot khoa hoc khong dong deu; progress va recency lam cho
            // khoa hoc dang hoc gan day anh huong manh hon vao profile.
            val weight = progressWeights[course.id] ?: 0.5
            categoryWeights[course.categoryId] = (categoryWeights[course.categoryId] ?: 0.0) + weight
            levelWeights[course.level] = (levelWeights[course.level] ?: 0.0) + weight
            instructorWeights[course.instructorId] = (instructorWeights[course.instructorId] ?: 0.0) + weight
            if (course.price > 0.0) {
                repeat(maxOf(1, (weight * 2.0).toInt())) {
                    weightedPrices += course.price
                }
            }
        }

        val totalWeight = enrolledCourses.size.toDouble().coerceAtLeast(1.0)
        categoryWeights.replaceAll { _, value -> value / totalWeight }
        levelWeights.replaceAll { _, value -> value / totalWeight }
        instructorWeights.replaceAll { _, value -> value / totalWeight }

        return UserProfile(
            categoryWeights = categoryWeights,
            levelWeights = levelWeights,
            instructorWeights = instructorWeights,
            averagePrice = if (weightedPrices.isEmpty()) 0.0 else weightedPrices.average(),
            minPrice = weightedPrices.minOrNull() ?: 0.0,
            maxPrice = weightedPrices.maxOrNull() ?: 0.0
        )
    }

    private fun buildCourseInteractionWeights(
        userId: String,
        progressSnapshot: QuerySnapshot,
        enrolledCourseIds: Set<String>,
        courseById: Map<String, Course>
    ): Map<String, Double> {
        // Trong so interaction duoc tao tu hai tin hieu:
        // muc do hoan thanh va do moi cua lan hoc gan nhat.
        val weights = mutableMapOf<String, Double>()
        val nowMs = System.currentTimeMillis()
        val dayMs = 24 * 60 * 60 * 1000.0

        progressSnapshot.documents
            .filter { it.getString("userId") == userId }
            .forEach { doc ->
                val courseId = doc.getString("courseId") ?: return@forEach
                val completedLessons = doc.getLong("completedLessons")?.toInt() ?: 0
                val totalLessons = courseById[courseId]?.lessonCount ?: 0
                val progressWeight = if (totalLessons > 0) {
                    (completedLessons.toDouble() / totalLessons * 2.0).coerceAtLeast(0.5)
                } else {
                    0.5
                }

                // Recency decay giup hanh vi moi co gia tri hon hanh vi qua cu,
                // nhung van co floor de khong mat sach dau vet hoc tap cu.
                val lastAccessedAt = doc.getLong("lastAccessedAt") ?: 0L
                val recencyWeight = if (lastAccessedAt > 0L) {
                    val daysAgo = ((nowMs - lastAccessedAt).coerceAtLeast(0L)) / dayMs
                    exp(-daysAgo / 30.0).coerceIn(0.3, 1.0)
                } else {
                    0.3
                }

                weights[courseId] = maxOf(weights[courseId] ?: 0.0, progressWeight * recencyWeight)
            }

        enrolledCourseIds.forEach { courseId ->
            weights[courseId] = maxOf(weights[courseId] ?: 0.0, 1.0)
        }

        return weights
    }

    private fun rankCourses(
        courses: List<Course>,
        userProfile: UserProfile,
        backendScores: Map<String, Double>
    ): List<Course> {
        // Hybrid ranking: backend ML duoc uu tien cao hon, nhung heuristic local
        // van giu vai tro neo de app tu dung duoc khi backend score thieu/mat.
        return courses
            .map { course ->
                val heuristicScore = calculateHeuristicScore(userProfile, course)
                val backendScore = backendScores[course.id]
                val finalScore = if (backendScore != null) {
                    (heuristicScore * HEURISTIC_WEIGHT) + (backendScore * BACKEND_WEIGHT)
                } else {
                    heuristicScore
                }
                course to finalScore
            }
            .sortedByDescending { it.second }
            .map { it.first }
    }

    private fun rankFallbackCourses(courses: List<Course>): List<Course> {
        // Fallback thuong duoc dung cho cold-start hoac backend loi,
        // nen uu tien cac course co quality signal de an toan cho home screen.
        return courses.sortedWith(
            compareByDescending<Course> { it.rating }
                .thenByDescending { it.reviewCount }
                .thenByDescending { it.enrollmentCount }
                .thenByDescending { it.createdAt }
        )
    }
    // Heuristic score được tính toán dựa trên sự kết hợp của nhiều yếu tố liên quan đến sở 
    //thích học tập của người dùng và tín hiệu chất lượng của khóa học. Điểm số này được sử 
    //dụng để xếp hạng các khóa học khi không có điểm số từ backend hoặc để kết hợp với điểm số 
    //từ backend nhằm tạo ra một danh sách đề xuất phù hợp hơn với người dùng.
    private fun calculateHeuristicScore(
        userProfile: UserProfile,
        course: Course
    ): Double {
        // Heuristic score tron hai nhom:
        // - profile fit cua user
        // - popularity/quality signal cua course
        // Trong do profile fit duoc tinh tu so thich cua user voi category/level/instructor 
        //va price affinity, ket hop voi freshness de uu tien khoa hoc moi hon. Popularity signal 
        //duoc tinh tu rating/enrollment/review count de uu tien khoa hoc chat luong cao.
        val categoryWeight = userProfile.categoryWeights[course.categoryId] ?: 0.0
        val levelWeight = userProfile.levelWeights[course.level] ?: 0.0
        val instructorWeight = userProfile.instructorWeights[course.instructorId] ?: 0.0
        val priceAffinity = calculatePriceAffinity(userProfile, course)
        val freshnessScore = calculateFreshnessScore(course)
        val reviewScore = course.reviewCount.coerceAtMost(200) / 200.0
        // Profile score duoc tinh toan de uu tien cho nhung khoa hoc hop voi so thich cua user, 
        //giup tang kha nang user click va mua khoa hoc duoc de xet den backend recommendation.
        val profileScore = (categoryWeight * 0.30) +
            (levelWeight * 0.18) +
            (instructorWeight * 0.16) +
            (priceAffinity * 0.18) +
            (freshnessScore * 0.18)
        // Popularity score duoc tinh toan de uu tien cho nhung khoa hoc chat luong cao, 
        //giup tang kha nang khoa hoc duoc de xet den backend recommendation va cung cap danh sach chat luong hon cho user.
        val popularityScore = (course.rating / 5.0) * 0.45 +
            (course.enrollmentCount.coerceAtMost(500) / 500.0) * 0.35 +
            reviewScore * 0.20

        return (profileScore * 0.60) + (popularityScore * 0.40)
    }
    // Price affinity duoc tinh toan de xep hang cao hon cho nhung khoa hoc co gia gan voi muc trung binh ma user da tung mua, giup tang kha nang user click va mua khoa hoc duoc de xet den backend recommendation.
    // Cong thuc tinh price affinity dua tren khoang cach tuyen tinh giua gia khoa hoc va muc trung binh trong profile, duoc chuan hoa de tra ve gia tri trong khoang [0, 1]. Neu user chua co lich su mua nao co gia thi tra ve 0.5 de khong uu tien hay loai tru khoa hoc theo price.
    private fun calculatePriceAffinity(
        userProfile: UserProfile,
        course: Course
    ): Double {
        if (userProfile.averagePrice <= 0.0 || course.price <= 0.0) return 0.5
        val span = maxOf(userProfile.maxPrice - userProfile.minPrice, userProfile.averagePrice * 0.5, 1.0)
        val normalizedGap = (abs(course.price - userProfile.averagePrice) / span).coerceIn(0.0, 1.0)
        return 1.0 - normalizedGap
    }
    // Freshness score duoc tinh toan de uu tien khoa hoc moi hon, giup tang kha nang khoa hoc duoc de xet den backend recommendation va cung cap danh sach moi me hon cho user.
    private fun calculateFreshnessScore(course: Course): Double {
        val nowMs = System.currentTimeMillis()
        val ageDays = ((nowMs - course.createdAt).coerceAtLeast(0L)) / (24 * 60 * 60 * 1000.0)
        return exp(-ageDays / 180.0).coerceIn(0.15, 1.0)
    }
    // Load tat ca course da publish tu Firestore, chi lay nhung field can thiet de hien thi va xep hang de giam thi luong data truyen ve cho app va backend.
    private suspend fun loadPublishedCourses(): List<Course> {
        // Recommendation chi score tren cac course da publish.
        val snapshot = coursesCollection
            .whereEqualTo("isPublished", true)
            .get()
            .await()
        return snapshot.documents.mapNotNull { doc ->
            doc.toObject(Course::class.java)?.copy(id = doc.id)
        }
    }
    // Load danh sach courseId ma user da enroll tu Firestore de loai tru khi xep hang va gui backend, giup dam bao rang cac khoa hoc da hoc se khong xuat hien trong danh sach recommendation.
    private suspend fun loadEnrolledCourseIds(userId: String): Set<String> {
        // Chi can ID de loai tru nhanh nhung course da mua/enroll.
        return enrollmentsCollection
            .whereEqualTo("userId", userId)
            .get()
            .await()
            .documents
            .mapNotNull { it.getString("courseId") }
            .toSet()
    }
    // Fallback cuoi cung neu backend khong tra ve duoc score, hoac co loi khi xin backend, hoac dang cold-start (chua co lich su de xay dung profile va xin backend).
    private suspend fun fallbackRecommendedCourses(
        limit: Int,
        categoryId: String? = null
    ): ResultState<List<Course>> {
        return try {
            // Fallback cuoi cung neu ca hybrid pipeline va backend deu loi.
            val fallbackCourses = loadPublishedCourses()
                .asSequence()
                .filter { categoryId.isNullOrBlank() || it.categoryId == categoryId }
                .toList()
            ResultState.Success(rankFallbackCourses(fallbackCourses).take(limit))
        } catch (fallbackException: Exception) {
            ResultState.Error(fallbackException.message ?: "Lấy gợi ý khóa học thất bại")
        }
    }
    // UserProfile la phan tom tat "so thich hoc tap" ma app tu xay, duoc su dung de tinh toan heuristic score khi rank course va cung co the duoc gui den backend de backend suy luan ra diem so recommendation hop profile hon cho tung course.
    private data class UserProfile(
        val categoryWeights: Map<String, Double>,
        val levelWeights: Map<CourseLevel, Double>,
        val instructorWeights: Map<String, Double>,
        val averagePrice: Double = 0.0,
        val minPrice: Double = 0.0,
        val maxPrice: Double = 0.0
    )
}
