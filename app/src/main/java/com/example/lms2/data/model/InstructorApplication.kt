package com.example.lms2.data.model

/**
 * Định nghĩa mô hình dữ liệu InstructorApplication dùng trong ứng dụng LMS Android.
 * File này khai báo contract dữ liệu được trao đổi giữa Firestore, repository, ViewModel và giao diện Compose.
 * Việc tách riêng model giúp các luồng nghiệp vụ dùng chung một cấu trúc dữ liệu nhất quán và dễ bảo trì.
 */

data class InstructorApplication(
    val expertise: String = "",            // Lĩnh vực chuyên môn
    val experienceYears: Int = 0,           // Số năm kinh nghiệm giảng dạy/làm việc
    val qualification: String = "",        // Bằng cấp/chứng chỉ chính
    val bio: String = "",                  // Mô tả ngắn về kinh nghiệm
    val portfolioUrl: String = "",         // Link portfolio/website (optional)
    val bankAccountName: String = "",      // Tên chủ tài khoản (optional)
    val bankAccountNumber: String = "",    // Số tài khoản (optional)
    val bankName: String = ""              // Tên ngân hàng (optional)
)
