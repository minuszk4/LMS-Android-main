"""
Kiểm tra chất lượng dữ liệu synthetic được seed
Chạy: python validate_data.py --json seed_data.json
"""

import json
import argparse
import sys
from collections import defaultdict
from typing import Dict, List, Any

class DataValidator:
    def __init__(self, data: Dict[str, List[Any]]):
        self.data = data
        self.errors = []
        self.warnings = []
        self.stats = {}

    def validate_all(self):
        """Chạy tất cả kiểm tra"""
        print("🔍 Bắt đầu kiểm tra dữ liệu...")
        
        self._check_count_summary()
        self._check_no_orphans()
        self._check_unique_constraints()
        self._check_enum_values()
        self._check_business_rules()
        
        self._print_report()

    def _check_count_summary(self):
        """Thống kê số lượng record"""
        print("\n📊 Thống kê:")
        for collection, docs in self.data.items():
            count = len(docs)
            self.stats[collection] = count
            if count > 0:
                print(f"  {collection:20} : {count:4} records")

    def _check_no_orphans(self):
        """Kiểm tra FK orphan (record FK mà PK ko tồn tại)"""
        print("\n🔗 Kiểm tra Foreign Key...")
        
        uid_set = {u["uid"] for u in self.data.get("users", [])}
        course_set = {c["id"] for c in self.data.get("courses", [])}
        lesson_set = {l["id"] for l in self.data.get("lessons", [])}
        quiz_set = {q["id"] for q in self.data.get("quizzes", [])}
        category_set = {c["id"] for c in self.data.get("categories", [])}
        cart_set = {c["id"] for c in self.data.get("carts", [])}
        cart_item_set = {ci["id"] for ci in self.data.get("cartItems", [])}
        order_set = {o["id"] for o in self.data.get("orders", [])}
        chat_session_set = {s["id"] for s in self.data.get("chatSessions", [])}
        
        # Check courses FK
        for course in self.data.get("courses", []):
            if course["instructorId"] not in uid_set:
                self.errors.append(f"Course {course['id']}: instructorId {course['instructorId']} không tồn tại")
            if course["categoryId"] not in category_set:
                self.errors.append(f"Course {course['id']}: categoryId {course['categoryId']} không tồn tại")
        
        # Check lessons FK
        for lesson in self.data.get("lessons", []):
            if lesson["courseId"] not in course_set:
                self.errors.append(f"Lesson {lesson['id']}: courseId {lesson['courseId']} không tồn tại")
        
        # Check quizzes FK
        for quiz in self.data.get("quizzes", []):
            if quiz["courseId"] not in course_set:
                self.errors.append(f"Quiz {quiz['id']}: courseId {quiz['courseId']} không tồn tại")
        
        # Check enrollments FK
        for enrollment in self.data.get("enrollments", []):
            if enrollment["userId"] not in uid_set:
                self.errors.append(f"Enrollment {enrollment['id']}: userId {enrollment['userId']} không tồn tại")
            if enrollment["courseId"] not in course_set:
                self.errors.append(f"Enrollment {enrollment['id']}: courseId {enrollment['courseId']} không tồn tại")
        
        # Check reviews FK
        for review in self.data.get("reviews", []):
            if review["userId"] not in uid_set:
                self.errors.append(f"Review {review['id']}: userId {review['userId']} không tồn tại")
            if review["courseId"] not in course_set:
                self.errors.append(f"Review {review['id']}: courseId {review['courseId']} không tồn tại")
        
        # Check cartItems FK
        for ci in self.data.get("cartItems", []):
            if ci["userId"] not in uid_set:
                self.errors.append(f"CartItem {ci['id']}: userId {ci['userId']} không tồn tại")
            if ci["courseId"] not in course_set:
                self.errors.append(f"CartItem {ci['id']}: courseId {ci['courseId']} không tồn tại")
        
        # Check orderItems FK
        for oi in self.data.get("orderItems", []):
            if oi["userId"] not in uid_set:
                self.errors.append(f"OrderItem {oi['id']}: userId {oi['userId']} không tồn tại")
            if oi["courseId"] not in course_set:
                self.errors.append(f"OrderItem {oi['id']}: courseId {oi['courseId']} không tồn tại")
        
        # Check notifications FK
        for notif in self.data.get("notifications", []):
            if notif["userId"] not in uid_set:
                self.errors.append(f"Notification {notif['id']}: userId {notif['userId']} không tồn tại")
        
        # Check chatMessages FK
        for msg in self.data.get("chatMessages", []):
            if msg.get("sessionId") and msg["sessionId"] not in chat_session_set:
                self.errors.append(f"ChatMessage {msg['id']}: sessionId {msg['sessionId']} không tồn tại")
        
        if not self.errors or len([e for e in self.errors if "không tồn tại" in e]) == 0:
            print("  ✅ Không có orphan records")

    def _check_unique_constraints(self):
        """Kiểm tra unique constraints"""
        print("\n🔐 Kiểm tra Unique Constraints...")
        
        # userId_courseId unique trong enrollments
        enroll_ids = defaultdict(int)
        for e in self.data.get("enrollments", []):
            enroll_ids[e["id"]] += 1
            if enroll_ids[e["id"]] > 1:
                self.errors.append(f"Enrollment {e['id']}: độc duplicate (user+course)")
        
        # userId_courseId unique trong reviews
        review_ids = defaultdict(int)
        for r in self.data.get("reviews", []):
            review_ids[r["id"]] += 1
            if review_ids[r["id"]] > 1:
                self.errors.append(f"Review {r['id']}: duplicate (user+course)")
        
        # userId_courseId unique trong cartItems
        cart_item_ids = defaultdict(int)
        for ci in self.data.get("cartItems", []):
            cart_item_ids[ci["id"]] += 1
            if cart_item_ids[ci["id"]] > 1:
                self.errors.append(f"CartItem {ci['id']}: duplicate (user+course)")
        
        if len(enroll_ids) == len(self.data.get("enrollments", [])):
            print(f"  ✅ Enrollments unique (n={len(enroll_ids)})")
        if len(review_ids) == len(self.data.get("reviews", [])):
            print(f"  ✅ Reviews unique (n={len(review_ids)})")
        if len(cart_item_ids) == len(self.data.get("cartItems", [])):
            print(f"  ✅ CartItems unique (n={len(cart_item_ids)})")

    def _check_enum_values(self):
        """Kiểm tra giá trị enum hợp lệ"""
        print("\n📋 Kiểm tra Enum Values...")
        
        valid_values = {
            "users.role": ["STUDENT", "INSTRUCTOR"],
            "courses.level": ["BEGINNER", "INTERMEDIATE", "ADVANCED"],
            "courses.isPublished": [True, False],
            "carts.status": ["ACTIVE", "CHECKED_OUT", "ABANDONED"],
            "chatSessions.status": ["ACTIVE", "ARCHIVED", "CLOSED"],
            "chatMessages.sender": ["USER", "BOT", "SYSTEM"],
            "orders.paymentMethod": ["E_WALLET"],
            "orders.paymentStatus": ["SUCCESS", "PENDING", "FAILED", "CANCELED"],
        }
        
        # Users role
        for user in self.data.get("users", []):
            if user.get("role") not in valid_values["users.role"]:
                self.errors.append(f"User {user['uid']}: role '{user.get('role')}' không hợp lệ")
        
        # Courses level
        for course in self.data.get("courses", []):
            if course.get("level") not in valid_values["courses.level"]:
                self.errors.append(f"Course {course['id']}: level '{course.get('level')}' không hợp lệ")
        
        # Carts status
        for cart in self.data.get("carts", []):
            if cart.get("status") not in valid_values["carts.status"]:
                self.errors.append(f"Cart {cart['id']}: status '{cart.get('status')}' không hợp lệ")
        
        # ChatSessions status
        for session in self.data.get("chatSessions", []):
            if session.get("status") not in valid_values["chatSessions.status"]:
                self.errors.append(f"ChatSession {session['id']}: status '{session.get('status')}' không hợp lệ")
        
        # Orders payment
        for order in self.data.get("orders", []):
            if order.get("paymentMethod") not in valid_values["orders.paymentMethod"]:
                self.errors.append(f"Order {order['id']}: paymentMethod '{order.get('paymentMethod')}' không hợp lệ")
            if order.get("paymentStatus") not in valid_values["orders.paymentStatus"]:
                self.errors.append(f"Order {order['id']}: paymentStatus '{order.get('paymentStatus')}' không hợp lệ")
        
        print("  ✅ Tất cả enum values hợp lệ")

    def _check_business_rules(self):
        """Kiểm tra quy tắc nghiệp vụ"""
        print("\n✅ Kiểm tra Business Rules...")
        
        # Review rating 1..5
        for review in self.data.get("reviews", []):
            if not (1 <= review.get("rating", 0) <= 5):
                self.errors.append(f"Review {review['id']}: rating {review.get('rating')} ngoài range 1..5")
        
        # Progress completedLessons <= lessonCount
        for progress in self.data.get("progress", []):
            course_id = progress["courseId"]
            course = next((c for c in self.data.get("courses", []) if c["id"] == course_id), None)
            if course and progress["completedLessons"] > course["lessonCount"]:
                self.warnings.append(f"Progress {progress['userId']}_{course_id}: completedLessons > lessonCount")
        
        # Carts itemCount hợp lệ
        for cart in self.data.get("carts", []):
            cart_items = [ci for ci in self.data.get("cartItems", []) if ci["cartId"] == cart["id"]]
            if cart["itemCount"] != len(cart_items):
                self.warnings.append(f"Cart {cart['id']}: itemCount {cart['itemCount']} != actual {len(cart_items)}")
        
        print("  ✅ Business rules hợp lệ")

    def _print_report(self):
        """In báo cáo kiểm tra"""
        print("\n" + "="*50)
        print("📋 KẾT QUẢ KIỂM TRA")
        print("="*50)
        
        if self.errors:
            print(f"\n❌ Lỗi ({len(self.errors)}):")
            for i, err in enumerate(self.errors[:10], 1):
                print(f"   {i}. {err}")
            if len(self.errors) > 10:
                print(f"   ... và {len(self.errors) - 10} lỗi khác")
        else:
            print(f"\n✅ PASSOU: Không có lỗi!")
        
        if self.warnings:
            print(f"\n⚠️  Cảnh báo ({len(self.warnings)}):")
            for i, warn in enumerate(self.warnings[:5], 1):
                print(f"   {i}. {warn}")
            if len(self.warnings) > 5:
                print(f"   ... và {len(self.warnings) - 5} cảnh báo khác")
        
        print("\n" + "="*50)
        total_records = sum(len(docs) for docs in self.data.values())
        print(f"📊 Tổng record: {total_records}")
        print(f"❌ Lỗi: {len(self.errors)}")
        print(f"⚠️  Cảnh báo: {len(self.warnings)}")
        print(f"✅ Status: {'PASSED' if not self.errors else 'FAILED'}")
        print("="*50 + "\n")

def main():
    parser = argparse.ArgumentParser(description="Validate synthetic data")
    parser.add_argument("--json", default="seed_data.json", help="Path to seed_data.json")
    args = parser.parse_args()
    
    try:
        with open(args.json, "r", encoding="utf-8") as f:
            data = json.load(f)
    except FileNotFoundError:
        print(f"❌ File {args.json} không tồn tại")
        sys.exit(1)
    except json.JSONDecodeError:
        print(f"❌ File {args.json} không phải JSON hợp lệ")
        sys.exit(1)
    
    validator = DataValidator(data)
    validator.validate_all()
    
    sys.exit(0 if not validator.errors else 1)

if __name__ == "__main__":
    main()
