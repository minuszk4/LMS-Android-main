package com.example.lms.data.repository

import com.example.lms.data.model.QuizProgress
import com.example.lms.util.ResultState
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await

class QuizAttemptRepository {

    private val firestore = FirebaseFirestore.getInstance()
    private val quizProgressCollection = firestore.collection("quizProgress")


    // ─────────────────────────────────────────
    // SUBMIT QUIZ ATTEMPT
    // ─────────────────────────────────────────

    suspend fun submitQuizAttempt(
        userId: String,
        courseId: String,
        quizId: String,
        selectedAnswers: List<Int>,
        correctCount: Int,
        wrongCount: Int,
        score: Int,
        passingScore: Int
    ): ResultState<QuizProgress> {
        return try {
            val docRef = quizProgressCollection.document("${userId}_${quizId}")
            val now = System.currentTimeMillis()

            val updatedProgress = firestore.runTransaction { transaction ->
                val currentSnapshot = transaction.get(docRef)
                val currentProgress = currentSnapshot.toObject(QuizProgress::class.java)

                val currentAttempts = currentProgress?.attempts ?: 0
                val currentBestScore = currentProgress?.bestScore ?: 0

                val newAttempts = currentAttempts + 1
                val newBestScore = maxOf(currentBestScore, score)
                val isPassed = newBestScore >= passingScore

                val updateData = mapOf(
                    "quizId" to quizId,
                    "userId" to userId,
                    "courseId" to courseId,
                    "attempts" to newAttempts,
                    "bestScore" to newBestScore,
                    "isPassed" to isPassed,
                    "lastAttemptAt" to now,
                    "lastAnswers" to selectedAnswers,
                    "lastCorrectCount" to correctCount,
                    "lastWrongCount" to wrongCount
                )

                transaction.set(docRef, updateData, SetOptions.merge())

                QuizProgress(
                    quizId = quizId,
                    userId = userId,
                    courseId = courseId,
                    attempts = newAttempts,
                    bestScore = newBestScore,
                    isPassed = isPassed,
                    lastAttemptAt = now,
                    lastAnswers = selectedAnswers,
                    lastCorrectCount = correctCount,
                    lastWrongCount = wrongCount
                )
            }.await()

            ResultState.Success(updatedProgress)
        } catch (e: Exception) {
            ResultState.Error(e.message ?: "Nộp bài kiểm tra thất bại")
        }
    }
}