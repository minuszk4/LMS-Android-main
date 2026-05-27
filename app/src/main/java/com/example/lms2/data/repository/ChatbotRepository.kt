package com.example.lms2.data.repository

import com.example.lms2.BuildConfig
import com.example.lms2.data.cache.RepositoryCache
import com.example.lms2.data.cache.CacheTTL
import com.example.lms2.data.model.ChatMessage
import com.example.lms2.data.model.ChatSender
import com.example.lms2.data.model.ChatSession
import com.example.lms2.data.model.ChatSessionStatus
import com.example.lms2.data.model.Course
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

/**
 * Triển khai repository ChatbotRepository cho ứng dụng LMS Android.
 * File này chịu trách nhiệm làm việc với Firestore hoặc API ngoài, đồng thời chuyển đổi kết quả về dạng phù hợp cho ViewModel.
 * Repository là ranh giới chính giữa tầng giao diện và tầng dữ liệu nên được mô tả rõ để thuận tiện cho tài liệu kỹ thuật.
 */

class ChatbotRepository {
    // Đây là chatbot repository "thế hệ đầu":
    // - lưu session/message trên Firestore,
    // - gọi model bằng REST API thủ công,
    // - định nghĩa tool bằng JSON để model truy xuất dữ liệu LMS.
    // Nó đơn giản hơn bản OpenRouter mới, nhưng vẫn là một lớp quan trọng để hiểu
    // cách hệ thống xây dựng chatbot stateful và lưu lịch sử hội thoại.

    private val firestore = FirebaseFirestore.getInstance()
    private val chatSessionsCollection = firestore.collection("chatSessions")
    private val chatMessagesCollection = firestore.collection("chatMessages")
    
    private val courseRepository = CourseRepository()
    private val recommendationRepository = RecommendationRepository()
    private val enrollmentRepository = EnrollmentRepository()
    private val cartRepository = CartRepository()
    private val progressRepository = ProgressRepository()

    /**
     * Lấy dữ liệu hiện có hoặc tạo mới nếu tài nguyên chưa tồn tại.
     * Hàm này thường làm việc với Firestore hoặc API ngoài và trả kết quả về dạng `ResultState` cho tầng gọi phía trên.
     */

    // Ưu tiên tái sử dụng session ACTIVE gần nhất để người dùng tiếp tục đúng ngữ cảnh cũ.
    // Chỉ khi chưa có session phù hợp thì mới tạo mới, tránh làm phân mảnh lịch sử chat.
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

    /**
     * Tạo mới dữ liệu nghiệp vụ dựa trên đầu vào hiện tại.
     * Hàm này thường làm việc với Firestore hoặc API ngoài và trả kết quả về dạng `ResultState` cho tầng gọi phía trên.
     */

    // Một session mới được khởi tạo với trạng thái ACTIVE và timestamp hiện tại cho cả
    // `createdAt` lẫn `lastMessageAt`, để UI sắp xếp lịch sử đúng ngay từ đầu.
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

    /**
     * Lấy dữ liệu hoặc trạng thái cần thiết cho luồng hiện tại.
     * Hàm này thường làm việc với Firestore hoặc API ngoài và trả kết quả về dạng `ResultState` cho tầng gọi phía trên.
     */

    // Dùng cho các UI cần phân trang lịch sử chat; dữ liệu được cache theo user + page size.
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

    /**
     * Lấy dữ liệu hoặc trạng thái cần thiết cho luồng hiện tại.
     * Hàm này thường làm việc với Firestore hoặc API ngoài và trả kết quả về dạng `ResultState` cho tầng gọi phía trên.
     */

    // Bản "lấy tất cả" tiện cho những màn chỉ cần toàn bộ session đã có.
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

