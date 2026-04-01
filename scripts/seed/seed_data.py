"""
Seed synthetic data vào Firestore
Chạy: python seed_data.py --profile standard --dry-run
"""

import argparse
import random
import json
import sys
import time
import hashlib
from datetime import datetime, timedelta
from typing import Dict, List, Any, Optional
import uuid

# Import Firebase
try:
    import firebase_admin
    from firebase_admin import credentials, firestore
except ImportError:
    firebase_admin = None
    credentials = None
    firestore = None

from config import (
    SEED_PROFILES, RANDOM_SEED, BASE_TIMESTAMP_MS, CATEGORIES,
    COURSE_LEVELS, COURSE_PRICE_RANGE, ENROLLMENT_COUNT_RANGE,
    RATING_RANGE, REVIEW_RATING_RANGE, QUIZ_PASSING_SCORE_RANGE,
    QUIZ_DURATION_RANGE, QUESTIONS_PER_QUIZ_RANGE, QUIZ_ATTEMPTS_RANGE,
    PROGRESS_COMPLETION_RATES, VIETNAMESE_NAMES,
    COURSE_TITLES_BY_CATEGORY, LESSON_TITLES_TEMPLATE, QUIZ_TITLES_TEMPLATE,
    REVIEW_CONTENTS, PAYMENT_METHOD, PAYMENT_STATUS, NOTIFICATION_TYPES,
    CART_STATUS, BANK_CATALOG
)

# ============================================
# HELPER FUNCTIONS
# ============================================

def random_timestamp(days_back: int, base_ts: int) -> int:
    """Tạo timestamp ngẫu nhiên trong range ngày"""
    delta = random.randint(0, days_back * 24 * 60 * 60 * 1000)
    return base_ts - delta

def random_name() -> str:
    """Lấy tên Việt ngẫu nhiên"""
    return random.choice(VIETNAMESE_NAMES)

def random_email() -> str:
    """Tạo email ngẫu nhiên"""
    name = random_name().lower().replace(" ", ".").split(".")
    domain = random.choice(["gmail.com", "outlook.com", "yahoo.com"])
    username = f"{name[0]}.{name[-1]}{random.randint(100, 999)}"
    return f"{username}@{domain}"

def random_price(min_p: int, max_p: int) -> float:
    """Giá khóa học"""
    return round(random.uniform(min_p, max_p) / 1000) * 1000

def random_rating(min_r: float, max_r: float) -> float:
    """Rating 0..5"""
    return round(random.uniform(min_r, max_r), 1)

def parse_collections_arg(raw: Optional[str]) -> Optional[List[str]]:
    """Parse comma-separated collection names."""
    if raw is None:
        return None
    items = [x.strip() for x in raw.split(",") if x.strip()]
    return items or None

def random_bank_info(account_holder: str) -> Dict[str, str]:
    """Sinh ngẫu nhiên thông tin ngân hàng theo schema hiện tại."""
    bank = random.choice(BANK_CATALOG)
    account_number = "".join(str(random.randint(0, 9)) for _ in range(10))
    return {
        "bankName": bank["name"],
        "bankCode": bank["code"],
        "bankAccountNumber": account_number,
        "bankAccountHolder": account_holder,
        "bankAccount": f"{bank['name']} - {account_number}",
    }

def build_avatar_url(role: str, index: int) -> str:
    """Sinh URL ảnh đại diện ổn định theo user để dễ test UI."""
    return f"https://i.pravatar.cc/256?img={((index - 1) % 70) + 1}&u={role}_{index}"

def build_course_thumbnail_url(course_id: str) -> str:
    """Sinh URL thumbnail khóa học ổn định, tránh dịch vụ placeholder hay lỗi."""
    seed = int(hashlib.md5(course_id.encode("utf-8")).hexdigest()[:8], 16) % 100000
    return f"https://picsum.photos/seed/lms_{seed}/640/360"

