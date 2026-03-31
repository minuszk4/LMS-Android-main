# Hướng Dẫn Sử Dụng - Recommendation System & AI Chatbot

## 🎯 Tổng Quan

Ứng dụng LMS-Android đã được nâng cấp với:
1. **Hệ thống gợi ý khóa học thông minh** - Sử dụng thuật toán Vector Space Model với TF-IDF
2. **Chatbot AI cá nhân hóa** - Sử dụng Google Gemini với khả năng gọi hàm (function calling)

## 📦 Cài Đặt

### Bước 1: Cấu hình API Key

Thêm vào file `local.properties` (ở thư mục gốc dự án):

```properties
# Các cấu hình hiện có...
google.web.client.id=your_google_client_id

# Thêm cấu hình mới cho Gemini AI
GEMINI_API_KEY=your_gemini_api_key_here
```

**Lấy API Key**:
1. Truy cập: https://aistudio.google.com/app/apikey
2. Đăng nhập với tài khoản Google
3. Tạo API key mới
4. Sao chép và dán vào `local.properties`

### Bước 2: Sync Gradle

```bash
./gradlew build
```

Hoặc trong Android Studio: File → Sync Project with Gradle Files

### Bước 3: Chạy Ứng Dụng

```bash
./gradlew installDebug
```

Hoặc nhấn nút Run trong Android Studio.

## 🚀 Sử Dụng

### 1. Hệ Thống Gợi Ý Khóa Học

#### Gợi ý cá nhân hóa
Hệ thống sẽ tự động gợi ý khóa học dựa trên:
- Các khóa học bạn đã đăng ký
- Tiến độ học tập của bạn
- Sở thích về danh mục, cấp độ, giảng viên
- Độ phổ biến của khóa học

#### Người dùng mới (Cold Start)
Nếu chưa đăng ký khóa học nào, hệ thống sẽ gợi ý dựa trên:
- Độ phổ biến (số lượng học viên)
- Đánh giá cao

#### Gợi ý theo danh mục
Gợi ý các khóa học hay nhất trong một danh mục cụ thể.

### 2. Chatbot AI

#### Mở Chatbot
Vào màn hình "Chatbot" hoặc "Trợ lý học tập" trong ứng dụng.

#### Các Câu Hỏi Mẫu

**1. Xem tiến độ học tập:**
```
Tôi đã học đến đâu rồi?
Cho tôi xem tiến độ học tập của tôi
Tôi đã hoàn thành bao nhiêu khóa học?
```

**2. Tìm kiếm khóa học:**
```
Tìm kiếm khóa học về lập trình Android
Có khóa học nào về Python không?
Tìm khóa học của giảng viên Nguyễn Văn A
```

**3. Gợi ý khóa học:**
```
Gợi ý cho tôi khóa học phù hợp
Tôi nên học khóa học nào tiếp theo?
Khóa học nào phù hợp với trình độ của tôi?
```

**4. Xem chi tiết khóa học:**
```
Cho tôi xem chi tiết khóa học [tên khóa học]
Khóa học Android cơ bản có gì?
```

**5. Thêm vào giỏ hàng:**
```
Thêm khóa học Python vào giỏ hàng
Tôi muốn mua khóa học [tên khóa học]
```

#### Tính Năng Đặc Biệt

**Cá nhân hóa:**
- Chatbot biết các khóa học bạn đang theo học
- Hiểu tiến độ học tập của bạn
- Đưa ra gợi ý phù hợp với trình độ

**Function Calling:**
- Tự động tìm kiếm khóa học khi cần
- Lấy dữ liệu tiến độ thực tế
- Thực hiện hành động (thêm vào giỏ hàng)

**Ngữ cảnh hội thoại:**
- Nhớ toàn bộ lịch sử trò chuyện
- Hiểu câu hỏi follow-up
- Duy trì ngữ cảnh qua nhiều phiên

## 🎨 Giao Diện

### ChatMessage Types

Chatbot hỗ trợ nhiều loại nội dung:

1. **TEXT** - Văn bản thông thường
2. **COURSE_CARD** - Thẻ khóa học (tương lai)
3. **COURSE_LIST** - Danh sách khóa học (tương lai)
4. **PROGRESS_CHART** - Biểu đồ tiến độ (tương lai)
5. **FUNCTION_CALL** - Hiển thị kết quả gọi hàm

*Lưu ý: Các loại nội dung phong phú (rich content) đã được chuẩn bị trong data model, UI có thể được mở rộng trong tương lai.*

## 🧪 Kiểm Tra

### Kiểm Tra Recommendation System

1. **Với người dùng mới:**
   - Xem danh sách khóa học gợi ý
   - Kiểm tra xem có sắp xếp theo độ phổ biến không
   - Không hiển thị khóa học đã đăng ký

2. **Với người dùng có lịch sử:**
   - Đăng ký vài khóa học khác nhau
   - Hoàn thành một phần tiến độ
   - Xem gợi ý có liên quan đến sở thích không

### Kiểm Tra Chatbot

1. **Kết nối API:**
   ```
   Chào bạn
   ```
   → Phải nhận được phản hồi từ Gemini

2. **Function Calling:**
   ```
   Tôi đã học đến đâu rồi?
   ```
   → Phải hiển thị danh sách khóa học và tiến độ

