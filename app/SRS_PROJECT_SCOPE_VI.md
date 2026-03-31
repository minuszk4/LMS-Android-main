# Đặc tả Yêu cầu Phần mềm (SRS)
## LMS Android - Mô tả Chi tiết Phạm vi Dự án

Phiên bản: 1.0
Ngày: 2026-03-31
Tác giả: Nhóm Dự án
Trạng thái: Bản nháp Baseline

---

## 1. Mục đích và Phạm vi

### 1.1 Mục đích
Tài liệu này xác định chi tiết phạm vi và các yêu cầu phần mềm của dự án LMS Android. Tài liệu thiết lập baseline dùng chung cho Product, Engineering, QA và các bên liên quan nhằm thống nhất nội dung bao gồm, nội dung loại trừ và mức chất lượng kỳ vọng.

### 1.2 Tóm tắt Phạm vi Sản phẩm
LMS Android là nền tảng học tập trên thiết bị di động cho học viên và giảng viên, bao gồm:
- Xác thực và truy cập theo vai trò.
- Danh mục khóa học, tìm kiếm và xem chi tiết.
- Ghi danh và theo dõi tiến độ học tập.
- Luồng học bài học và làm bài kiểm tra.
- Giỏ hàng, thanh toán và theo dõi đơn hàng.
- Quản lý khóa học và phân tích cho giảng viên.
- Chatbot trợ lý AI với công cụ khóa học và hành động điều hướng.

### 1.3 Loại Baseline Phạm vi
SRS này mô tả:
- Phạm vi chức năng (tính năng và luồng nghiệp vụ).
- Phạm vi phi chức năng (hiệu năng, bảo mật, độ tin cậy, tính khả dụng).
- Phạm vi dữ liệu và tích hợp.
- Ranh giới ngoài phạm vi được nêu rõ.

---

## 2. Các bên liên quan và Nhóm người dùng

### 2.1 Các bên liên quan
- Product Owner: xác định mục tiêu kinh doanh và ưu tiên phát hành.
- Nhóm Engineering: triển khai ứng dụng di động và logic tích hợp backend.
- Nhóm QA: xác minh yêu cầu và chất lượng bản phát hành.
- Người dùng cuối: học viên và giảng viên.
- Quản trị viên (tương lai): quản trị nền tảng và kiểm duyệt.

### 2.2 Nhóm người dùng
- Khách: duyệt không xác thực (giới hạn).
- Học viên: ghi danh và học khóa học.
- Giảng viên: tạo và quản lý khóa học/curriculum của mình.

---

## 3. Mục tiêu Kinh doanh

- Tăng khả năng khám phá khóa học và chuyển đổi ghi danh.
- Cải thiện tỷ lệ hoàn thành học tập thông qua hiển thị tiến độ.
- Cung cấp kênh tự phục vụ qua chatbot AI.
- Hỗ trợ giảng viên xuất bản/quản lý nội dung và theo dõi kết quả.
- Đảm bảo vận hành ổn định trên thiết bị Android với độ trễ chấp nhận được.

---

## 4. Các tính năng Trong phạm vi

## 4.1 Định danh và Truy cập
- Đăng nhập, đăng ký, quên mật khẩu, luồng kiểm tra email.
- Duy trì phiên đăng nhập.
- Điều hướng theo vai trò học viên/giảng viên.

## 4.2 Trải nghiệm Học viên
- Trang chủ hiển thị danh sách khóa học.
- Tìm kiếm và lọc khóa học.
- Xem chi tiết khóa học và preview curriculum.
- Thêm vào giỏ và quản lý giỏ hàng.
- Luồng thanh toán và màn hình thanh toán thành công.
- Màn hình My Learning và chỉ báo tiến độ.
- Màn hình thông báo.

## 4.3 Luồng Học tập
- Tạo ghi danh.
- Trình phát bài học cho nội dung khóa học.
- Luồng làm bài kiểm tra.
- Kết quả kiểm tra và xem lại bài làm.
- Cập nhật tiến độ cấp khóa học và cấp bài học.