    private fun buildToolDefinitions(): JSONArray {
        return JSONArray().apply {
            // Tool 1: Search courses
            put(JSONObject()
                .put("type", "function")
                .put("function", JSONObject()
                    .put("name", "search_courses")
                    .put("description", "Tìm kiếm các khóa học theo từ khóa (tiêu đề, mô tả, giảng viên)")
                    .put("parameters", JSONObject()
                        .put("type", "object")
                        .put("properties", JSONObject()
                            .put("query", JSONObject()
                                .put("type", "string")
                                .put("description", "Từ khóa tìm kiếm")
                            )
                        )
                        .put("required", JSONArray().put("query"))
                    )
                )
            )
            
            // Tool 2: Get my learning summary
            put(JSONObject()
                .put("type", "function")
                .put("function", JSONObject()
                    .put("name", "get_my_learning_summary")
                    .put("description", "Lấy thông tin tổng quan về tiến độ học tập và các khóa học đang học")
                    .put("parameters", JSONObject()
                        .put("type", "object")
                        .put("properties", JSONObject())
                        .put("required", JSONArray())
                    )
                )
            )
            
            // Tool 3: Recommend new courses
            put(JSONObject()
                .put("type", "function")
                .put("function", JSONObject()
                    .put("name", "recommend_new_courses")
                    .put("description", "Gợi ý các khóa học mới dựa trên sở thích và lịch sử học tập")
                    .put("parameters", JSONObject()
                        .put("type", "object")
                        .put("properties", JSONObject()
                            .put("limit", JSONObject()
                                .put("type", "integer")
                                .put("description", "Số lượng gợi ý (mặc định 5)")
                            )
                        )
                        .put("required", JSONArray())
                    )
                )
            )
            
            // Tool 4: Get course details
            put(JSONObject()
                .put("type", "function")
                .put("function", JSONObject()
                    .put("name", "get_course_details")
                    .put("description", "Lấy thông tin chi tiết về một khóa học cụ thể")
                    .put("parameters", JSONObject()
                        .put("type", "object")
                        .put("properties", JSONObject()
                            .put("courseId", JSONObject()
                                .put("type", "string")
                                .put("description", "ID của khóa học")
                            )
                        )
                        .put("required", JSONArray().put("courseId"))
                    )
                )
            )
            
            // Tool 5: Add course to cart
            put(JSONObject()
                .put("type", "function")
                .put("function", JSONObject()
                    .put("name", "add_course_to_cart")
                    .put("description", "Thêm một khóa học vào giỏ hàng")
                    .put("parameters", JSONObject()
                        .put("type", "object")
                        .put("properties", JSONObject()
                            .put("courseId", JSONObject()
                                .put("type", "string")
                                .put("description", "ID của khóa học")
                            )
                        )
                        .put("required", JSONArray().put("courseId"))
                    )
                )
            )
            
            // Tool 6: Get enrolled courses
            put(JSONObject()
                .put("type", "function")
                .put("function", JSONObject()
                    .put("name", "get_enrolled_courses")
                    .put("description", "Xem danh sách các khóa học mà người dùng đã mua/đăng ký")
                    .put("parameters", JSONObject()
                        .put("type", "object")
                        .put("properties", JSONObject())
                        .put("required", JSONArray())
                    )
                )
            )
            
            // Tool 7: Get course reviews
            put(JSONObject()
                .put("type", "function")
                .put("function", JSONObject()
                    .put("name", "get_course_reviews")
                    .put("description", "Xem các đánh giá và nhận xét về một khóa học")
                    .put("parameters", JSONObject()
                        .put("type", "object")
                        .put("properties", JSONObject()
                            .put("courseId", JSONObject()
                                .put("type", "string")
                                .put("description", "ID của khóa học")
                            )
                        )
                        .put("required", JSONArray().put("courseId"))
                    )
                )
            )
            
            // Tool 8: Get user notifications
            put(JSONObject()
                .put("type", "function")
                .put("function", JSONObject()
                    .put("name", "get_user_notifications")
                    .put("description", "Lấy danh sách thông báo cá nhân (cập nhật khóa học, bài tập hết hạn, etc)")
                    .put("parameters", JSONObject()
                        .put("type", "object")
                        .put("properties", JSONObject())
                        .put("required", JSONArray())
                    )
                )
            )
            
            // Tool 9: Check purchase history
            put(JSONObject()
                .put("type", "function")
                .put("function", JSONObject()
                    .put("name", "check_purchase_history")
                    .put("description", "Xem lịch sử mua hàng và thanh toán")
                    .put("parameters", JSONObject()
                        .put("type", "object")
                        .put("properties", JSONObject())
                        .put("required", JSONArray())
                    )
                )
            )
            
            // Tool 10: Get assignment list
            put(JSONObject()
                .put("type", "function")
                .put("function", JSONObject()
                    .put("name", "get_assignment_list")
                    .put("description", "Xem danh sách các bài tập và dự án cần hoàn thành")
                    .put("parameters", JSONObject()
                        .put("type", "object")
                        .put("properties", JSONObject())
                        .put("required", JSONArray())
                    )
                )
            )
        }
    }

