"""Model tính điểm recommendation được dùng trong ML backend của LMS.

Lớp này hỗ trợ ba chế độ phục vụ để backend luôn có thể suy luận:
1. RandomForest đã được huấn luyện và lưu dưới dạng pickle,
2. artifact tuyến tính cũ để tương thích ngược với các phiên bản trước,
3. heuristic scoring khi chưa nạp được artifact nào.
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
    """Đóng gói logic trích xuất feature, train, score và lưu/nạp model."""

    def __init__(self):
        self.model = None
        self.scaler = StandardScaler()
        self.linear_weights: Optional[np.ndarray] = None
        self.linear_bias: float = 0.0
        # Thứ tự feature phải được giữ cố định giữa lúc train và lúc serving.
        # Nếu thay đổi thứ tự mà không train lại, model sẽ đọc sai ý nghĩa của từng cột.
        self.feature_names = [
            "category_affinity",
            "level_affinity",
            "instructor_affinity",
            "price_affinity",
            "rating",
            "review_count",
            "enrollment_count",
            "lesson_count",
            "course_freshness",
            "popularity_score",
            "recency_weight",
        ]

    def extract_features(
        self,
        user_profile: Dict,
        course: Dict,
        user_enrollments: List[str],
    ) -> np.ndarray:
        """Trích xuất vector feature cho một course theo hồ sơ người dùng.

        Vector đầu ra trộn các tín hiệu về sở thích học viên, chất lượng khóa học,
        độ phổ biến và một số heuristic nghiệp vụ như độ mới của course.
        """
        # Ba feature affinity đầu tiên đo mức khớp giữa sở thích user và metadata course.
        category_affinity = float(
            user_profile.get("categoryWeights", {}).get(course.get("categoryId"), 0.0)
        )
        level_affinity = float(
            user_profile.get("levelWeights", {}).get(course.get("level"), 0.0)
        )
        instructor_affinity = float(
            user_profile.get("instructorWeights", {}).get(course.get("instructorId"), 0.0)
        )

        # Phần còn lại là quality/popularity/business signals được chuẩn hóa về [0, 1]
        # để model học trên các thang đo ổn định hơn.
        price_affinity = self._calculate_price_affinity(
            user_profile, float(course.get("price", 0.0) or 0.0)
        )
        rating = float(course.get("rating", 0.0) or 0.0) / 5.0
        review_count = min(int(course.get("reviewCount", 0) or 0), 200) / 200.0
        enrollment_count = min(int(course.get("enrollmentCount", 0) or 0), 500) / 500.0
        lesson_count = min(int(course.get("lessonCount", 0) or 0), 200) / 200.0

        course_freshness = self._calculate_course_freshness(course)
        popularity_score = (rating * 0.45) + (review_count * 0.20) + (enrollment_count * 0.35)

        recency_weight = 0.0 if course.get("id") in user_enrollments else 1.0

        feature_map = {
            "category_affinity": category_affinity,
            "level_affinity": level_affinity,
            "instructor_affinity": instructor_affinity,
            "price_affinity": price_affinity,
            "rating": rating,
            "review_count": review_count,
            "enrollment_count": enrollment_count,
            "lesson_count": lesson_count,
            "course_freshness": course_freshness,
            "popularity_score": popularity_score,
            "recency_weight": recency_weight,
        }

        return np.asarray(
            [float(feature_map.get(name, 0.0)) for name in self.feature_names],
            dtype=float,
        )

    def predict_scores(
        self,
        user_profile: Dict,
        courses: List[Dict],
        user_enrollments: List[str],
    ) -> Dict[str, float]:
        """Dự đoán điểm recommendation cho danh sách course.

        Hàm ưu tiên dùng artifact đã train, nhưng vẫn có thể fallback sang
        artifact tuyến tính cũ hoặc heuristic thuần khi cần.
        """
        if self.model is None and self.linear_weights is None:
            return self._heuristic_scores(user_profile, courses, user_enrollments)

        scores = {}
        for course in courses:
            features = self.extract_features(user_profile, course, user_enrollments)
            if self.model is not None:
                # RandomForest được train trên feature đã scale, nên runtime phải
                # dùng lại chính scaler đi kèm artifact.
                features_scaled = self.scaler.transform([features])[0]

                try:
                    prob = self.model.predict_proba([features_scaled])[0]
                    score = prob[1] if len(prob) > 1 else 0.5
                    scores[course.get("id")] = float(score)
                    continue
                except Exception:
                    pass

            if self.linear_weights is not None:
                # Tương thích ngược với artifact tuyến tính cũ trong repo.
                score = self._calculate_linear_score(features)
                scores[course.get("id")] = float(score)
            else:
                # Fallback sau cùng: không có artifact nào thì score bằng heuristic thuần.
                scores[course.get("id")] = self._calculate_heuristic(user_profile, course)

        return scores

    def _heuristic_scores(
        self,
        user_profile: Dict,
        courses: List[Dict],
        user_enrollments: List[str],
    ) -> Dict[str, float]:
        """Tính điểm heuristic hàng loạt khi không có model học máy khả dụng."""
        scores = {}
        for course in courses:
            score = self._calculate_heuristic(user_profile, course)
            scores[course.get("id")] = score
        return scores

    def _calculate_heuristic(self, user_profile: Dict, course: Dict) -> float:
        """Tính heuristic score cho một course từ profile-fit và popularity."""
        category_weight = user_profile.get("categoryWeights", {}).get(course.get("categoryId"), 0.0)
        level_weight = user_profile.get("levelWeights", {}).get(course.get("level"), 0.0)
        instructor_weight = user_profile.get("instructorWeights", {}).get(
            course.get("instructorId"), 0.0
        )
        price_affinity = self._calculate_price_affinity(
            user_profile, float(course.get("price", 0.0) or 0.0)
        )
        rating = float(course.get("rating", 0.0) or 0.0) / 5.0
        review_score = min(int(course.get("reviewCount", 0) or 0), 200) / 200.0
        popularity_score = (
            rating * 0.45
            + (min(course.get("enrollmentCount", 0), 500) / 500.0) * 0.35
            + review_score * 0.20
        )
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
        """Huấn luyện model recommendation với trọng số mẫu mặc định."""
        self.train_with_sample_weights(X_train, y_train, sample_weight=None)

    def train_with_sample_weights(
        self,
        X_train: np.ndarray,
        y_train: np.ndarray,
        sample_weight: Optional[np.ndarray],
    ) -> None:
        """Huấn luyện model recommendation với sample weight tùy chọn.

        Sample weight cho phép pipeline tăng trọng số cho các tín hiệu mạnh hơn
        như enroll, purchase, completion hoặc feedback tích cực.
        """
        # Chuẩn hóa feature để bộ phân loại nhìn thấy các thang đo số
        # ổn định giữa engagement, popularity và các trường liên quan giá.
        X_scaled = self.scaler.fit_transform(X_train)

        # RandomForest được chọn vì dễ train, dễ explain và không quá nhạy cảm
        # với feature engineering ban đầu của bài toán recommendation này.
        self.model = RandomForestClassifier(
            n_estimators=100,
            max_depth=10,
            random_state=42,
            n_jobs=-1,
        )
        fit_kwargs = {}
        if sample_weight is not None:
            fit_kwargs["sample_weight"] = sample_weight
        self.model.fit(X_scaled, y_train, **fit_kwargs)
        self.linear_weights = None
        self.linear_bias = 0.0

    def save(self, filepath: str):
        """Lưu model hiện tại ra file artifact trên đĩa."""
        path = Path(filepath)
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(self.dump_bytes())

    def load(self, filepath: str):
        """Nạp model từ file artifact.

        Hàm này hỗ trợ cả artifact pickle mới và artifact JSON tuyến tính cũ.
        """
        path = Path(filepath)
        # Repo từng có artifact JSON tuyến tính; branch này giữ khả năng đọc format cũ
        # để backend có thể nâng cấp mà không vỡ ngay luồng serving.
        if path.suffix.lower() == ".json":
            self._load_legacy_json(path.read_text(encoding="utf-8"))
            return
        self.load_bytes(path.read_bytes())

    def dump_bytes(self) -> bytes:
        """Đóng gói toàn bộ trạng thái model thành bytes để lưu registry."""
        state = {
            "model": self.model,
            "scaler": self.scaler,
            "feature_names": self.feature_names,
            "linear_weights": self.linear_weights,
            "linear_bias": self.linear_bias,
        }
        return pickle.dumps(state)

    def load_bytes(self, payload: bytes) -> None:
        """Khôi phục trạng thái model từ payload bytes đã serialize."""
        data = pickle.loads(payload)
        self.model = data.get("model")
        self.scaler = data.get("scaler", StandardScaler())
        self.feature_names = data.get("feature_names", self.feature_names)
        self.linear_weights = data.get("linear_weights")
        self.linear_bias = float(data.get("linear_bias", 0.0))

    @property
    def is_trained(self) -> bool:
        return self.model is not None or self.linear_weights is not None

    def _load_legacy_json(self, raw_json: str) -> None:
        """Nạp artifact tuyến tính legacy đã tồn tại từ các phiên bản cũ của repo."""
        payload = json.loads(raw_json)
        weights = payload.get("weights") or []
        if len(weights) != len(self.feature_names):
            raise ValueError("Legacy weight artifact does not match expected feature size")
        # Khi nạp artifact legacy thì vô hiệu hóa random forest và scaler train mới,
        # backend sẽ phục vụ bằng nhánh linear-score để giữ backward compatibility.
        self.model = None
        self.scaler = StandardScaler()
        self.linear_weights = np.asarray(weights, dtype=float)
        self.linear_bias = float(payload.get("bias", 0.0))
        self.feature_names = payload.get("feature_names", self.feature_names)

    def _calculate_linear_score(self, features: np.ndarray) -> float:
        """Tính điểm cho một vector feature bằng artifact tuyến tính cũ."""
        logits = float(np.dot(features, self.linear_weights) + self.linear_bias)
        return 1.0 / (1.0 + np.exp(-np.clip(logits, -20.0, 20.0)))

    def _calculate_price_affinity(self, user_profile: Dict, course_price: float) -> float:
        """Đo mức độ phù hợp của giá course với lịch sử chi tiêu của học viên."""
        stats = user_profile.get("priceStats", {}) or {}
        if course_price <= 0.0:
            return 0.55
        average_price = float(stats.get("averagePrice", 0.0) or 0.0)
        max_price = float(stats.get("maxPrice", average_price) or average_price or 1.0)
        min_price = float(stats.get("minPrice", average_price) or average_price or 0.0)
        if average_price <= 0.0:
            return 0.5

        price_span = max(max_price - min_price, average_price * 0.5, 1.0)
        normalized_gap = min(abs(course_price - average_price) / price_span, 1.0)
        return 1.0 - normalized_gap

    def _calculate_course_freshness(self, course: Dict) -> float:
        """Ưu tiên khóa học mới hơn bằng hàm suy giảm theo thời gian."""
        now_ms = int(time.time() * 1000)
        created_at = int(course.get("createdAt", 0) or 0)
        age_days = ((now_ms - created_at) / (24 * 60 * 60 * 1000.0)) if created_at > 0 else 365.0
        return float(np.exp(-max(age_days, 0.0) / 180.0))
