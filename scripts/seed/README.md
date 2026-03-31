# 🌱 LMS Android - Synthetic Data Seeder

Công cụ sinh dữ liệu mẫu (synthetic data) tự động cho ứng dụng LMS Android phục vụ demo và QA testing.

## 📋 Mục đích

- ✅ Sinh dữ liệu mẫu toàn vẹn theo schema hiện tại
- ✅ Tuân thủ quy tắc nghiệp vụ (FK, Unique, Enum, ...)
- ✅ Hỗ trợ 3 profile seed: quick, standard, heavy
- ✅ Tái tạo dữ liệu giống nhau (cùng random seed)
- ✅ Kiểm tra tự động chất lượng dữ liệu

## 🚀 Quick Start

### 1. Cài đặt dependencies

```bash
cd scripts/seed
pip install -r requirements.txt
```

### 2. Chạy Dry-run (xem trước dữ liệu)

```bash
python seed_data.py --profile standard --dry-run --save-json
```

Lệnh này sẽ:
- Sinh dữ liệu theo profile `standard`
- Không upload lên Firestore
- Lưu ra file `seed_data.json` để xem

### 3. Kiểm tra dữ liệu

```bash
python validate_data.py --json seed_data.json
```

Báo cáo kiểm tra:
- ✅ Số lượng record mỗi collection
- ✅ Không có orphan FK records
- ✅ Unique constraints tuân thủ
- ✅ Enum values hợp lệ
- ✅ Business rules pass/fail

### 4. Upload lên Firestore (thực tế)

```bash
# 4a. Chuẩn bị Firebase Service Account Key
# - Vào Firebase Console → Project Settings → Service Accounts
# - Download JSON file → lưu vào scripts/seed/serviceAccountKey.json

# 4b. Upload dữ liệu
python seed_data.py --profile standard --credentials serviceAccountKey.json
```

## 📊 Seed Profiles

### Quick (Dev/Demo nhanh)
- 3 categories, 2 instructors, 5 students
- 2-4 lessons/course, 1-2 quizzes/course
- 50% enrollment rate, 30% cart rate
- ~30 ngày dữ liệu quá khứ
- Dùng khi: test feature nhanh, UI storyboard

```bash
python seed_data.py --profile quick --dry-run
```

### Standard (Demo đầy đủ - MẶC ĐỊNH)
- 5 categories, 5 instructors, 20 students
- 3-8 lessons/course, 1-3 quizzes/course
- 50% enrollment rate, 20% cart rate
- ~6 tháng dữ liệu quá khứ
- Dùng khi: demo để client, QA testing thực

```bash
python seed_data.py --profile standard --save-json
```

### Heavy (Demo tải cao)
- 10 categories, 15 instructors, 50 students
- 5-12 lessons/course, 2-5 quizzes/course
- 40% enrollment rate, 15% cart rate
- ~1 năm dữ liệu quá khứ
- Dùng khi: test performance, pagination, filtering

```bash
python seed_data.py --profile heavy --save-json
```

## 📁 Cấu trúc dữ liệu

Mỗi profile seed tạo các collection Firestore:

### Cơ sở dữ liêu (Base)
- `users` → uid = "{student_X | instructor_X}"
- `instructors` → uid = FK của users
- `categories` → id = "cat_X"

### Nội dung khóa học (Curriculum)
- `courses` → id = "course_instr_idx_course_idx"
- `lessons` → id = "lesson_{course_id}_{idx}", courseId → courses.id
- `quizzes` → id = "quiz_{course_id}_{idx}", courseId → courses.id

### Tiến độ học tập (Progress)
- `enrollments` → id = "uid_courseId", userId + courseId unique
- `progress` → doc_id = "uid_courseId", tracking tiến độ khóa học
- `lessonProgress` →  doc_id = "uid_lessonId", tracking từng bài học
- `quizProgress` → doc_id = "uid_quizId", tracking từng quiz

### Phê bình & Đánh giá (Review)
- `reviews` → id = "uid_courseId", 1 user 1 review/course, rating 1-5

### Mua hàng (E-commerce)
- `carts` → id = uid, mỗi user 1 cart logic
- `cartItems` → id = "uid_courseId", unique uid + course
- `orders` → id = "order_uid_random"
- `orderItems` → id = "orderId_courseId"