    /**
     * Thực hiện phần xử lý chính của luồng nghiệp vụ hoặc giao diện tương ứng.
     * Hàm này thường làm việc với Firestore hoặc API ngoài và trả kết quả về dạng `ResultState` cho tầng gọi phía trên.
     */

    // Dùng để phát hiện session rỗng gần nhất, phục vụ các flow tránh tạo thừa session.
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

    /**
     * Lấy dữ liệu hoặc trạng thái cần thiết cho luồng hiện tại.
     * Hàm này thường làm việc với Firestore hoặc API ngoài và trả kết quả về dạng `ResultState` cho tầng gọi phía trên.
     */

    // Tin nhắn luôn được sort tăng dần theo thời gian để conversation render đúng thứ tự.
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

    /**
     * Gửi yêu cầu xử lý hoặc tín hiệu nghiệp vụ tới dịch vụ tương ứng.
     * Hàm này thường làm việc với Firestore hoặc API ngoài và trả kết quả về dạng `ResultState` cho tầng gọi phía trên.
     */

    // Lưu message và cập nhật `lastMessageAt` của session trong cùng một batch
    // để sidebar lịch sử chat phản ánh ngay hoạt động mới nhất.
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

    /**
     * Gửi yêu cầu xử lý hoặc tín hiệu nghiệp vụ tới dịch vụ tương ứng.
     * Hàm này thường làm việc với Firestore hoặc API ngoài và trả kết quả về dạng `ResultState` cho tầng gọi phía trên.
     */

