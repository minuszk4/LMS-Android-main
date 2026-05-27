from __future__ import annotations

from collections import defaultdict
from typing import DefaultDict, Dict, List


def build_course_index(data: dict) -> Dict[str, dict]:
    """Trả về map các course đã publish theo course ID để tra cứu nhanh.

    Hàm này là lớp chỉ mục cơ bản nhất của pipeline recommendation.
    Chỉ những course đã publish mới được đưa vào candidate pool để:
    - tránh train hoặc suy luận trên course chưa công khai,
    - giữ cho app chỉ nhận các gợi ý có thể hiển thị thật.
    """
    return {
        course.get("id"): course
        for course in data.get("courses", [])
        if course.get("id") and course.get("isPublished", True)
    }


def build_progress_by_user(data: dict) -> Dict[str, List[dict]]:
    """Nhóm các bản ghi progress theo user ID.

    Cấu trúc này được dùng lại ở cả runtime scoring và offline training
    để tính mức độ học sâu của từng user trên từng course.
    """
    progress_by_user: DefaultDict[str, List[dict]] = defaultdict(list)
    for progress in data.get("progress", []):
        user_id = progress.get("userId")
        course_id = progress.get("courseId")
        if user_id and course_id:
            progress_by_user[user_id].append(progress)
    return dict(progress_by_user)


def build_positive_interactions(data: dict, course_by_id: Dict[str, dict]) -> Dict[str, Dict[str, float]]:
    """Quy đổi nhiều nguồn hành vi thành tín hiệu dương có trọng số.

    Thay vì chỉ coi `enroll` là một positive signal nhị phân, pipeline gom thêm:
    - progress,
    - review,
    - quiz progress,
    - add-to-cart,
    - purchase thành công.

    Mỗi nguồn được quy đổi sang một mức điểm khác nhau để phản ánh
    cường độ quan tâm thực tế của user với course.
    """
    interactions: DefaultDict[str, Dict[str, float]] = defaultdict(dict)

    for enrollment in data.get("enrollments", []):
        # Enroll là tín hiệu dương mạnh nhất ở phía nghiệp vụ học tập.
        user_id = enrollment.get("userId")
        course_id = enrollment.get("courseId")
        if user_id and course_id in course_by_id:
            interactions[user_id][course_id] = max(interactions[user_id].get(course_id, 0.0), 1.0)

    for progress in data.get("progress", []):
        # Progress phản ánh user có học thật hay chỉ đăng ký rồi bỏ đó.
        user_id = progress.get("userId")
        course_id = progress.get("courseId")
        if not user_id or course_id not in course_by_id:
            continue
        lesson_count = max(int(course_by_id[course_id].get("lessonCount", 0) or 0), 0)
        completed_lessons = max(int(progress.get("completedLessons", 0) or 0), 0)
        ratio = (completed_lessons / lesson_count) if lesson_count > 0 else 0.0
        score = max(0.45, min(0.9, 0.45 + ratio * 0.55))
        interactions[user_id][course_id] = max(interactions[user_id].get(course_id, 0.0), score)

    for review in data.get("reviews", []):
        # Review có trọng số dương vì người dùng đã đủ quan tâm để để lại đánh giá.
        user_id = review.get("userId")
        course_id = review.get("courseId")
        if not user_id or course_id not in course_by_id:
            continue
        rating = float(review.get("rating", 0.0) or 0.0)
        score = max(0.55, min(0.9, 0.5 + (rating / 5.0) * 0.4))
        interactions[user_id][course_id] = max(interactions[user_id].get(course_id, 0.0), score)

    for quiz_progress in data.get("quizProgress", []):
        # Quiz là tín hiệu engagement sâu hơn việc chỉ mở course.
        user_id = quiz_progress.get("userId")
        course_id = quiz_progress.get("courseId")
        if not user_id or course_id not in course_by_id:
            continue
        attempts = int(quiz_progress.get("attempts", 0) or 0)
        best_score = float(quiz_progress.get("bestScore", 0.0) or 0.0)
        passed_bonus = 0.1 if bool(quiz_progress.get("isPassed")) else 0.0
        score = min(0.9, 0.35 + min(attempts, 5) * 0.08 + (best_score / 100.0) * 0.25 + passed_bonus)
        interactions[user_id][course_id] = max(interactions[user_id].get(course_id, 0.0), score)

    for cart_item in data.get("cartItems", []):
        # Add-to-cart yếu hơn enroll/purchase nhưng vẫn đáng xem là ý định tích cực.
        user_id = cart_item.get("userId")
        course_id = cart_item.get("courseId")
        if user_id and course_id in course_by_id:
            interactions[user_id][course_id] = max(interactions[user_id].get(course_id, 0.0), 0.35)

    successful_orders = {
        order.get("id"): order
        for order in data.get("orders", [])
        if str(order.get("paymentStatus") or order.get("status") or "").upper() == "SUCCESS"
    }

    for order_item in data.get("orderItems", []):
        # Purchase thành công được nâng lên mức tín hiệu tối đa tương đương enroll mạnh.
        order_id = order_item.get("orderId")
        order = successful_orders.get(order_id)
        if not order:
            continue
        user_id = order.get("userId")
        course_id = order_item.get("courseId")
        if user_id and course_id in course_by_id:
            interactions[user_id][course_id] = max(interactions[user_id].get(course_id, 0.0), 1.0)

    return {user_id: dict(scores) for user_id, scores in interactions.items()}


