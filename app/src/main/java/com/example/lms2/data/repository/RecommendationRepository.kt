package com.example.lms2.data.repository

import com.example.lms2.BuildConfig
import com.example.lms2.data.model.Course
import com.example.lms2.data.model.CourseLevel
import com.example.lms2.util.ResultState
import com.google.firebase.firestore.FirebaseFirestore
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
import kotlin.math.exp

class RecommendationRepository {

    private companion object {
        const val HEURISTIC_WEIGHT = 0.35
        const val BACKEND_WEIGHT = 0.65
        const val BACKEND_TIMEOUT_SECONDS = 12L
        const val BACKEND_RECOMMENDATION_PATH = "/recommendations"
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

    suspend fun getRecommendedCourses(
        userId: String,
        limit: Int = 10
    ): ResultState<List<Course>> {
        if (userId.isBlank()) return ResultState.Error("Thiếu thông tin người dùng")

        return try {
            val enrolledCoursesSnapshot = enrollmentsCollection
                .whereEqualTo("userId", userId)
                .get()
                .await()

            val enrolledCourseIds = enrolledCoursesSnapshot.documents
                .mapNotNull { it.getString("courseId") }
                .toSet()

            val coursesSnapshot = coursesCollection
                .whereEqualTo("isPublished", true)
                .get()
                .await()

            val allCourses = coursesSnapshot.documents.mapNotNull { doc ->
                doc.toObject(Course::class.java)?.copy(id = doc.id)
            }

            val availableCourses = allCourses.filter { it.id !in enrolledCourseIds }
            if (availableCourses.isEmpty()) {
                return ResultState.Success(emptyList())
            }

            if (enrolledCourseIds.isEmpty()) {
                return ResultState.Success(
                    availableCourses
                        .sortedWith(
                            compareByDescending<Course> { it.enrollmentCount }
                                .thenByDescending { it.rating }
                        )
                        .take(limit)
                )
            }

            val enrolledCourses = allCourses.filter { it.id in enrolledCourseIds }
            val progressSnapshot = progressCollection
                .whereEqualTo("userId", userId)
                .get()
                .await()

            val userProfile = buildUserProfile(
                userId = userId,
                enrolledCourses = enrolledCourses,
                progressSnapshot = progressSnapshot,
                courseById = allCourses.associateBy { it.id }
            )

            val backendScores = fetchBackendRecommendations(
                userId = userId,
                limit = limit,
                candidateCourseIds = availableCourses.map { it.id }
            )

            val recommendedCourses = availableCourses
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
                .take(limit)
                .map { it.first }

            ResultState.Success(recommendedCourses)
        } catch (e: Exception) {
            ResultState.Error(e.message ?: "Lấy gợi ý khóa học thất bại")
        }
    }

    suspend fun getRecommendedCoursesByCategory(
        userId: String,
        categoryId: String,
        limit: Int = 5
    ): ResultState<List<Course>> {
        if (userId.isBlank()) return ResultState.Error("Thiếu thông tin người dùng")

        return try {
            val enrolledCoursesSnapshot = enrollmentsCollection
                .whereEqualTo("userId", userId)
                .get()
                .await()

            val enrolledCourseIds = enrolledCoursesSnapshot.documents
                .mapNotNull { it.getString("courseId") }
                .toSet()

            val coursesSnapshot = coursesCollection
                .whereEqualTo("categoryId", categoryId)
                .whereEqualTo("isPublished", true)
                .get()
                .await()

            val categoryCourses = coursesSnapshot.documents.mapNotNull { doc ->
                doc.toObject(Course::class.java)?.copy(id = doc.id)
            }.filter { it.id !in enrolledCourseIds }

            if (categoryCourses.isEmpty()) {
                return ResultState.Success(emptyList())
            }

            val progressSnapshot = progressCollection
                .whereEqualTo("userId", userId)
                .get()
                .await()

            val userProfile = buildUserProfile(
                userId = userId,
                enrolledCourses = categoryCourses.filter { it.id in enrolledCourseIds },
                progressSnapshot = progressSnapshot,
                courseById = categoryCourses.associateBy { it.id }
            )

            val backendScores = fetchBackendRecommendations(
                userId = userId,
                limit = limit,
                candidateCourseIds = categoryCourses.map { it.id },
                categoryId = categoryId
            )

            val recommendedCourses = categoryCourses
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
                .take(limit)
                .map { it.first }

            ResultState.Success(recommendedCourses)
        } catch (e: Exception) {
            ResultState.Error(e.message ?: "Lấy gợi ý khóa học theo danh mục thất bại")
        }
    }

    private suspend fun fetchBackendRecommendations(
        userId: String,
        limit: Int,
        candidateCourseIds: List<String>,
        categoryId: String? = null
    ): Map<String, Double> {
        if (recommendationBackendUrl.isBlank()) return emptyMap()

        return withContext(Dispatchers.IO) {
            try {
                val payload = JSONObject().apply {
                    put("userId", userId)
                    put("limit", limit)
                    put("candidateCourseIds", JSONArray(candidateCourseIds))
                    categoryId?.takeIf { it.isNotBlank() }?.let { put("categoryId", it) }
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
                        scores[courseId] = maxOf(scores[courseId] ?: 0.0, normalizedRankScore(index, array.length(), Double.NaN))
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
        progressSnapshot: com.google.firebase.firestore.QuerySnapshot,
        courseById: Map<String, Course>
    ): UserProfile {
        val categoryWeights = mutableMapOf<String, Double>()
        val levelWeights = mutableMapOf<CourseLevel, Double>()
        val instructorWeights = mutableMapOf<String, Double>()

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
        }

        val totalWeight = enrolledCourses.size.toDouble().coerceAtLeast(1.0)
        categoryWeights.replaceAll { _, value -> value / totalWeight }
        levelWeights.replaceAll { _, value -> value / totalWeight }
        instructorWeights.replaceAll { _, value -> value / totalWeight }

        return UserProfile(
            categoryWeights = categoryWeights,
            levelWeights = levelWeights,
            instructorWeights = instructorWeights
        )
    }

    private fun buildCourseInteractionWeights(
        userId: String,
        progressSnapshot: com.google.firebase.firestore.QuerySnapshot,
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

    private fun calculateHeuristicScore(
        userProfile: UserProfile,
        course: Course
    ): Double {
        val categoryWeight = userProfile.categoryWeights[course.categoryId] ?: 0.0
        val levelWeight = userProfile.levelWeights[course.level] ?: 0.0
        val instructorWeight = userProfile.instructorWeights[course.instructorId] ?: 0.0

        val profileScore = (categoryWeight * 0.45) + (levelWeight * 0.30) + (instructorWeight * 0.25)
        val popularityScore = (course.rating / 5.0) * 0.5 +
            (course.enrollmentCount.coerceAtMost(100) / 100.0) * 0.5

        return (profileScore * 0.7) + (popularityScore * 0.3)
    }

    private data class UserProfile(
        val categoryWeights: Map<String, Double>,
        val levelWeights: Map<CourseLevel, Double>,
        val instructorWeights: Map<String, Double>
    )
}
