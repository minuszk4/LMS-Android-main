package com.example.lms2.data.repository

import com.example.lms2.data.model.Course
import com.example.lms2.data.model.CourseLevel
import com.example.lms2.util.ResultState
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sqrt

class RecommendationRepository {
    
    private val firestore = FirebaseFirestore.getInstance()
    private val coursesCollection = firestore.collection("courses")
    private val enrollmentsCollection = firestore.collection("enrollments")
    private val progressCollection = firestore.collection("progress")

    suspend fun getRecommendedCourses(
        userId: String,
        limit: Int = 10
    ): ResultState<List<Course>> {
        if (userId.isBlank()) return ResultState.Error("Thiếu thông tin người dùng")

        return try {
            // Get user's enrolled courses
            val enrolledCoursesSnapshot = enrollmentsCollection
                .whereEqualTo("userId", userId)
                .get()
                .await()
            
            val enrolledCourseIds = enrolledCoursesSnapshot.documents
                .mapNotNull { it.getString("courseId") }
                .toSet()

            // Get all published courses
            val coursesSnapshot = coursesCollection
                .whereEqualTo("isPublished", true)
                .get()
                .await()

            val allCourses = coursesSnapshot.documents.mapNotNull { doc ->
                doc.toObject(Course::class.java)?.copy(id = doc.id)
            }

            // Filter out enrolled courses
            val availableCourses = allCourses.filter { it.id !in enrolledCourseIds }

            if (availableCourses.isEmpty()) {
                return ResultState.Success(emptyList())
            }

            // If user has no enrollments, use popularity-based ranking (cold start)
            if (enrolledCourseIds.isEmpty()) {
                val popularCourses = availableCourses
                    .sortedWith(
                        compareByDescending<Course> { it.enrollmentCount }
                            .thenByDescending { it.rating }
                    )
                    .take(limit)
                return ResultState.Success(popularCourses)
            }

            // Get enrolled courses details
            val enrolledCourses = allCourses.filter { it.id in enrolledCourseIds }

            // Calculate user profile vector based on preferences
            val userProfile = calculateUserProfile(userId, enrolledCourses)

            // Calculate similarity scores for available courses
            val recommendations = availableCourses
                .map { course ->
                    val similarity = calculateCosineSimilarity(userProfile, course)
                    Pair(course, similarity)
                }
                .sortedByDescending { it.second }
                .take(limit)
                .map { it.first }

            ResultState.Success(recommendations)
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
            // Get user's enrolled courses
            val enrolledCoursesSnapshot = enrollmentsCollection
                .whereEqualTo("userId", userId)
                .get()
                .await()
            
            val enrolledCourseIds = enrolledCoursesSnapshot.documents
                .mapNotNull { it.getString("courseId") }
                .toSet()

            // Get courses in the specified category
            val coursesSnapshot = coursesCollection
                .whereEqualTo("categoryId", categoryId)
                .whereEqualTo("isPublished", true)
                .get()
                .await()

            val categoryCourses = coursesSnapshot.documents
                .mapNotNull { doc ->
                    doc.toObject(Course::class.java)?.copy(id = doc.id)
                }
                .filter { it.id !in enrolledCourseIds }

            if (categoryCourses.isEmpty()) {
                return ResultState.Success(emptyList())
            }

            // Sort by popularity
            val recommendations = categoryCourses
                .sortedWith(
                    compareByDescending<Course> { it.rating }
                        .thenByDescending { it.enrollmentCount }
                )
                .take(limit)

            ResultState.Success(recommendations)
        } catch (e: Exception) {
            ResultState.Error(e.message ?: "Lấy gợi ý khóa học theo danh mục thất bại")
        }
    }

