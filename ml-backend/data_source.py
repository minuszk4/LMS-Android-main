"""Tien ich nap du lieu runtime va du lieu huan luyen cho API recommendation.

Service recommendation co the chay theo hai nguon:
- snapshot seed local cho demo va phat trien offline,
- Firestore cho moi truong muon su dung du lieu thuc te.
"""

import json
import os
import time
from pathlib import Path
from typing import Dict, Iterable, List, Tuple

from firebase_utils import get_firestore_client, is_firebase_configured


DEFAULT_SEED_DATA_PATH = Path(__file__).resolve().parent.parent / "scripts" / "seed" / "seed_data.json"
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
    """Nap file JSON seed local dung cho demo hoac train offline."""
    with seed_data_path.open("r", encoding="utf-8") as file:
        return json.load(file)


def load_firestore_data(collection_names: Iterable[str] = DEFAULT_COLLECTIONS) -> dict:
    """Doc cac collection runtime can thiet cho pipeline recommendation."""
    db = get_firestore_client()
    if db is None:
        raise RuntimeError("Firebase is not configured for ml-backend")

    data: Dict[str, List[dict]] = {}
    for collection_name in collection_names:
        documents = []
        for snapshot in db.collection(collection_name).stream():
            payload = snapshot.to_dict() or {}
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
    """Chon nguon du lieu uu tien va fallback an toan khi can."""
    preferred = (source or "auto").strip().lower()

    if preferred in {"firestore", "auto"} and is_firebase_configured():
        try:
            return load_firestore_data(), "FIRESTORE"
        except Exception:
            if preferred == "firestore":
                raise

    return load_seed_data(seed_data_path), "SEED"


def resolve_seed_data_path() -> Path:
    """Xac dinh duong dan seed snapshot tu env hoac gia tri mac dinh."""
    raw_path = str(os.getenv("SEED_DATA_PATH", "")).strip()
    if raw_path:
        return Path(raw_path)
    return DEFAULT_SEED_DATA_PATH


def filter_data_by_window(data: dict, window_days: int) -> dict:
    """Loc lai cac ban ghi tuong tac gan day cho train theo cua so thoi gian."""
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
                    continue
            if not timestamps or max(timestamps) >= cutoff_ms:
                filtered_records.append(record)
        filtered[collection_name] = filtered_records
    return filtered
