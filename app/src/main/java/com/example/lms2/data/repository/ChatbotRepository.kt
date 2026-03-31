package com.example.lms2.data.repository

import com.example.lms2.BuildConfig
import com.example.lms2.data.cache.RepositoryCache
import com.example.lms2.data.cache.CacheTTL
import com.example.lms2.data.model.ChatMessage
import com.example.lms2.data.model.ChatSender
import com.example.lms2.data.model.ChatSession
import com.example.lms2.data.model.ChatSessionStatus
import com.example.lms2.data.paging.PageRequest
import com.example.lms2.data.paging.PageResult
import com.example.lms2.util.ResultState
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

class ChatbotRepository {

    private val firestore = FirebaseFirestore.getInstance()
    private val chatSessionsCollection = firestore.collection("chatSessions")
    private val chatMessagesCollection = firestore.collection("chatMessages")

    suspend fun getOrCreateActiveSession(
        userId: String,
        defaultTitle: String = "Trợ lý học tập"
    ): ResultState<ChatSession> {
        if (userId.isBlank()) return ResultState.Error("Thiếu thông tin người dùng")

        return try {
            val snapshot = chatSessionsCollection
                .whereEqualTo("userId", userId)
                .whereEqualTo("status", ChatSessionStatus.ACTIVE.name)
                .get()
                .await()

            val activeSession = snapshot.documents
                .map { toChatSession(it.data.orEmpty(), it.id) }
                .maxByOrNull { it.lastMessageAt }

            if (activeSession != null) {
                ResultState.Success(activeSession)
            } else {
                createSession(userId, defaultTitle)
            }
        } catch (e: Exception) {
            ResultState.Error(e.message ?: "Khởi tạo phiên chat thất bại")
        }
    }

    suspend fun createSession(
        userId: String,
        title: String = "Trợ lý học tập"
    ): ResultState<ChatSession> {
        if (userId.isBlank()) return ResultState.Error("Thiếu thông tin người dùng")

        return try {
            val now = System.currentTimeMillis()
            val sessionRef = chatSessionsCollection.document()
            val session = ChatSession(
                id = sessionRef.id,
                userId = userId,
                title = title,
                status = ChatSessionStatus.ACTIVE,
                lastMessageAt = now,
                createdAt = now
            )
            sessionRef.set(session.toMap()).await()
            invalidateUserSessionsCache(userId)
            ResultState.Success(session)
        } catch (e: Exception) {
            ResultState.Error(e.message ?: "Tạo phiên chat thất bại")
        }
    }

    suspend fun getUserSessionsPaged(
        userId: String,
        pageRequest: PageRequest
    ): ResultState<PageResult<ChatSession>> {
        if (userId.isBlank()) return ResultState.Error("Thiếu thông tin người dùng")

        val cacheKey = "chatSessions:$userId:${pageRequest.pageSize}"
        RepositoryCache.get<PageResult<ChatSession>>(cacheKey)?.let { 
            return ResultState.Success(it) 
        }

        return try {
            val snapshot = chatSessionsCollection
                .whereEqualTo("userId", userId)
                .orderBy("lastMessageAt", Query.Direction.DESCENDING)
                .get()
                .await()

            val sessions = snapshot.documents
                .map { toChatSession(it.data.orEmpty(), it.id) }

            if (sessions.isEmpty()) {
                val emptyResult = PageResult(
                    items = emptyList<ChatSession>(),
                    hasMore = false,
                    nextCursor = null,
                    totalCount = 0
                )
                RepositoryCache.put(cacheKey, emptyResult, CacheTTL.MEDIUM)
                return ResultState.Success(emptyResult)
            }

            val start = pageRequest.cursor?.toIntOrNull() ?: 0
            val end = (start + pageRequest.pageSize).coerceAtMost(sessions.size)
            val paginatedSessions = sessions.subList(start, end)

            val result = PageResult(
                items = paginatedSessions,
                hasMore = end < sessions.size,
                nextCursor = if (end < sessions.size) end.toString() else null,
                totalCount = sessions.size
            )
            RepositoryCache.put(cacheKey, result, CacheTTL.MEDIUM)
            ResultState.Success(result)
        } catch (e: Exception) {
            ResultState.Error(e.message ?: "Lấy danh sách phiên chat thất bại")
        }
    }