# ============================================
# SEED DATA BUILDERS
# ============================================

class SeedDataBuilder:
    def __init__(self, profile: str, dry_run: bool = False):
        self.profile = SEED_PROFILES.get(profile, SEED_PROFILES["standard"])
        self.dry_run = dry_run
        self.db = None
        self.data = {
            "users": [],
            "instructors": [],
            "categories": [],
            "courses": [],
            "lessons": [],
            "quizzes": [],
            "enrollments": [],
            "reviews": [],
            "progress": [],
            "lessonProgress": [],
            "quizProgress": [],
            "cartItems": [],
            "carts": [],
            "orders": [],
            "orderItems": [],
            "notifications": [],
            "chatSessions": [],
            "chatMessages": [],
        }
        random.seed(RANDOM_SEED)

    def connect_firestore(self, credentials_path: str):
        """Kết nối Firestore"""
        if self.dry_run:
            print(f"📌 DRY RUN: Không sẽ ghi vào Firestore")
            return

        if firebase_admin is None or credentials is None or firestore is None:
            print("❌ Firebase Admin SDK chưa cài: pip install firebase-admin")
            sys.exit(1)
        
        try:
            cred = credentials.Certificate(credentials_path)
            firebase_admin.initialize_app(cred)
            self.db = firestore.client()
            print(f"✅ Kết nối Firestore thành công")
        except Exception as e:
            print(f"❌ Lỗi kết nối Firestore: {e}")
            sys.exit(1)

    def seed_users(self):
        """Sinh users (mix student + instructor)"""
        num_instructors = self.profile["num_instructors"]
        num_students = self.profile["num_students"]
        
        # Instructors
        for i in range(num_instructors):
            uid = f"instructor_{i+1}"
            full_name = f"GV {random_name()}"
            bank_info = random_bank_info(full_name)
            avatar_url = build_avatar_url("instructor", i + 1)
            self.data["users"].append({
                "uid": uid,
                "fullName": full_name,
                "email": f"instructor{i+1}@lms.local",
                "role": "INSTRUCTOR",
                "isActive": True,
                "instructorRequestStatus": "APPROVED",
                "instructorRequestSubmittedAt": random_timestamp(self.profile["days_lookback"], BASE_TIMESTAMP_MS),
                "instructorRequestReviewedAt": random_timestamp(self.profile["days_lookback"], BASE_TIMESTAMP_MS),
                "instructorRequestReviewedBy": "admin_seed",
                "instructorRequestRejectReason": None,
                "instructorApplication": None,
                "avatarUrl": avatar_url,
                "createdAt": random_timestamp(self.profile["days_lookback"], BASE_TIMESTAMP_MS),
            })
            self.data["instructors"].append({
                "uid": uid,
                "expertise": random.choice(["Mobile", "Web", "AI", "Cloud"]),
                "experienceYears": random.randint(2, 15),
                "qualification": "Bachelor/Master",
                "bankAccount": bank_info["bankAccount"],
                "bankName": bank_info["bankName"],
                "bankCode": bank_info["bankCode"],
                "bankAccountNumber": bank_info["bankAccountNumber"],
                "bankAccountHolder": bank_info["bankAccountHolder"],
            })

        # Students
        for i in range(num_students):
            uid = f"student_{i+1}"
            avatar_url = build_avatar_url("student", i + 1)
            self.data["users"].append({
                "uid": uid,
                "fullName": f"HS {random_name()}",
                "email": f"student{i+1}@lms.local",
                "role": "STUDENT",
                "isActive": True,
                "instructorRequestStatus": "NONE",
                "instructorRequestSubmittedAt": None,
                "instructorRequestReviewedAt": None,
                "instructorRequestReviewedBy": None,
                "instructorRequestRejectReason": None,
                "instructorApplication": None,
                "avatarUrl": avatar_url,
                "createdAt": random_timestamp(self.profile["days_lookback"], BASE_TIMESTAMP_MS),
            })
        
        print(f"✅ Tạo {num_instructors} instructors + {num_students} students")

    def seed_categories(self):
        """Sinh categories"""
        num = self.profile["num_categories"]
        self.data["categories"] = [
            {"id": f"cat_{i}", "name": CATEGORIES[i % len(CATEGORIES)]["name"]}
            for i in range(num)
        ]
        print(f"✅ Tạo {num} categories")

    def seed_courses(self):
        """Sinh courses"""
        num_instructors = self.profile["num_instructors"]
        num_per_instructor = self.profile["num_courses_per_instructor"]
        categories = self.data["categories"]
        
        for instr_idx in range(num_instructors):
            instr_uid = f"instructor_{instr_idx + 1}"
            instr = next((u for u in self.data["users"] if u["uid"] == instr_uid), None)
            
            for c_idx in range(num_per_instructor):
                course_id = f"course_{instr_idx}_{c_idx}"
                category = random.choice(categories)
                
                self.data["courses"].append({
                    "id": course_id,
                    "title": f"{random.choice(COURSE_TITLES_BY_CATEGORY.get(category['name'], ['Khóa học mẫu']))[:50]}",
                    "instructorId": instr_uid,
                    "instructorName": instr["fullName"] if instr else "Giảng viên",
                    "thumbnailUrl": build_course_thumbnail_url(course_id),
                    "thumbnailPublicId": "",
                    "description": f"Khóa học {random.choice(COURSE_LEVELS)} về {category['name']}. Học mọi lúc mọi nơi.",
                    "categoryId": category["id"],
                    "level": random.choice(COURSE_LEVELS),
                    "price": random_price(*COURSE_PRICE_RANGE),
                    "rating": random_rating(*RATING_RANGE),
                    "reviewCount": random.randint(0, 20),
                    "enrollmentCount": 0,
                    "lessonCount": 0,  # Sẽ cập nhật khi tạo lessons
                    "duration": f"{random.randint(10, 100)} giờ",
                    "isPublished": True,
                    "createdAt": random_timestamp(self.profile["days_lookback"], BASE_TIMESTAMP_MS),
                    "updatedAt": BASE_TIMESTAMP_MS,
                })
        
        print(f"✅ Tạo {len(self.data['courses'])} courses")

    def seed_curriculum(self):
        """Sinh lessons và quizzes"""
        for course in self.data["courses"]:
            course_id = course["id"]
            num_lessons = random.randint(*self.profile["lessons_per_course_range"])
            num_quizzes = random.randint(*self.profile["quizzes_per_course_range"])
            
            # Lessons
            for l_idx in range(num_lessons):
                lesson_id = f"lesson_{course_id}_{l_idx}"
                self.data["lessons"].append({
                    "id": lesson_id,
                    "courseId": course_id,
                    "title": f"Bài {l_idx + 1}: {random.choice(LESSON_TITLES_TEMPLATE)}",
                    "description": f"Nội dung bài học {l_idx + 1}",
                    "videoUrl": f"https://youtube.com/watch?v={random.randint(100000, 999999)}",
                    "duration": f"{random.randint(15, 120)} phút",
                    "orderIndex": l_idx,
                    "attachments": [],
                    "createdAt": random_timestamp(self.profile["days_lookback"], BASE_TIMESTAMP_MS),
                    "updatedAt": BASE_TIMESTAMP_MS,
                })
            
            # Quizzes
            for q_idx in range(num_quizzes):
                quiz_id = f"quiz_{course_id}_{q_idx}"
                num_questions = random.randint(*QUESTIONS_PER_QUIZ_RANGE)
                
                questions = []
                for q_id in range(num_questions):
                    questions.append({
                        "id": f"q_{quiz_id}_{q_id}",
                        "text": f"Câu hỏi {q_id + 1} - {quiz_id}?",
                        "options": [
                            f"Lựa chọn A",
                            f"Lựa chọn B",
                            f"Lựa chọn C",
                            f"Lựa chọn D",
                        ],
                        "correctAnswerIndex": random.randint(0, 3),
                    })
                
                self.data["quizzes"].append({
                    "id": quiz_id,
                    "courseId": course_id,
                    "title": f"{random.choice(QUIZ_TITLES_TEMPLATE)}",
                    "description": f"Kiểm tra về {course['title']}",
                    "orderIndex": num_lessons + q_idx,
                    "questions": questions,
                    "durationMinutes": random.randint(*QUIZ_DURATION_RANGE),
                    "passingScore": random.randint(*QUIZ_PASSING_SCORE_RANGE),
                    "createdAt": random_timestamp(self.profile["days_lookback"], BASE_TIMESTAMP_MS),
                    "updatedAt": BASE_TIMESTAMP_MS,
                })
            
            # Cập nhật lessonCount
            course["lessonCount"] = num_lessons
        
        print(f"✅ Tạo {len(self.data['lessons'])} lessons + {len(self.data['quizzes'])} quizzes")

    def seed_enrollments_progress(self):
        """Sinh enrollments và progress"""
        students = [u for u in self.data["users"] if u["role"] == "STUDENT"]
        courses = self.data["courses"]
        enrollment_counter_by_course = {c["id"]: 0 for c in courses}
        
        for student in students:
            student_uid = student["uid"]
            
            # Chọn courses ngẫu nhiên để enroll
            num_enroll = max(1, int(len(courses) * self.profile["enrollment_rate"]))
            enrolled_courses = random.sample(courses, min(num_enroll, len(courses)))
            
            for course in enrolled_courses:
                course_id = course["id"]
                enroll_ts = random_timestamp(self.profile["days_lookback"], BASE_TIMESTAMP_MS)
                
                # Enrollment
                enroll_id = f"{student_uid}_{course_id}"
                self.data["enrollments"].append({
                    "id": enroll_id,
                    "userId": student_uid,
                    "courseId": course_id,
                    "enrolledAt": enroll_ts,
                })
                enrollment_counter_by_course[course_id] = enrollment_counter_by_course.get(course_id, 0) + 1
                
                # Progress cấp course
                completion_rate = random.choice(PROGRESS_COMPLETION_RATES)
                lessons = [l for l in self.data["lessons"] if l["courseId"] == course_id]
                completed_lessons = int(len(lessons) * completion_rate)
                
                progress_id = enroll_id
                self.data["progress"].append({
                    "userId": student_uid,
                    "courseId": course_id,
                    "lastAccessedAt": BASE_TIMESTAMP_MS - random.randint(0, self.profile["days_lookback"] * 24 * 60 * 60 * 1000),
                    "lastLessonId": lessons[min(completed_lessons, len(lessons)-1)]["id"] if lessons else "",
                    "completedLessons": completed_lessons,
                    "isCompleted": completion_rate == 1.0,
                })
                
                # Lesson progress
                for l_idx, lesson in enumerate(lessons[:completed_lessons + 1]):
                    self.data["lessonProgress"].append({
                        "lessonId": lesson["id"],
                        "userId": student_uid,
                        "courseId": course_id,
                        "isCompleted": l_idx < completed_lessons,
                    })

        for course in courses:
            course["enrollmentCount"] = enrollment_counter_by_course.get(course["id"], 0)
        
        print(f"✅ Tạo {len(self.data['enrollments'])} enrollments + progress")

    def seed_reviews(self):
        """Sinh reviews"""
        students = [u for u in self.data["users"] if u["role"] == "STUDENT"]
        enrollments = self.data["enrollments"]
        
        for enrollment in enrollments:
            if random.random() > 0.5:  # 50% enroll có review
                student = next((u for u in students if u["uid"] == enrollment["userId"]), None)
                if not student:
                    continue
                
                review_id = f"{enrollment['userId']}_{enrollment['courseId']}"
                course = next((c for c in self.data["courses"] if c["id"] == enrollment["courseId"]), None)
                
                self.data["reviews"].append({
                    "id": review_id,
                    "courseId": enrollment["courseId"],
                    "userId": enrollment["userId"],
                    "userName": student["fullName"],
                    "userAvatarUrl": student.get("avatarUrl", ""),
                    "rating": random.randint(*REVIEW_RATING_RANGE),
                    "content": random.choice(REVIEW_CONTENTS),
                    "isEdited": False,
                    "isHidden": False,
                    "createdAt": enrollment["enrolledAt"] + random.randint(24 * 60 * 60 * 1000, 30 * 24 * 60 * 60 * 1000),
                    "updatedAt": BASE_TIMESTAMP_MS,
                })
        
        print(f"✅ Tạo {len(self.data['reviews'])} reviews")

    def seed_quiz_progress(self):
        """Sinh quizProgress cho dữ liệu học tập."""
        enrollments = self.data["enrollments"]
        quizzes_by_course = {}
        for quiz in self.data["quizzes"]:
            quizzes_by_course.setdefault(quiz["courseId"], []).append(quiz)

        for enrollment in enrollments:
            user_id = enrollment["userId"]
            course_id = enrollment["courseId"]
            quizzes = quizzes_by_course.get(course_id, [])

            for quiz in quizzes:
                attempts = random.randint(*QUIZ_ATTEMPTS_RANGE)
                best_score = random.randint(40, 100)
                is_passed = best_score >= quiz["passingScore"]
                total_questions = len(quiz.get("questions", []))
                correct = int(round(total_questions * (best_score / 100.0)))

                self.data["quizProgress"].append({
                    "quizId": quiz["id"],
                    "userId": user_id,
                    "courseId": course_id,
                    "attempts": attempts,
                    "bestScore": best_score,
                    "isPassed": is_passed,
                    "lastAttemptAt": random_timestamp(self.profile["days_lookback"], BASE_TIMESTAMP_MS),
                    "lastAnswers": [random.randint(0, 3) for _ in range(total_questions)],
                    "lastCorrectCount": correct,
                    "lastWrongCount": max(0, total_questions - correct),
                })

        print(f"✅ Tạo {len(self.data['quizProgress'])} quizProgress")

    def seed_carts_orders(self):
        """Sinh carts, cartItems, orders, orderItems"""
        students = [u for u in self.data["users"] if u["role"] == "STUDENT"]
        courses = self.data["courses"]
        
        for student in students:
            student_uid = student["uid"]
            
            # Cart
            cart_id = student_uid  # Cart ID = User ID
            cart_courses = random.sample(courses, min(random.randint(1, 3), len(courses)))
            cart_total = 0
            
            for course in cart_courses:
                cart_total += course["price"]
                self.data["cartItems"].append({
                    "id": f"{student_uid}_{course['id']}",
                    "cartId": cart_id,
                    "userId": student_uid,
                    "courseId": course["id"],
                    "courseThumbnail": course["thumbnailUrl"],
                    "courseTitle": course["title"][:50],
                    "coursePrice": course["price"],
                    "addedAt": random_timestamp(self.profile["days_lookback"], BASE_TIMESTAMP_MS),
                })
            
            if random.random() < self.profile["cart_rate"]:
                self.data["carts"].append({
                    "id": cart_id,
                    "userId": student_uid,
                    "status": random.choice(CART_STATUS),
                    "itemCount": len(cart_courses),
                    "totalAmount": cart_total,
                    "createdAt": random_timestamp(self.profile["days_lookback"], BASE_TIMESTAMP_MS),
                    "updatedAt": BASE_TIMESTAMP_MS,
                })
                
                # Orders - 50% carts được checkout thành công
                if random.random() > 0.5:
                    order_id = f"order_{student_uid}_{random.randint(1000, 9999)}"
                    payee_instructor_id = cart_courses[0]["instructorId"] if cart_courses else ""
                    payee = next((x for x in self.data["instructors"] if x["uid"] == payee_instructor_id), None)
                    transfer_content = f"LMS {student_uid} {order_id}".upper()
                    self.data["orders"].append({
                        "id": order_id,
                        "userId": student_uid,
                        "itemCount": len(cart_courses),
                        "paymentMethod": "E_WALLET",
                        "paymentStatus": "SUCCESS",
                        "totalAmount": cart_total,
                        "createdAt": random_timestamp(self.profile["days_lookback"], BASE_TIMESTAMP_MS),
                        "payeeInstructorId": payee_instructor_id,
                        "bankName": (payee or {}).get("bankName", ""),
                        "bankCode": (payee or {}).get("bankCode", ""),
                        "bankAccountNumber": (payee or {}).get("bankAccountNumber", ""),
                        "bankAccountHolder": (payee or {}).get("bankAccountHolder", ""),
                        "transferContent": transfer_content,
                        "transferContentNormalized": transfer_content.replace(" ", ""),
                        "qrCodeUrl": "",
                        "confirmedAt": random_timestamp(self.profile["days_lookback"], BASE_TIMESTAMP_MS),
                    })
                    
                    for course in cart_courses:
                        self.data["orderItems"].append({
                            "id": f"{order_id}_{course['id']}",
                            "orderId": order_id,
                            "userId": student_uid,
                            "courseId": course["id"],
                            "courseTitle": course["title"][:50],
                            "coursePrice": course["price"],
                            "createdAt": random_timestamp(self.profile["days_lookback"], BASE_TIMESTAMP_MS),
                        })
        
        print(f"✅ Tạo {len(self.data['carts'])} carts + {len(self.data['cartItems'])} cartItems + {len(self.data['orders'])} orders")

    def seed_chat_data(self):
        """Sinh chatSessions và chatMessages cơ bản cho chatbot."""
        students = [u for u in self.data["users"] if u["role"] == "STUDENT"]

        for student in students:
            if random.random() > 0.7:
                continue

            session_id = f"chat_{student['uid']}_{random.randint(1000, 9999)}"
            created_at = random_timestamp(self.profile["days_lookback"], BASE_TIMESTAMP_MS)
            self.data["chatSessions"].append({
                "id": session_id,
                "userId": student["uid"],
                "title": "Hỗ trợ học tập",
                "status": "ACTIVE",
                "lastMessageAt": created_at,
                "createdAt": created_at,
            })

            message_pairs = random.randint(1, 3)
            for idx in range(message_pairs):
                user_ts = created_at + (idx * 60_000)
                bot_ts = user_ts + 10_000
                self.data["chatMessages"].append({
                    "id": f"msg_u_{session_id}_{idx}",
                    "sessionId": session_id,
                    "sender": "USER",
                    "content": "Cho tôi gợi ý khóa học phù hợp",
                    "messageType": "TEXT",
                    "metadata": {},
                    "createdAt": user_ts,
                })
                self.data["chatMessages"].append({
                    "id": f"msg_b_{session_id}_{idx}",
                    "sessionId": session_id,
                    "sender": "BOT",
                    "content": "Mình đã tìm thấy một số khóa học phù hợp cho bạn.",
                    "messageType": "TEXT",
                    "metadata": {},
                    "createdAt": bot_ts,
                })

        print(f"✅ Tạo {len(self.data['chatSessions'])} chatSessions + {len(self.data['chatMessages'])} chatMessages")

    def seed_notifications(self):
        """Sinh notifications"""
        students = [u for u in self.data["users"] if u["role"] == "STUDENT"]
        
        for student in students:
            num_notif = random.randint(2, 5)
            for n_idx in range(num_notif):
                self.data["notifications"].append({
                    "id": f"notif_{student['uid']}_{n_idx}",
                    "userId": student["uid"],
                    "title": f"Thông báo {n_idx + 1}",
                    "body": f"Bạn có {random.choice(['khóa học mới', 'bài kiểm tra', 'tin nhắn'])} từ hệ thống.",
                    "type": random.choice(NOTIFICATION_TYPES),
                    "isRead": random.random() > 0.3,
                    "createdAt": random_timestamp(self.profile["days_lookback"], BASE_TIMESTAMP_MS),
                })
        
        print(f"✅ Tạo {len(self.data['notifications'])} notifications")

    def build_all(self):
        """Sinh tất cả dữ liệu"""
        print(f"\n🚀 Bắt đầu seed dữ liệu với profile: {self.profile}")
        self.seed_users()
        self.seed_categories()
        self.seed_courses()
        self.seed_curriculum()
        self.seed_enrollments_progress()
        self.seed_reviews()
        self.seed_quiz_progress()
        self.seed_carts_orders()
        self.seed_notifications()
        self.seed_chat_data()
        print(f"\n✅ Hoàn thành xây dựng dữ liệu")

    def save_to_json(self, filename: str = "seed_data.json"):
        """Lưu dữ liệu ra JSON"""
        with open(filename, "w", encoding="utf-8") as f:
            json.dump(self.data, f, ensure_ascii=False, indent=2)
        print(f"💾 Lưu vào {filename}")

    def upload_to_firestore(
        self,
        selected_collections: Optional[List[str]] = None,
        max_writes: Optional[int] = None,
        resume_mode: bool = False,
        batch_size: int = 200,
        sleep_ms: int = 400,
    ):
        """Upload lên Firestore với cơ chế tối ưu cho free tier."""
        if self.dry_run or not self.db:
            print("📌 Bỏ qua upload (dry-run hoặc chưa kết nối)")
            return

        if batch_size <= 0:
            print("❌ batch_size phải > 0")
            return

        if max_writes is not None and max_writes <= 0:
            print("❌ max_writes phải > 0")
            return

        all_collections = list(self.data.keys())
        if selected_collections is None:
            collections_to_upload = all_collections
        else:
            invalid = [c for c in selected_collections if c not in self.data]
            if invalid:
                print(f"❌ Collection không hợp lệ: {invalid}")
                print(f"   Hợp lệ: {all_collections}")
                return
            collections_to_upload = selected_collections
        
        print("\n⬆️  Đang upload dữ liệu (free-tier mode)...")
        print(f"   Collections: {collections_to_upload}")
        print(f"   Resume: {resume_mode}")
        print(f"   Batch size: {batch_size}")
        print(f"   Sleep mỗi batch: {sleep_ms}ms")
        if max_writes is not None:
            print(f"   Max writes lần chạy này: {max_writes}")

        total_written = 0
        stopped_by_limit = False
        
        for collection_name in collections_to_upload:
            docs = self.data[collection_name]
            collection_ref = self.db.collection(collection_name)

            docs_to_upload = docs
            skipped_existing = 0

            if resume_mode:
                existing_ids = {doc.id for doc in collection_ref.stream()}
                filtered_docs = []
                for doc in docs:
                    doc_id = doc.get("id") or doc.get("uid")
                    if doc_id and doc_id in existing_ids:
                        skipped_existing += 1
                        continue
                    filtered_docs.append(doc)
                docs_to_upload = filtered_docs

            if max_writes is not None:
                remaining = max_writes - total_written
                if remaining <= 0:
                    stopped_by_limit = True
                    break
                docs_to_upload = docs_to_upload[:remaining]

            if not docs_to_upload:
                print(f"⏭️  Skip {collection_name} (không có doc cần ghi)")
                continue
            
            print(
                f"📦 {collection_name}: target={len(docs)}, "
                f"skip_existing={skipped_existing}, write_now={len(docs_to_upload)}"
            )

            collection_written = 0

            # Tách dữ liệu thành các batch
            for i in range(0, len(docs_to_upload), batch_size):
                batch = self.db.batch()
                batch_docs = docs_to_upload[i:i + batch_size]
                
                for doc in batch_docs:
                    doc_id = doc.get("id") or doc.get("uid") or str(uuid.uuid4())
                    batch.set(collection_ref.document(doc_id), doc)
                
                # Retry logic với exponential backoff
                max_retries = 5
                retry_delay = 2
                for attempt in range(max_retries):
                    try:
                        batch.commit()
                        print(f"  ✓ Batch {i // batch_size + 1}: {len(batch_docs)} docs")
                        break
                    except Exception as e:
                        if "Quota exceeded" in str(e) or "429" in str(e):
                            if attempt < max_retries - 1:
                                wait_time = retry_delay * (2 ** attempt)
                                print(f"  ⚠️  Quota exceeded, retry in {wait_time}s (attempt {attempt + 1}/{max_retries})...")
                                time.sleep(wait_time)
                            else:
                                print(f"  ❌ Max retries exceeded for batch {i // batch_size + 1}: {str(e)[:50]}")
                                print("  ⛔ Dừng sớm để tránh đốt quota thêm")
                                return
                        else:
                            raise

                collection_written += len(batch_docs)
                total_written += len(batch_docs)
                time.sleep(sleep_ms / 1000)
            
            print(f"✅ Uploaded {collection_written}/{len(docs)} docs to {collection_name}")

            if max_writes is not None and total_written >= max_writes:
                stopped_by_limit = True
                break
        
        print(f"🧾 Tổng writes đã dùng: {total_written}")
        if stopped_by_limit:
            print("⏹️  Đã đạt max_writes, dừng theo kế hoạch")
        print("✅ Upload hoàn tất!")