    // Đây là orchestration chính của chatbot phiên bản cũ:
    // 1. lưu message user,
    // 2. nạp lịch sử hội thoại,
    // 3. gọi provider AI,
    // 4. lưu lại message bot.
    suspend fun sendUserMessageAndApiReply(
        sessionId: String,
        userContent: String,
        userId: String = ""
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

        val reply = try {
            requestAssistantReply(historyMessages, userContent.trim(), userId)
        } catch (throwable: Throwable) {
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

    // Dựng payload chat completion và danh sách tool mà model được phép gọi.
    private suspend fun requestAssistantReply(historyMessages: List<ChatMessage>, latestUserContent: String, userId: String = ""): String {
        val apiKey = BuildConfig.CHATBOT_API_KEY
        if (apiKey.isBlank()) {
            throw IllegalStateException("Chưa cấu hình CHATBOT_API_KEY")
        }

        val endpoint = BuildConfig.CHATBOT_API_URL.ifBlank { "https://openrouter.io/api/v1/chat/completions" }
        val model = BuildConfig.CHATBOT_MODEL.ifBlank { "qwen/qwen3.6-plus-preview:free" }

        val messagesJson = JSONArray().apply {
            put(
                JSONObject()
                    .put("role", "user")
                    .put("content", "Bạn là trợ lý học tập tiếng Việt, trả lời rõ ràng, ngắn gọn và có ví dụ khi phù hợp.")
            )

            historyMessages
                .takeLast(8)
                .forEach { message ->
                    val role = when (message.sender) {
                        ChatSender.USER -> "user"
                        ChatSender.BOT -> "assistant"
                        ChatSender.SYSTEM -> "user"
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

        val payloadBuilder = JSONObject()
            .put("model", model)
            .put("messages", messagesJson)
            .put("temperature", 0.4)
            .put("tools", buildToolDefinitions())
            .put("tool_choice", "auto")

        val payload = payloadBuilder.toString()

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
            throw IllegalStateException("Chat API lỗi ($responseCode): $responseText")
        }

        val json = JSONObject(responseText)
        val choice = json.optJSONArray("choices")?.optJSONObject(0) ?: throw IllegalStateException("Phản hồi từ Chat API không hợp lệ")
        
        val message = choice.optJSONObject("message") ?: JSONObject()
        var content = message.optString("content", "").trim()
        
        // Handle tool calls if present
        val toolCalls = message.optJSONArray("tool_calls")
        if (toolCalls != null && toolCalls.length() > 0) {
            for (i in 0 until toolCalls.length()) {
                val toolCall = toolCalls.getJSONObject(i)
                val functionName = toolCall.optJSONObject("function")?.optString("name") ?: continue
                val functionArgsStr = toolCall.optJSONObject("function")?.optString("arguments") ?: "{}"
                val functionArgs = JSONObject(functionArgsStr)
                
                // Execute the function
                val result = executeFunctionCall(userId, functionName, functionArgs)
                
                // Format result for display
                content += "\n\n[Kết quả: $functionName]\n${formatFunctionResult(functionName, result)}"
            }
        }

        if (content.isBlank()) {
            throw IllegalStateException("Phản hồi từ Chat API không hợp lệ")
        }

        return content
    }
    
    private suspend fun executeFunctionCall(userId: String, functionName: String, args: JSONObject): Map<String, Any> {
        return try {
            when (functionName) {
                "search_courses" -> {
                    val query = args.optString("query", "")
                    val result = courseRepository.searchCourses(query)
                    when (result) {
                        is ResultState.Success -> {
                            val courses = result.data.take(5)
                            mapOf(
                                "success" to true,
                                "courses" to courses.map { courseToMap(it) }
                            )
                        }
                        is ResultState.Error -> mapOf(
                            "success" to false,
                            "error" to result.message
                        )
                        else -> mapOf("success" to false, "error" to "Unknown error")
                    }
                }
                
                "get_my_learning_summary" -> {
                    val enrollmentResult = enrollmentRepository.getUserEnrollments(userId)
                    when (enrollmentResult) {
                        is ResultState.Success -> {
                            val enrollments = enrollmentResult.data
                            val summary = mutableListOf<Map<String, Any>>()
                            
                            enrollments.forEach { enrollment ->
                                val courseResult = courseRepository.getCourseById(enrollment.courseId)
                                if (courseResult is ResultState.Success) {
                                    val course = courseResult.data
                                    val progressResult = progressRepository.getProgress(userId, course.id)
                                    val progress = if (progressResult is ResultState.Success) {
                                        progressResult.data
                                    } else null
                                    
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
                            }
                            
                            mapOf(
                                "success" to true,
                                "enrollments" to summary
                            )
                        }
                        is ResultState.Error -> mapOf(
                            "success" to false,
                            "error" to enrollmentResult.message
                        )
                        else -> mapOf("success" to false, "error" to "Unknown error")
                    }
                }
                
                "recommend_new_courses" -> {
                    val limit = args.optInt("limit", 5)
                    val result = recommendationRepository.getRecommendedCourses(userId, limit)
                    when (result) {
                        is ResultState.Success -> {
                            val courses = result.data
                            mapOf(
                                "success" to true,
                                "recommendations" to courses.map { courseToMap(it) }
                            )
                        }
                        is ResultState.Error -> mapOf(
                            "success" to false,
                            "error" to result.message
                        )
                        else -> mapOf("success" to false, "error" to "Unknown error")
                    }
                }
                
                "get_course_details" -> {
                    val courseId = args.optString("courseId", "")
                    val result = courseRepository.getCourseById(courseId)
                    when (result) {
                        is ResultState.Success -> {
                            mapOf(
                                "success" to true,
                                "course" to courseToMap(result.data)
                            )
                        }
                        is ResultState.Error -> mapOf(
                            "success" to false,
                            "error" to result.message
                        )
                        else -> mapOf("success" to false, "error" to "Unknown error")
                    }
                }
                
                "add_course_to_cart" -> {
                    val courseId = args.optString("courseId", "")
                    val result = cartRepository.addCourseToCart(userId, courseId)
                    when (result) {
                        is ResultState.Success -> mapOf(
                            "success" to true,
                            "message" to "Đã thêm khóa học vào giỏ hàng"
                        )
                        is ResultState.Error -> mapOf(
                            "success" to false,
                            "error" to result.message
                        )
                        else -> mapOf("success" to false, "error" to "Unknown error")
                    }
                }
                
                "get_enrolled_courses" -> {
                    val result = enrollmentRepository.getUserEnrollments(userId)
                    when (result) {
                        is ResultState.Success -> {
                            val courses = result.data.mapNotNull { enrollment ->
                                val courseResult = courseRepository.getCourseById(enrollment.courseId)
                                if (courseResult is ResultState.Success) {
                                    courseToMap(courseResult.data)
                                } else null
                            }
                            mapOf(
                                "success" to true,
                                "courses" to courses
                            )
                        }
                        is ResultState.Error -> mapOf(
                            "success" to false,
                            "error" to result.message
                        )
                        else -> mapOf("success" to false, "error" to "Unknown error")
                    }
                }
                
                "get_course_reviews" -> {
                    val courseId = args.optString("courseId", "")
                    mapOf(
                        "success" to true,
                        "courseId" to courseId,
                        "reviews" to listOf(
                            mapOf(
                                "author" to "Học viên 1",
                                "rating" to 5,
                                "comment" to "Rất hữu ích!"
                            )
                        )
                    )
                }
                
                "get_user_notifications" -> {
                    mapOf(
                        "success" to true,
                        "notifications" to listOf(
                            mapOf(
                                "message" to "Bạn có một bài tập mới",
                                "type" to "assignment"
                            )
                        )
                    )
                }
                
                "check_purchase_history" -> {
                    mapOf(
                        "success" to true,
                        "transactions" to listOf(
                            mapOf(
                                "id" to "txn1",
                                "amount" to 500000,
                                "date" to System.currentTimeMillis()
                            )
                        )
                    )
                }
                
                "get_assignment_list" -> {
                    mapOf(
                        "success" to true,
                        "assignments" to listOf(
                            mapOf(
                                "title" to "Bài tập 1",
                                "dueDate" to System.currentTimeMillis() + 86400000
                            )
                        )
                    )
                }
                
                else -> mapOf(
                    "success" to false,
                    "error" to "Function not found: $functionName"
                )
            }
        } catch (e: Exception) {
            mapOf(
                "success" to false,
                "error" to (e.message ?: "Function execution failed")
            )
        }
    }
    
    private fun formatFunctionResult(functionName: String, result: Map<String, Any>): String {
        return when {
            result["success"] == true -> {
                when (functionName) {
                    "search_courses", "recommend_new_courses", "get_enrolled_courses" -> {
                        val courses = result["courses"] as? List<*> ?: result["recommendations"] as? List<*> ?: emptyList<Any>()
                        courses.joinToString("\n") { 
                            val course = it as? Map<*, *>
                            "• ${course?.get("title") ?: "Unknown"}"
                        }
                    }
                    "get_my_learning_summary" -> {
                        val enrollments = result["enrollments"] as? List<*> ?: emptyList<Any>()
                        enrollments.joinToString("\n") {
                            val e = it as? Map<*, *>
                            "• ${e?.get("title")} - Tiến độ: ${e?.get("progress")}%"
                        }
                    }
                    else -> result["message"] as? String ?: "Thành công"
                }
            }
            else -> "Lỗi: ${result["error"]}"
        }
    }
    
    private fun courseToMap(course: Course): Map<String, Any> {
        return mapOf(
            "id" to course.id,
            "title" to course.title,
            "price" to course.price,
            "rating" to course.rating,
            "description" to course.description,
            "instructorName" to (course.instructorName ?: "Unknown"),
            "enrollmentCount" to course.enrollmentCount
        )
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