    suspend fun getUserSessions(userId: String): ResultState<List<ChatSession>> {
        if (userId.isBlank()) return ResultState.Error("Thiếu thông tin người dùng")

        return try {
            val snapshot = chatSessionsCollection
                .whereEqualTo("userId", userId)
                .get()
                .await()

            val sessions = snapshot.documents
                .map { toChatSession(it.data.orEmpty(), it.id) }
                .sortedByDescending { it.lastMessageAt }

            ResultState.Success(sessions)
        } catch (e: Exception) {
            ResultState.Error(e.message ?: "Lấy danh sách phiên chat thất bại")
        }
    }

    private fun invalidateUserSessionsCache(userId: String) {
        if (userId.isBlank()) return
        RepositoryCache.invalidateByPrefix("chatSessions:$userId")
    }

    suspend fun findLatestEmptyActiveSession(userId: String): ResultState<ChatSession?> {
        if (userId.isBlank()) return ResultState.Error("Thiếu thông tin người dùng")

        return try {
            val snapshot = chatSessionsCollection
                .whereEqualTo("userId", userId)
                .whereEqualTo("status", ChatSessionStatus.ACTIVE.name)
                .get()
                .await()

            val sessions = snapshot.documents
                .map { toChatSession(it.data.orEmpty(), it.id) }
                .sortedByDescending { it.lastMessageAt }

            val emptySession = sessions.firstOrNull { session ->
                val messageSnapshot = chatMessagesCollection
                    .whereEqualTo("sessionId", session.id)
                    .limit(1)
                    .get()
                    .await()
                messageSnapshot.isEmpty
            }

            ResultState.Success(emptySession)
        } catch (e: Exception) {
            ResultState.Error(e.message ?: "Kiểm tra phiên chat thất bại")
        }
    }

    suspend fun getSessionMessages(sessionId: String): ResultState<List<ChatMessage>> {
        if (sessionId.isBlank()) return ResultState.Error("Thiếu thông tin phiên chat")

        return try {
            val snapshot = chatMessagesCollection
                .whereEqualTo("sessionId", sessionId)
                .get()
                .await()

            val messages = snapshot.documents
                .map { toChatMessage(it.data.orEmpty(), it.id) }
                .sortedBy { it.createdAt }

            ResultState.Success(messages)
        } catch (e: Exception) {
            ResultState.Error(e.message ?: "Lấy lịch sử hội thoại thất bại")
        }
    }

    suspend fun sendMessage(
        sessionId: String,
        sender: ChatSender,
        content: String
    ): ResultState<ChatMessage> {
        if (sessionId.isBlank()) return ResultState.Error("Thiếu thông tin phiên chat")
        if (content.isBlank()) return ResultState.Error("Nội dung tin nhắn không hợp lệ")

        return try {
            val now = System.currentTimeMillis()
            val messageRef = chatMessagesCollection.document()
            val message = ChatMessage(
                id = messageRef.id,
                sessionId = sessionId,
                sender = sender,
                content = content.trim(),
                createdAt = now
            )

            val sessionDocSnapshot = chatSessionsCollection.document(sessionId).get().await()
            val userId = sessionDocSnapshot.getString("userId") ?: ""

            firestore.runBatch { batch ->
                batch.set(messageRef, message.toMap())
                batch.set(
                    chatSessionsCollection.document(sessionId),
                    mapOf("lastMessageAt" to now),
                    SetOptions.merge()
                )
            }.await()

            if (userId.isNotBlank()) {
                invalidateUserSessionsCache(userId)
            }

            ResultState.Success(message)
        } catch (e: Exception) {
            ResultState.Error(e.message ?: "Gửi tin nhắn thất bại")
        }
    }

    suspend fun sendUserMessageAndApiReply(
        sessionId: String,
        userContent: String
    ): ResultState<List<ChatMessage>> {
        val userMessageResult = sendMessage(
            sessionId = sessionId,
            sender = ChatSender.USER,
            content = userContent
        )

        val userMessage = when (userMessageResult) {
            is ResultState.Success -> userMessageResult.data
            is ResultState.Error -> return ResultState.Error(userMessageResult.message)
            else -> return ResultState.Error("Gửi tin nhắn thất bại")
        }

        val historyMessages = when (val historyResult = getSessionMessages(sessionId)) {
            is ResultState.Success -> historyResult.data
            else -> emptyList()
        }

        val reply = runCatching { requestAssistantReply(historyMessages, userContent.trim()) }
            .getOrElse { throwable ->
                return ResultState.Error(throwable.message ?: "Gọi trợ lý học tập thất bại")
            }

        val botMessageResult = sendMessage(
            sessionId = sessionId,
            sender = ChatSender.BOT,
            content = reply
        )

        return when (botMessageResult) {
            is ResultState.Success -> ResultState.Success(listOf(userMessage, botMessageResult.data))
            is ResultState.Error -> ResultState.Error(botMessageResult.message)
            else -> ResultState.Error("Gửi phản hồi chatbot thất bại")
        }
    }

