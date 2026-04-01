package com.example.lms2.data.model

/**
 * Hồ sơ đăng ký giảng viên do học viên cung cấp để admin duyệt.
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
