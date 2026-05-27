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
 * Repository gợi ý khóa học cá nhân hóa ở phía Android.
 *
 * Lớp này dùng chiến lược hybrid:
 * - app tự xây hồ sơ học tập của người dùng từ enrollment + progress,
 * - app gọi backend ML để lấy score recommendation trên tập candidate course,
 * - app trộn score backend với heuristic local để vẫn hoạt động khi backend lỗi,
 * - app gửi feedback như impression, add-to-cart, purchase, enroll để backend học dần.
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
     * Gợi ý danh sách khóa học cho trang chủ học viên.
     *
     * Luồng chính:
     * 1. loại các course user đã enroll,
     * 2. nếu user mới hoàn toàn thì fallback theo quality/popularity,
     * 3. nếu user đã có lịch sử thì xây `UserProfile`,
     * 4. gọi backend ML để xin score,
     * 5. trộn score backend với heuristic local rồi xếp hạng.
     */
    suspend fun getRecommendedCourses(
        userId: String,
        limit: Int = 10
    ): ResultState<List<Course>> {
        if (userId.isBlank()) return ResultState.Error("Thiếu thông tin người dùng")

        return try {
            val enrolledCourseIds = loadEnrolledCourseIds(userId)
            val allCourses = loadPublishedCourses()
            val availableCourses = allCourses.filter { it.id !in enrolledCourseIds }
            if (availableCourses.isEmpty()) {
                return ResultState.Success(emptyList())
            }

            if (enrolledCourseIds.isEmpty()) {
                return ResultState.Success(rankFallbackCourses(availableCourses).take(limit))
            }

            val progressSnapshot = progressCollection
                .whereEqualTo("userId", userId)
                .get()
                .await()

            val userProfile = buildUserProfile(
                userId = userId,
                enrolledCourses = allCourses.filter { it.id in enrolledCourseIds },
                progressSnapshot = progressSnapshot,
                courseById = allCourses.associateBy { it.id }
            )

            val backendScores = fetchBackendRecommendations(
                userId = userId,
                limit = limit,
                candidateCourseIds = availableCourses.map { it.id },
                source = "app_home"
            )

            ResultState.Success(rankCourses(availableCourses, userProfile, backendScores).take(limit))
        } catch (_: Exception) {
            fallbackRecommendedCourses(limit = limit)
        }
    }

    /**
     * Gợi ý course trong một category cụ thể.
     *
     * App lọc candidate theo `categoryId` trước rồi mới gửi sang backend,
     * nhờ đó backend chỉ tập trung chấm điểm đúng tập course liên quan.
     */
    suspend fun getRecommendedCoursesByCategory(
        userId: String,
        categoryId: String,
        limit: Int = 5
    ): ResultState<List<Course>> {
        if (userId.isBlank()) return ResultState.Error("Thiếu thông tin người dùng")

        return try {
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
     * Gửi một tín hiệu feedback recommendation về backend.
     *
     * Đây là telemetry kiểu best-effort: nếu backend URL chưa cấu hình hoặc backend lỗi
     * thì app vẫn tiếp tục chạy bình thường, vì feedback không được phép làm hỏng UX chính.
     */
    suspend fun logRecommendationFeedback(
        userId: String,
        courseId: String,
        eventType: String,
        source: String,
        modelVersion: String? = null
    ): ResultState<Unit> {
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
     * Ghi nhiều event `IMPRESSION`, mỗi course là một event riêng.
     *
     * Thiết kế này giúp backend tính CTR và các metric ranking theo item rõ ràng hơn
     * thay vì phải tách một payload danh sách lớn ở phía server.
     */
    suspend fun logRecommendationImpressions(
        userId: String,
        courseIds: List<String>,
        source: String,
        modelVersion: String? = null
    ) {
        if (userId.isBlank()) return

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
        return courses.sortedWith(
            compareByDescending<Course> { it.rating }
                .thenByDescending { it.reviewCount }
                .thenByDescending { it.enrollmentCount }
                .thenByDescending { it.createdAt }
        )
    }

    /**
     * Heuristic local chấm điểm course theo hai nhóm tín hiệu:
     * - mức độ khớp với hồ sơ người dùng,
     * - tín hiệu chất lượng/phổ biến của course.
     *
     * Nhờ đó app vẫn có thể tạo danh sách recommendation chấp nhận được
     * ngay cả khi backend ML không trả score.
     */
    private fun calculateHeuristicScore(
        userProfile: UserProfile,
        course: Course
    ): Double {
        val categoryWeight = userProfile.categoryWeights[course.categoryId] ?: 0.0
        val levelWeight = userProfile.levelWeights[course.level] ?: 0.0
        val instructorWeight = userProfile.instructorWeights[course.instructorId] ?: 0.0
        val priceAffinity = calculatePriceAffinity(userProfile, course)
        val freshnessScore = calculateFreshnessScore(course)
        val reviewScore = course.reviewCount.coerceAtMost(200) / 200.0

        val profileScore = (categoryWeight * 0.30) +
            (levelWeight * 0.18) +
            (instructorWeight * 0.16) +
            (priceAffinity * 0.18) +
            (freshnessScore * 0.18)

        val popularityScore = (course.rating / 5.0) * 0.45 +
            (course.enrollmentCount.coerceAtMost(500) / 500.0) * 0.35 +
            reviewScore * 0.20

        return (profileScore * 0.60) + (popularityScore * 0.40)
    }

    /**
     * Đo mức độ “hợp giá” giữa course hiện tại và khoảng giá user từng học/mua.
     *
     * Nếu user chưa có lịch sử giá đủ rõ thì trả về giá trị trung tính 0.5
     * để không vô tình ưu ái hoặc loại bỏ course chỉ vì thiếu dữ liệu.
     */
    private fun calculatePriceAffinity(
        userProfile: UserProfile,
        course: Course
    ): Double {
        if (userProfile.averagePrice <= 0.0 || course.price <= 0.0) return 0.5
        val span = maxOf(userProfile.maxPrice - userProfile.minPrice, userProfile.averagePrice * 0.5, 1.0)
        val normalizedGap = (abs(course.price - userProfile.averagePrice) / span).coerceIn(0.0, 1.0)
        return 1.0 - normalizedGap
    }

    /**
     * Ưu tiên nhẹ cho các khóa học mới hơn để danh sách gợi ý không quá cũ.
     */
    private fun calculateFreshnessScore(course: Course): Double {
        val nowMs = System.currentTimeMillis()
        val ageDays = ((nowMs - course.createdAt).coerceAtLeast(0L)) / (24 * 60 * 60 * 1000.0)
        return exp(-ageDays / 180.0).coerceIn(0.15, 1.0)
    }

    /**
     * Đọc toàn bộ course đã publish từ Firestore.
     *
     * Recommendation chỉ chấm điểm trên tập course public, vì course chưa publish
     * không nên xuất hiện trong bất kỳ danh sách gợi ý nào của học viên.
     */
    private suspend fun loadPublishedCourses(): List<Course> {
        val snapshot = coursesCollection
            .whereEqualTo("isPublished", true)
            .get()
            .await()
        return snapshot.documents.mapNotNull { doc ->
            doc.toObject(Course::class.java)?.copy(id = doc.id)
        }
    }

    /**
     * Lấy tập `courseId` mà user đã enroll để loại khỏi recommendation.
     */
    private suspend fun loadEnrolledCourseIds(userId: String): Set<String> {
        return enrollmentsCollection
            .whereEqualTo("userId", userId)
            .get()
            .await()
            .documents
            .mapNotNull { it.getString("courseId") }
            .toSet()
    }

    /**
     * Fallback cuối cùng nếu cả pipeline hybrid và backend ML đều không dùng được.
     */
    private suspend fun fallbackRecommendedCourses(
        limit: Int,
        categoryId: String? = null
    ): ResultState<List<Course>> {
        return try {
            val fallbackCourses = loadPublishedCourses()
                .asSequence()
                .filter { categoryId.isNullOrBlank() || it.categoryId == categoryId }
                .toList()
            ResultState.Success(rankFallbackCourses(fallbackCourses).take(limit))
        } catch (fallbackException: Exception) {
            ResultState.Error(fallbackException.message ?: "Lấy gợi ý khóa học thất bại")
        }
    }

    /**
     * Bản tóm tắt sở thích học tập mà app tự suy ra từ lịch sử học của user.
     *
     * Đây là contract trung gian giữa dữ liệu thô và bước scoring heuristic/backend.
     */
    private data class UserProfile(
        val categoryWeights: Map<String, Double>,
        val levelWeights: Map<CourseLevel, Double>,
        val instructorWeights: Map<String, Double>,
        val averagePrice: Double = 0.0,
        val minPrice: Double = 0.0,
        val maxPrice: Double = 0.0
    )
}