## 4.4 Trải nghiệm Giảng viên
- Trang chủ và trang hồ sơ giảng viên.
- Form tạo/sửa khóa học.
- Quản lý curriculum: bài học và bài kiểm tra.
- Danh sách quản lý My Courses.
- Màn hình thống kê và phân tích khóa học.

## 4.5 Trải nghiệm Chatbot AI
- Tạo phiên chat và chuyển đổi phiên.
- Lưu lịch sử tin nhắn người dùng và bot.
- Hành động hỗ trợ bởi AI với phản hồi theo công cụ:
  - Tìm kiếm khóa học.
  - Lấy tóm tắt học tập.
  - Gợi ý khóa học.
  - Xem chi tiết khóa học.
  - Thêm khóa học vào giỏ.
- Render tin nhắn có cấu trúc:
  - Tin nhắn văn bản.
  - Thẻ khóa học.
  - Danh sách khóa học.
  - Biểu đồ tiến độ.
- Nút hành động trong chat có điều hướng trực tiếp:
  - Đi tới chi tiết khóa học.
  - Mở giỏ hàng.
  - Đi tới hồ sơ giảng viên.
  - Đi tới My Learning.
  - Thanh toán nhanh (route checkout trực tiếp).

## 4.6 Gợi ý khóa học
- Repository gợi ý dựa trên độ tương tự vector và xếp hạng.
- Cá nhân hóa gợi ý cho học viên.
- Cơ chế fallback khi lịch sử người dùng còn ít.

---

## 5. Ngoài phạm vi (Bản phát hành hiện tại)

- Ứng dụng web và ứng dụng iOS.
- Đa ngôn ngữ vượt ngoài ngôn ngữ hiện tại của ứng dụng.
- SSO doanh nghiệp (SAML/OAuth cho nhà cung cấp doanh nghiệp).
- Chế độ học offline-first đầy đủ với xử lý xung đột.
- Console kiểm duyệt thủ công dành cho admin.
- Pipeline huấn luyện ML trên cloud đầy đủ cho mô hình gợi ý.
- Cơ chế thi có giám sát và chống gian lận.
- Vòng đời hoàn tiền/tranh chấp cho thanh toán.

---

## 6. Giả định và Phụ thuộc

### 6.1 Giả định
- Dịch vụ Firebase sẵn sàng và đã cấu hình.
- Mạng di động sẵn có cho đa số hành động người dùng.
- API key được quản lý qua local properties/build config trong môi trường phát triển.

### 6.2 Phụ thuộc bên ngoài
- Firebase Auth.
- Cloud Firestore.
- Firebase Storage.
- Endpoint nhà cung cấp AI (luồng tích hợp OpenRouter/Gemini trong logic repository).
- Thư viện media/image bên thứ ba (theo cấu hình dependency hiện tại).

---

## 7. Yêu cầu Chức năng

### FR-01 Xác thực
- Hệ thống phải cho phép đăng ký và đăng nhập.
- Hệ thống phải hỗ trợ đặt lại mật khẩu qua luồng email.
- Hệ thống phải điều hướng người dùng tới luồng chính tương ứng vai trò sau đăng nhập.

### FR-02 Khám phá Khóa học
- Hệ thống phải cung cấp danh sách khóa học cho học viên.
- Hệ thống phải hỗ trợ tìm kiếm theo từ khóa.
- Hệ thống phải mở được chi tiết khóa học từ các hành động list/search/chat.

### FR-03 Ghi danh và Học tập
- Hệ thống phải cho phép ghi danh vào các khóa học khả dụng.
- Hệ thống phải hiển thị bài học và bài kiểm tra theo thứ tự curriculum.
- Hệ thống phải theo dõi tiến độ ở mức khóa học và bài học.

### FR-04 Vòng đời Bài kiểm tra
- Hệ thống phải cho phép người dùng bắt đầu một lần làm bài kiểm tra.
- Hệ thống phải lưu và chấm câu trả lời.
- Hệ thống phải hiển thị kết quả và luồng xem lại bài.

