package com.example.lms2.data.repository

import com.example.lms2.data.model.CurriculumItem
import com.example.lms2.data.model.Lesson
import com.example.lms2.data.model.NotificationType
import com.example.lms2.data.model.Quiz
import com.example.lms2.util.ResultState
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Transaction
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.tasks.await
import kotlin.math.max

class CurriculumRepository {
    private val firestore = FirebaseFirestore.getInstance()
    private val lessonsCollection = firestore.collection("lessons")
    private val quizzesCollection = firestore.collection("quizzes")
    private val coursesCollection = firestore.collection("courses")
    private val notificationRepository = NotificationRepository()

    // ─────────────────────────────────────────
    // LESSON CRUD
    // ─────────────────────────────────────────

    suspend fun createLesson(lesson: Lesson): ResultState<String> {
        return try {
            val docRef = lessonsCollection.document()
            val now = System.currentTimeMillis()
            val nextOrderIndex = getNextOrderIndex(lesson.courseId)

            val createdId = firestore.runTransaction { txn ->
                val newLesson = lesson.copy(
                    id = docRef.id,
                    orderIndex = nextOrderIndex,
                    createdAt = now,
                    updatedAt = now
                )

                txn.set(docRef, newLesson)

                // Tăng lessonCount của khóa học trong cùng transaction để tránh lệch
                val courseRef = coursesCollection.document(lesson.courseId)
                txn.update(courseRef, "lessonCount", FieldValue.increment(1))

                docRef.id
            }.await()

            ResultState.Success(createdId)
        } catch (e: Exception) {
            ResultState.Error(e.message ?: "Tạo bài học thất bại")
        }
    }

    suspend fun updateLesson(lesson: Lesson): ResultState<Unit> {
        return try {
            val updated = lesson.copy(updatedAt = System.currentTimeMillis())
            lessonsCollection
                .document(lesson.id)
                .set(updated, SetOptions.merge())
                .await()
            ResultState.Success(Unit)
        } catch (e: Exception) {
            ResultState.Error(e.message ?: "Cập nhật bài học thất bại")
        }
    }

    suspend fun deleteLesson(lessonId: String, courseId: String): ResultState<Unit> {
        return try {
            firestore.runBatch { batch ->
                batch.delete(lessonsCollection.document(lessonId))
                val courseRef = coursesCollection.document(courseId)
                batch.update(courseRef, "lessonCount", FieldValue.increment(-1))
            }.await()
            
            ResultState.Success(Unit)
        } catch (e: Exception) {
            ResultState.Error(e.message ?: "Xóa bài học thất bại")
        }
    }

    // ─────────────────────────────────────────
    // QUIZ CRUD (Quiz không tính vào lessonCount)
    // ─────────────────────────────────────────

    suspend fun createQuiz(quiz: Quiz): ResultState<String> {
        return try {
            val docRef = quizzesCollection.document()
            val now = System.currentTimeMillis()
            val nextOrderIndex = getNextOrderIndex(quiz.courseId)

            val createdId = firestore.runTransaction { txn ->
                val newQuiz = quiz.copy(
                    id = docRef.id,
                    orderIndex = nextOrderIndex,
                    createdAt = now,
                    updatedAt = now
                )

                txn.set(docRef, newQuiz)
                docRef.id
            }.await()

            runCatching {
                val template = notificationRepository.quizCreatedTemplate(quiz.title)
                notificationRepository.addNotificationToCourseEnrollments(
                    courseId = quiz.courseId,
                    title = template.title,
                    body = template.body,
                    type = NotificationType.QUIZ_AVAILABLE
                )
            }

            ResultState.Success(createdId)
        } catch (e: Exception) {
            ResultState.Error(e.message ?: "Tạo bài kiểm tra thất bại")
        }
    }

    suspend fun updateQuiz(quiz: Quiz): ResultState<Unit> {
        return try {
            val updated = quiz.copy(updatedAt = System.currentTimeMillis())
            quizzesCollection
                .document(quiz.id)
                .set(updated, SetOptions.merge())
                .await()
            ResultState.Success(Unit)
        } catch (e: Exception) {
            ResultState.Error(e.message ?: "Cập nhật bài kiểm tra thất bại")
        }
    }

    suspend fun deleteQuiz(quizId: String): ResultState<Unit> {
        return try {
            quizzesCollection.document(quizId).delete().await()
            ResultState.Success(Unit)
        } catch (e: Exception) {
            ResultState.Error(e.message ?: "Xóa bài kiểm tra thất bại")
        }
    }

    suspend fun getCurriculum(courseId: String): ResultState<List<CurriculumItem>> {
        return try {
            coroutineScope {
                val lessonsDeferred = async {
                    lessonsCollection
                        .whereEqualTo("courseId", courseId)
                        .get()
                        .await()
                        .toObjects(Lesson::class.java)
                }
                val quizzesDeferred = async {
                    quizzesCollection
                        .whereEqualTo("courseId", courseId)
                        .get()
                        .await()
                        .toObjects(Quiz::class.java)
                }

                val lessons = lessonsDeferred.await().map { CurriculumItem.LessonItem(it) }
                val quizzes = quizzesDeferred.await().map { CurriculumItem.QuizItem(it) }

                val sorted = (lessons + quizzes).sortedBy { it.orderIndex }
                ResultState.Success(sorted)
            }
        } catch (e: Exception) {
            ResultState.Error(e.message ?: "Lấy nội dung khóa học thất bại")
        }
    }

    suspend fun updateOrder(items: List<CurriculumItem>): ResultState<Unit> {
        return try {
            items.mapIndexed { index, item -> item to index }
                .chunked(499)
                .forEach { chunk ->
                    val batch = firestore.batch()
                    chunk.forEach { (item, newIndex) ->
                        val ref = when (item) {
                            is CurriculumItem.LessonItem ->
                                lessonsCollection.document(item.id)
                            is CurriculumItem.QuizItem ->
                                quizzesCollection.document(item.id)
                        }
                        batch.update(ref, mapOf(
                            "orderIndex" to newIndex,
                            "updatedAt" to System.currentTimeMillis()
                        ))
                    }
                    batch.commit().await()
                }
            ResultState.Success(Unit)
        } catch (e: Exception) {
            ResultState.Error(e.message ?: "Cập nhật thứ tự thất bại")
        }
    }

    private suspend fun getNextOrderIndex(courseId: String): Int {
        val lastLessonIndex = lessonsCollection
            .whereEqualTo("courseId", courseId)
            .orderBy("orderIndex", Query.Direction.DESCENDING)
            .limit(1)
            .get()
            .await()
            .documents
            .firstOrNull()
            ?.getLong("orderIndex")
            ?.toInt()
            ?: -1

        val lastQuizIndex = quizzesCollection
            .whereEqualTo("courseId", courseId)
            .orderBy("orderIndex", Query.Direction.DESCENDING)
            .limit(1)
            .get()
            .await()
            .documents
            .firstOrNull()
            ?.getLong("orderIndex")
            ?.toInt()
            ?: -1

        return maxOf(lastLessonIndex, lastQuizIndex) + 1
    }
}
