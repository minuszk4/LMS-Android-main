package com.example.lms2.data.repository

import com.example.lms2.data.model.LessonProgress
import com.example.lms2.data.model.Progress
import com.example.lms2.data.model.QuizProgress
import com.example.lms2.util.ResultState
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.tasks.await

/**
 * Repository quản lý tiến độ học tập ở các mức:
 * - toàn khóa học (`progress`),
 * - từng bài học (`lessonProgress`),
 * - từng bài quiz (`quizProgress`).
 *
 * Đây là nguồn dữ liệu chính cho:
 * - màn học tập của học viên,
 * - chatbot khi trả lời câu hỏi về tiến độ,
 * - recommendation khi suy ra mức độ gắn bó của user với từng course.
 */
class ProgressRepository {

    private val firestore = FirebaseFirestore.getInstance()
    private val progressCollection = firestore.collection("progress")
    private val lessonProgressCollection = firestore.collection("lessonProgress")
    private val quizProgressCollection = firestore.collection("quizProgress")

    /**
     * Lấy bản ghi tiến độ tổng quát của user trong một khóa học.
     *
     * Bản ghi này thường chứa các field như `completedLessons`, `lastLessonId`,
     * `lastAccessedAt`, `isCompleted` và được dùng như “ảnh chụp nhanh” của course progress.
     */
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

    /**
     * Cập nhật lesson truy cập gần nhất của user trong một course.
     *
     * Đây là tín hiệu nhẹ nhưng rất quan trọng cho UX “học tiếp từ đâu”
     * và cũng được recommendation dùng như một tín hiệu recency.
     */
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
                )
                .await()
            ResultState.Success(Unit)
        } catch (e: Exception) {
            ResultState.Error(e.message ?: "Cập nhật tiến độ thất bại")
        }
    }

    /**
     * Lấy tiến độ của một lesson cụ thể.
     */
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

    /**
     * Lấy toàn bộ lesson progress của user trong một course.
     *
     * Hàm này thường phục vụ màn player hoặc dashboard tiến độ chi tiết,
     * nơi UI cần biết chính xác những bài nào đã hoàn thành.
     */
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

    /**
     * Đánh dấu hoàn thành hoặc bỏ hoàn thành một bài học.
     *
     * Hàm dùng transaction để cập nhật đồng thời:
     * - trạng thái của lesson cụ thể,
     * - số lượng bài đã hoàn thành trong course,
     * - cờ `isCompleted` của toàn khóa.
     *
     * Nhờ transaction, hệ thống tránh được tình trạng đếm sai `completedLessons`
     * khi nhiều thao tác cập nhật xảy ra gần nhau.
     */
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

                val lessonSnapshot = transaction.get(lessonProgressRef)
                val alreadyCompleted = lessonSnapshot.getBoolean("isCompleted") ?: false
                if (alreadyCompleted == isCompleted) return@runTransaction

                val progressSnapshot = transaction.get(progressRef)
                val currentCompleted = progressSnapshot.getLong("completedLessons") ?: 0
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

    /**
     * Lấy tiến độ làm quiz của một user cho một quiz cụ thể.
     */
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

    /**
     * Nạp đồng thời ba lớp dữ liệu tiến độ của một khóa học.
     *
     * Repository dùng `coroutineScope + async` để song song hóa truy vấn:
     * - progress tổng quát của course,
     * - lesson progress,
     * - quiz progress.
     *
     * Kết quả rất phù hợp cho các màn cần dựng bức tranh tiến độ đầy đủ chỉ trong một lần gọi.
     */
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

    /**
     * Lấy toàn bộ progress toàn hệ thống cho các màn hình quản trị hoặc analytics nội bộ.
     */
    suspend fun getAllProgress(): ResultState<List<Progress>> {
        return try {
            val snapshot = progressCollection
                .orderBy("lastAccessedAt", Query.Direction.DESCENDING)
                .get()
                .await()
            ResultState.Success(snapshot.toObjects(Progress::class.java))
        } catch (e: Exception) {
            ResultState.Error(e.message ?: "Lấy tất cả tiến độ thất bại")
        }
    }
}
