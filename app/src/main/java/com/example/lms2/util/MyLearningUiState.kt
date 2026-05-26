package com.example.lms2.util

import com.example.lms2.data.model.MyLearningItem

/**
 * Mô tả trạng thái giao diện của luồng MyLearning.
 * Đối tượng này gom dữ liệu hiển thị, cờ tải, lỗi kiểm tra và các trạng thái tạm mà Compose cần quan sát.
 * Việc gom toàn bộ state vào một nơi giúp màn hình render nhất quán và dễ kiểm thử hơn.
 */

enum class MyLearningTab {
    IN_PROGRESS,
    COMPLETED
}

/**
 * Khai báo MyLearningUiState trong file này để phục vụ một trách nhiệm cụ thể của hệ thống.
 */

data class MyLearningUiState(
    val isLoading: Boolean = false,
    val hasLoadedOnce: Boolean = false,
    val selectedTab: MyLearningTab = MyLearningTab.IN_PROGRESS,
    val inProgressCourses: List<MyLearningItem> = emptyList(),
    val completedCourses: List<MyLearningItem> = emptyList(),
    // Pagination
    val isLoadingMore: Boolean = false,
    val inProgressCursor: String? = null,
    val completedCursor: String? = null,
    val hasMoreInProgress: Boolean = true,
    val hasMoreCompleted: Boolean = true
) {
    val visibleCourses: List<MyLearningItem>
        get() = when (selectedTab) {
            MyLearningTab.IN_PROGRESS -> inProgressCourses
            MyLearningTab.COMPLETED -> completedCourses
        }
}

