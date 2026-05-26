package com.example.lms2.data.model

import com.google.firebase.firestore.DocumentId

/**
 * Định nghĩa mô hình dữ liệu Category dùng trong ứng dụng LMS Android.
 * File này khai báo contract dữ liệu được trao đổi giữa Firestore, repository, ViewModel và giao diện Compose.
 * Việc tách riêng model giúp các luồng nghiệp vụ dùng chung một cấu trúc dữ liệu nhất quán và dễ bảo trì.
 */

data class Category(
    /**
     * Mã document của danh mục trên Firestore.
     */
    @DocumentId
    val id: String = "",

    /**
     * Tên hiển thị của danh mục trên giao diện.
     */
    val name: String = "",
)
