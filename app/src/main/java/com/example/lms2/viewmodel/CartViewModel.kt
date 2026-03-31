package com.example.lms2.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.lms2.data.paging.PageRequest
import com.example.lms2.data.repository.CartRepository
import com.example.lms2.util.CartEvent
import com.example.lms2.util.CartUiState
import com.example.lms2.util.ResultState
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class CartViewModel(
    private val cartRepository: CartRepository = CartRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(CartUiState())
    val uiState: StateFlow<CartUiState> = _uiState.asStateFlow()

    private val _event = MutableSharedFlow<CartEvent>()
    val event = _event.asSharedFlow()

    fun loadCart(userId: String) {
        if (userId.isBlank() || _uiState.value.isLoading) return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            val cartResult = cartRepository.getOrCreateActiveCart(userId)
            val itemsResult = cartRepository.getCartItemsPage(
                userId = userId,
                pageRequest = PageRequest(
                    pageSize = 10,
                    cursor = null,
                    refresh = true,
                    useCache = true
                )
            )

            if (cartResult is ResultState.Success && itemsResult is ResultState.Success) {
                val currentState = _uiState.value
                val availableCourseIds = itemsResult.data.items.map { it.courseId }.toSet()
                val selectedIds = when {
                    !currentState.hasLoadedOnce && currentState.selectedCourseIds.isEmpty() -> availableCourseIds
                    else -> currentState.selectedCourseIds.intersect(availableCourseIds)
                }

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        hasLoadedOnce = true,
                        cart = cartResult.data,
                        items = itemsResult.data.items,
                        selectedCourseIds = selectedIds,
                        currentCursor = itemsResult.data.nextCursor,
                        hasMoreItems = itemsResult.data.hasMore,
                        isLoadingMore = false
                    )
                }
            } else {
                _uiState.update { it.copy(isLoading = false, hasLoadedOnce = true) }
                val message = (cartResult as? ResultState.Error)?.message
                    ?: (itemsResult as? ResultState.Error)?.message
                    ?: "Tải giỏ hàng thất bại"
                _event.emit(CartEvent.ShowError(message))
            }
        }
    }

    fun removeCourseFromCart(userId: String, courseId: String) {
        if (userId.isBlank() || courseId.isBlank()) return

        viewModelScope.launch {
            val previousState = _uiState.value
            val removedItem = previousState.items.firstOrNull { it.courseId == courseId }
            if (removedItem == null) {
                _event.emit(CartEvent.ShowError("Khóa học không tồn tại trong giỏ hàng"))
                return@launch
            }

            _uiState.update { state ->
                val updatedItems = state.items.filterNot { it.courseId == courseId }
                val updatedCart = state.cart?.copy(
                    itemCount = (state.cart.itemCount - 1).coerceAtLeast(0),
                    totalAmount = (state.cart.totalAmount - removedItem.coursePrice).coerceAtLeast(0.0),
                    updatedAt = System.currentTimeMillis()
                )

                state.copy(
                    items = updatedItems,
                    cart = updatedCart,
                    selectedCourseIds = state.selectedCourseIds - courseId
                )
            }

            when (val result = cartRepository.removeCourseFromCart(userId, courseId)) {
                is ResultState.Success -> {
                    _event.emit(
                        CartEvent.ItemRemoved(
                            courseId = courseId,
                            courseTitle = removedItem.courseTitle.ifBlank { "khóa học" }
                        )
                    )
                }
                is ResultState.Error -> {
                    _uiState.value = previousState
                    _event.emit(CartEvent.ShowError(result.message))
                }
                else -> Unit
            }
        }
    }

    fun toggleCourseSelection(courseId: String) {
        if (courseId.isBlank()) return

        _uiState.update { state ->
            val next = if (courseId in state.selectedCourseIds) {
                state.selectedCourseIds - courseId
            } else {
                state.selectedCourseIds + courseId
            }
            state.copy(selectedCourseIds = next)
        }
    }

    fun selectAllCourses() {
        _uiState.update { state ->
            state.copy(selectedCourseIds = state.items.map { it.courseId }.toSet())
        }
    }

    fun clearSelection() {
        _uiState.update { it.copy(selectedCourseIds = emptySet()) }
    }

    fun proceedToPayment() {
        val selectedIds = _uiState.value.selectedCourseIds.toList()
        if (selectedIds.isEmpty()) {
            viewModelScope.launch {
                _event.emit(CartEvent.ShowError("Vui lòng chọn ít nhất một khóa học để thanh toán"))
            }
            return
        }

        viewModelScope.launch {
            _event.emit(CartEvent.NavigateToPayment(selectedIds))
        }
    }

    fun loadMoreItems(userId: String) {
        if (userId.isBlank()) return
        val currentState = _uiState.value
        if (currentState.isLoadingMore || !currentState.hasMoreItems) return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingMore = true) }
            val pageRequest = PageRequest(
                pageSize = 10,
                cursor = currentState.currentCursor
            )
            when (val result = cartRepository.getCartItemsPage(userId, pageRequest)) {
                is ResultState.Success -> {
                    _uiState.update {
                        val mergedItems = (it.items + result.data.items)
                            .distinctBy { item -> item.id }
                        it.copy(
                            isLoadingMore = false,
                            items = mergedItems,
                            currentCursor = result.data.nextCursor,
                            hasMoreItems = result.data.hasMore
                        )
                    }
                }
                is ResultState.Error -> {
                    _uiState.update { it.copy(isLoadingMore = false) }
                    _event.emit(CartEvent.ShowError(result.message))
                }
                else -> {
                    _uiState.update { it.copy(isLoadingMore = false) }
                }
            }
        }
    }
}

