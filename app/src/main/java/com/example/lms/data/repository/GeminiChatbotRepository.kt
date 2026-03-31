package com.example.lms.data.repository

import com.example.lms.BuildConfig
import com.example.lms.data.model.ChatMessage
import com.example.lms.data.model.ChatMessageType
import com.example.lms.data.model.ChatSender
import com.example.lms.data.model.ChatSession
import com.example.lms.data.model.ChatSessionStatus
import com.example.lms.data.model.Course
import com.example.lms.util.ResultState
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.Content
import com.google.ai.client.generativeai.type.FunctionDeclaration
import com.google.ai.client.generativeai.type.FunctionType
import com.google.ai.client.generativeai.type.Schema
import com.google.ai.client.generativeai.type.Tool
import com.google.ai.client.generativeai.type.content
import com.google.ai.client.generativeai.type.generationConfig
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject

class GeminiChatbotRepository {

    private val firestore = FirebaseFirestore.getInstance()
    private val chatSessionsCollection = firestore.collection("chatSessions")
    private val chatMessagesCollection = firestore.collection("chatMessages")
    
    private val courseRepository = CourseRepository()
    private val recommendationRepository = RecommendationRepository()
    private val enrollmentRepository = EnrollmentRepository()
    private val cartRepository = CartRepository()
    private val progressRepository = ProgressRepository()

    // Define function calling tools
    private val searchCoursesTool = FunctionDeclaration(
        name = "search_courses",
        description = "Tìm kiếm các khóa học theo từ khóa. Trả về danh sách các khóa học phù hợp.",
        parameters = listOf(
            Schema.str("query", "Từ khóa tìm kiếm (tiêu đề, mô tả, giảng viên)")
        ),
        requiredParameters = listOf("query")
    )

    private val getLearningSummaryTool = FunctionDeclaration(
        name = "get_my_learning_summary",
        description = "Lấy thông tin tổng quan về tiến độ học tập của người dùng. Trả về danh sách các khóa học đang học và tiến độ hoàn thành.",
        parameters = emptyList(),
        requiredParameters = emptyList()
    )

    private val recommendCoursesTool = FunctionDeclaration(
        name = "recommend_new_courses",
        description = "Gợi ý các khóa học mới dựa trên sở thích và lịch sử học tập của người dùng.",
        parameters = listOf(
            Schema.int("limit", "Số lượng khóa học gợi ý (mặc định 5)")
        ),
        requiredParameters = emptyList()
    )

    private val getCourseDetailsTool = FunctionDeclaration(
        name = "get_course_details",
        description = "Lấy thông tin chi tiết về một khóa học cụ thể.",
        parameters = listOf(
            Schema.str("courseId", "ID của khóa học cần xem chi tiết")
        ),
        requiredParameters = listOf("courseId")
    )

    private val addToCartTool = FunctionDeclaration(
        name = "add_course_to_cart",
        description = "Thêm một khóa học vào giỏ hàng của người dùng.",
        parameters = listOf(
            Schema.str("courseId", "ID của khóa học cần thêm vào giỏ hàng")
        ),
        requiredParameters = listOf("courseId")
    )

    private val tools = listOf(
        Tool(
            functionDeclarations = listOf(
                searchCoursesTool,
                getLearningSummaryTool,
                recommendCoursesTool,
                getCourseDetailsTool,
                addToCartTool
            )
        )
    )