# ============================================
# MAIN
# ============================================

def parse_args():
    parser = argparse.ArgumentParser(description="Seed LMS Firestore")
    parser.add_argument("--profile", default="standard", 
                       choices=list(SEED_PROFILES.keys()),
                       help="Seed profile")
    parser.add_argument("--credentials", default="./serviceAccountKey.json",
                       help="Path to Firebase service account key")
    parser.add_argument("--dry-run", action="store_true",
                       help="Simulate only, don't upload")
    parser.add_argument("--save-json", action="store_true",
                       help="Save to seed_data.json")
    parser.add_argument("--collections", default=None,
                       help="Danh sách collection, cách nhau bởi dấu phẩy")
    parser.add_argument("--resume", action="store_true",
                       help="Chỉ upload docs chưa tồn tại (theo document ID)")
    parser.add_argument("--max-writes", type=int, default=None,
                       help="Giới hạn số write mỗi lần chạy")
    parser.add_argument("--batch-size", type=int, default=200,
                       help="Số docs mỗi batch commit")
    parser.add_argument("--sleep-ms", type=int, default=400,
                       help="Delay giữa các batch (ms)")
    return parser.parse_args()

if __name__ == "__main__":
    args = parse_args()
    
    print(f"""
    ╔══════════════════════════════════════════╗
    ║  LMS Android - Synthetic Data Seeder  ║
    ╚══════════════════════════════════════════╝
    Profile:  {args.profile}
    Dry-run:  {args.dry_run}
    Save JSON: {args.save_json}
    Resume:   {args.resume}
    Max writes: {args.max_writes}
    Batch size: {args.batch_size}
    """)
    
    builder = SeedDataBuilder(args.profile, args.dry_run)
    builder.build_all()
    
    if args.save_json:
        builder.save_to_json()
    
    if not args.dry_run:
        builder.connect_firestore(args.credentials)
        builder.upload_to_firestore(
            selected_collections=parse_collections_arg(args.collections),
            max_writes=args.max_writes,
            resume_mode=args.resume,
            batch_size=args.batch_size,
            sleep_ms=args.sleep_ms,
        )
    
    print("\n✅ Done!")