def list_student_ids(data: dict, interactions: Dict[str, Dict[str, float]]) -> List[str]:
    """Lấy danh sách học viên hợp lệ để train hoặc đánh giá.

    Ưu tiên đọc từ collection `users` để giữ đúng role STUDENT.
    Nếu snapshot user không đầy đủ, fallback sang tập user xuất hiện trong interactions.
    """
    student_ids = [
        user.get("uid")
        for user in data.get("users", [])
        if user.get("uid") and str(user.get("role") or "").upper() == "STUDENT"
    ]
    if student_ids:
        return student_ids
    return sorted(interactions.keys())


def build_user_profile(
    user_id: str,
    positive_course_ids: List[str],
    course_by_id: Dict[str, dict],
    progress_records: List[dict]
) -> dict:
    """Xây dựng profile sở thích gọn nhẹ từ lịch sử positive courses.

    Profile đầu ra là contract chung giữa:
    - app Android khi tính heuristic,
    - backend lúc suy luận model,
    - pipeline train lúc trích xuất feature.
    """
    if not positive_course_ids:
        return {
            "categoryWeights": {},
            "levelWeights": {},
            "instructorWeights": {}
        }

    category_weights: Dict[str, float] = {}
    level_weights: Dict[str, float] = {}
    instructor_weights: Dict[str, float] = {}
    progress_weights: Dict[str, float] = {}
    weighted_prices: List[float] = []

    enrolled_courses = [course_by_id[course_id] for course_id in positive_course_ids if course_id in course_by_id]

    for progress in progress_records:
        # Tiến độ cao hơn làm tăng trọng số của course trong hồ sơ người dùng.
        if progress.get("userId") != user_id:
            continue

        course_id = progress.get("courseId")
        if course_id not in positive_course_ids:
            continue

        completed_lessons = int(progress.get("completedLessons", 0) or 0)
        total_lessons = int(course_by_id.get(course_id, {}).get("lessonCount", 1) or 1)
        if total_lessons > 0:
            progress_weight = max(0.5, (completed_lessons / total_lessons) * 2.0)
        else:
            progress_weight = 0.5
        progress_weights[course_id] = progress_weight

    for course in enrolled_courses:
        # Mỗi course đóng góp vào category, level, instructor và price affinity.
        weight = progress_weights.get(course.get("id"), 0.5)
        price = float(course.get("price", 0.0) or 0.0)
        if price > 0.0:
            weighted_prices.extend([price] * max(1, int(round(weight * 2))))

        category = course.get("categoryId")
        if category:
            category_weights[category] = category_weights.get(category, 0.0) + weight

        level = course.get("level")
        if level:
            level_weights[level] = level_weights.get(level, 0.0) + weight

        instructor = course.get("instructorId")
        if instructor:
            instructor_weights[instructor] = instructor_weights.get(instructor, 0.0) + weight

    # Chuẩn hóa các trọng số để profile ổn định hơn giữa user học ít và user học nhiều.
    total_weight = float(len(enrolled_courses)) if enrolled_courses else 1.0
    for key in list(category_weights.keys()):
        category_weights[key] /= total_weight
    for key in list(level_weights.keys()):
        level_weights[key] /= total_weight
    for key in list(instructor_weights.keys()):
        instructor_weights[key] /= total_weight

    return {
        "categoryWeights": category_weights,
        "levelWeights": level_weights,
        "instructorWeights": instructor_weights,
        "priceStats": {
            "averagePrice": (sum(weighted_prices) / len(weighted_prices)) if weighted_prices else 0.0,
            "minPrice": min(weighted_prices) if weighted_prices else 0.0,
            "maxPrice": max(weighted_prices) if weighted_prices else 0.0,
        }
    }
