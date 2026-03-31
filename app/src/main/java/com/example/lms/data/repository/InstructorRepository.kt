package com.example.lms.data.repository

import com.example.lms.data.model.Instructor
import com.example.lms.util.ResultState
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await

class InstructorRepository {

    private val firestore = FirebaseFirestore.getInstance()
    private val instructorsCollection = firestore.collection("instructors")

    suspend fun getInstructorById(instructorId: String): ResultState<Instructor> {
        if (instructorId.isBlank()) return ResultState.Error("Thiếu thông tin giảng viên")

        return try {
            val snapshot = instructorsCollection.document(instructorId).get().await()
            if (!snapshot.exists()) {
                return ResultState.Error("Chưa có thông tin cá nhân giảng viên")
            }

            val instructor = snapshot.toObject(Instructor::class.java)
                ?: return ResultState.Error("Không đọc được thông tin giảng viên")

            ResultState.Success(
                normalizeInstructor(
                    if (instructor.uid.isBlank()) instructor.copy(uid = snapshot.id) else instructor
                )
            )
        } catch (e: Exception) {
            ResultState.Error(e.message ?: "Lấy thông tin giảng viên thất bại")
        }
    }

    suspend fun updateBankInfo(
        instructorId: String,
        bankName: String,
        bankCode: String,
        bankAccountNumber: String,
        bankAccountHolder: String
    ): ResultState<Unit> {
        if (instructorId.isBlank()) return ResultState.Error("Thiếu thông tin giảng viên")

        val normalizedBankName = bankName.trim()
        val normalizedBankCode = bankCode.trim()
        val normalizedAccountNumber = bankAccountNumber.trim()
        val normalizedAccountHolder = bankAccountHolder.trim()

        if (normalizedBankName.isBlank() || normalizedBankCode.isBlank() || normalizedAccountNumber.isBlank() || normalizedAccountHolder.isBlank()) {
            return ResultState.Error("Vui lòng nhập đầy đủ thông tin tài khoản ngân hàng")
        }

        return try {
            val data = mapOf(
                "uid" to instructorId,
                "bankName" to normalizedBankName,
                "bankCode" to normalizedBankCode,
                "bankAccountNumber" to normalizedAccountNumber,
                "bankAccountHolder" to normalizedAccountHolder,
                "bankAccount" to "$normalizedBankName - $normalizedAccountNumber"
            )

            instructorsCollection
                .document(instructorId)
                .set(data, SetOptions.merge())
                .await()

            ResultState.Success(Unit)
        } catch (e: Exception) {
            ResultState.Error(e.message ?: "Cập nhật thông tin ngân hàng thất bại")
        }
    }

    fun hasValidBankInfo(instructor: Instructor): Boolean {
        return instructor.bankName.isNotBlank() &&
            instructor.bankCode.isNotBlank() &&
            instructor.bankAccountNumber.isNotBlank() &&
            instructor.bankAccountHolder.isNotBlank()
    }

    private fun normalizeInstructor(instructor: Instructor): Instructor {
        if (
            instructor.bankName.isNotBlank() &&
            instructor.bankCode.isNotBlank() &&
            instructor.bankAccountNumber.isNotBlank() &&
            instructor.bankAccountHolder.isNotBlank()
        ) {
            return instructor
        }

        if (instructor.bankAccount.isBlank()) {
            return instructor
        }

        val parts = instructor.bankAccount.split("-").map { it.trim() }
        val guessedBankName = if (parts.isNotEmpty()) parts[0] else ""
        val guessedAccountNumber = if (parts.size > 1) parts[1] else ""

        return instructor.copy(
            bankName = instructor.bankName.ifBlank { guessedBankName },
            bankAccountNumber = instructor.bankAccountNumber.ifBlank { guessedAccountNumber }
        )
    }
}

