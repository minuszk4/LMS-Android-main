package com.example.lms2.data.model

import com.google.firebase.firestore.PropertyName

/**
 * Định nghĩa mô hình dữ liệu Review dùng trong ứng dụng LMS Android.
 * File này khai báo contract dữ liệu được trao đổi giữa Firestore, repository, ViewModel và giao diện Compose.
 * Việc tách riêng model giúp các luồng nghiệp vụ dùng chung một cấu trúc dữ liệu nhất quán và dễ bảo trì.
 */

data class Review(
    val id: String = "",
    val courseId: String = "",
    val userId: String = "",
    val userName: String = "",
    val userAvatarUrl: String = "",
    val rating: Int = 1,
    val content: String = "",

    @get:PropertyName("isEdited")
    @set:PropertyName("isEdited")
    var isEdited: Boolean = false,

    @get:PropertyName("isHidden")
    @set:PropertyName("isHidden")
    var isHidden: Boolean = false,

    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

