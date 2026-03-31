# Implementation Plan - Recommendation System & AI Chatbot Integration

The goal is to enhance the LMS-Android application with a recommendation system and a personalized chatbot capable of executing system functions and providing learning support based on user data.

## User Review Required

> [!IMPORTANT]
> - **API Keys**: To use the advanced chatbot, we will transition to the Google AI SDK (Gemini). You will need a `GEMINI_API_KEY` (replacing or alongside the existing `CHATBOT_API_KEY`).
> - **Function Calling**: The chatbot will be able to "see" your courses and progress to give better advice. This involves sending some metadata (not personal credentials) to the AI model.

## Proposed Changes

### 1. Data Layer Enhancements

#### [NEW] [RecommendationRepository.kt](file:///c:/Users/tranv/Downloads/LMS-Android-main/LMS-Android-main/app/src/main/java/com/example/lms/data/repository/RecommendationRepository.kt)
Implement a self-contained "Small ML" engine for recommendation.
- **Model**: **Vector Space Model (VSM)** with **TF-IDF** inspired weighting.
- **Logic**:
    - Each course is converted into a feature vector (Categories, Level, Instructor).
    - User profile is represented as a 'Preference Vector' based on enrolled courses and interaction time.
    - **Cosine Similarity**: Calculation to find the "closest" courses to the user's preference vector.
    - **Cold Start Handling**: Fallback to popularity-based ranking for new users.

#### [NEW] [GeminiChatbotRepository.kt](file:///c:/Users/tranv/Downloads/LMS-Android-main/LMS-Android-main/app/src/main/java/com/example/lms/data/repository/GeminiChatbotRepository.kt)
Create a new chatbot repository using Google's `generativeai` SDK.
- **Feat**: Function calling (Tools) support.
- **Feat**: **Advanced Session Memory**:
    - Instead of just last 8 messages, we will leverage Gemini's large context window.
    - History will be synchronized with Firestore `chatMessages`.
    - When starting a session, the chatbot will "remember" previous context by loading relevant history and summarizing if needed.
- **Feat**: Personalized system prompt containing user's enrolled courses and progress summary.
- **Tools to implement**:
    - `search_courses(query)`
    - `get_my_learning_summary()`
    - `recommend_new_courses()`
    - `get_course_details(courseId)`
    - `add_course_to_cart(courseId)`

### 2. Dependency Updates

#### [MODIFY] [build.gradle.kts (app)](file:///c:/Users/tranv/Downloads/LMS-Android-main/LMS-Android-main/app/build.gradle.kts)
- Add `implementation("com.google.ai.client.generativeai:generativeai:0.9.0")`.
- Update `BuildConfig` to include `GEMINI_API_KEY`.

### 3. ViewModel & UI Logic

#### [MODIFY] [ChatbotViewModel.kt](file:///c:/Users/tranv/Downloads/LMS-Android-main/LMS-Android-main/app/src/main/java/com/example/lms/viewmodel/ChatbotViewModel.kt)
- Transition from `ChatbotRepository` to `GeminiChatbotRepository`.
- Load user profile and learning data before initializing the chat.
- Handle function call results by creating messages with specific `metadata`.

#### [MODIFY] [ChatbotScreen.kt](file:///c:/Users/tranv/Downloads/LMS-Android-main/LMS-Android-main/app/src/main/java/com/example/lms/ui/screen/student/ChatbotScreen.kt)
- **Rich UI Support**:
    - Enhance `ChatMessage` to support item types like `COURSE_CARD`, `COURSE_LIST`, `PROGRESS_CHART`.
    - Implement custom Compose components to render these types within the chat scroll.
    - Interactive elements: Clicking on a course card will navigate to the course details.

## Open Questions

1. **AI Engine**: Are you comfortable switching to Gemini (Google AI SDK)? It is much better suited for function calling and personalization in Android.
2. **Recommendation Logic**: Do you prefer the recommendation system to run locally on the device (algorithmic) or do you want to explore a cloud-based ML approach?

## Verification Plan

### Automated Tests
- Integration test for `RecommendationRepository` to ensure it filters out already enrolled courses.
- Unit tests for Chatbot logic to verify tool selection (Mocking Gemini API).

### Manual Verification
- Open the Chatbot and ask: "Tôi đã học đến đâu rồi?" (Where am I in my learning?) - Verify it lists enrolled courses.
- Ask: "Gợi ý cho tôi khóa học về lập trình Kotlin" - Verify it calls the recommendation tool.
- Ask: "Thêm khóa học X vào giỏ hàng" - Verify the cart is updated.
