import json
import math
from pathlib import Path
from typing import Dict, Iterable, List


class RecommendationModel:
    def __init__(self):
        self.weights = [0.0] * 7
        self.bias = 0.0
        self.feature_names = [
            "category_affinity",
            "level_affinity",
            "instructor_affinity",
            "rating",
            "enrollment_count",
            "lesson_count",
            "recency_weight"
        ]
        self.is_trained = False

    def extract_features(self, user_profile: Dict, course: Dict, user_enrollments: List[str]) -> List[float]:
        """Extract normalized features for a candidate course."""
        category_affinity = user_profile.get("categoryWeights", {}).get(course.get("categoryId"), 0.0)
        level_affinity = user_profile.get("levelWeights", {}).get(course.get("level"), 0.0)
        instructor_affinity = user_profile.get("instructorWeights", {}).get(course.get("instructorId"), 0.0)

        rating = float(course.get("rating", 0.0)) / 5.0
        enrollment_count = min(float(course.get("enrollmentCount", 0)), 100.0) / 100.0
        lesson_count = min(float(course.get("lessonCount", 0)), 100.0) / 100.0
        recency_weight = 0.0 if course.get("id") in user_enrollments else 1.0

        return [
            float(category_affinity),
            float(level_affinity),
            float(instructor_affinity),
            rating,
            enrollment_count,
            lesson_count,
            recency_weight
        ]

    def predict_scores(
        self,
        user_profile: Dict,
        courses: List[Dict],
        user_enrollments: List[str]
    ) -> Dict[str, float]:
        """Predict recommendation scores for each course using the heuristic path."""
        return self._heuristic_scores(user_profile, courses, user_enrollments)

    def _heuristic_scores(
        self,
        user_profile: Dict,
        courses: List[Dict],
        user_enrollments: List[str]
    ) -> Dict[str, float]:
        scores = {}
        for course in courses:
            scores[course.get("id")] = self._calculate_heuristic(user_profile, course)
        return scores

    def _calculate_heuristic(self, user_profile: Dict, course: Dict) -> float:
        category_weight = user_profile.get("categoryWeights", {}).get(course.get("categoryId"), 0.0)
        level_weight = user_profile.get("levelWeights", {}).get(course.get("level"), 0.0)
        instructor_weight = user_profile.get("instructorWeights", {}).get(course.get("instructorId"), 0.0)

        profile_score = (category_weight * 0.45) + (level_weight * 0.30) + (instructor_weight * 0.25)
        popularity_score = (float(course.get("rating", 0.0)) / 5.0) * 0.5 + (min(float(course.get("enrollmentCount", 0)), 100.0) / 100.0) * 0.5

        return (profile_score * 0.7) + (popularity_score * 0.3)

    def train(self, X_train: Iterable[Iterable[float]], y_train: Iterable[int]):
        """Train a simple linear scorer from positive and negative examples."""
        X_rows = [list(map(float, row)) for row in X_train]
        y_values = [int(value) for value in y_train]

        if not X_rows:
            raise ValueError("Training data is empty")

        if len(set(y_values)) < 2:
            raise ValueError("Training data must contain at least two classes")

        feature_count = len(X_rows[0])
        positive_sum = [0.0] * feature_count
        negative_sum = [0.0] * feature_count
        positive_count = 0
        negative_count = 0

        for features, label in zip(X_rows, y_values):
            if len(features) != feature_count:
                raise ValueError("Inconsistent feature vector lengths")

            if label == 1:
                positive_count += 1
                for index, value in enumerate(features):
                    positive_sum[index] += value
            else:
                negative_count += 1
                for index, value in enumerate(features):
                    negative_sum[index] += value

        positive_mean = [value / positive_count if positive_count else 0.0 for value in positive_sum]
        negative_mean = [value / negative_count if negative_count else 0.0 for value in negative_sum]

        self.weights = [positive_mean[index] - negative_mean[index] for index in range(feature_count)]
        self.bias = math.log((positive_count + 1.0) / (negative_count + 1.0))
        self.is_trained = True

    def save(self, filepath: str):
        """Save model weights to JSON."""
        payload = {
            "weights": self.weights,
            "bias": self.bias,
            "feature_names": self.feature_names
        }
        path = Path(filepath)
        path.parent.mkdir(parents=True, exist_ok=True)
        with path.open("w", encoding="utf-8") as file:
            json.dump(payload, file, ensure_ascii=False, indent=2)

    def load(self, filepath: str):
        """Load model weights from JSON."""
        path = Path(filepath)
        with path.open("r", encoding="utf-8") as file:
            data = json.load(file)

        self.weights = [float(value) for value in data.get("weights", [])]
        self.bias = float(data.get("bias", 0.0))
        self.feature_names = list(data.get("feature_names", self.feature_names))
        self.is_trained = len(self.weights) == len(self.feature_names)

    def _linear_score(self, features: List[float]) -> float:
        return sum(weight * value for weight, value in zip(self.weights, features)) + self.bias

    @staticmethod
    def _sigmoid(value: float) -> float:
        if value >= 0:
            z = math.exp(-value)
            return 1.0 / (1.0 + z)
        z = math.exp(value)
        return z / (1.0 + z)
