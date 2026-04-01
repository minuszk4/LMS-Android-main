package com.example.lms2.data.repository

import com.example.lms2.BuildConfig
import com.example.lms2.data.model.ChatMessage
import com.example.lms2.data.model.ChatMessageType
import com.example.lms2.data.model.ChatSender
import com.example.lms2.data.model.ChatSession
import com.example.lms2.data.model.ChatSessionStatus
import com.example.lms2.data.model.Course
import com.example.lms2.util.ResultState
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.squareup.moshi.Json
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.tasks.await
import okhttp3.OkHttpClient
import org.json.JSONArray
import org.json.JSONObject
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import java.util.concurrent.TimeUnit

data class OpenRouterToolFunction(
    @Json(name = "name")
    val name: String,
    @Json(name = "description")
    val description: String,
    @Json(name = "parameters")
    val parameters: Map<String, Any>
)

data class OpenRouterTool(
    @Json(name = "type")
    val type: String = "function",
    @Json(name = "function")
    val function: OpenRouterToolFunction
)

data class OpenRouterToolCallFunction(
    @Json(name = "name")
    val name: String,
    @Json(name = "arguments")
    val arguments: String? = null
)

data class OpenRouterToolCall(
    @Json(name = "id")
    val id: String,
    @Json(name = "type")
    val type: String,
    @Json(name = "function")
    val function: OpenRouterToolCallFunction
)

data class OpenRouterMessage(
    @Json(name = "role")
    val role: String,
    @Json(name = "content")
    val content: String? = null,
    @Json(name = "tool_calls")
    val toolCalls: List<OpenRouterToolCall>? = null,
    @Json(name = "tool_call_id")
    val toolCallId: String? = null,
    @Json(name = "name")
    val name: String? = null
)

data class OpenRouterRequest(
    @Json(name = "model")
    val model: String,
    @Json(name = "messages")
    val messages: List<OpenRouterMessage>,
    @Json(name = "tools")
    val tools: List<OpenRouterTool>? = null,
    @Json(name = "tool_choice")
    val toolChoice: String? = null,
    @Json(name = "temperature")
    val temperature: Float = 0.6f,
    @Json(name = "top_p")
    val topP: Float = 0.9f,
    @Json(name = "max_tokens")
    val maxTokens: Int = 1024
)

data class OpenRouterChoice(
    @Json(name = "message")
    val message: OpenRouterMessage
)

data class OpenRouterResponse(
    @Json(name = "choices")
    val choices: List<OpenRouterChoice> = emptyList()
)

interface OpenRouterService {
    @POST("v1/chat/completions")
    suspend fun chat(
        @Header("Authorization") authorization: String,
        @Header("HTTP-Referer") referer: String = "https://lms-android.local",
        @Header("X-Title") title: String = "LMS Android",
        @Body request: OpenRouterRequest
    ): OpenRouterResponse
}

data class PendingCourseSelection(
    val action: String,
    val candidates: List<Map<String, String>>
)

class OpenRouterChatbotRepository {

    private val firestore = FirebaseFirestore.getInstance()
    private val chatSessionsCollection = firestore.collection("chatSessions")
    private val chatMessagesCollection = firestore.collection("chatMessages")

    private val courseRepository = CourseRepository()
    private val recommendationRepository = RecommendationRepository()
    private val enrollmentRepository = EnrollmentRepository()
    private val cartRepository = CartRepository()
    private val progressRepository = ProgressRepository()
    private val pendingCourseSelections = mutableMapOf<String, PendingCourseSelection>()
    private val fallbackModels = listOf(
        "qwen/qwen3.6-plus-preview:free",
        "google/gemma-2-9b-it:free"
    )

