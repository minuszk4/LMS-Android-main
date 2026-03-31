package com.example.lms.data.repository

import com.example.lms.data.model.User
import com.example.lms.data.model.UserRole
import com.example.lms.util.ResultState
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await

class AuthRepository {

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()

    suspend fun getUserDetails(uid: String): ResultState<User> {
        return try {
            val snapshot = firestore.collection("users").document(uid).get().await()
            val user = snapshot.toObject(User::class.java)
            if (user != null) {
                ResultState.Success(user)
            } else {
                ResultState.Error("Không tìm thấy thông tin người dùng")
            }
        } catch (e: Exception) {
            ResultState.Error(e.localizedMessage ?: "Lỗi khi lấy thông tin người dùng")
        }
    }

    suspend fun login(
        email: String,
        password: String
    ): ResultState<String> {
        return try {
            val result = auth
                .signInWithEmailAndPassword(email.trim(), password)
                .await()
            val user = result.user
            if (user != null) {
                ResultState.Success(user.uid)
            } else {
                ResultState.Error("Không tìm thấy tài khoản")
            }
        } catch (e: FirebaseAuthException) {
            ResultState.Error(mapFirebaseLoginError(e.errorCode))
        } catch (e: Exception) {
            ResultState.Error("Lỗi hệ thống: ${e.localizedMessage}")
        }
    }

    suspend fun register(
        email: String,
        password: String,
        fullName: String
    ): ResultState<String> {
        return try {
            val result = auth
                .createUserWithEmailAndPassword(email.trim(), password)
                .await()
            
            val firebaseUser = result.user
            
            if (firebaseUser != null) {
                val newUser = User(
                    uid = firebaseUser.uid,
                    fullName = fullName,
                    email = email.trim(),
                    role = UserRole.STUDENT,
                    createdAt = System.currentTimeMillis()
                )

                firestore.collection("users")
                    .document(firebaseUser.uid)
                    .set(newUser)
                    .await()

                ResultState.Success(firebaseUser.uid)
            } else {
                ResultState.Error("Đăng ký thất bại")
            }
        } catch (e: FirebaseAuthException) {
            ResultState.Error(mapFirebaseRegisterError(e.errorCode))
        } catch (e: Exception) {
            ResultState.Error("Lỗi khi lưu dữ liệu: ${e.localizedMessage}")
        }
    }

    suspend fun signInWithGoogle(idToken: String): ResultState<String> {
        return try {
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            val result = auth.signInWithCredential(credential).await()
            val firebaseUser = result.user
            
            if (firebaseUser != null) {
                val userDoc = firestore.collection("users").document(firebaseUser.uid).get().await()
                
                if (!userDoc.exists()) {
                    val newUser = User(
                        uid = firebaseUser.uid,
                        fullName = firebaseUser.displayName ?: "Người dùng Google",
                        email = firebaseUser.email ?: "",
                        role = UserRole.STUDENT,
                        avatarUrl = firebaseUser.photoUrl?.toString(),
                        createdAt = System.currentTimeMillis()
                    )
                    firestore.collection("users").document(firebaseUser.uid).set(newUser).await()
                }
                
                ResultState.Success(firebaseUser.uid)
            } else {
                ResultState.Error("Đăng nhập Google thất bại")
            }
        } catch (e: Exception) {
            ResultState.Error("Lỗi đăng nhập Google: ${e.localizedMessage}")
        }
    }

    suspend fun sendPasswordResetEmail(email: String): ResultState<Unit> {
        return try {
            auth.sendPasswordResetEmail(email.trim()).await()
            ResultState.Success(Unit)
        } catch (e: FirebaseAuthException) {
            ResultState.Error(mapFirebaseResetError(e.errorCode))
        } catch (e: Exception) {
            ResultState.Error("Gửi email khôi phục thất bại: ${e.localizedMessage}")
        }
    }

    suspend fun updateProfile(
        uid: String,
        fullName: String,
        avatarUrl: String?
    ): ResultState<Unit> {
        return try {
            val updates = mapOf(
                "fullName" to fullName.trim(),
                "avatarUrl" to avatarUrl?.trim().orEmpty().ifBlank { null }
            )

            firestore.collection("users")
                .document(uid)
                .set(updates, SetOptions.merge())
                .await()

            ResultState.Success(Unit)
        } catch (e: Exception) {
            ResultState.Error(e.localizedMessage ?: "Không thể cập nhật thông tin tài khoản")
        }
    }

    fun logout() {
        auth.signOut()
    }

    fun isUserLoggedIn(): Boolean {
        return auth.currentUser != null
    }

    fun getCurrentUserId(): String? {
        return auth.currentUser?.uid
    }

    /* ========================
       ERROR MAPPING
       ======================== */

    private fun mapFirebaseLoginError(code: String): String {
        return when (code) {
            "ERROR_INVALID_EMAIL" -> "Email không hợp lệ"
            "ERROR_USER_NOT_FOUND" -> "Tài khoản không tồn tại"
            "ERROR_WRONG_PASSWORD" -> "Mật khẩu không chính xác"
            "ERROR_INVALID_CREDENTIAL" -> "Email hoặc mật khẩu không chính xác"
            "ERROR_USER_DISABLED" -> "Tài khoản đã bị vô hiệu hóa"
            "ERROR_TOO_MANY_REQUESTS" -> "Quá nhiều yêu cầu. Vui lòng thử lại sau."
            else -> "Đăng nhập thất bại ($code)"
        }
    }

    private fun mapFirebaseRegisterError(code: String): String {
        return when (code) {
            "ERROR_INVALID_EMAIL" -> "Email không hợp lệ"
            "ERROR_EMAIL_ALREADY_IN_USE" -> "Email đã được sử dụng"
            "ERROR_WEAK_PASSWORD" -> "Mật khẩu quá yếu (tối thiểu 6 ký tự)"
            else -> "Đăng ký thất bại ($code)"
        }
    }

    private fun mapFirebaseResetError(code: String): String {
        return when (code) {
            "ERROR_INVALID_EMAIL" -> "Email không hợp lệ"
            "ERROR_USER_NOT_FOUND" -> "Email này chưa được đăng ký tài khoản"
            else -> "Gửi email thất bại ($code)"
        }
    }
}
