from __future__ import annotations

from collections import defaultdict
from typing import DefaultDict, Dict, List


def build_course_index(data: dict) -> Dict[str, dict]:
    """Tra ve map cac course da publish theo course ID de tra cuu nhanh."""
    return {
        course.get("id"): course
        for course in data.get("courses", [])
        if course.get("id") and course.get("isPublished", True)
    }


def build_progress_by_user(data: dict) -> Dict[str, List[dict]]:
    """Nhom cac ban ghi progress theo user ID."""
    progress_by_user: DefaultDict[str, List[dict]] = defaultdict(list)
    for progress in data.get("progress", []):
        user_id = progress.get("userId")
        course_id = progress.get("courseId")
        if user_id and course_id:
            progress_by_user[user_id].append(progress)
    return dict(progress_by_user)


def build_positive_interactions(data: dict, course_by_id: Dict[str, dict]) -> Dict[str, Dict[str, float]]:
    """Quy doi nhieu nguon hanh vi hoc vien thanh tin hieu positive co trong so.

    Dau ra cua ham nay duoc dung lai cho ca huan luyen offline va xay dung
    user profile luc runtime, giup thong nhat logic scoring toan bo stack.
    """
    interactions: DefaultDict[str, Dict[str, float]] = defaultdict(dict)

    for enrollment in data.get("enrollments", []):
        user_id = enrollment.get("userId")
        course_id = enrollment.get("courseId")
        if user_id and course_id in course_by_id:
            interactions[user_id][course_id] = max(interactions[user_id].get(course_id, 0.0), 1.0)

    for progress in data.get("progress", []):
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
        user_id = review.get("userId")
        course_id = review.get("courseId")
        if not user_id or course_id not in course_by_id:
            continue
        rating = float(review.get("rating", 0.0) or 0.0)
        score = max(0.55, min(0.9, 0.5 + (rating / 5.0) * 0.4))
        interactions[user_id][course_id] = max(interactions[user_id].get(course_id, 0.0), score)

    for quiz_progress in data.get("quizProgress", []):
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
    """Lay danh sach hoc vien hop le de train hoac danh gia."""
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
    """Xay dung profile so thich gon nhe tu lich su khoa hoc positive."""
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