    private val tools = listOf(
        OpenRouterTool(
            function = OpenRouterToolFunction(
                name = "search_courses",
                description = "Tìm kiếm khóa học theo từ khóa.",
                parameters = mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "query" to mapOf(
                            "type" to "string",
                            "description" to "Từ khóa tìm kiếm"
                        )
                    ),
                    "required" to listOf("query")
                )
            )
        ),
        OpenRouterTool(
            function = OpenRouterToolFunction(
                name = "get_my_learning_summary",
                description = "Lấy tiến độ học tập của người dùng hiện tại.",
                parameters = mapOf(
                    "type" to "object",
                    "properties" to emptyMap<String, Any>()
                )
            )
        ),
        OpenRouterTool(
            function = OpenRouterToolFunction(
                name = "recommend_new_courses",
                description = "Gợi ý khóa học mới cho người dùng.",
                parameters = mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "limit" to mapOf(
                            "type" to "integer",
                            "description" to "Số lượng khóa học gợi ý"
                        )
                    )
                )
            )
        ),
        OpenRouterTool(
            function = OpenRouterToolFunction(
                name = "get_course_details",
                description = "Lấy thông tin chi tiết của một khóa học.",
                parameters = mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "courseId" to mapOf(
                            "type" to "string",
                            "description" to "ID khóa học"
                        ),
                        "courseName" to mapOf(
                            "type" to "string",
                            "description" to "Tên khóa học (dùng khi không có ID)"
                        )
                    )
                )
            )
        ),
        OpenRouterTool(
            function = OpenRouterToolFunction(
                name = "add_course_to_cart",
                description = "Thêm khóa học vào giỏ hàng.",
                parameters = mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "courseId" to mapOf(
                            "type" to "string",
                            "description" to "ID khóa học"
                        ),
                        "courseName" to mapOf(
                            "type" to "string",
                            "description" to "Tên khóa học (dùng khi không có ID)"
                        )
                    )
                )
            )
        ),
        OpenRouterTool(
            function = OpenRouterToolFunction(
                name = "remove_course_from_cart",
                description = "Xóa khóa học khỏi giỏ hàng.",
                parameters = mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "courseId" to mapOf(
                            "type" to "string",
                            "description" to "ID khóa học"
                        ),
                        "courseName" to mapOf(
                            "type" to "string",
                            "description" to "Tên khóa học (dùng khi không có ID)"
                        )
                    )
                )
            )
        ),
        OpenRouterTool(
            function = OpenRouterToolFunction(
                name = "get_my_cart_items",
                description = "Lấy danh sách khóa học trong giỏ hàng của tôi.",
                parameters = mapOf(
                    "type" to "object",
                    "properties" to emptyMap<String, Any>()
                )
            )
        ),
        OpenRouterTool(
            function = OpenRouterToolFunction(
                name = "check_course_enrollment",
                description = "Kiểm tra người dùng đã đăng ký một khóa học chưa.",
                parameters = mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "courseId" to mapOf(
                            "type" to "string",
                            "description" to "ID khóa học"
                        ),
                        "courseName" to mapOf(
                            "type" to "string",
                            "description" to "Tên khóa học (dùng khi không có ID)"
                        )
                    )
                )
            )
        ),
        OpenRouterTool(
            function = OpenRouterToolFunction(
                name = "check_course_in_cart",
                description = "Kiểm tra một khóa học có trong giỏ hàng không.",
                parameters = mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "courseId" to mapOf(
                            "type" to "string",
                            "description" to "ID khóa học"
                        ),
                        "courseName" to mapOf(
                            "type" to "string",
                            "description" to "Tên khóa học (dùng khi không có ID)"
                        )
                    )
                )
            )
        ),
        OpenRouterTool(
            function = OpenRouterToolFunction(
                name = "get_popular_courses",
                description = "Lấy danh sách khóa học phổ biến nhất.",
                parameters = mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "limit" to mapOf(
                            "type" to "integer",
                            "description" to "Số lượng khóa học"
                        )
                    )
                )
            )
        ),
        OpenRouterTool(
            function = OpenRouterToolFunction(
                name = "recommend_courses_by_category",
                description = "Gợi ý khóa học theo danh mục.",
                parameters = mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "categoryId" to mapOf(
                            "type" to "string",
                            "description" to "ID danh mục"
                        ),
                        "limit" to mapOf(
                            "type" to "integer",
                            "description" to "Số lượng khóa học"
                        )
                    ),
                    "required" to listOf("categoryId")
                )
            )
        ),
        OpenRouterTool(
            function = OpenRouterToolFunction(
                name = "get_enrolled_courses",
                description = "Lấy danh sách các khóa học mà người dùng đã ghi danh.",
                parameters = mapOf(
                    "type" to "object",
                    "properties" to emptyMap<String, Any>()
                )
            )
        ),
        OpenRouterTool(
            function = OpenRouterToolFunction(
                name = "get_course_reviews",
                description = "Lấy đánh giá của một khóa học.",
                parameters = mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "courseId" to mapOf(
                            "type" to "string",
                            "description" to "ID khóa học"
                        ),
                        "courseName" to mapOf(
                            "type" to "string",
                            "description" to "Tên khóa học (dùng khi không có ID)"
                        )
                    )
                )
            )
        ),
        OpenRouterTool(
            function = OpenRouterToolFunction(
                name = "get_user_notifications",
                description = "Lấy danh sách thông báo gần đây của người dùng.",
                parameters = mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "limit" to mapOf(
                            "type" to "integer",
                            "description" to "Số lượng thông báo"
                        )
                    )
                )
            )
        ),
        OpenRouterTool(
            function = OpenRouterToolFunction(
                name = "get_purchase_history",
                description = "Lấy lịch sử mua hàng của người dùng.",
                parameters = mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "limit" to mapOf(
                            "type" to "integer",
                            "description" to "Số lượng đơn hàng gần nhất"
                        )
                    )
                )
            )
        ),
        OpenRouterTool(
            function = OpenRouterToolFunction(
                name = "get_quiz_results",
                description = "Lấy kết quả quiz gần đây của người dùng.",
                parameters = mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "limit" to mapOf(
                            "type" to "integer",
                            "description" to "Số lượng kết quả quiz"
                        )
                    )
                )
            )
        )
    )

    private val openRouterService: OpenRouterService = createOpenRouterService()

    private fun createOpenRouterService(): OpenRouterService {
        val moshi = Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()

        val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()

        return Retrofit.Builder()
            .baseUrl("https://openrouter.ai/api/")
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(OpenRouterService::class.java)
    }

    suspend fun createSession(userId: String, title: String): ResultState<ChatSession> {
        if (userId.isBlank()) return ResultState.Error("Thiếu thông tin người dùng")

        return try {
            val sessionId = chatSessionsCollection.document().id
            val now = System.currentTimeMillis()
            val session = ChatSession(
                id = sessionId,
                userId = userId,
                title = title,
                status = ChatSessionStatus.ACTIVE,
                createdAt = now,
                lastMessageAt = now
            )
            chatSessionsCollection.document(sessionId).set(session).await()
            ResultState.Success(session)
        } catch (e: Exception) {
            ResultState.Error(e.message ?: "Tạo phiên chat thất bại")
        }
    }

    suspend fun getOrCreateActiveSession(
        userId: String,
        defaultTitle: String = "Trợ lý học tập AI"
    ): ResultState<ChatSession> {
        if (userId.isBlank()) return ResultState.Error("Thiếu thông tin người dùng")

        return try {
            val snapshot = chatSessionsCollection
                .whereEqualTo("userId", userId)
                .whereEqualTo("status", ChatSessionStatus.ACTIVE.name)
                .limit(1)
                .get()
                .await()

            val existing = snapshot.documents.firstOrNull()?.let { doc ->
                doc.toObject(ChatSession::class.java)?.copy(id = doc.id)
            }

            if (existing != null) ResultState.Success(existing) else createSession(userId, defaultTitle)
        } catch (e: Exception) {
            ResultState.Error(e.message ?: "Tải phiên chat thất bại")
        }
    }

    suspend fun getUserSessions(userId: String): ResultState<List<ChatSession>> {
        if (userId.isBlank()) return ResultState.Error("Thiếu thông tin người dùng")

        return try {
            val snapshot = chatSessionsCollection
                .whereEqualTo("userId", userId)
                .get()
                .await()

            val sessions = snapshot.documents.mapNotNull { doc ->
                doc.toObject(ChatSession::class.java)?.copy(id = doc.id)
            }.sortedByDescending { it.lastMessageAt }

            ResultState.Success(sessions)
        } catch (e: Exception) {
            ResultState.Error(e.message ?: "Lấy danh sách phiên chat thất bại")
        }
    }

    suspend fun getSessionMessages(sessionId: String): ResultState<List<ChatMessage>> {
        if (sessionId.isBlank()) return ResultState.Error("Thiếu ID phiên chat")

        return try {
            val snapshot = chatMessagesCollection
                .whereEqualTo("sessionId", sessionId)
                .get()
                .await()

            val messages = snapshot.documents.mapNotNull { doc ->
                doc.toObject(ChatMessage::class.java)?.copy(id = doc.id)
            }.sortedBy { it.createdAt }

            ResultState.Success(messages)
        } catch (e: Exception) {
            ResultState.Error(e.message ?: "Lấy tin nhắn thất bại")
        }
    }

    suspend fun deleteSession(sessionId: String): ResultState<Unit> {
        if (sessionId.isBlank()) return ResultState.Error("Thiếu ID phiên chat")

        return try {
            val messageSnapshot = chatMessagesCollection
                .whereEqualTo("sessionId", sessionId)
                .get()
                .await()

            val messageRefs = messageSnapshot.documents.map { it.reference }
            messageRefs.chunked(400).forEach { chunk ->
                val batch = firestore.batch()
                chunk.forEach { ref -> batch.delete(ref) }
                batch.commit().await()
            }

            chatSessionsCollection.document(sessionId).delete().await()
            ResultState.Success(Unit)
        } catch (e: Exception) {
            ResultState.Error(e.message ?: "Xóa phiên chat thất bại")
        }
    }

    suspend fun sendMessage(
        sessionId: String,
        sender: ChatSender,
        content: String,
        messageType: ChatMessageType = ChatMessageType.TEXT,
        metadata: Map<String, Any> = emptyMap()
    ): ResultState<ChatMessage> {
        if (sessionId.isBlank() || (content.isBlank() && messageType == ChatMessageType.TEXT)) {
            return ResultState.Error("Thiếu nội dung tin nhắn")
        }

        return try {
            val messageId = chatMessagesCollection.document().id
            val now = System.currentTimeMillis()
            val message = ChatMessage(
                id = messageId,
                sessionId = sessionId,
                sender = sender,
                content = content.trim(),
                messageType = messageType,
                metadata = metadata,
                createdAt = now
            )

            firestore.runBatch { batch ->
                batch.set(chatMessagesCollection.document(messageId), message)
                batch.set(
                    chatSessionsCollection.document(sessionId),
                    mapOf("lastMessageAt" to now),
                    SetOptions.merge()
                )
            }.await()

            ResultState.Success(message)
        } catch (e: Exception) {
            ResultState.Error(e.message ?: "Gửi tin nhắn thất bại")
        }
    }

    suspend fun sendUserMessageAndAIReply(
        sessionId: String,
        userId: String,
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

        if (isRecommendationIntent(userContent)) {
            return handleMlRecommendation(sessionId, userId, userContent, userMessage)
        }

        return try {
            val history = when (val historyResult = getSessionMessages(sessionId)) {
                is ResultState.Success -> historyResult.data.filter { it.id != userMessage.id }
                else -> emptyList()
            }

            handlePendingSelectionIntent(sessionId, userId, userMessage, userContent)?.let {
                return it
            }

            // Resolve follow-up intent like "thêm khóa học đó vào giỏ hàng" based on recent bot results.
            if (isImplicitAddToCartIntent(userContent)) {
                val referencedCourse = resolveReferencedCourseFromHistory(history, userContent)
                if (referencedCourse != null) {
                    return handleAddReferencedCourseToCart(
                        sessionId = sessionId,
                        userId = userId,
                        userMessage = userMessage,
                        course = referencedCourse
                    )
                }
            }

            handleCombinedSearchAndAddIntent(sessionId, userId, userMessage, userContent)?.let {
                return it
            }

            val apiKey = BuildConfig.OPENROUTER_API_KEY
            if (apiKey.isBlank()) {
                return handleLocalFallbackIntent(sessionId, userId, userMessage, userContent)
            }

            val conversation = mutableListOf<OpenRouterMessage>()
            conversation.add(
                OpenRouterMessage(
                    role = "system",
                    content = "Bạn là trợ lý học tập LMS. Trả lời rõ ràng, không markdown. Luôn dùng function tools khi cần dữ liệu thật thay vì đoán."
                )
            )
            conversation.addAll(buildOpenRouterChatHistory(history))
            conversation.add(OpenRouterMessage(role = "user", content = userContent))

            val functionTrace = mutableListOf<Pair<String, Map<String, Any>>>()
            var finalReply = ""

            var callCount = 0
            while (callCount < 5) {
                callCount++

                val response = callOpenRouterWithFallback(
                    apiKey = apiKey,
                    messages = conversation,
                    tools = tools,
                    toolChoice = "auto"
                )

                val assistantMessage = response.choices.firstOrNull()?.message ?: break

                if (!assistantMessage.content.isNullOrBlank()) {
                    finalReply = assistantMessage.content
                }

                val toolCalls = assistantMessage.toolCalls.orEmpty()
                if (toolCalls.isEmpty()) {
                    break
                }

                conversation.add(
                    OpenRouterMessage(
                        role = "assistant",
                        content = assistantMessage.content,
                        toolCalls = toolCalls
                    )
                )

                toolCalls.forEach { toolCall ->
                    val functionName = toolCall.function.name
                    val args = parseFunctionArgs(toolCall.function.arguments)
                    val result = executeFunctionCall(userId, functionName, args)

                    if ((result["needsSelection"] as? Boolean) == true) {
                        @Suppress("UNCHECKED_CAST")
                        val candidates = (result["candidates"] as? List<Map<String, String>>) ?: emptyList()
                        if (candidates.isNotEmpty()) {
                            pendingCourseSelections[sessionId] = PendingCourseSelection(
                                action = result["action"] as? String ?: functionName,
                                candidates = candidates
                            )

                            val selectionMetadata = mapOf(
                                "courses" to candidates.map {
                                    mapOf(
                                        "id" to (it["id"] ?: ""),
                                        "title" to (it["title"] ?: "Unknown"),
                                        "price" to "",
                                        "rating" to 0.0,
                                        "enrollmentCount" to 0
                                    )
                                },
                                "type" to "course_list"
                            )

                            val botMessageResult = sendMessage(
                                sessionId = sessionId,
                                sender = ChatSender.BOT,
                                content = "Mình thấy nhiều khóa học trùng tên. Bạn chọn khóa thứ 1, 2, hoặc 3 nhé.",
                                messageType = ChatMessageType.COURSE_LIST,
                                metadata = selectionMetadata
                            )

                            return when (botMessageResult) {
                                is ResultState.Success -> ResultState.Success(listOf(userMessage, botMessageResult.data))
                                is ResultState.Error -> ResultState.Error(botMessageResult.message)
                                else -> ResultState.Error("Lưu phản hồi chatbot thất bại")
                            }
                        }
                    }

                    functionTrace.add(functionName to result)

                    conversation.add(
                        OpenRouterMessage(
                            role = "tool",
                            toolCallId = toolCall.id,
                            name = functionName,
                            content = JSONObject(result).toString()
                        )
                    )
                }
            }

            val cleanedReply = normalizeAiText(
                if (finalReply.isBlank()) "Xin lỗi, tôi chưa thể trả lời lúc này." else finalReply
            )

            val (messageType, metadata) = analyzeResponseContent(functionTrace)

            val botMessageResult = sendMessage(
                sessionId = sessionId,
                sender = ChatSender.BOT,
                content = cleanedReply,
                messageType = messageType,
                metadata = metadata
            )

            when (botMessageResult) {
                is ResultState.Success -> ResultState.Success(listOf(userMessage, botMessageResult.data))
                is ResultState.Error -> ResultState.Error(botMessageResult.message)
                else -> ResultState.Error("Lưu phản hồi chatbot thất bại")
            }
        } catch (e: Exception) {
            handleLocalFallbackIntent(
                sessionId = sessionId,
                userId = userId,
                userMessage = userMessage,
                userContent = userContent,
                apiError = e.message
            )
        }
    }

    private suspend fun handleLocalFallbackIntent(
        sessionId: String,
        userId: String,
        userMessage: ChatMessage,
        userContent: String,
        apiError: String? = null
    ): ResultState<List<ChatMessage>> {
        val text = userContent.lowercase()

        if (listOf("hướng dẫn", "huong dan", "help", "trợ giúp", "tro giup").any { text.contains(it) }) {
            val helpText = buildString {
                appendLine("Bạn có thể yêu cầu mình theo các mẫu sau:")
                appendLine("- Tìm khóa học: Tìm khóa học Kotlin")
                appendLine("- Xem chi tiết: Xem chi tiết khóa học Android")
                appendLine("- Tiến độ học tập: Tôi đã học đến đâu rồi?")
                appendLine("- Thêm vào giỏ: Thêm khóa học React vào giỏ")
                append("- Gợi ý khóa học: Gợi ý cho tôi 5 khóa học phù hợp")
            }

            val bot = sendMessage(
                sessionId = sessionId,
                sender = ChatSender.BOT,
                content = helpText
            )
            return when (bot) {
                is ResultState.Success -> ResultState.Success(listOf(userMessage, bot.data))
                is ResultState.Error -> ResultState.Error(bot.message)
                else -> ResultState.Error("Lưu phản hồi chatbot thất bại")
            }
        }

        val functionName: String?
        val args: Map<String, Any?>
        val defaultText: String

        when {
            listOf("tiến độ", "học đến đâu", "learning summary", "tóm tắt học tập").any { text.contains(it) } -> {
                functionName = "get_my_learning_summary"
                args = emptyMap()
                defaultText = "Đây là tiến độ học tập hiện tại của bạn."
            }

            (text.contains("chi tiết") || text.contains("chi tiet") || text.contains("details")) -> {
                val query = extractSearchQuery(userContent)
                if (query.isBlank()) {
                    val bot = sendMessage(
                        sessionId = sessionId,
                        sender = ChatSender.BOT,
                        content = "Bạn muốn xem chi tiết khóa học nào? Hãy nhập tên khóa học nhé."
                    )
                    return when (bot) {
                        is ResultState.Success -> ResultState.Success(listOf(userMessage, bot.data))
                        is ResultState.Error -> ResultState.Error(bot.message)
                        else -> ResultState.Error("Lưu phản hồi chatbot thất bại")
                    }
                }
                functionName = "get_course_details"
                args = mapOf("courseName" to query)
                defaultText = "Đây là thông tin chi tiết khóa học bạn cần."
            }

            (text.contains("tìm") || text.contains("search")) -> {
                val query = extractSearchQuery(userContent).ifBlank { userContent.trim() }
                functionName = "search_courses"
                args = mapOf("query" to query)
                defaultText = "Đây là các khóa học phù hợp với từ khóa của bạn."
            }

            ((text.contains("thêm") || text.contains("add")) && (text.contains("giỏ") || text.contains("cart"))) -> {
                val query = extractSearchQuery(userContent)
                if (query.isBlank()) {
                    val bot = sendMessage(
                        sessionId = sessionId,
                        sender = ChatSender.BOT,
                        content = "Bạn muốn thêm khóa học nào vào giỏ? Hãy nhập tên khóa học nhé."
                    )
                    return when (bot) {
                        is ResultState.Success -> ResultState.Success(listOf(userMessage, bot.data))
                        is ResultState.Error -> ResultState.Error(bot.message)
                        else -> ResultState.Error("Lưu phản hồi chatbot thất bại")
                    }
                }
                functionName = "add_course_to_cart"
                args = mapOf("courseName" to query)
                defaultText = "Mình đã thử thêm khóa học vào giỏ cho bạn."
            }

            else -> {
                val fallbackText = if (apiError.isNullOrBlank()) {
                    "Hiện tại AI đang ở chế độ cục bộ. Bạn có thể yêu cầu: tìm khóa học, xem chi tiết, xem tiến độ, thêm vào giỏ."
                } else {
                    "AI tạm thời không khả dụng (${apiError.take(80)}). Mình đã chuyển sang chế độ cục bộ: tìm khóa học, xem chi tiết, xem tiến độ, thêm vào giỏ."
                }
                val bot = sendMessage(
                    sessionId = sessionId,
                    sender = ChatSender.BOT,
                    content = fallbackText
                )
                return when (bot) {
                    is ResultState.Success -> ResultState.Success(listOf(userMessage, bot.data))
                    is ResultState.Error -> ResultState.Error(bot.message)
                    else -> ResultState.Error("Lưu phản hồi chatbot thất bại")
                }
            }
        }

        val result = executeFunctionCall(userId, functionName, args)

        if ((result["needsSelection"] as? Boolean) == true) {
            @Suppress("UNCHECKED_CAST")
            val candidates = (result["candidates"] as? List<Map<String, String>>) ?: emptyList()
            if (candidates.isNotEmpty()) {
                pendingCourseSelections[sessionId] = PendingCourseSelection(
                    action = result["action"] as? String ?: functionName,
                    candidates = candidates
                )

                val selectionMetadata = mapOf(
                    "courses" to candidates.map {
                        mapOf(
                            "id" to (it["id"] ?: ""),
                            "title" to (it["title"] ?: "Unknown"),
                            "price" to "",
                            "rating" to 0.0,
                            "enrollmentCount" to 0
                        )
                    },
                    "type" to "course_list"
                )

                val bot = sendMessage(
                    sessionId = sessionId,
                    sender = ChatSender.BOT,
                    content = "Mình thấy nhiều kết quả trùng tên. Bạn chọn khóa thứ 1, 2, hoặc 3 nhé.",
                    messageType = ChatMessageType.COURSE_LIST,
                    metadata = selectionMetadata
                )
                return when (bot) {
                    is ResultState.Success -> ResultState.Success(listOf(userMessage, bot.data))
                    is ResultState.Error -> ResultState.Error(bot.message)
                    else -> ResultState.Error("Lưu phản hồi chatbot thất bại")
                }
            }
        }

        val success = result["success"] as? Boolean ?: false
        val (messageType, metadata) = analyzeResponseContent(listOf(functionName to result))
        val responseText = if (success) {
            normalizeAiText(defaultText)
        } else {
            normalizeAiText(result["error"] as? String ?: "Mình chưa xử lý được yêu cầu này ở chế độ cục bộ.")
        }

        val bot = sendMessage(
            sessionId = sessionId,
            sender = ChatSender.BOT,
            content = responseText,
            messageType = if (success) messageType else ChatMessageType.TEXT,
            metadata = if (success) metadata else emptyMap()
        )

        return when (bot) {
            is ResultState.Success -> ResultState.Success(listOf(userMessage, bot.data))
            is ResultState.Error -> ResultState.Error(bot.message)
            else -> ResultState.Error("Lưu phản hồi chatbot thất bại")
        }
    }

    private suspend fun handleMlRecommendation(
        sessionId: String,
        userId: String,
        userContent: String,
        userMessage: ChatMessage
    ): ResultState<List<ChatMessage>> {
        val limit = extractRecommendationLimit(userContent)
        return when (val result = recommendationRepository.getRecommendedCourses(userId, limit)) {
            is ResultState.Success<*> -> {
                val recommendations = result.data as List<Course>
                if (recommendations.isEmpty()) {
                    val botMessageResult = sendMessage(
                        sessionId = sessionId,
                        sender = ChatSender.BOT,
                        content = "Hiện chưa có khóa học phù hợp để gợi ý."
                    )
                    return when (botMessageResult) {
                        is ResultState.Success<*> -> ResultState.Success(listOf(userMessage, (botMessageResult as ResultState.Success<ChatMessage>).data))
                        is ResultState.Error -> ResultState.Error(botMessageResult.message)
                        else -> ResultState.Error("Lưu phản hồi chatbot thất bại")
                    }
                } else {
                    val metadata = mapOf("courses" to recommendations.map { courseToCourseListItem(it) }, "type" to "course_list")
                    val botMessageResult = sendMessage(
                        sessionId = sessionId,
                        sender = ChatSender.BOT,
                        content = "Đây là các khóa học phù hợp với bạn:",
                        messageType = ChatMessageType.COURSE_LIST,
                        metadata = metadata
                    )
                    return when (botMessageResult) {
                        is ResultState.Success<*> -> ResultState.Success(listOf(userMessage, (botMessageResult as ResultState.Success<ChatMessage>).data))
                        is ResultState.Error -> ResultState.Error(botMessageResult.message)
                        else -> ResultState.Error("Lưu phản hồi chatbot thất bại")
                    }
                }
            }
            is ResultState.Error -> ResultState.Error(result.message)
            else -> ResultState.Error("Lấy gợi ý khóa học thất bại")
        }
    }

    private fun buildOpenRouterChatHistory(messages: List<ChatMessage>): List<OpenRouterMessage> {
        return messages.mapNotNull { message ->
            val role = when (message.sender) {
                ChatSender.USER -> "user"
                ChatSender.BOT -> "assistant"
                else -> null
            }
            role?.let { OpenRouterMessage(role = it, content = message.content) }
        }
    }

    private fun parseFunctionArgs(argumentsJson: String?): Map<String, Any?> {
        if (argumentsJson.isNullOrBlank()) return emptyMap()
        return try {
            jsonObjectToMap(JSONObject(argumentsJson))
        } catch (_: Exception) {
            emptyMap()
        }
    }

    private fun jsonObjectToMap(json: JSONObject): Map<String, Any?> {
        val map = mutableMapOf<String, Any?>()
        val keys = json.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val value = json.get(key)
            map[key] = when (value) {
                is JSONObject -> jsonObjectToMap(value)
                is JSONArray -> jsonArrayToList(value)
                JSONObject.NULL -> null
                else -> value
            }
        }
        return map
    }

    private fun jsonArrayToList(array: JSONArray): List<Any?> {
        val list = mutableListOf<Any?>()
        for (i in 0 until array.length()) {
            val value = array.get(i)
            list.add(
                when (value) {
                    is JSONObject -> jsonObjectToMap(value)
                    is JSONArray -> jsonArrayToList(value)
                    JSONObject.NULL -> null
                    else -> value
                }
            )
        }
        return list
    }

    private suspend fun executeFunctionCall(
        userId: String,
        functionName: String,
        args: Map<String, Any?>
    ): Map<String, Any> {
        return try {
            when (functionName) {
                "search_courses" -> {
                    val query = args["query"] as? String ?: ""
                    when (val result = courseRepository.searchCourses(query)) {
                        is ResultState.Success -> mapOf(
                            "success" to true,
                            "courses" to result.data.take(5).map { courseToMap(it) }
                        )
                        is ResultState.Error -> mapOf("success" to false, "error" to result.message)
                        else -> mapOf("success" to false, "error" to "Unknown error")
                    }
                }

                "get_my_learning_summary" -> {
                    when (val enrollmentResult = enrollmentRepository.getUserEnrollments(userId)) {
                        is ResultState.Success -> {
                            val summary = mutableListOf<Map<String, Any>>()
                            enrollmentResult.data.forEach { enrollment ->
                                when (val courseResult = courseRepository.getCourseById(enrollment.courseId)) {
                                    is ResultState.Success -> {
                                        val course = courseResult.data
                                        val progressResult = progressRepository.getProgress(userId, course.id)
                                        val progress = if (progressResult is ResultState.Success) progressResult.data else null
                                        val progressPercent = if (progress != null && course.lessonCount > 0) {
                                            (progress.completedLessons * 100 / course.lessonCount)
                                        } else 0

                                        summary.add(
                                            mapOf(
                                                "courseId" to course.id,
                                                "title" to course.title,
                                                "progress" to progressPercent,
                                                "completedItems" to (progress?.completedLessons ?: 0),
                                                "totalItems" to course.lessonCount
                                            )
                                        )
                                    }
                                    else -> Unit
                                }
                            }
                            mapOf("success" to true, "enrollments" to summary)
                        }
                        is ResultState.Error -> mapOf("success" to false, "error" to enrollmentResult.message)
                        else -> mapOf("success" to false, "error" to "Unknown error")
                    }
                }

                "recommend_new_courses" -> {
                    val limit = (args["limit"] as? Number)?.toInt() ?: 5
                    when (val result = recommendationRepository.getRecommendedCourses(userId, limit)) {
                        is ResultState.Success<*> -> mapOf(
                            "success" to true,
                            "recommendations" to (result.data as List<Course>).map { courseToMap(it) }
                        )
                        is ResultState.Error -> mapOf("success" to false, "error" to result.message)
                        else -> mapOf("success" to false, "error" to "Unknown error")
                    }
                }

                "get_course_details" -> {
                    val candidates = resolveCourseCandidatesByArgs(args)
                    val resolvedCourse = candidates.firstOrNull()
                    if (candidates.size > 1) {
                        mapOf(
                            "success" to false,
                            "needsSelection" to true,
                            "action" to "get_course_details",
                            "candidates" to candidates.take(5).map { mapOf("id" to it.id, "title" to it.title) }
                        )
                    } else if (resolvedCourse == null) {
                        mapOf("success" to false, "error" to "Không tìm thấy khóa học theo tên hoặc ID đã cung cấp")
                    } else when (val result = courseRepository.getCourseById(resolvedCourse.id)) {
                        is ResultState.Success -> mapOf("success" to true, "course" to courseToMap(result.data))
                        is ResultState.Error -> mapOf("success" to false, "error" to result.message)
                        else -> mapOf("success" to false, "error" to "Unknown error")
                    }
                }

                "add_course_to_cart" -> {
                    val candidates = resolveCourseCandidatesByArgs(args)
                    val resolvedCourse = candidates.firstOrNull()
                    if (candidates.size > 1) {
                        mapOf(
                            "success" to false,
                            "needsSelection" to true,
                            "action" to "add_course_to_cart",
                            "candidates" to candidates.take(5).map { mapOf("id" to it.id, "title" to it.title) }
                        )
                    } else if (resolvedCourse == null) {
                        mapOf("success" to false, "error" to "Không tìm thấy khóa học theo tên hoặc ID đã cung cấp")
                    } else when (val result = cartRepository.addCourseToCart(userId, resolvedCourse.id)) {
                        is ResultState.Success -> mapOf("success" to true, "message" to "Đã thêm khóa học vào giỏ hàng")
                        is ResultState.Error -> mapOf("success" to false, "error" to result.message)
                        else -> mapOf("success" to false, "error" to "Unknown error")
                    }
                }

                "remove_course_from_cart" -> {
                    val candidates = resolveCourseCandidatesByArgs(args)
                    val resolvedCourse = candidates.firstOrNull()
                    if (candidates.size > 1) {
                        mapOf(
                            "success" to false,
                            "needsSelection" to true,
                            "action" to "remove_course_from_cart",
                            "candidates" to candidates.take(5).map { mapOf("id" to it.id, "title" to it.title) }
                        )
                    } else if (resolvedCourse == null) {
                        mapOf("success" to false, "error" to "Không tìm thấy khóa học theo tên hoặc ID đã cung cấp")
                    } else when (val result = cartRepository.removeCourseFromCart(userId, resolvedCourse.id)) {
                        is ResultState.Success -> mapOf("success" to true, "message" to "Đã xóa khóa học khỏi giỏ hàng")
                        is ResultState.Error -> mapOf("success" to false, "error" to result.message)
                        else -> mapOf("success" to false, "error" to "Unknown error")
                    }
                }

                "get_my_cart_items" -> {
                    when (val result = cartRepository.getCartItems(userId)) {
                        is ResultState.Success -> mapOf(
                            "success" to true,
                            "cartItems" to result.data.map {
                                mapOf(
                                    "id" to it.courseId,
                                    "title" to it.courseTitle,
                                    "price" to it.coursePrice,
                                    "rating" to 0.0,
                                    "enrollmentCount" to 0
                                )
                            }
                        )
                        is ResultState.Error -> mapOf("success" to false, "error" to result.message)
                        else -> mapOf("success" to false, "error" to "Unknown error")
                    }
                }

                "check_course_enrollment" -> {
                    val candidates = resolveCourseCandidatesByArgs(args)
                    val resolvedCourse = candidates.firstOrNull()
                    if (candidates.size > 1) {
                        mapOf(
                            "success" to false,
                            "needsSelection" to true,
                            "action" to "check_course_enrollment",
                            "candidates" to candidates.take(5).map { mapOf("id" to it.id, "title" to it.title) }
                        )
                    } else if (resolvedCourse == null) {
                        mapOf("success" to false, "error" to "Không tìm thấy khóa học theo tên hoặc ID đã cung cấp")
                    } else when (val result = cartRepository.isCourseEnrolled(userId, resolvedCourse.id)) {
                        is ResultState.Success -> mapOf(
                            "success" to true,
                            "isEnrolled" to result.data,
                            "courseId" to resolvedCourse.id,
                            "courseName" to resolvedCourse.title
                        )
                        is ResultState.Error -> mapOf("success" to false, "error" to result.message)
                        else -> mapOf("success" to false, "error" to "Unknown error")
                    }
                }

                "check_course_in_cart" -> {
                    val candidates = resolveCourseCandidatesByArgs(args)
                    val resolvedCourse = candidates.firstOrNull()
                    if (candidates.size > 1) {
                        mapOf(
                            "success" to false,
                            "needsSelection" to true,
                            "action" to "check_course_in_cart",
                            "candidates" to candidates.take(5).map { mapOf("id" to it.id, "title" to it.title) }
                        )
                    } else if (resolvedCourse == null) {
                        mapOf("success" to false, "error" to "Không tìm thấy khóa học theo tên hoặc ID đã cung cấp")
                    } else when (val result = cartRepository.isCourseInCart(userId, resolvedCourse.id)) {
                        is ResultState.Success -> mapOf(
                            "success" to true,
                            "isInCart" to result.data,
                            "courseId" to resolvedCourse.id,
                            "courseName" to resolvedCourse.title
                        )
                        is ResultState.Error -> mapOf("success" to false, "error" to result.message)
                        else -> mapOf("success" to false, "error" to "Unknown error")
                    }
                }

                "get_popular_courses" -> {
                    val limit = (args["limit"] as? Number)?.toInt() ?: 5
                    when (val result = courseRepository.getAllPublishedCourses()) {
                        is ResultState.Success -> {
                            val popular = result.data
                                .sortedWith(compareByDescending<Course> { it.enrollmentCount }.thenByDescending { it.rating })
                                .take(limit)
                            mapOf(
                                "success" to true,
                                "courses" to popular.map { courseToMap(it) }
                            )
                        }
                        is ResultState.Error -> mapOf("success" to false, "error" to result.message)
                        else -> mapOf("success" to false, "error" to "Unknown error")
                    }
                }

                "recommend_courses_by_category" -> {
                    val categoryId = args["categoryId"] as? String ?: ""
                    val limit = (args["limit"] as? Number)?.toInt() ?: 5
                    when (val result = recommendationRepository.getRecommendedCoursesByCategory(userId, categoryId, limit)) {
                        is ResultState.Success<*> -> mapOf(
                            "success" to true,
                            "recommendations" to (result.data as List<Course>).map { courseToMap(it) }
                        )
                        is ResultState.Error -> mapOf("success" to false, "error" to result.message)
                        else -> mapOf("success" to false, "error" to "Unknown error")
                    }
                }

                "get_enrolled_courses" -> {
                    when (val enrollResult = enrollmentRepository.getUserEnrollments(userId)) {
                        is ResultState.Success -> {
                            val courses = mutableListOf<Map<String, Any>>()
                            enrollResult.data.forEach { enrollment ->
                                when (val courseResult = courseRepository.getCourseById(enrollment.courseId)) {
                                    is ResultState.Success -> courses.add(courseToMap(courseResult.data))
                                    else -> Unit
                                }
                            }
                            mapOf("success" to true, "courses" to courses)
                        }
                        is ResultState.Error -> mapOf("success" to false, "error" to enrollResult.message)
                        else -> mapOf("success" to false, "error" to "Unknown error")
                    }
                }

                "get_course_reviews" -> {
                    val candidates = resolveCourseCandidatesByArgs(args)
                    val resolvedCourse = candidates.firstOrNull()
                    if (candidates.size > 1) {
                        mapOf(
                            "success" to false,
                            "needsSelection" to true,
                            "action" to "get_course_reviews",
                            "candidates" to candidates.take(5).map { mapOf("id" to it.id, "title" to it.title) }
                        )
                    } else if (resolvedCourse == null) {
                        mapOf("success" to false, "error" to "Không tìm thấy khóa học theo tên hoặc ID đã cung cấp")
                    } else {
                        val snapshot = firestore.collection("reviews")
                            .whereEqualTo("courseId", resolvedCourse.id)
                            .limit(20)
                            .get()
                            .await()

                        val reviews = snapshot.documents.map { doc ->
                            val data = doc.data.orEmpty()
                            mapOf(
                                "id" to doc.id,
                                "rating" to ((data["rating"] as? Number)?.toDouble() ?: 0.0),
                                "comment" to (data["comment"] as? String ?: ""),
                                "userId" to (data["userId"] as? String ?: ""),
                                "createdAt" to ((data["createdAt"] as? Number)?.toLong() ?: 0L)
                            )
                        }

                        mapOf(
                            "success" to true,
                            "courseId" to resolvedCourse.id,
                            "courseName" to resolvedCourse.title,
                            "reviews" to reviews
                        )
                    }
                }

                "get_user_notifications" -> {
                    val limit = (args["limit"] as? Number)?.toLong() ?: 20L
                    val snapshot = firestore.collection("notifications")
                        .whereEqualTo("userId", userId)
                        .limit(limit.coerceIn(1L, 50L))
                        .get()
                        .await()

                    val notifications = snapshot.documents
                        .map { doc ->
                            val data = doc.data.orEmpty()
                            mapOf(
                                "id" to doc.id,
                                "title" to (data["title"] as? String ?: ""),
                                "body" to (data["body"] as? String ?: ""),
                                "type" to (data["type"] as? String ?: ""),
                                "readAt" to ((data["readAt"] as? Number)?.toLong() ?: 0L),
                                "createdAt" to ((data["createdAt"] as? Number)?.toLong() ?: 0L)
                            )
                        }
                        .sortedByDescending { (it["createdAt"] as? Long) ?: 0L }

                    mapOf("success" to true, "notifications" to notifications)
                }

                "get_purchase_history" -> {
                    val limit = (args["limit"] as? Number)?.toLong() ?: 20L
                    val snapshot = firestore.collection("orders")
                        .whereEqualTo("userId", userId)
                        .limit(limit.coerceIn(1L, 50L))
                        .get()
                        .await()

                    val orders = snapshot.documents
                        .map { doc ->
                            val data = doc.data.orEmpty()
                            mapOf(
                                "id" to doc.id,
                                "amount" to ((data["amount"] as? Number)?.toLong() ?: 0L),
                                "status" to (data["status"] as? String ?: ""),
                                "paymentMethod" to (data["paymentMethod"] as? String ?: ""),
                                "createdAt" to ((data["createdAt"] as? Number)?.toLong() ?: 0L)
                            )
                        }
                        .sortedByDescending { (it["createdAt"] as? Long) ?: 0L }

                    mapOf("success" to true, "orders" to orders)
                }

                "get_quiz_results" -> {
                    val limit = (args["limit"] as? Number)?.toLong() ?: 20L
                    val snapshot = firestore.collection("quizProgress")
                        .whereEqualTo("userId", userId)
                        .limit(limit.coerceIn(1L, 50L))
                        .get()
                        .await()

                    val results = snapshot.documents
                        .map { doc ->
                            val data = doc.data.orEmpty()
                            mapOf(
                                "id" to doc.id,
                                "courseId" to (data["courseId"] as? String ?: ""),
                                "quizId" to (data["quizId"] as? String ?: ""),
                                "score" to ((data["score"] as? Number)?.toDouble() ?: 0.0),
                                "submittedAt" to ((data["submittedAt"] as? Number)?.toLong() ?: 0L)
                            )
                        }
                        .sortedByDescending { (it["submittedAt"] as? Long) ?: 0L }

                    mapOf("success" to true, "quizResults" to results)
                }

                else -> mapOf("success" to false, "error" to "Function not found: $functionName")
            }
        } catch (e: Exception) {
            mapOf("success" to false, "error" to (e.message ?: "Function execution failed"))
        }
    }

    private fun analyzeResponseContent(
        functionTrace: List<Pair<String, Map<String, Any>>>
    ): Pair<ChatMessageType, Map<String, Any>> {
        if (functionTrace.isEmpty()) return Pair(ChatMessageType.TEXT, emptyMap())

        for ((functionName, functionResult) in functionTrace.asReversed()) {
            val success = functionResult["success"] as? Boolean ?: false
            if (!success) continue

            when (functionName) {
                "get_my_learning_summary" -> {
                    val metadata = extractMetadataFromLearningResult(functionResult)
                    if (metadata.isNotEmpty()) return Pair(ChatMessageType.PROGRESS_CHART, metadata)
                }

                "recommend_new_courses", "search_courses", "get_enrolled_courses" -> {
                    val metadata = extractMetadataFromCourseList(functionResult)
                    if (metadata.isNotEmpty()) return Pair(ChatMessageType.COURSE_LIST, metadata)
                }

                "get_popular_courses", "recommend_courses_by_category", "get_my_cart_items" -> {
                    val metadata = extractMetadataFromCourseList(functionResult)
                    if (metadata.isNotEmpty()) return Pair(ChatMessageType.COURSE_LIST, metadata)
                }

                "get_course_details" -> {
                    val metadata = extractMetadataFromCourseCard(functionResult)
                    if (metadata.isNotEmpty()) return Pair(ChatMessageType.COURSE_CARD, metadata)
                }
            }
        }

        return Pair(ChatMessageType.TEXT, emptyMap())
    }

    private fun extractMetadataFromLearningResult(result: Map<String, Any>): Map<String, Any> {
        @Suppress("UNCHECKED_CAST")
        val enrollments = result["enrollments"] as? List<Map<String, Any>> ?: emptyList()

        val courses = enrollments.map {
            mapOf(
                "title" to (it["title"] as? String ?: "Unknown"),
                "progress" to ((it["progress"] as? Number)?.toInt() ?: 0)
            )
        }

        return if (courses.isNotEmpty()) mapOf("courses" to courses, "type" to "learning_summary") else emptyMap()
    }

    private fun extractMetadataFromCourseList(result: Map<String, Any>): Map<String, Any> {
        @Suppress("UNCHECKED_CAST")
        val courseList = (result["courses"] as? List<Map<String, Any>>)
            ?: (result["recommendations"] as? List<Map<String, Any>>)
            ?: (result["cartItems"] as? List<Map<String, Any>>)
            ?: emptyList()

        val courses = courseList.map {
            mapOf(
                "id" to (it["id"] as? String ?: ""),
                "title" to (it["title"] as? String ?: "Unknown"),
                "instructorId" to (it["instructorId"] as? String ?: ""),
                "instructorName" to (it["instructorName"] as? String ?: "Unknown"),
                "price" to toPriceText(it["price"]),
                "rating" to ((it["rating"] as? Number)?.toDouble() ?: 0.0),
                "enrollmentCount" to ((it["enrollmentCount"] as? Number)?.toInt() ?: 0)
            )
        }

        return if (courses.isNotEmpty()) mapOf("courses" to courses, "type" to "course_list") else emptyMap()
    }

    private fun extractMetadataFromCourseCard(result: Map<String, Any>): Map<String, Any> {
        @Suppress("UNCHECKED_CAST")
        val course = result["course"] as? Map<String, Any> ?: return emptyMap()

        return mapOf(
            "id" to (course["id"] as? String ?: ""),
            "instructorId" to (course["instructorId"] as? String ?: ""),
            "title" to (course["title"] as? String ?: ""),
            "instructor" to (course["instructorName"] as? String ?: "Unknown"),
            "description" to (course["description"] as? String ?: ""),
            "price" to toPriceText(course["price"]),
            "rating" to ((course["rating"] as? Number)?.toDouble() ?: 0.0),
            "enrollmentCount" to ((course["enrollmentCount"] as? Number)?.toInt() ?: 0)
        )
    }

    private fun toPriceText(raw: Any?): String {
        return when (raw) {
            is Number -> raw.toLong().toString()
            is String -> raw
            else -> ""
        }
    }

    private fun courseToMap(course: Course): Map<String, Any> {
        return mapOf(
            "id" to course.id,
            "title" to course.title,
            "instructorId" to course.instructorId,
            "instructorName" to course.instructorName,
            "description" to course.description,
            "level" to course.level.name,
            "price" to course.price,
            "rating" to course.rating,
            "enrollmentCount" to course.enrollmentCount,
            "thumbnailUrl" to course.thumbnailUrl
        )
    }

    private fun courseToCourseListItem(course: Course): Map<String, Any> {
        return mapOf(
            "id" to course.id,
            "title" to course.title,
            "instructorId" to course.instructorId,
            "instructorName" to course.instructorName,
            "price" to course.price.toLong().toString(),
            "rating" to course.rating,
            "enrollmentCount" to course.enrollmentCount
        )
    }

    private suspend fun resolveCourseByArgs(args: Map<String, Any?>): Course? {
        return resolveCourseCandidatesByArgs(args).firstOrNull()
    }

    private suspend fun resolveCourseCandidatesByArgs(args: Map<String, Any?>): List<Course> {
        val courseId = args["courseId"] as? String
        val courseName = args["courseName"] as? String

        if (!courseId.isNullOrBlank()) {
            val byId = courseRepository.getCourseById(courseId)
            if (byId is ResultState.Success) return listOf(byId.data)
        }

        if (!courseName.isNullOrBlank()) {
            val search = courseRepository.searchCourses(courseName)
            if (search is ResultState.Success) {
                val normalized = courseName.trim().lowercase()
                val exact = search.data.firstOrNull { it.title.trim().lowercase() == normalized }
                if (exact != null) return listOf(exact)
                return search.data.take(5)
            }
        }

        return emptyList()
    }

    private suspend fun callOpenRouterWithFallback(
        apiKey: String,
        messages: List<OpenRouterMessage>,
        tools: List<OpenRouterTool>?,
        toolChoice: String?
    ): OpenRouterResponse {
        var lastError: Exception? = null
        for (model in fallbackModels) {
            try {
                return openRouterService.chat(
                    authorization = "Bearer $apiKey",
                    request = OpenRouterRequest(
                        model = model,
                        messages = messages,
                        tools = tools,
                        toolChoice = toolChoice
                    )
                )
            } catch (e: Exception) {
                lastError = e
            }
        }
        throw lastError ?: IllegalStateException("Không thể gọi model nào từ OpenRouter")
    }

    private suspend fun handlePendingSelectionIntent(
        sessionId: String,
        userId: String,
        userMessage: ChatMessage,
        userContent: String
    ): ResultState<List<ChatMessage>>? {
        val pending = pendingCourseSelections[sessionId] ?: return null
        val index = extractReferencedIndex(userContent)
        val picked = pending.candidates.getOrNull(index) ?: return null
        val courseId = picked["id"].orEmpty()
        val courseName = picked["title"].orEmpty()

        val args = mapOf("courseId" to courseId, "courseName" to courseName)
        val result = executeFunctionCall(userId, pending.action, args)
        pendingCourseSelections.remove(sessionId)

        val success = result["success"] as? Boolean ?: false
        val botText = if (success) {
            when (pending.action) {
                "add_course_to_cart" -> "Đã thêm \"$courseName\" vào giỏ hàng."
                "remove_course_from_cart" -> "Đã xóa \"$courseName\" khỏi giỏ hàng."
                "check_course_enrollment" -> if ((result["isEnrolled"] as? Boolean) == true) "Bạn đã đăng ký khóa \"$courseName\" rồi." else "Bạn chưa đăng ký khóa \"$courseName\"."
                "check_course_in_cart" -> if ((result["isInCart"] as? Boolean) == true) "Khóa \"$courseName\" đang có trong giỏ hàng." else "Khóa \"$courseName\" chưa có trong giỏ hàng."
                else -> "Đã xử lý khóa \"$courseName\"."
            }
        } else {
            result["error"] as? String ?: "Không thể xử lý lựa chọn của bạn."
        }

        val (messageType, metadata) = when (pending.action) {
            "get_course_details" -> Pair(ChatMessageType.COURSE_CARD, extractMetadataFromCourseCard(result))
            else -> Pair(ChatMessageType.TEXT, emptyMap())
        }

        val botMessageResult = sendMessage(
            sessionId = sessionId,
            sender = ChatSender.BOT,
            content = normalizeAiText(botText),
            messageType = messageType,
            metadata = metadata
        )

        return when (botMessageResult) {
            is ResultState.Success -> ResultState.Success(listOf(userMessage, botMessageResult.data))
            is ResultState.Error -> ResultState.Error(botMessageResult.message)
            else -> ResultState.Error("Lưu phản hồi chatbot thất bại")
        }
    }

    private suspend fun handleCombinedSearchAndAddIntent(
        sessionId: String,
        userId: String,
        userMessage: ChatMessage,
        userContent: String
    ): ResultState<List<ChatMessage>>? {
        val text = userContent.lowercase()
        val hasSearch = text.contains("tìm") || text.contains("search")
        val hasAddCart = (text.contains("thêm") || text.contains("add")) && (text.contains("giỏ") || text.contains("cart"))
        if (!hasSearch || !hasAddCart) return null

        val query = extractQueryForCombinedIntent(userContent)
        if (query.isBlank()) return null

        val searchResult = courseRepository.searchCourses(query)
        if (searchResult !is ResultState.Success || searchResult.data.isEmpty()) return null

        val index = extractReferencedIndex(userContent)
        val picked = searchResult.data.getOrNull(index) ?: searchResult.data.first()

        return handleAddReferencedCourseToCart(sessionId, userId, userMessage, picked)
    }

    private fun extractQueryForCombinedIntent(userContent: String): String {
        val text = userContent.trim()
        val quoted = Regex("\"([^\"]+)\"").find(text)?.groupValues?.get(1)
        if (!quoted.isNullOrBlank()) return quoted

        val lower = text.lowercase()
        val start = lower.indexOf("tìm")
        if (start == -1) return ""
        val after = text.substring(start + 3).trim()
        val stopWords = listOf("thêm", "add", "vào giỏ", "vao gio", "cart")
        val cutAt = stopWords.map { after.lowercase().indexOf(it) }.filter { it >= 0 }.minOrNull() ?: -1
        return if (cutAt > 0) after.substring(0, cutAt).trim() else after
    }

    private fun extractSearchQuery(userContent: String): String {
        val text = userContent.trim()
        val quoted = Regex("\"([^\"]+)\"").find(text)?.groupValues?.get(1)
        if (!quoted.isNullOrBlank()) return quoted

        val lower = text.lowercase()
        val markers = listOf("tìm kiếm", "tim kiem", "tìm", "search", "chi tiết", "chi tiet", "details")
        val marker = markers.firstOrNull { lower.contains(it) } ?: return ""
        val start = lower.indexOf(marker)
        if (start < 0) return ""
        val after = text.substring(start + marker.length).trim(' ', ':', '-', '"', '\'')
        val cutWords = listOf("vào giỏ", "vao gio", "giỏ", "cart", "thêm", "add")
        val cutAt = cutWords.map { after.lowercase().indexOf(it) }.filter { it >= 0 }.minOrNull() ?: -1
        return (if (cutAt > 0) after.substring(0, cutAt) else after).trim()
    }

    private fun isRecommendationIntent(content: String): Boolean {
        val text = content.lowercase()
        return listOf("gợi ý", "goi y", "đề xuất", "de xuat", "recommend", "khóa học phù hợp")
            .any { text.contains(it) }
    }

    private fun extractRecommendationLimit(content: String): Int {
        val regex = Regex("""(\d+)\s*(?:khóa|course|cái)""")
        val match = regex.find(content.lowercase())
        return match?.groupValues?.get(1)?.toIntOrNull() ?: 5
    }

    private fun normalizeAiText(input: String): String {
        var text = input
        text = text.replace(Regex("```[\\s\\S]*?```"), "")
        text = text.replace("```", "")
        text = text.replace(Regex("^\\s{0,3}#{1,6}\\s+", RegexOption.MULTILINE), "")
        text = text.replace("**", "")
        text = text.replace("__", "")
        text = text.replace("`", "")
        text = text.replace(Regex("^\\s*[-*+]\\s+", RegexOption.MULTILINE), "")
        text = text.replace(Regex("\\n{3,}"), "\\n\\n")
        return text.trim()
    }

    private fun isImplicitAddToCartIntent(content: String): Boolean {
        val text = content.lowercase()
        val hasAddIntent = listOf("thêm", "add", "cho vào", "bỏ vào").any { text.contains(it) }
        val hasCartIntent = listOf("giỏ", "cart").any { text.contains(it) }
        val hasReference = listOf("đó", "nay", "này", "vừa", "thứ", "item").any { text.contains(it) }
        return hasAddIntent && hasCartIntent && hasReference
    }

    private suspend fun resolveReferencedCourseFromHistory(
        history: List<ChatMessage>,
        userContent: String
    ): Course? {
        val recent = history.asReversed().firstOrNull {
            it.sender == ChatSender.BOT &&
                (it.messageType == ChatMessageType.COURSE_LIST || it.messageType == ChatMessageType.COURSE_CARD)
        } ?: return null

        val index = extractReferencedIndex(userContent)

        if (recent.messageType == ChatMessageType.COURSE_CARD) {
            val title = recent.metadata["title"] as? String
            val id = recent.metadata["id"] as? String
            return resolveCourseByIdOrName(id, title)
        }

        @Suppress("UNCHECKED_CAST")
        val courses = recent.metadata["courses"] as? List<Map<String, Any>> ?: emptyList()
        if (courses.isEmpty()) return null

        val picked = courses.getOrNull(index) ?: courses.first()
        val title = picked["title"] as? String
        val id = picked["id"] as? String
        return resolveCourseByIdOrName(id, title)
    }

    private fun extractReferencedIndex(userContent: String): Int {
        val text = userContent.lowercase()
        val explicit = Regex("""thứ\s*(\d+)""").find(text)?.groupValues?.get(1)?.toIntOrNull()
        if (explicit != null && explicit > 0) return explicit - 1

        if (text.contains("đầu") || text.contains("first") || text.contains("1")) return 0
        if (text.contains("hai") || text.contains("second") || text.contains("2")) return 1
        if (text.contains("ba") || text.contains("third") || text.contains("3")) return 2
        return 0
    }

    private suspend fun resolveCourseByIdOrName(id: String?, title: String?): Course? {
        if (!id.isNullOrBlank()) {
            when (val byId = courseRepository.getCourseById(id)) {
                is ResultState.Success -> return byId.data
                else -> Unit
            }
        }
        if (!title.isNullOrBlank()) {
            val search = courseRepository.searchCourses(title)
            if (search is ResultState.Success) {
                val normalized = title.trim().lowercase()
                return search.data.firstOrNull { it.title.trim().lowercase() == normalized }
                    ?: search.data.firstOrNull()
            }
        }
        return null
    }

    private suspend fun handleAddReferencedCourseToCart(
        sessionId: String,
        userId: String,
        userMessage: ChatMessage,
        course: Course
    ): ResultState<List<ChatMessage>> {
        return when (val addResult = cartRepository.addCourseToCart(userId, course.id)) {
            is ResultState.Success -> {
                val botMessageResult = sendMessage(
                    sessionId = sessionId,
                    sender = ChatSender.BOT,
                    content = "Đã thêm khóa học \"${course.title}\" vào giỏ hàng.",
                    messageType = ChatMessageType.COURSE_CARD,
                    metadata = mapOf(
                        "id" to course.id,
                        "title" to course.title,
                        "instructor" to course.instructorName,
                        "description" to course.description,
                        "price" to course.price.toLong().toString(),
                        "rating" to course.rating,
                        "enrollmentCount" to course.enrollmentCount
                    )
                )

                when (botMessageResult) {
                    is ResultState.Success -> ResultState.Success(listOf(userMessage, botMessageResult.data))
                    is ResultState.Error -> ResultState.Error(botMessageResult.message)
                    else -> ResultState.Error("Lưu phản hồi chatbot thất bại")
                }
            }
            is ResultState.Error -> ResultState.Error(addResult.message)
            else -> ResultState.Error("Không thể thêm khóa học vào giỏ hàng")
        }
    }
}