### FR-05 Giỏ hàng và Thanh toán
- Hệ thống phải cho phép thêm/xóa khóa học trong giỏ.
- Hệ thống phải hỗ trợ màn hình thanh toán với danh sách course ID đã chọn.
- Hệ thống phải lưu đơn hàng và chi tiết đơn hàng sau khi checkout thành công.

### FR-06 Quản lý Khóa học cho Giảng viên
- Hệ thống phải cho phép giảng viên tạo và cập nhật dữ liệu khóa học.
- Hệ thống phải cho phép giảng viên quản lý bài học/bài kiểm tra trong curriculum.
- Hệ thống phải cung cấp các màn hình phân tích cho giảng viên.

### FR-07 Chatbot Core
- Hệ thống phải lưu phiên chat và tin nhắn chat.
- Hệ thống phải hỗ trợ kiểu phản hồi bot có cấu trúc qua metadata.
- Hệ thống phải hỗ trợ các hành động chatbot dạng tool cho luồng học tập.

### FR-08 Hành động Điều hướng từ Chatbot
- Hệ thống phải cung cấp nút hành động trong chat để điều hướng trực tiếp trong app.
- Hệ thống phải điều hướng tới chi tiết khóa học khi có course ID.
- Hệ thống phải điều hướng được tới giỏ hàng, hồ sơ giảng viên, My Learning và route thanh toán từ action trong chat.

### FR-09 Gợi ý
- Hệ thống phải trả về khóa học gợi ý dựa trên hành vi người dùng và đặc trưng khóa học.
- Hệ thống phải tránh gợi ý chất lượng thấp khi dữ liệu lịch sử còn thưa thông qua cơ chế fallback ranking.

---

## 8. Yêu cầu Phi chức năng

### NFR-01 Hiệu năng
- Điều hướng màn hình phổ biến cần hoàn thành trong ngưỡng UX di động chấp nhận được.
- Tương tác UI chat phải giữ phản hồi tốt trong lúc gọi API.
- Render danh sách phải xử lý được tập dữ liệu demo thực tế mà không treo UI.

### NFR-02 Độ tin cậy
- Ứng dụng phải xử lý lỗi mạng tạm thời một cách mềm dẻo.
- Ứng dụng phải hiển thị thông báo lỗi thân thiện khi thao tác thất bại.
- Cập nhật dữ liệu phải giữ nhất quán cho các luồng quan trọng (giỏ/thanh toán/ghi danh/tiến độ).

### NFR-03 Bảo mật và Quyền riêng tư
- API key không được hardcode trong source control.
- Dữ liệu nhạy cảm phải được bảo vệ bằng Firebase security rules và nguyên tắc phân quyền tối thiểu.
- Trạng thái xác thực phải được kiểm tra trước các thao tác được bảo vệ.

### NFR-04 Tính khả dụng
- Luồng học viên và giảng viên phải được tách rõ theo vai trò.
- Hành động trong chat phải dễ hiểu và dễ khám phá.
- Tác vụ quan trọng (tìm kiếm, chi tiết, giỏ, thanh toán, học tập) cần đạt được với số bước tối thiểu.

### NFR-05 Khả năng bảo trì
- Mã nguồn cần duy trì tính module theo ranh giới repository, viewmodel và screen.
- Hành động chatbot mới phải mở rộng được mà không cần viết lại kiến trúc chat cốt lõi.
- Thay đổi yêu cầu cần ánh xạ rõ sang các module bị tác động.

---

## 9. Phạm vi Dữ liệu

Các thực thể logic chính trong phạm vi dự án gồm:
- users
- instructors
- categories
- courses
- lessons
- quizzes
- enrollments
- progress
- lessonProgress
- reviews
- cartItems
- carts
- orders
- orderItems
- notifications
- chatSessions
- chatMessages

