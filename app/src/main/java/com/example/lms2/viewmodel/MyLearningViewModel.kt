package com.example.lms2.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.lms2.data.paging.PageRequest
import com.example.lms2.data.repository.MyLearningRepository
import com.example.lms2.util.MyLearningEvent
import com.example.lms2.util.MyLearningTab
import com.example.lms2.util.MyLearningUiState
import com.example.lms2.util.ResultState
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MyLearningViewModel(
    private val myLearningRepository: MyLearningRepository = MyLearningRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(MyLearningUiState())
    val uiState: StateFlow<MyLearningUiState> = _uiState.asStateFlow()

    private val _event = MutableSharedFlow<MyLearningEvent>()
    val event = _event.asSharedFlow()

    fun loadMyLearning(userId: String) {
        if (_uiState.value.isLoading) return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            when (
                val result = myLearningRepository.getMyLearningPaged(
                    userId = userId,
                    pageRequest = PageRequest(
                        pageSize = 10,
                        cursor = null,
                        refresh = true,
                        useCache = true
                    )
                )
            ) {
                is ResultState.Success -> {
                    val inProgress = result.data.items.filter { !it.isCompleted }
                    val completed = result.data.items.filter { it.isCompleted }
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            hasLoadedOnce = true,
                            inProgressCourses = inProgress,
                            completedCourses = completed,
                            inProgressCursor = result.data.nextCursor,
                            completedCursor = result.data.nextCursor,
                            hasMoreInProgress = result.data.hasMore,
                            hasMoreCompleted = result.data.hasMore,
                            isLoadingMore = false
                        )
                    }
                }

                is ResultState.Error -> {
                    _uiState.update { it.copy(isLoading = false, hasLoadedOnce = true) }
                    _event.emit(MyLearningEvent.ShowError(result.message))
                }

                else -> {
                    _uiState.update { it.copy(isLoading = false, hasLoadedOnce = true) }
                }
            }
        }
    }

    fun refreshMyLearning(userId: String) {
        loadMyLearning(userId)
    }

    fun selectTab(tab: MyLearningTab) {
        _uiState.update { it.copy(selectedTab = tab) }
    }

    fun loadMoreInProgress(userId: String) {
        loadMore(userId)
    }

    fun loadMoreCompleted(userId: String) {
        loadMore(userId)
    }

    private fun loadMore(userId: String) {
        val currentState = _uiState.value
        val currentCursor = currentState.inProgressCursor ?: currentState.completedCursor
        val hasMore = currentState.hasMoreInProgress || currentState.hasMoreCompleted
        if (currentState.isLoadingMore || !hasMore) return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingMore = true) }
            val pageRequest = PageRequest(
                pageSize = 10,
                cursor = currentCursor
            )
            when (val result = myLearningRepository.getMyLearningPaged(userId, pageRequest)) {
                is ResultState.Success -> {
                    _uiState.update {
                        val nextInProgress = (it.inProgressCourses + result.data.items.filter { item -> !item.isCompleted })
                            .distinctBy { item -> item.course.id }
                        val nextCompleted = (it.completedCourses + result.data.items.filter { item -> item.isCompleted })
                            .distinctBy { item -> item.course.id }
                        it.copy(
                            isLoadingMore = false,
                            inProgressCourses = nextInProgress,
                            completedCourses = nextCompleted,
                            inProgressCursor = result.data.nextCursor,
                            completedCursor = result.data.nextCursor,
                            hasMoreInProgress = result.data.hasMore,
                            hasMoreCompleted = result.data.hasMore
                        )
                    }
                }
                is ResultState.Error -> {
                    _uiState.update { it.copy(isLoadingMore = false) }
                    _event.emit(MyLearningEvent.ShowError(result.message))
                }
                else -> {
                    _uiState.update { it.copy(isLoadingMore = false) }
                }
            }
        }
    }
}

