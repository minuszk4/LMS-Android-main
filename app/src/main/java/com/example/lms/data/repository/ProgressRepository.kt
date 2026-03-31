package com.example.lms.data.repository

import com.example.lms.data.model.LessonProgress
import com.example.lms.data.model.Progress
import com.example.lms.data.model.QuizProgress
import com.example.lms.util.ResultState
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.tasks.await

class ProgressRepository {

    private val firestore = FirebaseFirestore.getInstance()
    private val progressCollection = firestore.collection("progress")
    private val lessonProgressCollection = firestore.collection("lessonProgress")
    private val quizProgressCollection = firestore.collection("quizProgress")

    // ─────────────────────────────────────────
    // PROGRESS (Course level)
    // ─────────────────────────────────────────

    suspend fun getProgress(userId: String, courseId: String): ResultState<Progress?> {
        return try {
            val snapshot = progressCollection
                .document("${userId}_${courseId}")
                .get()
                .await()
            ResultState.Success(snapshot.toObject(Progress::class.java))
        } catch (e: Exception) {
            ResultState.Error(e.message ?: "Lấy tiến độ thất bại")
        }
    }

    suspend fun updateLastAccessed(
        userId: String,
        courseId: String,
        lessonId: String
    ): ResultState<Unit> {
        return try {
            progressCollection
                .document("${userId}_${courseId}")
                .set(
                    mapOf(
                        "userId" to userId,
                        "courseId" to courseId,
                        "lastLessonId" to lessonId,
                        "lastAccessedAt" to System.currentTimeMillis()
                    ),
                    SetOptions.merge()
                ).await()
            ResultState.Success(Unit)
        } catch (e: Exception) {
            ResultState.Error(e.message ?: "Cập nhật tiến độ thất bại")
        }
    }

    // ─────────────────────────────────────────
    // LESSON PROGRESS
    // ─────────────────────────────────────────

    suspend fun getLessonProgress(
        userId: String,
        lessonId: String
    ): ResultState<LessonProgress?> {
        return try {
            val snapshot = lessonProgressCollection
                .document("${userId}_${lessonId}")
                .get()
                .await()
            ResultState.Success(snapshot.toObject(LessonProgress::class.java))
        } catch (e: Exception) {
            ResultState.Error(e.message ?: "Lấy tiến độ bài học thất bại")
        }
    }

    suspend fun getAllLessonProgress(
        userId: String,
        courseId: String
    ): ResultState<List<LessonProgress>> {
        return try {
            val snapshot = lessonProgressCollection
                .whereEqualTo("userId", userId)
                .whereEqualTo("courseId", courseId)
                .get()
                .await()
            ResultState.Success(snapshot.toObjects(LessonProgress::class.java))
        } catch (e: Exception) {
            ResultState.Error(e.message ?: "Lấy tiến độ các bài học thất bại")
        }
    }

    suspend fun toggleLessonComplete(
        userId: String,
        courseId: String,
        lessonId: String,
        isCompleted: Boolean,
        totalLessons: Int
    ): ResultState<Unit> {
        return try {
            firestore.runTransaction { transaction ->
                val lessonProgressRef = lessonProgressCollection.document("${userId}_${lessonId}")
                val progressRef = progressCollection.document("${userId}_${courseId}")
                
                // 1. Kiểm tra trạng thái bài học hiện tại để tránh cộng dồn sai
                val lessonSnapshot = transaction.get(lessonProgressRef)
                val alreadyCompleted = lessonSnapshot.getBoolean("isCompleted") ?: false
                
                // Nếu trạng thái yêu cầu giống trạng thái hiện tại, không làm gì cả
                if (alreadyCompleted == isCompleted) return@runTransaction

                // 2. Đọc tiến độ khóa học
                val progressSnapshot = transaction.get(progressRef)
                val currentCompleted = progressSnapshot.getLong("completedLessons") ?: 0

                // 3. Tính toán số lượng bài học hoàn thành mới
                val newCompleted = if (isCompleted) {
                    currentCompleted + 1
                } else {
                    (currentCompleted - 1).coerceAtLeast(0)
                }
                val isCourseCompleted = newCompleted >= totalLessons

                transaction.set(
                    lessonProgressRef,
                    mapOf(
                        "lessonId" to lessonId,
                        "userId" to userId,
                        "courseId" to courseId,
                        "isCompleted" to isCompleted
                    ),
                    SetOptions.merge()
                )

                transaction.set(
                    progressRef,
                    mapOf(
                        "userId" to userId,
                        "courseId" to courseId,
                        "completedLessons" to newCompleted,
                        "isCompleted" to isCourseCompleted
                    ),
                    SetOptions.merge()
                )
            }.await()

            ResultState.Success(Unit)
        } catch (e: Exception) {
            ResultState.Error(e.message ?: "Cập nhật trạng thái bài học thất bại")
        }
    }

    // ─────────────────────────────────────────
    // QUIZ PROGRESS
    // ─────────────────────────────────────────

    suspend fun getQuizProgress(
        userId: String,
        quizId: String
    ): ResultState<QuizProgress?> {
        return try {
            val snapshot = quizProgressCollection
                .document("${userId}_${quizId}")
                .get()
                .await()
            ResultState.Success(snapshot.toObject(QuizProgress::class.java))
        } catch (e: Exception) {
            ResultState.Error(e.message ?: "Lấy tiến độ bài kiểm tra thất bại")
        }
    }

    // ─────────────────────────────────────────
    // LOAD ALL — dùng cho LessonPlayerScreen
    // ─────────────────────────────────────────

    suspend fun loadCourseProgress(
        userId: String,
        courseId: String
    ): ResultState<Triple<Progress?, List<LessonProgress>, List<QuizProgress>>> {
        return try {
            coroutineScope {
                val progressDeferred = async {
                    progressCollection
                        .document("${userId}_${courseId}")
                        .get()
                        .await()
                        .toObject(Progress::class.java)
                }
                val lessonProgressDeferred = async {
                    lessonProgressCollection
                        .whereEqualTo("userId", userId)
                        .whereEqualTo("courseId", courseId)
                        .get()
                        .await()
                        .toObjects(LessonProgress::class.java)
                }
                val quizProgressDeferred = async {
                    quizProgressCollection
                        .whereEqualTo("userId", userId)
                        .whereEqualTo("courseId", courseId)
                        .get()
                        .await()
                        .toObjects(QuizProgress::class.java)
                }

                ResultState.Success(
                    Triple(
                        progressDeferred.await(),
                        lessonProgressDeferred.await(),
                        quizProgressDeferred.await()
                    )
                )
            }
        } catch (e: Exception) {
            ResultState.Error(e.message ?: "Tải tiến độ khóa học thất bại")
        }
    }
}