Ràng buộc chất lượng dữ liệu trong phạm vi:
- Toàn vẹn tham chiếu ở tầng ứng dụng cho các quan hệ khóa chính.
- Tính duy nhất tổ hợp tại các luồng nghiệp vụ yêu cầu (mẫu enrollment/review/cart item).
- Giá trị trạng thái hợp lệ cho các trường dạng enum (payment status, cart status, role, level).

---

## 10. Phạm vi Giao diện

### 10.1 Giao diện nội bộ
- Hợp đồng ViewModel - Repository cho từng module nghiệp vụ.
- Chatbot repository kết nối tới recommendation/course/cart/progress repositories.

### 10.2 Giao diện bên ngoài
- Giao diện SDK Firebase cho auth/firestore/storage.
- Giao diện API AI cho sinh phản hồi chatbot và luồng tool-loop.

### 10.3 Giao diện điều hướng
- Điều hướng theo route cho các màn hình student/instructor/chat/payment/learning/quiz.
- Tham số route kiểu deep-link cho course ID, quiz ID và đầu vào thanh toán.

---

## 11. Ràng buộc

- Phiên bản Android SDK và dependency theo cấu hình gradle của dự án.
- Quota cloud free-tier có thể giới hạn thao tác ghi dữ liệu lớn trong môi trường test/demo.
- Bối cảnh phát hành hiện tại chỉ cho nền tảng di động.

---

## 12. Rủi ro và Giảm thiểu

### Risk-01: Biến động dịch vụ AI
- Rủi ro: thay đổi hành vi model/API upstream có thể ảnh hưởng tính nhất quán của chatbot.
- Giảm thiểu: giữ luồng metadata tool mang tính xác định và thông điệp fallback.

### Risk-02: Giới hạn quota cho dữ liệu demo
- Rủi ro: giới hạn ghi có thể làm gián đoạn quá trình seed dữ liệu lớn.
- Giảm thiểu: upload theo giai đoạn, chế độ resume, giới hạn max-write cho mỗi lần chạy.

### Risk-03: Lệch hợp đồng điều hướng trong chat action
- Rủi ro: thay đổi route contract có thể làm hỏng action điều hướng trong chat.
- Giảm thiểu: tập trung hằng số route và chạy kiểm tra hồi quy cho điều hướng chatbot.

---

## 13. Phạm vi Nghiệm thu (Mức phát hành)

Một bản phát hành được xem là hoàn thành trong phạm vi khi:
- Luồng cốt lõi của học viên chạy end-to-end.
- Luồng quản lý nội dung của giảng viên chạy end-to-end.
- Chatbot trả về được phản hồi có cấu trúc cho khóa học/tiến độ và kích hoạt được action điều hướng trực tiếp.
- Luồng thanh toán, ghi danh và tiến độ vận hành nhất quán.
- Các hạng mục ngoài phạm vi đã biết không bị đưa vào như cam kết ngầm.

---

## 14. Tổng quan Truy vết

Ánh xạ phạm vi sang module (mức cao):
- Xác thực: auth screens + auth viewmodel + Firebase Auth.
- Học tập học viên: student screens + course/enrollment/progress repositories.
- Quản lý giảng viên: instructor screens + curriculum/course repositories.
- Chatbot: chatbot screen + chatbot viewmodel + logic repository OpenRouter/Gemini.
- Gợi ý: recommendation repository + dữ liệu hành vi người học/khóa học.
- Thanh toán/giỏ: cart/payment screens + cart/order repositories.

---

## 15. Quản lý Thay đổi

Mọi thay đổi phạm vi trong tương lai cần xác định:
- Requirement ID bị tác động.
- Module và route bị tác động.
- Tác động migration lên mô hình dữ liệu.
- Cập nhật checklist kiểm thử hồi quy của QA.

---

## 16. Phụ lục A - Thuật ngữ

- LMS: Learning Management System.
- SRS: Software Requirements Specification.
- In-scope: hạng mục cam kết trong baseline phát hành hiện tại.
- Out-of-scope: hạng mục bị loại trừ rõ ràng.
- Chat action: nút hành động do người dùng bấm bên trong tin nhắn chatbot.

---

Kết thúc tài liệu.
