"""Tiện ích nạp dữ liệu runtime và dữ liệu huấn luyện cho API recommendation.

Service recommendation của hệ thống có thể chạy theo hai nguồn:
- snapshot seed local để phục vụ demo, test nhanh và phát triển offline,
- Firestore để dùng dữ liệu thực tế của ứng dụng trong môi trường chạy thật.

Module này gom toàn bộ logic đọc dữ liệu đầu vào để các phần train model,
refresh cache và suy luận runtime không phải tự xử lý từng nguồn riêng lẻ.
"""

import json
import os
import time
from pathlib import Path
from typing import Dict, Iterable, List, Tuple

from firebase_utils import get_firestore_client, is_firebase_configured


# Đường dẫn mặc định tới snapshot dữ liệu seed được lưu trong repo.
# File này thường dùng khi chưa cấu hình Firebase hoặc khi muốn tái lập dữ liệu demo ổn định.
DEFAULT_SEED_DATA_PATH = Path(__file__).resolve().parent.parent / "scripts" / "seed" / "seed_data.json"
# Danh sách collection tối thiểu cần cho pipeline recommendation.
# Đây là các nguồn tín hiệu chính để tạo user profile, lịch sử tương tác và quality signals của course.
DEFAULT_COLLECTIONS = (
    "users",
    "courses",
    "enrollments",
    "reviews",
    "progress",
    "quizProgress",
    "cartItems",
    "orders",
    "orderItems",
    "chatMessages",
)


def load_seed_data(seed_data_path: Path = DEFAULT_SEED_DATA_PATH) -> dict:
    """Nạp file JSON seed local dùng cho demo hoặc train offline.

    Hàm này phù hợp cho các môi trường không có Firebase, CI, hoặc khi muốn
    tái sử dụng một snapshot dữ liệu cố định để kiểm thử và so sánh model.
    """
    with seed_data_path.open("r", encoding="utf-8") as file:
        return json.load(file)


def load_firestore_data(collection_names: Iterable[str] = DEFAULT_COLLECTIONS) -> dict:
    """Đọc các collection runtime cần thiết cho pipeline recommendation.

    Mỗi collection được stream toàn bộ document từ Firestore rồi chuẩn hóa lại
    thành dict thuần Python. Hàm cũng đảm bảo bản ghi luôn có khóa định danh:
    - `uid` cho collection `users`,
    - `id` cho các collection còn lại.
    """
    db = get_firestore_client()
    if db is None:
        raise RuntimeError("Firebase is not configured for ml-backend")

    data: Dict[str, List[dict]] = {}
    for collection_name in collection_names:
        documents = []
        for snapshot in db.collection(collection_name).stream():
            payload = snapshot.to_dict() or {}
            # Chuẩn hóa khóa định danh để các bước downstream không phải phụ thuộc
            # vào metadata riêng của Firestore snapshot.
            if collection_name == "users":
                payload.setdefault("uid", snapshot.id)
            else:
                payload.setdefault("id", snapshot.id)
            documents.append(payload)
        data[collection_name] = documents
    return data


def load_runtime_data(
    source: str = "auto",
    seed_data_path: Path = DEFAULT_SEED_DATA_PATH,
) -> Tuple[dict, str]:
    """Chọn nguồn dữ liệu ưu tiên và fallback an toàn khi cần.

    Quy tắc:
    - nếu caller yêu cầu `firestore` thì ưu tiên Firestore và chỉ fallback khi ở chế độ `auto`,
    - nếu chọn `auto` thì thử Firestore trước, lỗi sẽ quay về seed local,
    - nếu không có Firebase thì dùng seed local ngay từ đầu.
    """
    preferred = (source or "auto").strip().lower()

    if preferred in {"firestore", "auto"} and is_firebase_configured():
        try:
            return load_firestore_data(), "FIRESTORE"
        except Exception:
            # Với `auto`, lỗi Firestore không được làm hỏng toàn bộ backend.
            # Khi đó service vẫn có thể tiếp tục bằng seed snapshot.
            if preferred == "firestore":
                raise

    return load_seed_data(seed_data_path), "SEED"


def resolve_seed_data_path() -> Path:
    """Xác định đường dẫn seed snapshot từ biến môi trường hoặc giá trị mặc định.

    Cho phép backend override đường dẫn dữ liệu seed mà không cần sửa code,
    rất hữu ích khi chạy nhiều bộ dữ liệu mẫu khác nhau.
    """
    raw_path = str(os.getenv("SEED_DATA_PATH", "")).strip()
    if raw_path:
        return Path(raw_path)
    return DEFAULT_SEED_DATA_PATH


def filter_data_by_window(data: dict, window_days: int) -> dict:
    """Lọc lại các bản ghi tương tác gần đây theo cửa sổ thời gian.

    Mục tiêu của hàm là giảm ảnh hưởng của dữ liệu quá cũ khi retrain model.
    Chỉ những collection mang ý nghĩa hành vi theo thời gian mới bị lọc;
    các collection nền như `users` hoặc `courses` sẽ được giữ nguyên.
    """
    if window_days <= 0:
        return data

    cutoff_ms = int(time.time() * 1000) - int(window_days * 24 * 60 * 60 * 1000)
    window_fields = {
        "enrollments": ("enrolledAt", "createdAt"),
        "reviews": ("createdAt",),
        "progress": ("lastAccessedAt", "updatedAt", "createdAt"),
        "quizProgress": ("updatedAt", "createdAt"),
        "cartItems": ("updatedAt", "createdAt"),
        "orders": ("paidAt", "createdAt"),
        "orderItems": ("createdAt",),
        "chatMessages": ("createdAt",),
    }

    filtered: Dict[str, List[dict]] = {}
    for collection_name, records in data.items():
        fields = window_fields.get(collection_name)
        if not fields:
            # Không có trường thời gian phù hợp thì giữ nguyên collection.
            filtered[collection_name] = list(records)
            continue

        filtered_records = []
        for record in records:
            timestamps = []
            for field_name in fields:
                raw_value = record.get(field_name)
                if raw_value is None:
                    continue
                try:
                    timestamps.append(int(raw_value))
                except (TypeError, ValueError):
                    # Bỏ qua giá trị timestamp lỗi định dạng thay vì làm hỏng cả batch.
                    continue
            # Nếu bản ghi không có timestamp hợp lệ nào thì giữ lại,
            # tránh vô tình làm mất dữ liệu vì thiếu field ở snapshot cũ.
            if not timestamps or max(timestamps) >= cutoff_ms:
                filtered_records.append(record)
        filtered[collection_name] = filtered_records
    return filtered