    private suspend fun calculateUserProfile(
        userId: String,
        enrolledCourses: List<Course>
    ): UserProfile {
        val categoryWeights = mutableMapOf<String, Double>()
        val levelWeights = mutableMapOf<CourseLevel, Double>()
        val instructorWeights = mutableMapOf<String, Double>()

        // Get progress data to weight courses by interaction time
        val progressSnapshot = progressCollection
            .whereEqualTo("userId", userId)
            .get()
            .await()

        val enrolledById = enrolledCourses.associateBy { it.id }

        val courseProgress = progressSnapshot.documents.associate { doc ->
            val courseId = doc.getString("courseId") ?: ""
            val completedLessons = (doc.getLong("completedLessons")?.toInt() ?: 0)
            val totalLessons = enrolledById[courseId]?.lessonCount ?: 0
            val progressWeight = if (totalLessons > 0) {
                (completedLessons.toDouble() / totalLessons * 2.0).coerceAtLeast(0.5)
            } else {
                0.5
            }
            courseId to progressWeight
        }

        // Calculate weighted preferences
        enrolledCourses.forEach { course ->
            val weight = courseProgress[course.id] ?: 0.5

            // Weight by category
            categoryWeights[course.categoryId] = 
                (categoryWeights[course.categoryId] ?: 0.0) + weight

            // Weight by level
            levelWeights[course.level] = 
                (levelWeights[course.level] ?: 0.0) + weight

            // Weight by instructor
            instructorWeights[course.instructorId] = 
                (instructorWeights[course.instructorId] ?: 0.0) + weight
        }

        // Normalize weights
        val totalWeight = enrolledCourses.size.toDouble()
        categoryWeights.replaceAll { _, v -> v / totalWeight }
        levelWeights.replaceAll { _, v -> v / totalWeight }
        instructorWeights.replaceAll { _, v -> v / totalWeight }

        return UserProfile(
            categoryWeights = categoryWeights,
            levelWeights = levelWeights,
            instructorWeights = instructorWeights
        )
    }

    private fun calculateCosineSimilarity(
        userProfile: UserProfile,
        course: Course
    ): Double {
        // Build feature vectors
        val userVector = mutableListOf<Double>()
        val courseVector = mutableListOf<Double>()

        // Category similarity (TF-IDF inspired)
        val categoryWeight = userProfile.categoryWeights[course.categoryId] ?: 0.0
        val categoryIdf = ln(1.0 + (1.0 / (categoryWeight + 0.01)))
        userVector.add(categoryWeight * categoryIdf)
        courseVector.add(if (course.categoryId.isNotEmpty()) 1.0 * categoryIdf else 0.0)

        // Level similarity
        val levelWeight = userProfile.levelWeights[course.level] ?: 0.0
        val levelIdf = ln(1.0 + (1.0 / (levelWeight + 0.01)))
        userVector.add(levelWeight * levelIdf)
        courseVector.add(1.0 * levelIdf)

        // Instructor similarity
        val instructorWeight = userProfile.instructorWeights[course.instructorId] ?: 0.0
        val instructorIdf = ln(1.0 + (1.0 / (instructorWeight + 0.01)))
        userVector.add(instructorWeight * instructorIdf)
        courseVector.add(if (course.instructorId.isNotEmpty()) 1.0 * instructorIdf else 0.0)

        // Add popularity boost (normalized)
        val popularityScore = (course.rating / 5.0) * 0.5 + 
                             (course.enrollmentCount.coerceAtMost(100) / 100.0) * 0.5
        userVector.add(popularityScore)
        courseVector.add(popularityScore)

        // Calculate cosine similarity
        val dotProduct = userVector.zip(courseVector).sumOf { (u, c) -> u * c }
        val userMagnitude = sqrt(userVector.sumOf { it.pow(2) })
        val courseMagnitude = sqrt(courseVector.sumOf { it.pow(2) })

        return if (userMagnitude > 0 && courseMagnitude > 0) {
            dotProduct / (userMagnitude * courseMagnitude)
        } else {
            0.0
        }
    }

    private data class UserProfile(
        val categoryWeights: Map<String, Double>,
        val levelWeights: Map<CourseLevel, Double>,
        val instructorWeights: Map<String, Double>
    )
}
