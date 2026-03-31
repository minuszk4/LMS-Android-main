package com.example.lms2.util

import com.example.lms2.data.model.MyLearningItem

enum class MyLearningTab {
    IN_PROGRESS,
    COMPLETED
}

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