### Thông báo & Chat (Communication)
- `notifications` → id = auto, userId FK
- `chatSessions` → id = auto, userId FK
- `chatMessages` → id = auto, sessionId FK

## 🔍 Quy tắc nghiệp vụ được tuân thủ

1. ✅ Course phải có instructor + category tồn tại
2. ✅ Lesson/Quiz phải belong to course tồn tại
3. ✅ Chỉ published courses mới xuất hiện trong danh sách public (isPublished=true)
4. ✅ 1 user chỉ enroll 1 lần/course (id = uid_courseId unique)
5. ✅ 1 user chỉ 1 review/course, rating 1-5
6. ✅ 1 user 1 cart; 1 course chỉ xuất hiện 1 lần trong cart
7. ✅ OrderItem chỉ được tạo khi checkout thành công (paymentStatus=SUCCESS)
8. ✅ Review chỉ tồn tại nếu (isHidden=false)
9. ✅ ChatSession ACTIVE được query theo whereEqualTo("status", "ACTIVE")
10. ✅ Progress isCompleted=true khi completedLessons >= totalLessons

## 📄 File Config

### config.py
Chứa:
- `SEED_PROFILES`: Dict 3 profile (quick, standard, heavy)
- `CATEGORIES`: Danh mục hardcoded
- `COURSE_LEVELS`, `CART_STATUS`, ... : Enum values
- `COURSE_PRICE_RANGE`, `RATING_RANGE`: Range giá trị
- `VIETNAMESE_NAMES`, `COURSE_TITLES_BY_CATEGORY`: Template data

Để thay đổi tâm số:
- Sửa `SEED_PROFILES["{profile}"]["num_students"]`
- Sửa `COURSE_PRICE_RANGE = (min_price, max_price)`
- Thêm categories vào `CATEGORIES` list

## 🔧 Lệnh nâng cao

### Reset + Re-seed
```bash
# Backup dữ liệu cũ (tùy chọn)
# ...

# Seed lại từ đầu (thay profile "standard" nếu cần)
python seed_data.py --profile standard --credentials serviceAccountKey.json
```

### Seed từng phần
```bash
# Xem trước trong JSON
python seed_data.py --profile quick --dry-run --save-json

# Chỉnh sửa seed_data.json theo ý
# (VD: số user, giá khóa học, ...)

# Upload từng collection thủ công từ JSON (nếu cần)
# - Parse seed_data.json
# - Upload từng collection tương ứng
```

### Tùy chỉnh random seed
Mở `config.py`, tìm:
```python
RANDOM_SEED = 42  # Thay số khác để sinh dữ liệu mới
```

## ⚠️ Lưu ý quan trọng

### Trước khi upload lên Production
1. ✅ Chạy kiểm tra: `python validate_data.py`
2. ✅ Xem trước JSON: `seed_data.py --dry-run --save-json`
3. ✅ Backup dữ liệu hiện tại nếu cần
4. ✅ Upload test vào staging Firestore trước

### Performance
- **Quick profile**: ~30s
- **Standard profile**: ~1-2 phút
- **Heavy profile**: ~5-10 phút
- Tùy vào tốc độ kết nối Firestore

### Lỗi thường gặp

#### "Firebase Admin SDK chưa cài"
```bash
pip install firebase-admin
```

#### "serviceAccountKey.json không tồn tại"
- Vào Firebase Console → Project Settings → Service Accounts
- Nhấn "Generate New Private Key"
- Lưu vào `scripts/seed/serviceAccountKey.json`

#### "Firestore quota exceeded"
- Chạy `--dry-run` để chỉ xem preview
- Chạy trên Emulator nếu có sẵn
- Chờ quota reset (thường 24 giờ)

## 📞 Support

- Kiểm tra chi tiết lỗi: `python seed_data.py --help`
- Xem báo cáo kiểm tra: `python validate_data.py --json seed_data.json`
- Báo lỗi: Tạo issue với profile + lỗi được gặp

## 🎯 Kế hoạch mở rộng

- [ ] Support seeding vào SQLite (local testing, offline)
- [ ] UI CLI tương tác để chọn profile
- [ ] Export dữ liệu hiện tại từ Firestore thành seed_data.json
- [ ] Thêm profile cho specific test scenarios (VD: student 1 user completed 100%)
- [ ] Integration test: seed → run test → validate → cleanup

---

**Version**: 1.0.0  
**Updated**: 2026-03-31
