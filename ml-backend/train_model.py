from __future__ import annotations

import argparse
import json
from collections import defaultdict
from pathlib import Path
from typing import Dict, List

from model import RecommendationModel

DEFAULT_SEED_DATA_PATH = Path(__file__).resolve().parent.parent / "scripts" / "seed" / "seed_data.json"
DEFAULT_MODEL_PATH = Path(__file__).resolve().parent / "artifacts" / "recommendation_model.json"


def load_seed_data(seed_data_path: Path) -> dict:
    with seed_data_path.open("r", encoding="utf-8") as file:
        return json.load(file)


def build_course_index(seed_data: dict) -> Dict[str, dict]:
    return {
        course.get("id"): course
        for course in seed_data.get("courses", [])
        if course.get("id") and course.get("isPublished", True)
    }


def build_enrollments_by_user(seed_data: dict) -> Dict[str, List[str]]:
    enrollments_by_user: Dict[str, List[str]] = defaultdict(list)
    for enrollment in seed_data.get("enrollments", []):
        user_id = enrollment.get("userId")
        course_id = enrollment.get("courseId")
        if user_id and course_id:
            enrollments_by_user[user_id].append(course_id)
    return enrollments_by_user


def build_progress_by_user(seed_data: dict) -> Dict[str, List[dict]]:
    progress_by_user: Dict[str, List[dict]] = defaultdict(list)
    for progress in seed_data.get("progress", []):
        user_id = progress.get("userId")
        course_id = progress.get("courseId")
        if user_id and course_id:
            progress_by_user[user_id].append(progress)
    return progress_by_user


def build_user_profile(
    user_id: str,
    enrolled_courses: List[dict],
    progress_records: List[dict]
) -> dict:
    if not enrolled_courses:
        return {
            "categoryWeights": {},
            "levelWeights": {},
            "instructorWeights": {}
        }

    category_weights: Dict[str, float] = {}
    level_weights: Dict[str, float] = {}
    instructor_weights: Dict[str, float] = {}
    progress_weights: Dict[str, float] = {}

    for progress in progress_records:
        if progress.get("userId") != user_id:
            continue

        course_id = progress.get("courseId")
        completed_lessons = progress.get("completedLessons", 0)

        for course in enrolled_courses:
            if course.get("id") == course_id:
                total_lessons = course.get("lessonCount", 1)
                if total_lessons > 0:
                    progress_weight = max(0.5, (completed_lessons / total_lessons) * 2.0)
                else:
                    progress_weight = 0.5
                progress_weights[course_id] = progress_weight
                break

    for course in enrolled_courses:
        weight = progress_weights.get(course.get("id"), 0.5)

        category = course.get("categoryId")
        if category:
            category_weights[category] = category_weights.get(category, 0.0) + weight

        level = course.get("level")
        if level:
            level_weights[level] = level_weights.get(level, 0.0) + weight

        instructor = course.get("instructorId")
        if instructor:
            instructor_weights[instructor] = instructor_weights.get(instructor, 0.0) + weight

    total_weight = len(enrolled_courses)
    if total_weight > 0:
        for key in category_weights:
            category_weights[key] /= total_weight
        for key in level_weights:
            level_weights[key] /= total_weight
        for key in instructor_weights:
            instructor_weights[key] /= total_weight

    return {
        "categoryWeights": category_weights,
        "levelWeights": level_weights,
        "instructorWeights": instructor_weights
    }


def build_training_set(seed_data: dict) -> tuple[list[list[float]], list[int]]:
    model = RecommendationModel()
    course_by_id = build_course_index(seed_data)
    enrollments_by_user = build_enrollments_by_user(seed_data)
    progress_by_user = build_progress_by_user(seed_data)

    student_ids = [
        user.get("uid")
        for user in seed_data.get("users", [])
        if user.get("role") == "STUDENT" and user.get("uid")
    ]

    X_train: List[List[float]] = []
    y_train: List[int] = []
    published_courses = list(course_by_id.values())

    for user_id in student_ids:
        enrolled_course_ids = list(dict.fromkeys(enrollments_by_user.get(user_id, [])))
        enrolled_courses = [course_by_id[course_id] for course_id in enrolled_course_ids if course_id in course_by_id]
        user_profile = build_user_profile(
            user_id=user_id,
            enrolled_courses=enrolled_courses,
            progress_records=progress_by_user.get(user_id, [])
        )

        for course in published_courses:
            label = 1 if course.get("id") in enrolled_course_ids else 0
            features = model.extract_features(user_profile, course, enrolled_course_ids)
            X_train.append(features)
            y_train.append(label)

    if not X_train:
        raise ValueError("No training samples were generated from seed data.")

    return X_train, y_train


def train_and_save(seed_data_path: Path, model_path: Path) -> None:
    seed_data = load_seed_data(seed_data_path)
    X_train, y_train = build_training_set(seed_data)

    if len(set(y_train)) < 2:
        raise ValueError("Training data must contain at least two classes.")

    model = RecommendationModel()
    model.train(X_train, y_train)

    model_path.parent.mkdir(parents=True, exist_ok=True)
    model.save(str(model_path))

    positive_count = sum(1 for value in y_train if value == 1)
    negative_count = sum(1 for value in y_train if value == 0)
    print(f"Trained recommendation model on {len(y_train)} samples")
    print(f"Positive samples: {positive_count}")
    print(f"Negative samples: {negative_count}")
    print(f"Saved model weights to: {model_path}")


def main() -> None:
    parser = argparse.ArgumentParser(description="Train the recommendation model from seed data.")
    parser.add_argument(
        "--seed-data",
        type=Path,
        default=DEFAULT_SEED_DATA_PATH,
        help="Path to seed_data.json"
    )
    parser.add_argument(
        "--output",
        type=Path,
        default=DEFAULT_MODEL_PATH,
        help="Path to save the trained model artifact"
    )
    args = parser.parse_args()

    train_and_save(args.seed_data, args.output)


if __name__ == "__main__":
    main()
