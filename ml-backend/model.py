"""Model tinh diem recommendation duoc dung trong ML backend cua LMS.

Lop nay ho tro ba che do phuc vu:
1. RandomForest da duoc train,
2. artifact linear cu de tuong thich nguoc,
3. fallback heuristic khi khong nap duoc artifact.
"""

import json
import pickle
import time
from pathlib import Path
from typing import Dict, List, Optional

import numpy as np
from sklearn.ensemble import RandomForestClassifier
from sklearn.preprocessing import StandardScaler

class RecommendationModel:
    """Dong goi logic trich xuat feature, train, score va luu/nap model."""

    def __init__(self):
        self.model = None
        self.scaler = StandardScaler()
        self.linear_weights: Optional[np.ndarray] = None
        self.linear_bias: float = 0.0
        self.feature_names = [
            'category_affinity',
            'level_affinity',
            'instructor_affinity',
            'price_affinity',
            'rating',
            'review_count',
            'enrollment_count',
            'lesson_count',
            'course_freshness',
            'popularity_score',
            'recency_weight'
        ]
    
    def extract_features(self, user_profile: Dict, course: Dict, user_enrollments: List[str]) -> np.ndarray:
        """
        Extract features for a course given a user profile.
        
        Vector feature tron cac tin hieu ve so thich hoc vien, chat luong
        khoa hoc va mot so heuristic nghiep vu nhu do moi cua course.
        """
        category_affinity = float(user_profile.get('categoryWeights', {}).get(course.get('categoryId'), 0.0))
        level_affinity = float(user_profile.get('levelWeights', {}).get(course.get('level'), 0.0))
        instructor_affinity = float(user_profile.get('instructorWeights', {}).get(course.get('instructorId'), 0.0))

        price_affinity = self._calculate_price_affinity(user_profile, float(course.get('price', 0.0) or 0.0))
        rating = float(course.get('rating', 0.0) or 0.0) / 5.0
        review_count = min(int(course.get('reviewCount', 0) or 0), 200) / 200.0
        enrollment_count = min(int(course.get('enrollmentCount', 0) or 0), 500) / 500.0
        lesson_count = min(int(course.get('lessonCount', 0) or 0), 200) / 200.0

        course_freshness = self._calculate_course_freshness(course)
        popularity_score = (rating * 0.45) + (review_count * 0.20) + (enrollment_count * 0.35)

        recency_weight = 0.0 if course.get('id') in user_enrollments else 1.0

        feature_map = {
            'category_affinity': category_affinity,
            'level_affinity': level_affinity,
            'instructor_affinity': instructor_affinity,
            'price_affinity': price_affinity,
            'rating': rating,
            'review_count': review_count,
            'enrollment_count': enrollment_count,
            'lesson_count': lesson_count,
            'course_freshness': course_freshness,
            'popularity_score': popularity_score,
            'recency_weight': recency_weight,
        }

        return np.asarray([float(feature_map.get(name, 0.0)) for name in self.feature_names], dtype=float)
    
    def predict_scores(
        self,
        user_profile: Dict,
        courses: List[Dict],
        user_enrollments: List[str]
    ) -> Dict[str, float]:
        """
        Predict recommendation scores for courses.
        
        Ham uu tien dung artifact da train, nhung van co the fallback sang
        artifact linear cu hoac heuristic thuần khi can.
        """
        if self.model is None and self.linear_weights is None:
            return self._heuristic_scores(user_profile, courses, user_enrollments)

        scores = {}
        for course in courses:
            features = self.extract_features(user_profile, course, user_enrollments)
            if self.model is not None:
                features_scaled = self.scaler.transform([features])[0]

                try:
                    prob = self.model.predict_proba([features_scaled])[0]
                    score = prob[1] if len(prob) > 1 else 0.5
                    scores[course.get('id')] = float(score)
                    continue
                except Exception:
                    pass

            if self.linear_weights is not None:
                score = self._calculate_linear_score(features)
                scores[course.get('id')] = float(score)
            else:
                scores[course.get('id')] = self._calculate_heuristic(user_profile, course)
        
        return scores
    
    def _heuristic_scores(
        self,
        user_profile: Dict,
        courses: List[Dict],
        user_enrollments: List[str]
    ) -> Dict[str, float]:
        """Fallback heuristic scoring."""
        scores = {}
        for course in courses:
            score = self._calculate_heuristic(user_profile, course)
            scores[course.get('id')] = score
        return scores
    
    def _calculate_heuristic(self, user_profile: Dict, course: Dict) -> float:
        """Calculate heuristic score for a course."""
        category_weight = user_profile.get('categoryWeights', {}).get(course.get('categoryId'), 0.0)
        level_weight = user_profile.get('levelWeights', {}).get(course.get('level'), 0.0)
        instructor_weight = user_profile.get('instructorWeights', {}).get(course.get('instructorId'), 0.0)
        price_affinity = self._calculate_price_affinity(user_profile, float(course.get('price', 0.0) or 0.0))
        rating = float(course.get('rating', 0.0) or 0.0) / 5.0
        review_score = min(int(course.get('reviewCount', 0) or 0), 200) / 200.0
        popularity_score = rating * 0.45 + (min(course.get('enrollmentCount', 0), 500) / 500.0) * 0.35 + review_score * 0.20
        freshness_score = self._calculate_course_freshness(course)

        profile_score = (
            category_weight * 0.33
            + level_weight * 0.20
            + instructor_weight * 0.17
            + price_affinity * 0.15
            + freshness_score * 0.15
        )

        return (profile_score * 0.62) + (popularity_score * 0.38)
    
    def train(self, X_train: np.ndarray, y_train: np.ndarray):
        """Train the recommendation model."""
        self.train_with_sample_weights(X_train, y_train, sample_weight=None)

    def train_with_sample_weights(
        self,
        X_train: np.ndarray,
        y_train: np.ndarray,
        sample_weight: Optional[np.ndarray]
    ) -> None:
        """Train the recommendation model with optional sample weights."""
        # Chuan hoa feature de bo phan classifier nhin thay cac thang do so
        # on dinh giua engagement, popularity va cac truong lien quan gia.
        X_scaled = self.scaler.fit_transform(X_train)

        self.model = RandomForestClassifier(
            n_estimators=100,
            max_depth=10,
            random_state=42,
            n_jobs=-1
        )
        fit_kwargs = {}
        if sample_weight is not None:
            fit_kwargs["sample_weight"] = sample_weight
        self.model.fit(X_scaled, y_train, **fit_kwargs)
        self.linear_weights = None
        self.linear_bias = 0.0
    
    def save(self, filepath: str):
        """Save model to file."""
        path = Path(filepath)
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(self.dump_bytes())
    
    def load(self, filepath: str):
        """Load model from file."""
        path = Path(filepath)
        if path.suffix.lower() == ".json":
            self._load_legacy_json(path.read_text(encoding="utf-8"))
            return
        self.load_bytes(path.read_bytes())

    def dump_bytes(self) -> bytes:
        """Serialize model state to bytes."""
        state = {
            'model': self.model,
            'scaler': self.scaler,
            'feature_names': self.feature_names,
            'linear_weights': self.linear_weights,
            'linear_bias': self.linear_bias
        }
        return pickle.dumps(state)

    def load_bytes(self, payload: bytes) -> None:
        """Deserialize model state from bytes."""
        data = pickle.loads(payload)
        self.model = data.get('model')
        self.scaler = data.get('scaler', StandardScaler())
        self.feature_names = data.get('feature_names', self.feature_names)
        self.linear_weights = data.get('linear_weights')
        self.linear_bias = float(data.get('linear_bias', 0.0))

    @property
    def is_trained(self) -> bool:
        return self.model is not None or self.linear_weights is not None

    def _load_legacy_json(self, raw_json: str) -> None:
        """Load the legacy linear-weight artifact already present in the repo."""
        payload = json.loads(raw_json)
        weights = payload.get("weights") or []
        if len(weights) != len(self.feature_names):
            raise ValueError("Legacy weight artifact does not match expected feature size")
        self.model = None
        self.scaler = StandardScaler()
        self.linear_weights = np.asarray(weights, dtype=float)
        self.linear_bias = float(payload.get("bias", 0.0))
        self.feature_names = payload.get("feature_names", self.feature_names)

    def _calculate_linear_score(self, features: np.ndarray) -> float:
        """Tinh diem cho mot vector feature bang artifact linear cu."""
        logits = float(np.dot(features, self.linear_weights) + self.linear_bias)
        return 1.0 / (1.0 + np.exp(-np.clip(logits, -20.0, 20.0)))

    def _calculate_price_affinity(self, user_profile: Dict, course_price: float) -> float:
        """Do muc do phu hop cua gia course voi lich su cua hoc vien."""
        stats = user_profile.get('priceStats', {}) or {}
        if course_price <= 0.0:
            return 0.55
        average_price = float(stats.get('averagePrice', 0.0) or 0.0)
        max_price = float(stats.get('maxPrice', average_price) or average_price or 1.0)
        min_price = float(stats.get('minPrice', average_price) or average_price or 0.0)
        if average_price <= 0.0:
            return 0.5

        price_span = max(max_price - min_price, average_price * 0.5, 1.0)
        normalized_gap = min(abs(course_price - average_price) / price_span, 1.0)
        return 1.0 - normalized_gap

    def _calculate_course_freshness(self, course: Dict) -> float:
        """Uu tien khoa hoc moi hon bang ham suy giam theo thoi gian."""
        now_ms = int(time.time() * 1000)
        created_at = int(course.get('createdAt', 0) or 0)
        age_days = ((now_ms - created_at) / (24 * 60 * 60 * 1000.0)) if created_at > 0 else 365.0
        return float(np.exp(-max(age_days, 0.0) / 180.0))