    suspend fun getOrCreateActiveSession(
        userId: String,
        defaultTitle: String = "Trợ lý học tập AI"
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
        title: String = "Trợ lý học tập AI"
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
            ResultState.Success(session)
        } catch (e: Exception) {
            ResultState.Error(e.message ?: "Tạo phiên chat thất bại")
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
        content: String,
        messageType: ChatMessageType = ChatMessageType.TEXT,
        metadata: Map<String, Any> = emptyMap()
    ): ResultState<ChatMessage> {
        if (sessionId.isBlank()) return ResultState.Error("Thiếu thông tin phiên chat")
        if (content.isBlank() && messageType == ChatMessageType.TEXT) {
            return ResultState.Error("Nội dung tin nhắn không hợp lệ")
        }

        return try {
            val now = System.currentTimeMillis()
            val messageRef = chatMessagesCollection.document()
            val message = ChatMessage(
                id = messageRef.id,
                sessionId = sessionId,
                sender = sender,
                content = content.trim(),
                messageType = messageType,
                metadata = metadata,
                createdAt = now
            )

            firestore.runBatch { batch ->
                batch.set(messageRef, message.toMap())
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
        // Send user message
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

        // Force recommendation queries to use ML algorithm directly.
        if (isRecommendationIntent(userContent)) {
            return handleMlRecommendation(sessionId, userId, userContent, userMessage)
        }

        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isBlank()) {
            return ResultState.Error("Chưa cấu hình GEMINI_API_KEY")
        }

        return try {
            // Get conversation history
            val historyMessages = when (val historyResult = getSessionMessages(sessionId)) {
                is ResultState.Success -> historyResult.data
                else -> emptyList()
            }

            // Generate personalized system prompt
            val systemPrompt = generateSystemPrompt(userId)

            // Build Gemini chat history
            val chatHistory = buildChatHistory(historyMessages, systemPrompt)

            // Initialize Gemini model with function calling
            val model = GenerativeModel(
                modelName = "gemini-2.0-flash",
                apiKey = apiKey,
                tools = tools,
                generationConfig = generationConfig {
                    temperature = 0.7f
                    topK = 40
                    topP = 0.95f
                }
            )

            // Start chat with history
            val chat = model.startChat(chatHistory)

            // Send user message and get response
            var response = chat.sendMessage(userContent)
            val allBotMessages = mutableListOf<ChatMessage>()
            
            // Track function calls for message type detection
            val functionTrace = mutableListOf<Pair<String, Map<String, Any>>>()

            // Handle function calls iteratively
            var functionCallCount = 0
            while (response.functionCalls.isNotEmpty() && functionCallCount < 5) {
                functionCallCount++

                val functionCall = response.functionCalls.first()
                val functionName = functionCall.name
                val functionArgs = functionCall.args

                // Execute the function
                val functionResult = executeFunctionCall(userId, functionName, functionArgs)
                
                // Track function calls in order
                functionTrace.add(functionName to functionResult)

                // Create a function result message
                val functionResultContent = content {
                    part(
                        com.google.ai.client.generativeai.type.FunctionResponsePart(
                            name = functionName,
                            response = JSONObject(functionResult)
                        )
                    )
                }

                // Send function result back to model
                response = chat.sendMessage(functionResultContent)
            }

            // Extract final text response
            val botReply = response.text ?: "Xin lỗi, tôi không thể trả lời câu hỏi này."

            // Determine message type and metadata based on function calls
            val (messageType, metadata) = analyzeResponseContent(
                functionTrace = functionTrace
            )

            // Save bot message
            val botMessageResult = sendMessage(
                sessionId = sessionId,
                sender = ChatSender.BOT,
                content = botReply,
                messageType = messageType,
                metadata = metadata
            )

            when (botMessageResult) {
                is ResultState.Success -> {
                    allBotMessages.add(botMessageResult.data)
                    ResultState.Success(listOf(userMessage) + allBotMessages)
                }
                is ResultState.Error -> ResultState.Error(botMessageResult.message)
                else -> ResultState.Error("Lưu phản hồi chatbot thất bại")
            }
        } catch (e: Exception) {
            ResultState.Error(e.message ?: "Gọi Gemini AI thất bại")
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
            is ResultState.Success -> {
                val recommendations = result.data
                if (recommendations.isEmpty()) {
                    val botMessageResult = sendMessage(
                        sessionId = sessionId,
                        sender = ChatSender.BOT,
                        content = "Hiện chưa có khóa học phù hợp để gợi ý. Bạn thử học thêm một vài khóa để hệ thống cá nhân hóa tốt hơn nhé.",
                        messageType = ChatMessageType.TEXT
                    )

                    when (botMessageResult) {
                        is ResultState.Success -> ResultState.Success(listOf(userMessage, botMessageResult.data))
                        is ResultState.Error -> ResultState.Error(botMessageResult.message)
                        else -> ResultState.Error("Lưu phản hồi chatbot thất bại")
                    }
                } else {
                    val metadata = extractMetadataFromCourseList(
                        mapOf("recommendations" to recommendations.map { courseToMap(it) })
                    )

                    val botMessageResult = sendMessage(
                        sessionId = sessionId,
                        sender = ChatSender.BOT,
                        content = "Đây là các khóa học được gợi ý bằng thuật toán ML từ hồ sơ học tập của bạn:",
                        messageType = if (metadata.isNotEmpty()) ChatMessageType.COURSE_LIST else ChatMessageType.TEXT,
                        metadata = metadata
                    )

                    when (botMessageResult) {
                        is ResultState.Success -> ResultState.Success(listOf(userMessage, botMessageResult.data))
                        is ResultState.Error -> ResultState.Error(botMessageResult.message)
                        else -> ResultState.Error("Lưu phản hồi chatbot thất bại")
                    }
                }
            }
            is ResultState.Error -> ResultState.Error(result.message)
            else -> ResultState.Error("Lấy gợi ý khóa học thất bại")
        }
    }

    private fun isRecommendationIntent(content: String): Boolean {
        val text = content.lowercase()
        return listOf(
            "gợi ý",
            "goi y",
            "đề xuất",
            "de xuat",
            "recommend",
            "khóa học phù hợp",
            "khoa hoc phu hop",
            "nên học",
            "nen hoc"
        ).any { keyword -> text.contains(keyword) }
    }

    private fun extractRecommendationLimit(content: String): Int {
        val number = Regex("(\\d+)").find(content)?.groupValues?.getOrNull(1)?.toIntOrNull()
        return (number ?: 5).coerceIn(1, 10)
    }

    private suspend fun generateSystemPrompt(userId: String): String {
        val enrollmentResult = enrollmentRepository.getUserEnrollments(userId)
        val enrollments = if (enrollmentResult is ResultState.Success) {
            enrollmentResult.data
        } else {
            emptyList()
        }

        val courseSummary = if (enrollments.isNotEmpty()) {
            val courseDetails = mutableListOf<String>()
            enrollments.take(5).forEach { enrollment ->
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
                    
                    courseDetails.add("- ${course.title} (Tiến độ: $progressPercent%)")
                }
            }
            "\n\nKhóa học người dùng đang theo học:\n" + courseDetails.joinToString("\n")
        } else {
            "\n\nNgười dùng chưa đăng ký khóa học nào."
        }

        return """
Bạn là trợ lý học tập thông minh tiếng Việt cho nền tảng LMS (Learning Management System).

**Nhiệm vụ của bạn:**
1. Trả lời các câu hỏi về học tập, khóa học, và tiến độ
2. Gợi ý khóa học phù hợp dựa trên sở thích người dùng
3. Hỗ trợ tìm kiếm và khám phá khóa học mới
4. Giúp người dùng quản lý giỏ hàng và đăng ký khóa học
5. Động viên và hướng dẫn người dùng trong quá trình học tập

**Phong cách giao tiếp:**
- Thân thiện, nhiệt tình và khuyến khích
- Trả lời rõ ràng, ngắn gọn
- Sử dụng emoji phù hợp để tạo không khí tích cực
- Đưa ra ví dụ cụ thể khi cần thiết

**Công cụ khả dụng:**
- search_courses: Tìm kiếm khóa học
- get_my_learning_summary: Xem tiến độ học tập
- recommend_new_courses: Gợi ý khóa học mới
- get_course_details: Xem chi tiết khóa học
- add_course_to_cart: Thêm khóa học vào giỏ hàng
$courseSummary
""".trimIndent()
    }

