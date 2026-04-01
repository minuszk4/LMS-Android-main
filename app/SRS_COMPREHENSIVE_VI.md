# 📚 Đặc Tả Yêu Cầu Phần Mềm Chuyên Sâu (SRS)
## LMS Android - Nền Tảng Học Tập Di Động

**Phiên bản:** 2.0 (Comprehensive)  
**Ngày cập nhật:** 2026-04-01  
**Tác giả:** Nhóm Development  
**Trạng thái:** In Progress  
**Vòng phát hành:** MVP 1.0

---

## 📖 Mục Lục
1. [Executive Summary](#executive-summary)
2. [Phạm Vi & Tổng Quan](#phạm-vi--tổng-quan)
3. [Các Bên Liên Quan & Người Dùng](#các-bên-liên-quan--người-dùng)
4. [Yêu Cầu Chức Năng](#yêu-cầu-chức-năng)
5. [Yêu Cầu Non-Functional](#yêu-cầu-non-functional)
6. [Luồng Người Dùng & Use Cases](#luồng-người-dùng--use-cases)
7. [Mô Hình Dữ Liệu](#mô-hình-dữ-liệu)
8. [Kiến Trúc & Tích Hợp](#kiến-trúc--tích-hợp)
9. [Bảo Mật & Quyền Riêng Tư](#bảo-mật--quyền-riêng-tư)
10. [Tiêu Chí Chất Lượng & Thử Nghiệm](#tiêu-chí-chất-lượng--thử-nghiệm)
11. [Giới Hạn & Giả Định](#giới-hạn--giả-định)
12. [Rủi Ro & Chiến Lược Giảm Thiểu](#rủi-ro--chiến-lược-giảm-thiểu)
13. [Tài Liệu Liên Quan](#tài-liệu-liên-quan)

---

# 1. Executive Summary

## 1.1 Tổng Quan Dự Án
**LMS Android** là nền tảng học tập di động phi lợi nhuận cho phép:
- **Học viên** tìm kiếm, ghi danh và hoàn thành các khóa học
- **Giảng viên** tạo, quản lý khóa học và theo dõi hiệu suất học viên
- **Hệ thống** cung cấp AI chatbot hỗ trợ, khuyến nghị cá nhân hóa, thanh toán an toàn

## 1.2 Lợi Ích Chính
- ✅ Học tập linh hoạt (bất kì lúc nào, ở bất kì đâu)
- ✅ Khám phá khóa học thông minh (recommendation engine)
- ✅ Thanh toán linh hoạt (MoMo e-wallet)
- ✅ Theo dõi tiến độ thời gian thực (progress tracking)
- ✅ Hỗ trợ AI 24/7 (OpenRouter + Qwen chatbot)
- ✅ Cộng đồng giảng viên (tools quản lý khóa học)

---

# 2. Phạm Vi & Tổng Quan

## 2.1 Định Nghĩa Phạm Vi

### Trong Phạm Vi (In Scope)
#### **Chức Năng Học Viên**
- Xác thực người dùng (đăng ký, đăng nhập, quên mật khẩu)
- Danh mục khóa học, tìm kiếm, lọc theo thể loại/cấp độ
- Xem chi tiết khóa học (mô tả, giảng viên, curriculum preview)
- Giỏ hàng và quản lý giỏ
- Thanh toán qua MoMo e-wallet
- Ghi danh sau khi mua/tặng khóa học
- Học bài học (video/tài liệu đính kèm)
- Thi bài kiểm tra (quiz) và xem kết quả
- Theo dõi tiến độ cá nhân (progress bar, badges)
- Nhận thông báo (mua hàng thành công, nhắc nhở học, bài mới)
- Xem lịch sử đơn mua
- Viết review và rating khóa học
- Sử dụng AI chatbot cho Q&A

#### **Chức Năng Giảng Viên**
- Hồ sơ giảng viên (thông tin cá nhân, tài khoản ngân hàng)
- Tạo/chỉnh sửa khóa học (tiêu đề, mô tả, giá, video giới thiệu)
- Quản lý curriculum (bài học, bài kiểm tra, thứ tự)
- Nhập câu hỏi quiz từ file CSV
- Xem danh sách khóa học của mình
- Xem số liệu thống kê khóa học:
  - Số ghi danh theo thời gian
  - Tổng doanh thu
  - Tỷ lệ hoàn thành khóa học
  - Điểm số trung bình quiz
- Phân tích người dùng (enrollment trend, performance)

#### **Chức Năng AI Chatbot**
- Phần trăng chat (tạo, chuyển đổi phiên)
- Xử lý câu hỏi tự nhiên (NLP)
- Function calling (kích hoạt hành động) - 16 functions:
  - **Tìm kiếm & Khám phá**: `search_courses`, `recommend_new_courses`, `get_popular_courses`, `recommend_courses_by_category`
  - **Quản lý học tập**: `get_my_learning_summary`, `get_enrolled_courses`, `get_quiz_results`
  - **Chi tiết khóa học**: `get_course_details`, `get_course_reviews`
  - **Giỏ hàng**: `add_course_to_cart`, `remove_course_from_cart`, `get_my_cart_items`, `check_course_in_cart`
  - **Trạng thái đăng ký**: `check_course_enrollment`
  - **Thông báo & lịch sử mua**: `get_user_notifications`, `get_purchase_history`
- Render tin nhắn có cấu trúc (course cards, charts)
- Lưu lịch sử tin nhắn

#### **Chức Năng Admin**
- Duyệt & phê duyệt yêu cầu giảng viên
- Quản lý người dùng (ban, unban, xem profile)
- Quản lý khóa học (archive, delete, hide from public)
- Quản lý danh mục (thêm, sửa, xóa categories)
- Tạo tài khoản giảng viên trực tiếp
- Xem dashboard tóm tắt hệ thống
- Xem lịch sử hoạt động

#### **Chức Năng Hệ Thống**
- Firebase Firestore cho dữ liệu realtime
- Cloud Functions cho webhook MoMo
- Cloudinary cho lưu trữ hình ảnh/video
- OpenRouter API (Qwen model) cho AI chatbot
- Push Notifications (local + Firebase Cloud Messaging)

### Ngoài Phạm Vi (Out of Scope)
- ❌ Phiên dịch ngôn ngữ (chỉ hỗ trợ Tiếng Việt)
- ❌ Video hosting/streaming tự giữ (sử dụng Cloudinary)
- ❌ Social features (follow, like, comment)
- ❌ Chứng chỉ blockchain (future)
- ❌ Offline mode
- ❌ Export khóa học sang các nền tảng khác

## 2.2 Tổng Quan Kiến Trúc

```
┌─────────────────────────────────────────┐
│   Android App (Kotlin + Compose)        │
│  ├─ UI Layer (Jetpack Compose)          │
│  ├─ ViewModel + State Management        │
│  └─ Repository Pattern (Data Layer)     │
├─────────────────────────────────────────┤
│   Firebase Services                     │
│  ├─ Firestore (Database)                │
│  ├─ Firebase Auth                       │
│  ├─ Cloud Functions                     │
│  └─ Cloud Messaging                     │
├─────────────────────────────────────────┤
│   External Integrations                 │
│  ├─ OpenRouter API (Qwen/Gemma Models)  │
│  ├─ Cloudinary (CDN)                    │
│  └─ MoMo API (Payment)                  │
└─────────────────────────────────────────┘
```

---
Admin** | Quản trị hệ thống | Oversight, moderation | Realtime |
| **
# 3. Các Bên Liên Quan & Người Dùng

## 3.1 Các Bên Liên Quan Chính
| Bên Liên Quan | Vai Trò | Quyền Lợi | ĐPM |
|--------------|--------|----------|-----|
| **Product Owner** | Chiến lược sản phẩm | Tối đa hóa MAU, ARR | Hàng tháng |
| **Engineering Lead** | Triển khai & QA | Độ tin cậy, maintainability | Sprint |
| **UX/Design** | Trải nghiệm người dùng | Usability, NPS | Hàng tháng |
| **Giảng viên** | Người tạo nội dung | Student reach, course success | Realtime |
| **Học viên** | Người học | Trải nghiệm học tập | Realtime |

## 3.2 Personas Người Dùng

### **Persona 1: Học viên Tích Cực (30%)**
- **Tên:** Trần Minh (25 tuổi, sinh viên)
- **Mục đích:** Học kỹ năng mới để đổi việc
- **Hành vi:** Học 5 ngày/tuần, hoàn thành 80% khóa học
- **Điểm đau:** Ma sát thanh toán, không biết khóa nào tốt
- **Giải pháp:** Thanh toán dễ, AI recommend, progress tracking

### **Persona 2: Học viên Bận Rộn (40%)**
- **Tên:** Lê Thị Hoa (32 tuổi, kỹ sư làm việc)
- **Mục đích:** Nâng cao kỹ năng hiện tại
- **Hành vi:** Học 2-3 lần/tuần, hoàn thành 40% khóa học
- **Điểm đau:** Quên trong lịch trình, cần gợi ý
- **Giải pháp:** Push notification nhắc nhở, AI advisor

### **Persona 3: Giảng viên Phát Triển (20%)**
- **Tên:** Nguyễn Văn Sơn (40 tuổi, expert kỹ thuật)
- **Mục đích:** Chia sẻ kiến thức và ảnh hưởng đến học viên
- **Hành vi:** Đăng 5-10 khóa học, cập nhật content định kỳ
- **Điểm đau:** Quản lý khóa học phức tạp, không biết kết quả học viên
- **Giải pháp:** Dashboard analytics, bulk upload quiz, detailed student performance

### **Persona 4: Học viên Khám Phá (10%)**
- **Tên:** Phạm Tuấn (20 tuổi, sinh viên năm 1)
- **Mục đích:** Khám phá, thử nhiều khóa học
- **Hành vi:** Xem preview nhiều khóa, hoàn thành <20%
- **Điểm đau:** Khó tìm khóa phù hợp, quá nhiều lựa chọn

### **Persona 5: Admin Quản Trị (5%)**
- **Tên:** Võ Minh Tuấn (35 tuổi, CTO/Ops)
- **Mục đích:** Quản lý hệ thống, duyệt nội dung, monitoring
- **Hành vi:** Kiểm tra 2-3 lần/ngày, handle xử lý nhanh chóng
- **Điểm đau:** Thiếu visibility vào hoạt động, xử lý manual lâu
- **Giải pháp:** Dashboard analytics, quick actions, audit logs
- **Giải pháp:** Recommendation engine, curated list, AI chatbot

---

# 4. Yêu Cầu Chức Năng

## 4.1 Lâu Không Xác Thực (Guest)

### **Use Case 1.1: Xem Danh Mục Khóa Học**
```
Actor: Khác
Precondition: Ứng dụng khởi động, chưa đăng nhập
Main Flow:
1. Hệ thống hiển thị 10 khóa học "Trending"
2. Khác cuộn danh sách, xem thêm khóa học
3. Khác nhấn khóa học → xem chi tiết
4. Chi tiết hiển thị: tên, mô tả, đánh giá, giảng viên
5. Khác nhấn "Thêm vào giỏ" → yêu cầu đăng nhập
Postcondition: Khách hiểu nội dung khóa học cơ bản
```

### **Use Case 1.2: Tìm Kiếm Khóa Học**
```
Actor: Khách
Main Flow:
1. Khách nhập từ khóa "Python" → API search
2. API trả về 50 kết quả, sắp xếp theo relevance
3. Khách lọc: Level=Intermediate, Price<300K
4. Kết quả sắp xếp lại: 12 khóa học matching
5. Khách chọn khóa → xem chi tiết
Postcondition: Khách tìm được khóa học phù hợp
```

## 4.2 Xác Thực & Hồ Sơ (Auth)

### **Use Case 2.1: Đăng Ký Tài Khoản**
```
Actor: Khách → Học viên
Input:
  - Email (unique)
  - Password (min. 8 chars, mix cases + numbers)
  - Full Name
  - Role (Student|Instructor)
Main Flow:
1. Khách nhập email, password, tên, chọn vai trò
2. Ứng dụng validate input lại
3. Firebase Auth tạo user
4. Firestore tạo document user (role, profile)
5. Gửi email xác thực (optional)
6. Hiển thị màn hình chào mừng
Error Handling:
  - Email đã tồn tại → Show error
  - Password yếu → Show requirement
  - Network error → Retry
Postcondition: Tài khoản được tạo, user đăng nhập
```

### **Use Case 2.2: Chỉnh Sửa Hồ Sơ**
```
Actor: Học viên / Giảng viên
Input:
  - Display Name
  - Avatar (image upload)
  - Bio / About
  - (Instructor) Expertise, Qualification, Experience Years
Main Flow:
1. User nhấn "Edit Profile"
2. Form pre-fill với dữ liệu hiện tại
3. User thay đổi, nhấn "Save"
4. Ứng dụng upload ảnh → Cloudinary
5. Firestore cập nhật user document
6. Reload UI với dữ liệu mới
Validation:
  - Tên không rỗng
  - Avatar < 5MB, JPG/PNG
  - Bio < 500 chars
Postcondition: Hồ sơ được cập nhật
```

## 4.3 Khóa Học & Danh Mục (Courses)

### **Use Case 3.1: Tạo Khóa Học (Giảng Viên)**
```
Actor: Giảng viên
Input:
  - Title (required, unique)
  - Description (rich text)
  - Category (select one: Programming, Design, Business, etc.)
  - Level (Beginner|Intermediate|Advanced)
  - Price (0 = free, > 0 = paid)
  - Intro Video URL (Cloudinary)
  - Thumbnail Image
  - Duration estimate (hours)
Main Flow:
1. Giảng viên nhấn "Create Course"
2. Form nhập thông tin khóa học
3. Upload video giới thiệu & thumbnail
4. Nhấn "Save Draft"
5. Firestore tạo course document (status: draft)
6. Course ID returned
7. Giảng viên chuyển sang "Add Lessons"
Validation:
  - Title required, < 200 chars
  - Description required, < 5000 chars
  - Price >= 0
  - Video < 500MB
  - Thumbnail < 5MB
Postcondition: Khóa học draft được tạo, sẵn sàng thêm bài học
```

### **Use Case 3.2: Quản Lý Curriculum (Giảng Viên)**
```
Actor: Giảng viên
Main Flow:
1. Giảng viên chọn khóa học
2. Hiển thị danh sách bài học + quiz
3. Giảng viên kéo-thả để sắp xếp (orderIndex)
4. Thêm bài học:
   - Nhập tiêu đề bài học
   - Nhập nội dung (markdown)
   - Upload tài liệu đính kèm (PDF, DOCX)
   - Firestore tạo lesson
5. Thêm quiz:
   - Chọn "Import from CSV" hoặc "Add manually"
   - CSV format: Question | Option A | B | C | D | Answer(A-D)
   - Parse CSV, validate câu hỏi
   - Firestore tạo quiz + questions
6. Xem preview curriculum
7. Nhấn "Publish" → status = published
Validation:
  - Bài học title required
  - File < 50MB
  - Quiz có >= 5 câu hỏi
  - Mỗi câu >= 2 options
Postcondition: Curriculum hoàn chỉnh, khóa học published
```

## 4.4 Ghi Danh & Thanh Toán (Enrollment & Payment)

### **Use Case 4.1: Thêm Vào Giỏ & Thanh Toán**
```
Actor: Học viên
Main Flow:
1. Học viên xem khóa học đã mua: "Enroll" hoặc "Add to Cart"
2. Nhấn "Add to Cart"
   - Kiểm tra: Đã ghi danh chưa?
   - Nếu YES: Show "Already enrolled"
   - Nếu NO: Add to cart collection
3. Xem giỏ hàng:
   - Hiển thị tất cả items (name, price, quantity)
   - Total price
   - "Proceed to Checkout"
4. Nhấn "Checkout":
   - Hiển thị form:
     * Email ghi danh
     * Phone number
     * Payment method: MoMo
   - Nhấn "Pay with MoMo"
5. Firestore tạo Order (status: pending)
6. Cloud Function gọi MoMo API:
   - Partner code, order info, amount
   - Return MoMo redirect URL
7. App redirect đến MoMo (external)
8. User hoàn thành thanh toán MoMo
9. MoMo callback webhook → Cloud Function
10. Cloud Function xác thực signature, update Order (status: completed)
11. Cloud Function tạo Enrollment (status: active)
12. App nhận notification, redirect tới MyLearning
Validation:
  - Amount > 0
  - Email valid
  - Phone Vietnamese format
Postcondition: Đơn hàng tạo, enrollment active, học viên có quyền học
```

### **Use Case 4.2: Xem Lịch Sử Mua Hàng**
```
Actor: Học viên
Main Flow:
1. Học viên xem "My Orders"
2. Query Order collection (userId = current)
3. Hiển thị danh sách orders:
   - Order ID, date, total amount, status (completed/pending/failed)
   - Số khóa học trong order
   - Invoice button → PDF export
4. Nhấn order → xem chi tiết:
   - Courses trong order
   - Payment method, date
   - Download invoice
Postcondition: Học viên xem rõ lịch sử mua hàng
```

## 4.5 Học Tập (Learning)

### **Use Case 5.1: Học Bài Học**
```
Actor: Học viên (enrolled)
Main Flow:
1. Học viên xem "My Learning" → chọn khóa học
2. Hiển thị curriculum (lessons + quizzes)
3. Nhấn bài học → hiển thị:
   - Title, description
   - Content (markdown rendered)
   - Attachments (PDF links)
   - Mã học tập: "✓" hoàn thành / "(Empty)" chưa
4. Học viên đọc/xem, nhấn "Mark as Complete"
5. Firestore cập nhật Progress:
   - lessonId, completedAt, duration
6. Tính toán course progress:
   - Progress % = (completed lessons / total lessons) * 100
7. UI update progress bar
Postcondition: Bài học đánh dấu hoàn thành, tiến độ cập nhật
```

### **Use Case 5.2: Làm Bài Kiểm Tra**
```
Actor: Học viên (enrolled)
Main Flow:
1. Học viên nhấn quiz → xem thông tin:
   - Tiêu đề, mô tả
   - Thời gian làm (durationMinutes)
   - Điểm đạo hạn (passingScore)
   - Số lần làm: "No attempts yet" / "Attempts: 2/3"
2. Nhấn "Start Quiz" (nếu còn lần)
3. Firestore tạo QuizSession (status: in_progress)
4. Hiển thị quiz UI:
   - Timer (countdown from durationMinutes)
   - Questions (shuffled)
   - Mỗi question: text + 4 options (radio button)
   - "Previous", "Next" buttons, "Finish" button (or auto-submit when time)
5. Học viên chọn answers
   - Tiến độ: "3/20 answered"
6. Nhấn "Finish Quiz":
   - Firestore lưu answers → QuizSession
   - Calculate score:
     * Correct answers / total questions * 100
   - Compare vs passingScore
   - Status = "completed"
   - Return score + feedback
7. Hiển thị result:
   - "Score: 85/100"
   - "Status: PASSED ✓ / FAILED ✗"
   - Option: "Review Answers" (show correct/wrong)
   - Option: "Retake" (if attempts left)
Validation:
  - Time limit enforced (auto-submit if time runs out)
  - Cannot submit empty quiz
Postcondition: Quiz attempt recorded, score stored, progress updated
```

## 4.6 Thông Báo (Notifications)

### **Use Case 6.1: Gửi Thông Báo**
```
Actor: System
Event Triggers: (All async via Cloud Functions)

Type 1: PURCHASE_SUCCESS
├─ Trigger: Order payment completed
├─ Content: "Chúc mừng bạn đã mua khóa X!"
├─ Deep link: "course/123"
└─ Schedule: Immediately

Type 2: STUDY_REMINDER
├─ Trigger: User hasn't studied in 3 days
├─ Content: "Hãy tiếp tục học khóa X (tiến độ 45%)"
├─ Deep link: "course/123"
└─ Schedule: Daily 9 AM

Type 3: NEW_LESSON
├─ Trigger: Instructor publishes lesson in enrolled course
├─ Content: "Giảng viên đã thêm bài học X vào khóa Y"
├─ Deep link: "lesson/456"
└─ Schedule: Immediately

Type 4: QUIZ_AVAILABLE
├─ Trigger: Instructor publishes quiz
├─ Content: "Bài kiểm tra X đã sẵn sàng"
├─ Deep link: "quiz/789"
└─ Schedule: Immediately

Type 5: COURSE_UPDATED
├─ Trigger: Course info/curriculum changed
├─ Content: "Khóa X vừa được cập nhật"
├─ Deep link: "course/123"
└─ Schedule: Immediately

Sending:
└─ Firebase Cloud Messaging (FCM)
   ├─ To: User FCM tokens (stored in Firestore)
   ├─ Format: JSON with title, body, deepLink
   └─ Retry: 3 times if failed

Storage:
└─ Notification collection:
   ├─ userId, type, title, body, deepLink
   ├─ createdAt, readAt
   └─ For UI history
```

## 4.7 AI Chatbot (OpenRouter + Qwen)

### **Use Case 7.1: Chat Interaktif**
```
Actor: User (authenticated)
Main Flow:
1. User muka "Chatbot" tab / screen
2. Firestore query/create ChatSession (current user)
3. Hiển thị chat UI:
   - Chat history (scrollable)
   - Input field + "Send" button
4. User nhập: "Tôi muốn học Python"
5. Kiểm tra nếu chưa có session → tạo mới
6. Repository gọi OpenRouter API (async):
  - POST message + context hội thoại + tools definitions
  - Model mặc định: `qwen/qwen3.6-plus-preview:free` (fallback: Gemma)
7. OpenRouter model processes:
  - Hiểu intent
  - Gọi function (ví dụ: `search_courses`)
  - App thực thi function thật (Firestore/repositories)
  - Trả kết quả tool về model để tổng hợp câu trả lời
8. Repository trả message + structured metadata (nếu có)
9. App displays:
   - Bot message
   - Structured content if present:
     * search_courses → Display course cards (image, title, price)
    * recommend_new_courses / get_popular_courses → Display recommended list
     * get_course_details → Display course detail card
     * add_course_to_cart → Show confirmation "Added X to cart"
10. Firestore saves ChatMessage:
    - sessionId, sender (user|bot), content, type (TEXT|COURSE_CARD|etc.)
    - timestamp, metadata (function results)
Postcondition: User gets conversational help, proper actions executed
```

## 4.8 Phân Tích (Analytics)

### **Use Case 8.1: Xem Thống Kê Khóa Học (Giảng Viên)**
```
Actor: Giảng viên
Main Flow:
1. Giảng viên nhấn "Analytics" → chọn khóa học
2. Hiển thị dashboard:

   ┌─ Revenue ─────────────────────┐
   │ Total: 50M VND                │
   │ This month: 15M VND (+20%)    │
   │ Avg per order: 500K VND       │
   └───────────────────────────────┘

   ┌─ Enrollment Trend (Chart) ────┐
   │ Last 7 days: 8/3-1/4          │
   │ Y-axis: # enrollments         │
   │ Line chart: 10→15→20→25...    │
   └───────────────────────────────┘

   ┌─ Chi tiết Enrollment ─────────┐
   │ Total enrollments: 250         │
   │ Active (learning): 180         │
   │ Completed: 45                  │
   │ Avg completion rate: 35%       │
   └───────────────────────────────┘

   ┌─ Quiz Statistics ─────────────┐
   │ Avg score: 72 / 100           │
   │ Pass rate: 65%                │
   │ # attempts: 450               │
   └───────────────────────────────┘

   ┌─ Reviews ─────────────────────┐
   │ Avg rating: 4.2 / 5 ⭐        │
   │ # reviews: 48                 │
   └───────────────────────────────┘

3. Filter by time range: Last 7 days | Last 30 | Last 90 | All time
   - Data recalculates
4. Giảng viên export → PDF report
Postcondition: Giảng viên biết chi tiết hiệu suất khóa học
```

## 4.9 Quản Lý (Admin)

### **Use Case 9.1: Duyệt Yêu Cầu Giảng Viên**
```
Actor: Admin (người dùng role ADMIN)
Main Flow:
1. Admin mở "Instructor Approvals" screen
2. Hiển thị danh sách pending instructor requests:
   - Tên, email, chuyên môn, ngày apply
   - Nút "Approve" / "Reject"
3. Admin nhấn "Approve":
   - Firestore update user: status = APPROVED, role = INSTRUCTOR
   - Gửi notification cho giảng viên
   - Move khỏi pending list
4. Hoặc nhấn "Reject":
   - Dialog nhập lý do từ chối
   - Update user: status = REJECTED, rejectionReason
   - Gửi notification với lý do
Postcondition: Yêu cầu giảng viên được xử lý
```

### **Use Case 9.2: Quản Lý Người Dùng**
```
Actor: Admin
Main Flow:
1. Admin mở "Users Management"
2. Hiển thị danh sách toàn bộ users:
   - Email, role (STUDENT|INSTRUCTOR), join date, status
   - Search, filter by role/status
3. Admin nhấn user → Chi tiết:
   - Hồ sơ (email, name, avatar, bio)
   - Khóa học enrolled (nếu student) / tạo (nếu instructor)
   - Lịch sử hành động
   - Nút "Ban" / "Unban"
4. Admin nhấn "Ban":
   - Firestore update: banned = true, bannedReason
   - User không thể đăng nhập
   - Logout tất cả sessions
Postcondition: Người dùng bị ban từ hệ thống
```

### **Use Case 9.3: Quản Lý Khóa Học**
```
Actor: Admin
Main Flow:
1. Admin mở "Courses Management"
2. Hiển thị danh sách tất cả courses:
   - Title, instructor, status (PUBLISHED|DRAFT|ARCHIVED), enrollments
   - Filter: status, category
3. Admin nhấn course → Chi tiết + Actions:
   - Info (title, description, price, level)
   - Curriculum (lessons, quizzes, count)
   - Enrollments (trending chart)
   - Nút: "Archive", "Delete", "Hide from store"
4. Admin nhấn "Archive":
   - Update status = ARCHIVED
   - Course không hiển thị ở public listing
   - Students vẫn access (nếu enrolled)
5. Admin nhấn "Delete":
   - Xác nhận
   - Xóa course (soft delete: update status)
   - Notification tới instructor
Postcondition: Khóa học được quản lý
```

### **Use Case 9.4: Quản Lý Danh Mục**
```
Actor: Admin
Main Flow:
1. Admin mở "Categories Management"
2. Hiển thị danh sách categories:
   - Name, # courses, actions
3. Admin nhấn "Add Category":
   - Input: name, icon (upload)
   - Firestore tạo Category document
4. Admin nhấn category → Chi tiết + Edit:
   - Name, icon upload
   - Nhấn "Save"
   - Firestore update
5. Admin nhấn "Delete":
   - Xác nhận (nếu category có courses, warning)
   - Firestore delete category
   - Courses được ungrouped
Postcondition: Danh mục được quản lý
```

### **Use Case 9.5: Tạo Tài Khoản Giảng Viên**
```
Actor: Admin
Main Flow:
1. Admin mở "Create Instructor Account"
2. Form nhập:
   - Email, password, name
   - Expertise, qualification, experience years
3. Admin nhấn "Create":
   - Firebase Auth tạo user
   - Firestore tạo user document: role = INSTRUCTOR, status = ACTIVE
   - Email gửi credentials (optional)
   - Skip pending approval
4. Notification: Giảng viên có thể đăng nhập ngay
Postcondition: Tài khoản giảng viên được tạo và active
```

### **Use Case 9.6: Xem Dashboard Admin**
```
Actor: Admin
Main Flow:
1. Admin mở "Dashboard"
2. Hiển thị tóm tắt:
   - # users (total, students, instructors, banned)
   - # courses (published, draft, archived)
   - # orders (amount, success rate)
   - Pending instructor approvals (count)
   - Recent activity log
3. Admin nhấn card → Chi tiết page
Postcondition: Admin có overview toàn hệ thống
```

---

# 5. Yêu Cầu Non-Functional

## 5.1 Hiệu Năng (Performance)

| Tiêu chí | Target | Method |
|----------|--------|--------|
| **App Startup** | < 3 seconds (cold start) | Google Play Console |
| **Screen Load** | < 2 seconds (average) | Okhttp interceptor logging |
| **API Request** | < 1 second (p95) | Firestore latency monitoring |
| **Database Query** | < 500ms | Firestore Monitoring |
| **Image Load** | < 1 second | Cloudinary delivery stats |
| **Quiz Render** | < 1.5 seconds | UI benchmark tests |
| **Memory Usage** | < 200MB (average) | Android Profiler |
| **Battery Impact** | 5% reduction over 2 hours typical usage | Battery historian |

## 5.2 Khả Năng Mở Rộng (Scalability)

| Component | Scaling Strategy | Limits |
|-----------|------------------|--------|
| **Firestore Database** | Partitionioning by userId + courseId | 100K concurrent users |
| **Cloud Functions** | Auto-scale 1000 instances | 10K/sec concurrent invocations |
| **Cloudinary CDN** | Global CDN, 180+ data centers | Unlimited bandwidth |
| **OpenRouter API (Qwen)** | API quota per key/model | Theo gói OpenRouter |
| **MoMo API** | Merchant tier scaling | Contact MoMo support |

## 5.3 Bảo Mật (Security)

### **Authentication & Authorization**
```
Firebase Auth:
├─ Email/password authentication
├─ Session tokens with 1-hour expiry
├─ Refresh tokens for long-lived sessions
└─ Client-side token caching + refresh logic

Firestore Security Rules:
├─ User can read own user document
├─ User can write own profile (name, avatar)
├─ User can read enrolled courses only
├─ Instructor can write own courses
├─ Public read on published courses/reviews
└─ Admin bypass (future)

API Layer:
├─ All APIs authenticated (Firebase token)
├─ Rate limiting: 1000 req/hour per user
├─ Input validation on all endpoints
└─ SQL injection prevention (N/A, using Firestore)
```

### **Data Encryption**
```
At Rest:
├─ Firestore: Google-managed encryption
├─ Images: Cloudinary encryption
└─ Sensitive data: Encrypt at app layer before upload (future)

In Transit:
├─ All APIs: HTTPS/TLS 1.2+
├─ Certificate pinning: Firebase cert pinning
└─ Secure channel to MoMo (server-side only)
```

### **PII Protection**
```
Sensitive Data:
├─ Passwords: Firebase hashed (bcrypt equivalent)
├─ Email: encrypted on export/backup
├─ Phone: encrypted storage
├─ Bank info: encrypted, visible only to owner
└─ Payment info: MoMo handles (PCI-DSS)

Handling:
├─ No logs of sensitive data
├─ Firebase Realtime Database: Not used for PII
├─ Compliance: GDPR partial (user can delete account)
└─ Retention: Keep until user deletion (no auto-expiry yet)
```

## 5.4 Keandalan & Availability (Reliability)

| Tiêu chí | Target | SLA |
|----------|--------|-----|
| **Uptime** | 99.9% | 43.2 minutes/month |
| **Crash-Free Users** | > 99.5% | Firebase Crashlytics threshold |
| **Data Integrity** | 100% | Firestore transactions + backups |
| **Disaster Recovery** | RTO 4 hours, RPO 1 hour | Firestore auto-backup |
| **Incident Response** | < 30 min acknowledge | PagerDuty/Slack alerts |

### **Error Handling**
```
Network Errors:
├─ Retry with exponential backoff (1s, 2s, 4s, 8s)
├─ User-friendly error messages
├─ Offline mode: show cached data with "Loading..." badge
└─ Auto-sync when online

Firebase Errors:
├─ Quota exceeded: Show "Service busy" + suggest retry later
├─ Permission denied: Show "Unauthorized" + logout
├─ Network error: Show "No connection" + offline indicator
└─ Unknown error: Log to Crashlytics + show generic message

Payment Errors:
├─ MoMo timeout: Show "Payment pending, check status" + retry
├─ Merchant error: Show specific MoMo error message
├─ DB error: Retry transaction
└─ Critical: Refund initiated automatically
```

## 5.5 Compatibility & Platforms

| Requirement | Specification |
|----------|----------|
| **Min Android Version** | 8.0 (API 26) |
| **Target Android Version** | 14.0 (API 34) |
| **Min RAM** | 2GB |
| **Min Internal Storage** | 100MB |
| **Screen Sizes** | 4.5" - 7" phones, tablets partial |
| **Orientation** | Portrait primary, landscape support |
| **Language** | Vietnamese (future: English) |
| **Testing Devices** | Samsung A12, A50; Google Pixel 4a; Redmi Note |

## 5.6 Maintainability & Code Quality

| Aspect | Standard |
|--------|----------|
| **Code Structure** | MVVM + Repository pattern |
| **Language** | Kotlin 100%, no Java mixing |
| **Compose** | Jetpack Compose 1.6+ |
| **Dependencies** | Latest stable, quarterly updates |
| **Documentation** | Docstring for all public functions |
| **Test Coverage** | 70%+ unit + integration tests |
| **Code Review** | 2 approvals before merge |
| **Build Size** | < 80MB APK (minified, ProGuard) |

---

# 6. Luồng Người Dùng & Use Cases

## 6.1 User Journey Map - Học Viên Mới

```
Stage 1: DISCOVERY
├─ Day 1: Install app → Browse courses (trending/search)
├─ Interaction: Search "Python", read reviews
├─ Pain point: Too many choices
└─ Solution: AI recommends top 5 courses

Stage 2: DECISION
├─ Day 1-2: Add course to cart, read details
├─ Interaction: Compare price, instructor, watch intro video
├─ Pain point: Unsure if worth it
└─ Solution: Chat with AI bot "Is this good for beginner?"

Stage 3: PURCHASE
├─ Day 2: Checkout with MoMo
├─ Interaction: Enter email, phone, click pay
├─ Pain point: Payment failure
└─ Solution: Clear error, retry option, support link

Stage 4: ONBOARDING
├─ Day 3-5: View course, skim lessons, 30% completion
├─ Interaction: Learn 2 lessons, attempt quiz
├─ Pain point: Motivation drop
└─ Solution: Push notification "You're 30% done, keep going!"

Stage 5: ENGAGEMENT (Weeks 2-4)
├─ Steady progress, 60-80% completion
├─ Interaction: Regular study sessions, pass quizzes
├─ Solution: Progress bar motivation, badge system
└─ Outcome: High satisfaction

Stage 6: COMPLETION (Week 4+)
├─ User completes course (100%)
├─ Interaction: View final score, download certificate (future)
├─ Solution: AI recommend related courses → add to cart
└─ Outcome: Repeat purchase likelihood +70%
```

## 6.2 Critical User Flows (Step-by-Step)

### **Flow A: End-to-End Purchase → Learning**

```
1. USER DISCOVERY
   └─ [Home] → Browse/Search → [Course Detail]

2. ADD TO CART
   └─ [Course Detail] "Add to Cart" → [Cart View]

3. CHECKOUT
   └─ [Cart] "Checkout" → [Checkout Form] (Email, Phone)
      → "Pay with MoMo" → [MoMo App/Browser]

4. PAYMENT (External)
   └─ [MoMo] User enters OTP → Successful

5. ORDER COMPLETION
   └─ [MoMo] Callback → [Cloud Function] Validate
      → Create Order + Enrollment → [Firebase Notification]

6. APP RECEIVES CONFIRMATION
   └─ [App] Receives FCM → Show notification
      → Redirect to [My Learning]

7. LEARNING FLOW
   └─ [My Learning] Select Course → [Curriculum]
      → [Lesson View] → [Quiz] → [Results]

8. PROGRESS TRACKING
   └─ Real-time updates: progress bar, badges, stats
```

### **Flow B: AI Chatbot Question → Action**

```
1. USER OPENS CHATBOT
   └─ [Chatbot] "Hi, how can I help?"

2. USER ASKS QUESTION
   └─ "Show me beginner Python courses"
   
3. CHATBOT PROCESSES
  └─ OpenRouter (Qwen) processes with system prompt + tool definitions
      → Detects function: search_courses
    → Passes tool call to app repository

4. APP REPOSITORY EXECUTES
   └─ Query Firestore: courses with category=Programming, level=Beginner
      → Returns 20 matching courses
  → Pass back to model as tool result

5. MODEL FORMATS RESPONSE
   └─ Human-readable message: "I found 20 beginner Python courses"
      + Structured data: course cards (image, title, rating, price)

6. APP RENDERS
   └─ Display message + course cards
      → User can scroll, click card to view detail
      → Or "Add to Cart" directly

7. USER ACTION
   └─ "Add X to cart" → Cart updated, success message
      → "Proceed to checkout?" → [Checkout Flow]
```

---

# 7. Mô Hình Dữ Liệu

## 7.1 Firestore Collections & Documents

### **Collection: users**
```firestore
/users/{userId}
├─ email: string (unique)
├─ displayName: string
├─ avatar: string (Cloudinary URL)
├─ role: string (STUDENT|INSTRUCTOR|ADMIN)
├─ bio: string
├─ banned: boolean (default: false)
├─ bannedReason: string (nullable)
├─ createdAt: timestamp
├─ updatedAt: timestamp
├─ lastLoginAt: timestamp
└─ (Instructor fields)
   ├─ expertise: string
   ├─ qualification: string
   ├─ experienceYears: integer (> 0)
   ├─ bankAccountHolder: string
   ├─ bankAccountNumber: string
   ├─ bankName: string
   └─ bankAccountVerified: boolean
```

### **Collection: courses**
```firestore
/courses/{courseId}
├─ title: string (required, unique)
├─ description: string (rich text)
├─ category: string (enum)
├─ level: string (BEGINNER|INTERMEDIATE|ADVANCED)
├─ price: integer (VND, >= 0)
├─ instructorId: string (doc ref to users)
├─ instructorName: string (denormalized for perf)
├─ thumbnail: string (Cloudinary URL)
├─ introVideoUrl: string (Cloudinary video)
├─ status: string (DRAFT|PUBLISHED|ARCHIVED)
├─ enrollmentCount: integer (real-time)
├─ averageRating: number (0-5, 2 decimals)
├─ reviewCount: integer
├─ createdAt: timestamp
├─ updatedAt: timestamp
├─ publishedAt: timestamp (nullable)
└─ metadata
   ├─ durationEstimate: integer (hours)
   ├─ totalLessons: integer
   ├─ totalQuizzes: integer
   └─ completionRate: number (0-100)
```

### **Collection: lessons**
```firestore
/lessons/{lessonId}
├─ courseId: string (doc ref)
├─ title: string
├─ content: string (markdown)
├─ attachments: array of strings (file URLs)
├─ orderIndex: integer (0-based)
├─ createdAt: timestamp
├─ updatedAt: timestamp
└─ metadata
   ├─ estimatedMinutes: integer
   └─ videoUrl: string (nullable)
```

### **Collection: quizzes**
```firestore
/quizzes/{quizId}
├─ courseId: string (doc ref)
├─ title: string
├─ description: string
├─ orderIndex: integer
├─ durationMinutes: integer (default: 30)
├─ passingScore: integer (0-100, default: 80)
├─ maxAttempts: integer (default: 3)
├─ questions: array of {
│  ├─ id: string (UUID)
│  ├─ text: string
│  ├─ options: array of 4 strings
│  └─ correctAnswerIndex: integer (0-3)
│  }
├─ createdAt: timestamp
└─ updatedAt: timestamp
```

### **Collection: enrollments**
```firestore
/enrollments/{enrollmentId} [Format: userId_courseId]
├─ userId: string
├─ courseId: string
├─ status: string (ACTIVE|COMPLETED|DROPPED)
├─ enrolledAt: timestamp
├─ completedAt: timestamp (nullable)
├─ progress: integer (0-100)
├─ lastAccessedAt: timestamp
└─ metadata
   ├─ source: string (PURCHASE|GIFT|PROMO)
   └─ completedLessonCount: integer
```

### **Collection: orders**
```firestore
/orders/{orderId}
├─ userId: string
├─ items: array of {
│  ├─ courseId: string
│  ├─ courseName: string
│  ├─ price: integer
│  └─ courseRefPath: string
│  }
├─ totalAmount: integer (VND)
├─ status: string (PENDING|COMPLETED|FAILED|REFUNDED)
├─ paymentMethod: string (MOMO)
├─ transactionId: string (MoMo reference)
├─ email: string
├─ phone: string
├─ createdAt: timestamp
├─ completedAt: timestamp (nullable)
└─ metadata
   ├─ userAgent: string
   └─ ipAddress: string
```

### **Collection: progress**
```firestore
/progress/{progressId} [Format: userId_courseId_lessonId]
├─ userId: string
├─ courseId: string
├─ lessonId: string
├─ completedAt: timestamp
├─ durationMinutes: integer
└─ notes: string (optional)
```

### **Collection: quizProgress**
```firestore
/quizProgress/{sessionId}
├─ userId: string
├─ quizId: string
├─ courseId: string
├─ attemptNumber: integer
├─ score: integer (0-100)
├─ status: string (IN_PROGRESS|COMPLETED)
├─ answers: array of { questionId, selectedIndex }
├─ createdAt: timestamp
├─ completedAt: timestamp (nullable)
└─ duration: integer (seconds)
```

### **Collection: reviews**
```firestore
/reviews/{reviewId}
├─ userId: string
├─ courseId: string
├─ rating: integer (1-5)
├─ comment: string
├─ helpful: integer (default: 0)
├─ createdAt: timestamp
├─ updatedAt: timestamp
└─ metadata
   ├─ verified: boolean (purchased before)
   └─ courseCompletion: integer (%)
```

### **Collection: notifications**
```firestore
/notifications/{notificationId}
├─ userId: string
├─ type: string (PURCHASE_SUCCESS|STUDY_REMINDER|etc.)
├─ title: string
├─ body: string
├─ deepLink: string (nullable)
├─ data: map (flexible payload)
├─ createdAt: timestamp
├─ readAt: timestamp (nullable)
├─ sentAt: timestamp (nullable)
└─ fcmTokens: array of strings (sent to which tokens)
```

### **Collection: chatSessions**
```firestore
/chatSessions/{sessionId}
├─ userId: string
├─ createdAt: timestamp
├─ updatedAt: timestamp
├─ messageCount: integer
└─ metadata
   ├─ lastMessageText: string (preview)
   ├─ isArchived: boolean
   └─ title: string (auto-generated or user-named)
```

### **Collection: chatMessages**
```firestore
/chatMessages/{messageId}
├─ sessionId: string (doc ref)
├─ userId: string
├─ sender: string (USER|BOT)
├─ content: string
├─ messageType: string (TEXT|COURSE_CARD|COURSE_LIST|etc.)
├─ metadata: map {
│  ├─ functionCalls: array (if function called)
│  ├─ courseIds: array (if related to courses)
│  └─ customData: map
│  }
├─ createdAt: timestamp
└─ updatedAt: timestamp
```

## 7.2 Data Relationships & Denormalization

```
User
 ├─ 1─ to ─N → Enrollments
 ├─ 1─ to ─N → Orders
 ├─ 1─ to ─N → Reviews
 ├─ 1─ to ─N → QuizProgress
 ├─ 1─ to ─N → ChatSessions
 └─ 1─ to ─N → Notifications

Course
 ├─ 1─ to ─N → Lessons (ordered by orderIndex)
 ├─ 1─ to ─N → Quizzes (ordered by orderIndex)
 ├─ 1─ to ─N → Enrollments
 ├─ 1─ to ─N → Orders (items)
 ├─ 1─ to ─N → Reviews
 └─ 1─ to ─1 → User (instructorId)

Denormalized Fields (for performance):
├─ courses.instructorName (avoid extra lookup)
├─ enrollments.courseId (composite key for queries)
├─ orders.courseName (snapshot at purchase time)
└─ reviews.verified (computed from enrollment)
```

---

# 8. Kiến Trúc & Tích Hợp

## 8.1 Android App Architecture

```
┌──────────────────────────────────────────────────┐
│           Presentation Layer (UI)                │
│         Jetpack Compose Components               │
│  ├─ Screens (Home, Courses, MyLearning, etc)   │
│  ├─ Components (Cards, Buttons, Dialogs)       │
│  └─ Theme & Resources                           │
└──────────────────────────────────────────────────┘
                      ↓ ↑
┌──────────────────────────────────────────────────┐
│         Application Layer (ViewModel)            │
│  ├─ State Management (UiState, Flow)           │
│  ├─ Events (One-time, SnackBars)               │
│  ├─ Lifecycle Aware (collectAsStateWithLifecycle)
│  └─ Coroutine Scoped (viewModelScope)           │
└──────────────────────────────────────────────────┘
                      ↓ ↑
┌──────────────────────────────────────────────────┐
│         Data Layer (Repository Pattern)          │
│  ├─ Repositories:                               │
│  │  ├─ CourseRepository (CRUD courses)          │
│  │  ├─ EnrollmentRepository (enrollment logic)  │
│  │  ├─ PaymentRepository (orders, payments)    │
│  │  ├─ CurriculumRepository (lesson, quiz)     │
│  │  ├─ InstructorAnalyticsRepository           │
│  │  ├─ RecommendationRepository (VSM engine)   │
│  │  ├─ OpenRouterChatbotRepository (AI)        │
│  │  └─ ...                                      │
│  └─ Result Wrapper (ResultState<T>)             │
└──────────────────────────────────────────────────┘
                      ↓ ↑
┌──────────────────────────────────────────────────┐
│        Backend Services (Firebase + External)    │
│  ├─ Firestore (Real-time DB)                   │
│  ├─ Firebase Auth (Authentication)              │
│  ├─ Cloud Messaging (Push notifications)        │
│  ├─ Cloudinary (Image/Video CDN)               │
│  ├─ OpenRouter API (Qwen/Gemma Chatbot)        │
│  ├─ MoMo API (Payment gateway)                 │
│  └─ Cloud Functions (Lambdas, webhooks)        │
└──────────────────────────────────────────────────┘
```

## 8.2 API Integration Points

### **Firebase Firestore**
```kotlin
// Read (Listening)
db.collection("courses")
  .whereEqualTo("status", "PUBLISHED")
  .orderBy("enrollmentCount", Query.Direction.DESCENDING)
  .limit(10)
  .addSnapshotListener { querySnapshot ->
    val courses = querySnapshot?.toObjects(Course::class.java)
  }

// Write (Transactions)
db.runTransaction { txn ->
  val courseRef = db.collection("courses").document(courseId)
  val course = txn.get(courseRef).toObject(Course::class.java)
  if (course != null) {
    txn.update(courseRef, "enrollmentCount", course.enrollmentCount + 1)
  }
}

// Batch Operations
db.batch { batch ->
  batch.set(enrollmentRef, enrollment)
  batch.update(courseRef, "enrollmentCount", FieldValue.increment(1))
  batch.update(

userRef, "lastModified", FieldValue.serverTimestamp())
}
```

### **Firebase Authentication**
```kotlin
// Register
FirebaseAuth.getInstance().createUserWithEmailAndPassword(email, password)
  .addOnCompleteListener { task ->
    if (task.isSuccessful) {
      val userId = task.result.user?.uid
      // Create user profile in Firestore
    }
  }

// Login
FirebaseAuth.getInstance().signInWithEmailAndPassword(email, password)
  .addOnCompleteListener { task ->
    if (task.isSuccessful) {
      val user = task.result.user
      // Navigate to main screen
    }
  }

// Get Current Token
FirebaseAuth.getInstance().currentUser?.getIdToken(true)?.addOnCompleteListener { task ->
  val token = task.result?.token // Use for API requests
}
```

### **OpenRouter API (In-app Repository)**
```kotlin
// OpenRouter request payload
val request = OpenRouterRequest(
  model = "qwen/qwen3.6-plus-preview:free",
  messages = conversation,
  tools = tools,
  toolChoice = "auto"
)

// Call OpenRouter
val response = openRouterService.chat(
  authorization = "Bearer $apiKey",
  request = request
)

// Execute returned tool calls in app repository
response.choices.firstOrNull()?.message?.toolCalls.orEmpty().forEach { toolCall ->
  val args = parseFunctionArgs(toolCall.function.arguments)
  val result = executeFunctionCall(userId, toolCall.function.name, args)
  conversation += OpenRouterMessage(
    role = "tool",
    toolCallId = toolCall.id,
    name = toolCall.function.name,
    content = JSONObject(result).toString()
  )
}
```

### **MoMo Payment (Cloud Function)**
```javascript
// Cloud Function: createMomoOrder
exports.createMomoOrder = functions.https.onCall(async (data, context) => {
  const { orderId, amount, email } = data;
  
  // Call MoMo API
  const response = await fetch("https://test-payment.momo.vn/v1/payment/confirm", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      partnerCode: "MOMOXXXXXX",
      accessKey: process.env.MOMO_ACCESS_KEY,
      requestId: orderId,
      amount: amount,
      orderId: orderId,
      orderInfo: `Order ${orderId}`,
      returnUrl: `${process.env.APP_URL}/payment/return`,
      notifyUrl: `${process.env.CLOUD_FUNCTION_URL}/momoWebhook`,
      // ... signed request signature
    }),
  });
  
  const result = await response.json();
  
  // Store payUrl in Firestore
  await admin.firestore().collection("orders").doc(orderId).update({
    payUrl: result.payUrl,
    status: "PENDING",
  });
  
  return { payUrl: result.payUrl, requestId: result.requestId };
});

// Cloud Function: momoWebhook (handles payment callback)
exports.momoWebhook = functions.https.onRequest(async (req, res) => {
  const { orderId, amount, transId, resultCode } = req.body;
  
  // Verify signature
  const isValid = verifyMomoSignature(req.body, process.env.MOMO_SECRET_KEY);
  if (!isValid) return res.status(400).send("Invalid signature");
  
  // Update Order status
  if (resultCode === 0) {
    // Payment successful
    await admin.firestore().collection("orders").doc(orderId).update({
      status: "COMPLETED",
      transactionId: transId,
      completedAt: admin.firestore.FieldValue.serverTimestamp(),
    });
    
    // Create enrollments for items in order
    const order = await admin.firestore().collection("orders").doc(orderId).get();
    for (const item of order.data().items) {
      const enrollmentId = `${order.data().userId}_${item.courseId}`;
      await admin.firestore().collection("enrollments").doc(enrollmentId).set({
        userId: order.data().userId,
        courseId: item.courseId,
        status: "ACTIVE",
        enrolledAt: admin.firestore.FieldValue.serverTimestamp(),
      });
    }
    
    // Send notification to user
    await sendNotification(order.data().userId, "PURCHASE_SUCCESS", {
      items: order.data().items,
    });
    
    res.status(200).send("OK");
  } else {
    // Payment failed
    await admin.firestore().collection("orders").doc(orderId).update({
      status: "FAILED",
    });
    res.status(200).send("OK");
  }
});
```

### **Cloudinary (Image Upload)**
```kotlin
// In Repository
suspend fun uploadImage(uri: Uri, folder: String): ResultState<String> = withContext(Dispatchers.IO) {
  return@withContext try {
    val bitmap = MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
    val stream = ByteArrayOutputStream()
    bitmap.compress(Bitmap.CompressFormat.JPEG, 85, stream)
    
    // Call Cloudinary API (might use wrapper library)
    val cloudinary = CloudinaryManager() // Wrapper
    val response = cloudinary.upload(
      stream.toByteArray(),
      folder = folder,
      publicId = "IMG_${System.currentTimeMillis()}"
    )
    
    ResultState.Success(response.secureUrl)
  } catch (e: Exception) {
    ResultState.Error(e.message ?: "Upload failed")
  }
}
```

---

# 9. Bảo Mật & Quyền Riêng Tư

## 9.1 Firestore Security Rules

```
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    
    // Users collection
    match /users/{userId} {
      allow read: if request.auth.uid == userId || isPublicProfile(resource) || isAdmin(request.auth.uid);
      allow write: if request.auth.uid == userId && hasValidUserData(request.resource.data) && !isChangingRole(resource, request.resource.data);
      allow update: if request.auth.uid == userId;
      allow delete: if false; // Only admin or server-side can delete
    }
    
    // Courses collection (public read, instructor write)
    match /courses/{courseId} {
      allow read: if isPublished(resource) || isInstructor(resource, request.auth.uid);
      allow create: if isInstructor(resource, request.auth.uid);
      allow update, delete: if isInstructor(resource, request.auth.uid);
    }
    
    // Lessons (read if enrolled + owner)
    match /lessons/{lessonId} {
      allow read: if canAccessLesson(lessonId, request.auth.uid);
      allow write: if isLessonOwner(lessonId, request.auth.uid);
    }
    
    // Enrollments (read/write owned, enrolled)
    match /enrollments/{enrollmentId} {
      allow read: if isEnrollmentOwner(enrollmentId, request.auth.uid);
      allow create: if isEnrollmentOwner(enrollmentId, request.auth.uid);
      allow update: if isSelfEnrollment(enrollmentId, request.auth.uid) || isDisputeResolution();
    }
    
    // Orders (read owned, create for self)
    match /orders/{orderId} {
      allow read: if isOrderOwner(orderId, request.auth.uid);
      allow create: if isOrderOwner(orderId, request.auth.uid) &&  validOrderData(request.resource.data);
      allow update: if false; // Server-side only (Cloud Functions)
    }
    
    // Reviews (read public, write owned)
    match /reviews/{reviewId} {
      allow read: if true;
      allow create: if isReviewOwner(reviewId, request.auth.uid) && hasEnrolled(request.resource.data.courseId);
      allow delete: if isReviewOwner(reviewId, request.auth.uid);
    }
    
    // Chat sessions (read/write owned)
    match /chatSessions/{sessionId} {
      allow read, write: if isChatSessionOwner(sessionId, request.auth.uid);
    }
    
    match /chatMessages/{messageId} {
      allow read: if isChatSessionOwner(getSessionId(messageId), request.auth.uid);
      allow create: if isMessageOwner(messageId, request.auth.uid) && sender == 'USER';
      allow update: if false;
    }
    
    // Admin-only collections
    match /admin/{document=**} {
      allow read, write: if isAdmin(request.auth.uid);
    }
    
    // Audit logs (admin read-only)
    match /auditLogs/{logId} {
      allow read: if isAdmin(request.auth.uid);
      allow write: if false; // Server-side only
    }
  }
  
  // Helper functions
  function isPublished(resource) {
    return resource.data.status == 'PUBLISHED';
  }
  
  function isInstructor(resource, uid) {
   
  
  function isChangingRole(oldData, newData) {
    return oldData.role != newData.role;
  } return resource.data.instructorId == uid;
  }
  
  function isAdmin(uid) {
    return exists(/databases/$(database)/documents/users/$(uid)) && 
           get(/databases/$(database)/documents/users/$(uid)).data.role == 'ADMIN';
  }
  
  function isEnrollmentOwner(enrollmentId, uid) {
    return enrollmentId.split('_')[0] == uid;
  }
  
  // ... more helper functions
}
```

## 9.2 Rate Limiting & DDoS Protection

```
Cloud Armor Policy (if using Cloud CDN):
├─ Rate limiting: 100 requests/minute per IP
├─ Geographic restrictions: None (global)
├─ WAF rules: Basic SQL injection, XSS detection
└─ Fallback: Firebase pricing tier limits (if attacked)

Firebase Limits:
├─ Write rate: 1.5 MB/sec
├─ Read rate: 800,000 reads/sec
├─ Concurrent connections: 100,000 per database
└─ Document write: 25 updates/sec per document
```

## 9.3 Sensitive Data Handling

```
Do NOT Store in Firestore (Plain):
├─ ❌ Passwords (use Firebase Auth hash)
├─ ❌ Credit cards (MoMo handles)
├─ ❌ SSN/ID numbers (not applicable, Vietnam)
└─ ❌ Medical/health info (out of scope)

Encryption Strategy:
├─ Passwords: Firebase-managed bcrypt
├─ Tokens: In-app secure storage (EncryptedSharedPreferences)
├─ Phone: Store last 4 digits only, full only in transient memory
├─ Email: Clear-text Firestore (no PII law in Vietnam yet), HTTPS in transit
└─ Bank info: Encrypted column at application layer + Firestore encryption

Audit Logging:
├─ All write operations: Cloud Audit Logs
├─ Sensitive access: Flag for manual review
├─ Compliance: GDPR partial (Vietnamese users mainly)
└─ Retention: 90 days default, delete on request
```

---

# 10. Tiêu Chí Chất Lượng & Thử Nghiệm

## 10.1 Quality Gates

| Tiêu Chí | Threshold | Check tool |
|----------|-----------|-----------|
| Code Coverage | ≥ 70% | Jacoco + GitHub Actions |
| Crash-Free Users | ≥ 99.5% | Firebase Crashlytics |
| ANR Rate | < 0.1% | Firebase Vitals |
| API Error Rate | < 0.5% | Cloud Logging |
| Test Pass Rate | 100% | Jest/Android JUnit4 |
| Code Review | 2 approvals | GitHub + Reviewers |
| Performance (cold startup) | < 3s | Baseline profiler |
| Build Size | < 80 MB | Gradle analyzer |

## 10.2 Testing Strategy

### **Unit Tests** (70% target)
```kotlin
// Example test
@Test
fun testEnrollmentIdFormat() {
  val userId = "USER123"
  val courseId = "COURSE456"
  val expected = "USER123_COURSE456"
  
  val result = buildEnrollmentId(userId, courseId)
  
  assertEquals(expected, result)
}

@Test
fun testCourseProgressCalculation() {
  val completedLessons = 3
  val total Lessons = 10
  val expected = 30
  
  val result = calculateProgress(completedLessons, totalLessons)
  
  assertEquals(expected, result)
}
```

### **Integration Tests** (15% target)
```kotlin
// Example integration test
@Test
fun testEnrollmentFlow() {
  // Setup
  val userId = "testUser"
  val courseId = "testCourse"
  val repository = EnrollmentRepository()
  
  // Action
  repository.createEnrollment(userId, courseId)
  
  // Assert
  val enrollment = repository.getEnrollment(userId, courseId)
  assertThat(enrollment.status).isEqualTo("ACTIVE")
}
```

### **UI Tests** (10% target)
```kotlin
// Example Compose UI test
@Test
fun testCourseCardClick() {
  composeTestRule.setContent {
    CourseCard(course = testCourse, onCourseClick = { courseId ->
      // Verify button clicked
      assertEquals(testCourse.id, courseId)
    })
  }
  
  composeTestRule.onNodeWithText(testCourse.title)
    .performClick()
}
```

### **End-to-End Tests** (5% target)
```javascript
// Example Cloud Function test
test("should create order and enrollment on payment", async () => {
  // Setup Firebase emulator
  const db = admin.firestore();
  
  // Create test data
  const testOrder = {
    userId: "user123",
    items: [{ courseId: "course456", price: 100000 }],
    totalAmount: 100000,
  };
  
  // Call webhook simulator
  const result = await momoWebhookSimulator(testOrder);
  
  // Verify
  expect(result.status).toBe(200);
  const enrollment = await db.collection("enrollments").doc("user123_course456").get();
  expect(enrollment.exists).toBe(true);
  expect(enrollment.data().status).toBe("ACTIVE");
});
```

## 10.3 Manual Testing Scenarios

### **Smoke Tests (Run on every build)**
1. App launches without crash
2. User can login
3. Can view courses
4. Can add course to cart
5. Can complete quiz

### **Regression Tests (Run weekly)**
1. Payment flow end-to-end
2. Enrollment & progress tracking
3. Notification delivery
4. Chatbot function calling
5. Instructor analytics

### **Performance Tests (Run monthly)**
```
Metrics:
├─ Cold startup: < 3s
├─ Screen transition: < 500ms
├─ List scroll (50 items): Maintain 60 FPS
├─ Image load: < 1s
├─ API response: < 1s (p95)
└─ Memory leak check: < 5MB increase over 5 min session

Tools:
├─ Android Studio Profiler
├─ Firebase Performance Monitoring
└─ Custom timing logs
```

---

# 11. Giới Hạn & Giả Định

## 11.1 Giới Hạn Hiện Tại

### **Chức Năng**
- Không hỗ trợ Live classes (video call)
- Không hỗ trợ group projects
- Không có certificate system (yet)
- Không hỗ trợ multi-language (Vietnamese only)
- Không có offline mode

### **Hardware**
- Min 2GB RAM (performance may degrade below this)
- Min 100MB free storage
- Android 8.0+ only

### **Dữ Liệu**
- Không lưu trữ video (dùng Cloudinary CDN)
- Không cache full courses offline
- Quiz có tối đa 100 câu hỏi

### **Tích Hợp**
- MoMo payment only (no credit card, banking future)
- OpenRouter API only (model ưu tiên: Qwen, fallback: Gemma)
- Cloudinary only (no alternative CDN)

## 11.2 Giả Định

### **Business**
- Người dùng chủ yếu từ Việt Nam (local focus)
- Khóa học được tạo bởi giảng viên verified
- Mua khóa học = vĩnh viễn (không hết hạn)
- Doanh thu chia sẻ với giảng viên (future: 70/30)

### **Technical**
- Firestore is reliable (99.95% SLA Firebase)
- Network available (cannot fully offline)
- Device time is correct (no syncing)
- Firebase Auth tokens last 1 hour
- Cloudinary service available globally

### **User Behavior**
- Users check progress regularly
- Users prefer mobile learning (primary platform)
- Users enable notifications (engagement)
- Instructors maintain courses regularly
- Support tickets respond within 24 hours

---

# 12. Rủi Ro & Chiến Lược Giảm Thiểu

## 12.1 Rủi Ro Kỹ Thuật

| Rủi Ro | Xác Suất | Tác Động | Giảm Thiểu |
|--------|----------|-----------|-----------|
| **Firestore downtime** | Low (1-2x/year) | Critical (0 data access) | ✓ Implement retry + offline cache |
| **Payment gateway fail** | Low | High (no revenue) | ✓ Fallback payment method, manual verification |
| **API rate limit** | Medium | High (user blocked) | ✓ Batch requests, queue system |
| **Memory leak** | Medium | Medium (crash) | ✓ Regular profiling, ANR monitoring |
| **OpenRouter API quota** | Low-Medium | Medium | ✓ Graceful degradation, model fallback, error handling |
| **Cloudinary CDN slow** | Low | Low (inconvenience) | ✓ Image optimization, CDN analytics |

## 12.2 Rủi Ro Khác

| Rủi Ro | Xác Suất | Tác Động | Giảm Thiểu |
|--------|----------|-----------|-----------|
| **Low course quality** | High | High (retention) | ✓ Content moderation, rating system |
| **Payment fraud** | Medium | High (chargebacks) | ✓ MoMo verification, phone checks |
| **Low user engagement** | High | High (MAU drop) | ✓ Push notifications, gamification |
| **Instructor churn** | Medium | Medium (course gaps) | ✓ Revenue sharing, instructor support |
| **Regulatory issues** | Low | Critical (shutdown) | ✓ Legal review, compliance team |

## 12.3 Mitigation Plans

### **Data Protection**
```
Backup Strategy:
├─ Firestore Daily backup → Cloud Storage
├─ Encryption: Google-managed + application-layer optional
├─ Disaster Recovery: RTO 4h, RPO 1h
└─ Tested monthly
```

### **Incident Response**
```
Alert Chain (PagerDuty):
├─ Severity 1 (Critical): Page on-call engineer
├─ Severity 2 (High): Slack notification
├─ Severity 3 (Medium): Daily digest
└─ Resolution SLA: < 30 min acknowledge, < 2h fix

Runbooks:
├─ Database outage
├─ Payment gateway failure
├─ Security incident
├─ Performance degradation
└─ Mass user complaint
```

---

# 13. Định Nghĩa Hoàn Tất (Definition of Done)

## **Một feature/task được coi là DONE khi:**

### **Code**
- ✅ Tất cả code viết bằng Kotlin (100%)
- ✅ Tuân theo MVVM + Repository pattern
- ✅ Có docstring cho all public functions/classes
- ✅ No compiler warnings
- ✅ ProGuard/R8 configured for release build

### **Testing**
- ✅ Unit tests độc lập (mock dependencies)
- ✅ Code coverage ≥ 70%
- ✅ All tests pass locally
- ✅ Tested on min 2 devices (emulator + real)
- ✅ Manual smoke testing completed

### **Documentation**
- ✅ Inline code comments for complex logic
- ✅ Updated corresponding SRS/README
- ✅ Breaking changes documented
- ✅ API changes documented (if applicable)

### **Performance**
- ✅ Cold startup impact < 200ms
- ✅ Memory impact < 10MB
- ✅ No ANRs introduced
- ✅ Database queries optimized (< 500ms)

### **Security**
- ✅ No hardcoded secrets
- ✅ Firestore rules reviewed
- ✅ Input validation applied
- ✅ No sensitive logs
- ✅ Security review passed (if security-critical)

### **QA / Code Review**
- ✅ 2-person code review approved
- ✅ CI/CD passes (build + tests)
- ✅ No merge conflicts
- ✅ Commits squashed & message clear

### **Release**
- ✅ Feature flag added (if risky)
- ✅ Rollback plan documented
- ✅ Release notes prepared
- ✅ Deployment checklist completed

---

# 14. Release & Quality Criteria

## **Quality Metrics to Track**

### **Stability**
- 99.5% crash-free rate
- <2s average screen load time
- API error rate < 0.5%
- Zero data corruption incidents

### **Engagement**
- 100+ Daily Active Users (target)
- 35% course completion rate
- 45% 7-day retention rate
- 4.0+ avg course rating

### **Quality**
- User satisfaction (NPS > 40)
- Support response time < 24 hours
- Bug fix rate 100% for critical bugs
- Zero security vulnerabilities

## **Go to Release Checklist**

### **Code & Testing**
- ✅ 70%+ code coverage
- ✅ All tests passing
- ✅ 2-person code review approved
- ✅ No known critical bugs
- ✅ Linting & format checks pass

### **Functionality**
- ✅ All core features working (auth, courses, learning, quiz)
- ✅ 99.5% crash-free in testing
- ✅ <2s avg load time
- ✅ Offline graceful degradation
- ✅ Error handling comprehensive

### **Security & Privacy**
- ✅ Security review passed
- ✅ No hardcoded secrets
- ✅ Firestore rules reviewed
- ✅ Privacy policy drafted
- ✅ PII handling verified

### **Documentation**
- ✅ User guide complete
- ✅ API docs up to date
- ✅ Installation guide ready
- ✅ Support runbooks prepared

---

# 16. Tài Liệu Liên Quan

- 📄 [App Architecture Overview](./IMPLEMENTATION_COMPLETE.md)
- 📄 [Developer Setup Guide](./implementation.md)
- 📄 [User Manual (Vietnamese)](./HUONG_DAN_SU_DUNG.md)
- 📄 [OpenRouter AI Setup](./OPENROUTER_SETUP.md)
- 📄 [Firebase Configuration](../../firebase.json)
- 📄 [Build Configuration](../../build.gradle.kts)

---

**Document Version**: 2.0  
**Last Updated**: April 1, 2026  
**Next Review**: May 1, 2026  
**Owner**: Development Team

---