3. **Search:**
   ```
   Tìm khóa học về Android
   ```
   → Phải trả về kết quả tìm kiếm

4. **Recommendation:**
   ```
   Gợi ý khóa học cho tôi
   ```
   → Phải gọi recommendation engine

5. **Add to Cart:**
   ```
   Thêm khóa học [ID hoặc tên] vào giỏ hàng
   ```
   → Phải cập nhật giỏ hàng

## ❗ Xử Lý Lỗi

### Lỗi: "Chưa cấu hình GEMINI_API_KEY"
**Nguyên nhân:** Chưa thêm API key vào local.properties
**Giải pháp:** Làm theo Bước 1 trong phần Cài Đặt

### Lỗi: "Chat API lỗi (401)"
**Nguyên nhân:** API key không hợp lệ
**Giải pháp:** 
- Kiểm tra API key đã copy đúng chưa
- Tạo API key mới từ Google AI Studio
- Đảm bảo không có khoảng trắng thừa

### Lỗi: "Chat API lỗi (429)"
**Nguyên nhân:** Quá giới hạn số lần gọi API
**Giải pháp:**
- Chờ vài phút rồi thử lại
- Kiểm tra quota tại https://aistudio.google.com

### Lỗi: "Gọi Gemini AI thất bại"
**Nguyên nhân:** Lỗi kết nối internet hoặc server
**Giải pháp:**
- Kiểm tra kết nối internet
- Thử lại sau vài giây
- Xem log để biết chi tiết lỗi

### Không có gợi ý khóa học
**Nguyên nhân:** 
- Chưa có khóa học published trong database
- Tất cả khóa học đã được đăng ký
**Giải pháp:**
- Kiểm tra Firestore có khóa học với `isPublished = true`
- Thử với tài khoản người dùng khác

## 📊 Thuật Toán

### Vector Space Model (VSM)

Hệ thống recommendation sử dụng VSM để tính độ tương đồng:

```
Similarity(User, Course) = cosine_similarity(UserVector, CourseVector)

UserVector bao gồm:
- Category weights (dựa trên lịch sử đăng ký)
- Level weights (dựa trên cấp độ đã học)
- Instructor weights (dựa trên giảng viên yêu thích)
- Progress weights (trọng số dựa trên tiến độ)

CourseVector bao gồm:
- Category features
- Level features
- Instructor features
- Popularity score
```

### TF-IDF Weighting

```
TF-IDF = TF * IDF
TF = frequency of feature in user profile
IDF = log(1 + 1/(feature_weight + 0.01))
```

### Cosine Similarity

```
cos(θ) = (A · B) / (||A|| × ||B||)

Trong đó:
- A · B = dot product
- ||A|| = magnitude of vector A
- ||B|| = magnitude of vector B
```

## 🔧 Cấu Trúc Code

```
data/
├── model/
│   ├── ChatMessage.kt (enhanced)
│   └── Course.kt
└── repository/
    ├── RecommendationRepository.kt (NEW)
    ├── GeminiChatbotRepository.kt (NEW)
    ├── CourseRepository.kt (updated)
    └── EnrollmentRepository.kt (updated)

viewmodel/
└── ChatbotViewModel.kt (updated)

ui/
└── screen/
    └── student/
        └── ChatbotScreen.kt
```

## 📝 Ghi Chú Quan Trọng

1. **API Key Security**: Không commit API key vào Git. File `local.properties` đã được ignore.

2. **Gemini Model**: Đang sử dụng `gemini-2.0-flash` (ổn định). Có thể đổi sang các model khác nếu cần.

3. **Rate Limits**: Gemini API có giới hạn request. Sử dụng hợp lý để tránh bị chặn.

4. **Offline Support**: Chatbot cần internet để hoạt động. Recommendation system hoạt động offline sau khi load data.

5. **Firestore Rules**: Đảm bảo Firestore rules cho phép đọc courses, enrollments, progress.

## 🎓 Best Practices

1. **Khi chat với AI:**
   - Hỏi câu hỏi rõ ràng, cụ thể
   - Sử dụng tiếng Việt tự nhiên
   - Đặt follow-up questions để AI hiểu rõ hơn

2. **Khi tìm kiếm khóa học:**
   - Dùng từ khóa liên quan (tên, mô tả, giảng viên)
   - Thử nhiều cách diễn đạt khác nhau
   - Hỏi AI để được gợi ý tốt hơn

3. **Tối ưu gợi ý:**
   - Hoàn thành tiến độ các khóa học đã đăng ký
   - Đăng ký nhiều khóa học trong các lĩnh vực quan tâm
   - Tương tác thường xuyên với hệ thống

## 🚀 Tính Năng Tương Lai

- [ ] Rich UI components (course cards, charts)
- [ ] Voice input/output
- [ ] Learning path recommendations
- [ ] Study reminders
- [ ] Multi-language support
- [ ] Advanced analytics
- [ ] Collaborative filtering
- [ ] Deep learning models

## 📞 Hỗ Trợ

Nếu gặp vấn đề:
1. Xem phần "Xử Lý Lỗi" ở trên
2. Kiểm tra log trong Logcat (Android Studio)
3. Xem file `IMPLEMENTATION_COMPLETE.md` để biết chi tiết kỹ thuật

---

**Chúc bạn sử dụng thành công! 🎉**