    private fun buildChatHistory(messages: List<ChatMessage>, systemPrompt: String): List<Content> {
        val history = mutableListOf<Content>()

        // Add system prompt as first user message
        history.add(
            content(role = "user") {
                text(systemPrompt)
            }
        )
        history.add(
            content(role = "model") {
                text("Chào bạn! Tôi là trợ lý học tập AI. Tôi sẵn sàng giúp bạn tìm kiếm khóa học, theo dõi tiến độ và gợi ý nội dung học tập phù hợp. Bạn cần hỗ trợ gì? 😊")
            }
        )

        // Add conversation history (skip the latest message as it will be sent separately)
        messages.dropLast(1).forEach { message ->
            when (message.sender) {
                ChatSender.USER -> {
                    history.add(
                        content(role = "user") {
                            text(message.content)
                        }
                    )
                }
                ChatSender.BOT -> {
                    history.add(
                        content(role = "model") {
                            text(message.content)
                        }
                    )
                }
                ChatSender.SYSTEM -> {
                    // Skip system messages in Gemini history
                }
            }
        }

        return history
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
                    val limit = (args["limit"] as? Number)?.toInt() ?: 5
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
                    val courseId = args["courseId"] as? String ?: ""
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
                    val courseId = args["courseId"] as? String ?: ""
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

    private fun courseToMap(course: Course): Map<String, Any> {
        return mapOf(
            "id" to course.id,
            "title" to course.title,
            "instructorName" to course.instructorName,
            "description" to course.description,
            "level" to course.level.name,
            "price" to course.price,
            "rating" to course.rating,
            "enrollmentCount" to course.enrollmentCount,
            "thumbnailUrl" to course.thumbnailUrl
        )
    }

    private fun analyzeResponseContent(
        functionTrace: List<Pair<String, Map<String, Any>>>
    ): Pair<ChatMessageType, Map<String, Any>> {
        // Use function calls to determine message type instead of heuristic text analysis

        if (functionTrace.isEmpty()) {
            return Pair(ChatMessageType.TEXT, emptyMap())
        }

        for ((functionName, functionResult) in functionTrace.asReversed()) {
            val success = functionResult["success"] as? Boolean ?: false
            if (!success) continue

            when (functionName) {
                "get_my_learning_summary" -> {
                    val metadata = extractMetadataFromLearningResult(functionResult)
                    if (metadata.isNotEmpty()) return Pair(ChatMessageType.PROGRESS_CHART, metadata)
                }

                "recommend_new_courses", "search_courses" -> {
                    val metadata = extractMetadataFromCourseList(functionResult)
                    if (metadata.isNotEmpty()) return Pair(ChatMessageType.COURSE_LIST, metadata)
                }

                "get_course_details" -> {
                    val metadata = extractMetadataFromCourseCard(functionResult)
                    if (metadata.isNotEmpty()) return Pair(ChatMessageType.COURSE_CARD, metadata)
                }
            }
        }

        // If no recognized successful function produced metadata, fall back to text.
        return Pair(ChatMessageType.TEXT, emptyMap())
    }
    
    private fun extractMetadataFromLearningResult(result: Map<String, Any>?): Map<String, Any> {
        if (result == null) return emptyMap()
        
        val data = mutableMapOf<String, Any>()
        val courses = mutableListOf<Map<String, Any>>()
        
        @Suppress("UNCHECKED_CAST")
        val enrollments = (result["enrollments"] as? List<Map<String, Any>>) ?: emptyList()
        
        enrollments.forEach { enrollment ->
            courses.add(
                mapOf(
                    "title" to (enrollment["title"] as? String ?: "Unknown"),
                    "progress" to ((enrollment["progress"] as? Number)?.toInt() ?: 0)
                )
            )
        }
        
        if (courses.isNotEmpty()) {
            data["courses"] = courses
            data["type"] = "learning_summary"
        }
        
        return data
    }
    
    private fun extractMetadataFromCourseList(result: Map<String, Any>?): Map<String, Any> {
        if (result == null) return emptyMap()
        
        val data = mutableMapOf<String, Any>()
        val courses = mutableListOf<Map<String, Any>>()
        
        @Suppress("UNCHECKED_CAST")
        val coursesList = ((result["courses"] ?: result["recommendations"]) as? List<Map<String, Any>>) ?: emptyList()
        
        coursesList.forEach { course ->
            val rawPrice = course["price"]
            val priceText = when (rawPrice) {
                is Number -> rawPrice.toLong().toString()
                is String -> rawPrice
                else -> ""
            }
            courses.add(
                mapOf(
                    "id" to (course["id"] as? String ?: ""),
                    "title" to (course["title"] as? String ?: "Unknown"),
                    "price" to priceText,
                    "rating" to ((course["rating"] as? Number)?.toDouble() ?: 0.0),
                    "enrollmentCount" to ((course["enrollmentCount"] as? Number)?.toInt() ?: 0)
                )
            )
        }
        
        if (courses.isNotEmpty()) {
            data["courses"] = courses
            data["type"] = "course_list"
        }
        
        return data
    }
    
    private fun extractMetadataFromCourseCard(result: Map<String, Any>?): Map<String, Any> {
        if (result == null) return emptyMap()
        
        val data = mutableMapOf<String, Any>()
        
        @Suppress("UNCHECKED_CAST")
        val course = (result["course"] as? Map<String, Any>) ?: return emptyMap()
        val rawPrice = course["price"]
        val priceText = when (rawPrice) {
            is Number -> rawPrice.toLong().toString()
            is String -> rawPrice
            else -> ""
        }
        
        data["title"] = course["title"] as? String ?: "Unknown"
        data["price"] = priceText
        data["rating"] = (course["rating"] as? Number)?.toDouble() ?: 0.0
        data["instructor"] = (course["instructorName"] as? String) ?: (course["instructor"] as? String) ?: "Unknown"
        data["enrollmentCount"] = (course["enrollmentCount"] as? Number)?.toInt() ?: 0
        data["description"] = course["description"] as? String ?: ""
        data["type"] = "course_card"
        
        return data
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
        
        val messageTypeText = data["messageType"] as? String
        val messageType = ChatMessageType.entries.firstOrNull { it.name == messageTypeText } ?: ChatMessageType.TEXT

        @Suppress("UNCHECKED_CAST")
        val metadata = (data["metadata"] as? Map<String, Any>) ?: emptyMap()

        return ChatMessage(
            id = id,
            sessionId = data["sessionId"] as? String ?: "",
            sender = sender,
            content = data["content"] as? String ?: "",
            messageType = messageType,
            metadata = metadata,
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
            "messageType" to messageType.name,
            "metadata" to metadata,
            "createdAt" to createdAt
        )
    }
}
