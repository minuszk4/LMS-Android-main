package com.example.lms2.data.repository

import com.example.lms2.data.model.Instructor
import com.example.lms2.util.ResultState
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

            val normalizedInstructor = normalizeInstructor(
                if (instructor.uid.isBlank()) instructor.copy(uid = snapshot.id) else instructor
            )

            if (shouldBackfillLegacyBankFields(instructor, normalizedInstructor)) {
                runCatching {
                    val backfillData = mapOf(
                        "uid" to normalizedInstructor.uid,
                        "bankName" to normalizedInstructor.bankName,
                        "bankAccountNumber" to normalizedInstructor.bankAccountNumber,
                        "bankAccount" to normalizedInstructor.bankAccount
                    )

                    instructorsCollection
                        .document(snapshot.id)
                        .set(backfillData, SetOptions.merge())
                        .await()
                }
            }

            ResultState.Success(normalizedInstructor)
        } catch (e: Exception) {
            ResultState.Error(e.message ?: "Lấy thông tin giảng viên thất bại")
        }
    }

    suspend fun updateInstructorProfile(
        instructorId: String,
        expertise: String,
        qualification: String,
        experienceYears: Int
    ): ResultState<Unit> {
        if (instructorId.isBlank()) return ResultState.Error("Thiếu thông tin giảng viên")

        val normalizedExpertise = expertise.trim()
        val normalizedQualification = qualification.trim()

        if (normalizedExpertise.isBlank() || normalizedQualification.isBlank() || experienceYears <= 0) {
            return ResultState.Error("Vui lòng nhập đầy đủ chuyên môn, bằng cấp và số năm kinh nghiệm (> 0)")
        }

        return try {
            val data = mapOf(
                "uid" to instructorId,
                "expertise" to normalizedExpertise,
                "qualification" to normalizedQualification,
                "experienceYears" to experienceYears
            )

            instructorsCollection
                .document(instructorId)
                .set(data, SetOptions.merge())
                .await()

            ResultState.Success(Unit)
        } catch (e: Exception) {
            ResultState.Error(e.message ?: "Cập nhật thông tin giảng viên thất bại")
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
        if (
            instructor.bankName.isNotBlank() &&
            instructor.bankCode.isNotBlank() &&
            instructor.bankAccountNumber.isNotBlank() &&
            instructor.bankAccountHolder.isNotBlank()
        ) {
            return true
        }

        return hasLegacyBankInfo(instructor)
    }

    private fun hasLegacyBankInfo(instructor: Instructor): Boolean {
        if (instructor.bankAccount.isBlank()) return false

        val parts = instructor.bankAccount.split("-", limit = 2).map { it.trim() }
        val guessedBankName = parts.getOrNull(0).orEmpty()
        val guessedAccountNumber = parts.getOrNull(1).orEmpty()

        return guessedBankName.isNotBlank() && guessedAccountNumber.isNotBlank()
    }

    private fun shouldBackfillLegacyBankFields(original: Instructor, normalized: Instructor): Boolean {
        if (original.bankAccount.isBlank()) return false

        return (original.bankName.isBlank() && normalized.bankName.isNotBlank()) ||
            (original.bankAccountNumber.isBlank() && normalized.bankAccountNumber.isNotBlank())
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

        val parts = instructor.bankAccount.split("-", limit = 2).map { it.trim() }
        val guessedBankName = if (parts.isNotEmpty()) parts[0] else ""
        val guessedAccountNumber = if (parts.size > 1) parts[1] else ""

        return instructor.copy(
            bankName = instructor.bankName.ifBlank { guessedBankName },
            bankAccountNumber = instructor.bankAccountNumber.ifBlank { guessedAccountNumber }
        )
    }
}

