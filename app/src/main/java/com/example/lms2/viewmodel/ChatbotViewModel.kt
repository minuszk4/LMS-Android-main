package com.example.lms2.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.lms2.data.model.ChatSession
import com.example.lms2.data.repository.OpenRouterChatbotRepository
import com.example.lms2.util.ChatbotEvent
import com.example.lms2.util.ChatbotUiState
import com.example.lms2.util.ResultState
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ChatbotViewModel(
    private val repository: OpenRouterChatbotRepository = OpenRouterChatbotRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatbotUiState())
    val uiState: StateFlow<ChatbotUiState> = _uiState.asStateFlow()

    private val _event = MutableSharedFlow<ChatbotEvent>()
    val event = _event.asSharedFlow()

    private var lastUserId: String = ""
    private var currentUserId: String = ""

    fun init(userId: String) {
        if (userId.isBlank()) return
        currentUserId = userId
        if (_uiState.value.hasLoadedOnce && lastUserId == userId) return
        loadSessionsAndMessages(userId)
    }

    fun refresh(userId: String) {
        if (userId.isBlank()) return
        currentUserId = userId
        loadSessionsAndMessages(userId)
    }

    fun selectSession(sessionId: String) {
        if (sessionId.isBlank()) return
        if (_uiState.value.sessionId == sessionId) return

        val target = _uiState.value.sessions.firstOrNull { it.id == sessionId } ?: return
        loadMessagesForSession(target)
    }

    fun createNewSession(userId: String) {
        if (userId.isBlank()) return
        currentUserId = userId

        viewModelScope.launch {
            // Check for empty sessions
            val currentSessions = _uiState.value.sessions
            val emptySession = currentSessions.firstOrNull { session ->
                val messages = _uiState.value.messages
                messages.isEmpty() && session.id == _uiState.value.sessionId
            }

            if (emptySession != null) {
                _uiState.update { it.copy(canCreateNewSession = false) }
                _event.emit(ChatbotEvent.ShowError("Bạn đang có một phiên mới chưa gửi tin nhắn"))
                return@launch
            }

            val nextIndex = (_uiState.value.sessions.size + 1).coerceAtLeast(1)
            val title = "Phiên $nextIndex"

            when (val result = repository.createSession(userId, title)) {
                is ResultState.Success -> {
                    val newSession = result.data
                    _uiState.update {
                        it.copy(
                            sessions = (listOf(newSession) + it.sessions).distinctBy { session -> session.id }
                                .sortedByDescending { session -> session.lastMessageAt },
                            sessionId = newSession.id,
                            sessionTitle = newSession.title,
                            messages = emptyList(),
                            hasLoadedOnce = true,
                            canCreateNewSession = false
                        )
                    }
                }

                is ResultState.Error -> {
                    _event.emit(ChatbotEvent.ShowError(result.message))
                }

                else -> Unit
            }
        }
    }

    fun deleteSession(sessionId: String) {
        if (sessionId.isBlank()) return

        val existing = _uiState.value.sessions.firstOrNull { it.id == sessionId } ?: return

        viewModelScope.launch {
            when (val result = repository.deleteSession(sessionId)) {
                is ResultState.Success -> {
                    val state = _uiState.value
                    val remainingSessions = state.sessions
                        .filterNot { it.id == sessionId }
                        .sortedByDescending { it.lastMessageAt }

                    if (remainingSessions.isEmpty()) {
                        val userId = currentUserId.ifBlank { lastUserId }
                        if (userId.isBlank()) {
                            _uiState.update {
                                it.copy(
                                    sessions = emptyList(),
                                    sessionId = "",
                                    sessionTitle = "",
                                    messages = emptyList(),
                                    canCreateNewSession = true
                                )
                            }
                            return@launch
                        }

                        when (val createResult = repository.getOrCreateActiveSession(userId)) {
                            is ResultState.Success -> {
                                val newSession = createResult.data
                                _uiState.update {
                                    it.copy(
                                        sessions = listOf(newSession),
                                        sessionId = newSession.id,
                                        sessionTitle = newSession.title,
                                        messages = emptyList(),
                                        canCreateNewSession = false
                                    )
                                }
                            }

                            is ResultState.Error -> {
                                _event.emit(ChatbotEvent.ShowError(createResult.message))
                            }

                            else -> Unit
                        }
                    } else if (state.sessionId == existing.id) {
                        val targetSession = remainingSessions.first()
                        _uiState.update {
                            it.copy(
                                sessions = remainingSessions,
                                sessionId = targetSession.id,
                                sessionTitle = targetSession.title,
                                messages = emptyList()
                            )
                        }
                        loadMessagesForSession(targetSession)
                    } else {
                        _uiState.update {
                            it.copy(sessions = remainingSessions)
                        }
                    }
                }

                is ResultState.Error -> {
                    _event.emit(ChatbotEvent.ShowError(result.message))
                }

                else -> Unit
            }
        }
    }

    fun sendMessage(content: String) {
        val trimmed = content.trim()
        if (trimmed.isBlank()) return

        val sessionId = _uiState.value.sessionId
        if (sessionId.isBlank()) {
            viewModelScope.launch {
                _event.emit(ChatbotEvent.ShowError("Phiên chat chưa sẵn sàng"))
            }
            return
        }

        if (currentUserId.isBlank()) {
            viewModelScope.launch {
                _event.emit(ChatbotEvent.ShowError("Thiếu thông tin người dùng"))
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSending = true) }

            when (val result = repository.sendUserMessageAndAIReply(sessionId, currentUserId, trimmed)) {
                is ResultState.Success -> {
                    val latestAt = result.data.maxOfOrNull { it.createdAt } ?: System.currentTimeMillis()
                    _uiState.update {
                        it.copy(
                            isSending = false,
                            messages = (it.messages + result.data).sortedBy { message -> message.createdAt },
                            sessions = it.sessions
                                .map { session ->
                                    if (session.id == sessionId) session.copy(lastMessageAt = latestAt) else session
                                }
                                .sortedByDescending { session -> session.lastMessageAt },
                            canCreateNewSession = true
                        )
                    }
                }

                is ResultState.Error -> {
                    _uiState.update { it.copy(isSending = false) }
                    _event.emit(ChatbotEvent.ShowError(result.message))
                }

                else -> {
                    _uiState.update { it.copy(isSending = false) }
                }
            }
        }
    }

    private fun loadSessionsAndMessages(userId: String) {
        viewModelScope.launch {
            lastUserId = userId
            _uiState.update { it.copy(isLoading = true) }

            when (val sessionsResult = repository.getUserSessions(userId)) {
                is ResultState.Success -> {
                    val sessions = sessionsResult.data.sortedByDescending { it.lastMessageAt }
                    val selectedSession = sessions.firstOrNull()

                    if (selectedSession != null) {
                        _uiState.update {
                            it.copy(
                                sessions = sessions,
                                sessionId = selectedSession.id,
                                sessionTitle = selectedSession.title,
                                canCreateNewSession = true
                            )
                        }
                        loadMessagesForSession(selectedSession)
                    } else {
                        when (val createResult = repository.getOrCreateActiveSession(userId)) {
                            is ResultState.Success -> {
                                val session = createResult.data
                                _uiState.update {
                                    it.copy(
                                        isLoading = false,
                                        hasLoadedOnce = true,
                                        sessionId = session.id,
                                        sessionTitle = session.title,
                                        sessions = listOf(session),
                                        messages = emptyList(),
                                        canCreateNewSession = false
                                    )
                                }
                            }

                            is ResultState.Error -> {
                                _uiState.update { it.copy(isLoading = false, hasLoadedOnce = true) }
                                _event.emit(ChatbotEvent.ShowError(createResult.message))
                            }

                            else -> _uiState.update { it.copy(isLoading = false, hasLoadedOnce = true) }
                        }
                    }
                }

                is ResultState.Error -> {
                    _uiState.update { it.copy(isLoading = false, hasLoadedOnce = true) }
                    _event.emit(ChatbotEvent.ShowError(sessionsResult.message))
                }

                else -> {
                    _uiState.update { it.copy(isLoading = false, hasLoadedOnce = true) }
                }
            }

            // Check for empty sessions
            val currentMessages = _uiState.value.messages
            _uiState.update {
                it.copy(canCreateNewSession = currentMessages.isNotEmpty())
            }
        }
    }

    private fun loadMessagesForSession(session: ChatSession) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    sessionId = session.id,
                    sessionTitle = session.title
                )
            }

            when (val messagesResult = repository.getSessionMessages(session.id)) {
                is ResultState.Success -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            hasLoadedOnce = true,
                            messages = messagesResult.data,
                            canCreateNewSession = messagesResult.data.isNotEmpty()
                        )
                    }
                }

                is ResultState.Error -> {
                    _uiState.update { it.copy(isLoading = false, hasLoadedOnce = true) }
                    _event.emit(ChatbotEvent.ShowError(messagesResult.message))
                }

                else -> {
                    _uiState.update { it.copy(isLoading = false, hasLoadedOnce = true) }
                }
            }
        }
    }
}