    private fun requestAssistantReply(historyMessages: List<ChatMessage>, latestUserContent: String): String {
        val apiKey = BuildConfig.CHATBOT_API_KEY
        if (apiKey.isBlank()) {
            throw IllegalStateException("Chưa cấu hình CHATBOT_API_KEY")
        }

        val endpoint = BuildConfig.CHATBOT_API_URL.ifBlank { "https://api.openai.com/v1/chat/completions" }
        val model = BuildConfig.CHATBOT_MODEL.ifBlank { "gpt-4o-mini" }

        val messagesJson = JSONArray().apply {
            put(
                JSONObject()
                    .put("role", "system")
                    .put("content", "Bạn là trợ lý học tập tiếng Việt, trả lời rõ ràng, ngắn gọn và có ví dụ khi phù hợp.")
            )

            historyMessages
                .takeLast(8)
                .forEach { message ->
                    val role = when (message.sender) {
                        ChatSender.USER -> "user"
                        ChatSender.BOT -> "assistant"
                        ChatSender.SYSTEM -> "system"
                    }
                    put(
                        JSONObject()
                            .put("role", role)
                            .put("content", message.content)
                    )
                }

            if (historyMessages.none { it.sender == ChatSender.USER && it.content == latestUserContent }) {
                put(
                    JSONObject()
                        .put("role", "user")
                        .put("content", latestUserContent)
                )
            }
        }

        val payload = JSONObject()
            .put("model", model)
            .put("messages", messagesJson)
            .put("temperature", 0.4)
            .toString()

        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Authorization", "Bearer $apiKey")
            setRequestProperty("Content-Type", "application/json")
            connectTimeout = 15000
            readTimeout = 30000
            doOutput = true
        }

        connection.outputStream.use { output ->
            output.write(payload.toByteArray(Charsets.UTF_8))
        }

        val responseCode = connection.responseCode
        val responseText = try {
            val stream = if (responseCode in 200..299) connection.inputStream else connection.errorStream
            BufferedReader(InputStreamReader(stream)).use { reader ->
                buildString {
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        append(line)
                    }
                }
            }
        } finally {
            connection.disconnect()
        }

        if (responseCode !in 200..299) {
            throw IllegalStateException("Chat API lỗi ($responseCode)")
        }

        val json = JSONObject(responseText)
        val content = json.optJSONArray("choices")
            ?.optJSONObject(0)
            ?.optJSONObject("message")
            ?.optString("content")
            ?.trim()
            .orEmpty()

        if (content.isBlank()) {
            throw IllegalStateException("Phản hồi từ Chat API không hợp lệ")
        }

        return content
    }

    private fun toChatSession(data: Map<String, Any>, id: String): ChatSession {
        val statusText = data["status"] as? String
        val status = ChatSessionStatus.entries.firstOrNull { it.name == statusText } ?: ChatSessionStatus.ACTIVE

        return ChatSession(
            id = id,
            userId = data["userId"] as? String ?: "",
            title = data["title"] as? String ?: "",
            status = status,
            lastMessageAt = (data["lastMessageAt"] as? Number)?.toLong() ?: 0L,
            createdAt = (data["createdAt"] as? Number)?.toLong() ?: 0L
        )
    }

    private fun toChatMessage(data: Map<String, Any>, id: String): ChatMessage {
        val senderText = data["sender"] as? String
        val sender = ChatSender.entries.firstOrNull { it.name == senderText } ?: ChatSender.USER

        return ChatMessage(
            id = id,
            sessionId = data["sessionId"] as? String ?: "",
            sender = sender,
            content = data["content"] as? String ?: "",
            createdAt = (data["createdAt"] as? Number)?.toLong() ?: 0L
        )
    }

    private fun ChatSession.toMap(): Map<String, Any> {
        return mapOf(
            "id" to id,
            "userId" to userId,
            "title" to title,
            "status" to status.name,
            "lastMessageAt" to lastMessageAt,
            "createdAt" to createdAt
        )
    }

    private fun ChatMessage.toMap(): Map<String, Any> {
        return mapOf(
            "id" to id,
            "sessionId" to sessionId,
            "sender" to sender.name,
            "content" to content,
            "createdAt" to createdAt
        )
    }
}

