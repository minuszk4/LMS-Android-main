from __future__ import annotations

import hashlib
import json
import os
import time
from pathlib import Path
from typing import Dict, List, Optional

from firebase_utils import get_firestore_client


ARTIFACT_ROOT = Path(__file__).resolve().parent / "artifacts"
DEFAULT_MODEL_PATH = ARTIFACT_ROOT / "recommendation_model.pkl"
DEFAULT_LEGACY_MODEL_PATH = ARTIFACT_ROOT / "recommendation_model.json"
DEFAULT_MANIFEST_PATH = ARTIFACT_ROOT / "active_model_manifest.json"
VERSION_ROOT = ARTIFACT_ROOT / "versions"
CHUNK_SIZE_BYTES = 700_000

REGISTRY_COLLECTION = os.getenv("RECOMMENDATION_MODEL_REGISTRY_COLLECTION", "recommendationModelRegistry")
ARTIFACT_COLLECTION = os.getenv("RECOMMENDATION_MODEL_ARTIFACT_COLLECTION", "recommendationModelArtifacts")
TRAINING_JOBS_COLLECTION = os.getenv("RECOMMENDATION_TRAINING_JOBS_COLLECTION", "recommendationTrainingJobs")
PREDICTION_LOG_COLLECTION = os.getenv("RECOMMENDATION_PREDICTION_LOG_COLLECTION", "recommendationPredictionsLog")
FEEDBACK_COLLECTION = os.getenv("RECOMMENDATION_FEEDBACK_COLLECTION", "recommendationFeedbackEvents")


def _now_ms() -> int:
    return int(time.time() * 1000)


class ModelRegistry:
    def __init__(self):
        self.db = get_firestore_client()

    @property
    def has_firestore(self) -> bool:
        return self.db is not None

    def get_active_model_bundle(self) -> Optional[Dict]:
        firestore_metadata = self._get_active_firestore_metadata()
        if firestore_metadata:
            version_id = str(firestore_metadata.get("id") or "").strip()
            if version_id:
                artifact_bytes = self._download_artifact_bytes(version_id)
                if artifact_bytes:
                    self._cache_active_artifact(version_id, artifact_bytes, firestore_metadata)
                    return {
                        "metadata": firestore_metadata,
                        "artifact_bytes": artifact_bytes,
                        "artifact_format": "pickle",
                    }

        local_manifest = self._read_local_manifest()
        if local_manifest and Path(local_manifest.get("artifactPath", "")).exists():
            return {
                "metadata": local_manifest,
                "artifact_path": local_manifest.get("artifactPath"),
                "artifact_format": local_manifest.get("artifactFormat", "pickle"),
            }

        if DEFAULT_MODEL_PATH.exists():
            return {
                "metadata": {
                    "id": "local_default",
                    "artifactPath": str(DEFAULT_MODEL_PATH),
                    "artifactFormat": "pickle",
                    "status": "ACTIVE",
                    "source": "LOCAL_FILE",
                },
                "artifact_path": str(DEFAULT_MODEL_PATH),
                "artifact_format": "pickle",
            }

        if DEFAULT_LEGACY_MODEL_PATH.exists():
            return {
                "metadata": {
                    "id": "legacy_json",
                    "artifactPath": str(DEFAULT_LEGACY_MODEL_PATH),
                    "artifactFormat": "json",
                    "status": "ACTIVE",
                    "source": "LOCAL_FILE",
                },
                "artifact_path": str(DEFAULT_LEGACY_MODEL_PATH),
                "artifact_format": "json",
            }

        return None

    def get_active_model_metadata(self) -> Optional[Dict]:
        bundle = self.get_active_model_bundle()
        return (bundle or {}).get("metadata") or None

    def list_model_versions(self, limit: int = 20) -> List[Dict]:
        local_versions: List[Dict] = []
        if VERSION_ROOT.exists():
            for manifest_path in VERSION_ROOT.glob("*/manifest.json"):
                try:
                    local_versions.append(json.loads(manifest_path.read_text(encoding="utf-8")))
                except Exception:
                    continue

        if self.has_firestore:
            remote_versions = [
                {**(snapshot.to_dict() or {}), "id": snapshot.id}
                for snapshot in self.db.collection(REGISTRY_COLLECTION).stream()
            ]
            if remote_versions:
                local_versions = remote_versions

        local_versions.sort(
            key=lambda item: int(item.get("activatedAt") or item.get("trainedAt") or item.get("savedAt") or 0),
            reverse=True,
        )
        return local_versions[: max(limit, 1)]

    def get_training_job(self, job_id: str) -> Optional[Dict]:
        if self.has_firestore:
            snapshot = self.db.collection(TRAINING_JOBS_COLLECTION).document(job_id).get()
            if snapshot.exists:
                return {**(snapshot.to_dict() or {}), "id": snapshot.id}
        return None

    def should_activate_model(self, candidate_metrics: Dict, current_metadata: Optional[Dict]) -> Dict:
        if not current_metadata:
            return {
                "shouldActivate": True,
                "reason": "No active model exists yet",
            }

        current_metrics = (current_metadata or {}).get("metrics") or {}
        candidate_ndcg10 = float(candidate_metrics.get("ndcgAt10") or 0.0)
        current_ndcg10 = float(current_metrics.get("ndcgAt10") or 0.0)
        candidate_recall10 = float(candidate_metrics.get("recallAt10") or 0.0)
        current_recall10 = float(current_metrics.get("recallAt10") or 0.0)

        ndcg_better = candidate_ndcg10 > current_ndcg10
        recall_guard = candidate_recall10 >= (current_recall10 - 0.01)
        should_activate = ndcg_better and recall_guard

        return {
            "shouldActivate": should_activate,
            "reason": (
                "Candidate model beat current ndcg@10 and passed recall@10 guardrail"
                if should_activate
                else "Candidate model did not beat the active model thresholds"
            ),
            "currentMetrics": current_metrics,
            "candidateMetrics": candidate_metrics,
            "checks": {
                "ndcgAt10Improved": ndcg_better,
                "recallAt10GuardrailPassed": recall_guard,
            },
        }

    def register_model_version(
        self,
        version_id: str,
        artifact_bytes: bytes,
        metadata: Dict,
        activate: bool = True,
    ) -> Dict:
        version_root = VERSION_ROOT / version_id
        version_root.mkdir(parents=True, exist_ok=True)
        artifact_path = version_root / "recommendation_model.pkl"
        manifest_path = version_root / "manifest.json"
        artifact_path.write_bytes(artifact_bytes)

        sha256 = hashlib.sha256(artifact_bytes).hexdigest()
        target_status = "ACTIVE" if activate else str(metadata.get("status") or "READY").upper()
        enriched = {
            **metadata,
            "id": version_id,
            "artifactPath": str(artifact_path),
            "artifactFormat": "pickle",
            "artifactSha256": sha256,
            "artifactSizeBytes": len(artifact_bytes),
            "savedAt": _now_ms(),
            "status": target_status,
        }
        manifest_path.write_text(json.dumps(enriched, ensure_ascii=False, indent=2), encoding="utf-8")

        if activate:
            self._cache_active_artifact(version_id, artifact_bytes, enriched)

        if self.has_firestore:
            if activate:
                self._archive_active_versions()
            firestore_metadata = {
                **enriched,
                "status": target_status,
                "activatedAt": _now_ms() if activate else int(metadata.get("activatedAt") or 0),
            }
            self.db.collection(REGISTRY_COLLECTION).document(version_id).set(firestore_metadata)
            self._upload_artifact_bytes(version_id, artifact_bytes)
            enriched = firestore_metadata

        return enriched

    def activate_model(self, version_id: str) -> Optional[Dict]:
        version_root = VERSION_ROOT / version_id
        manifest_path = version_root / "manifest.json"
        artifact_path = version_root / "recommendation_model.pkl"
        if not manifest_path.exists() or not artifact_path.exists():
            return None

        metadata = json.loads(manifest_path.read_text(encoding="utf-8"))
        metadata["status"] = "ACTIVE"
        metadata["activatedAt"] = _now_ms()
        manifest_path.write_text(json.dumps(metadata, ensure_ascii=False, indent=2), encoding="utf-8")
        self._cache_active_artifact(version_id, artifact_path.read_bytes(), metadata)

        if self.has_firestore:
            self._archive_active_versions()
            self.db.collection(REGISTRY_COLLECTION).document(version_id).set(metadata, merge=True)

        return metadata

    def create_training_job(self, payload: Dict) -> str:
        job_id = payload.get("id") or f"job_{_now_ms()}"
        record = {
            **payload,
            "id": job_id,
            "scheduledAt": int(payload.get("scheduledAt") or _now_ms()),
            "status": str(payload.get("status") or "PENDING").upper(),
        }
        if self.has_firestore:
            self.db.collection(TRAINING_JOBS_COLLECTION).document(job_id).set(record)
        return job_id

    def update_training_job(self, job_id: str, payload: Dict) -> None:
        if not self.has_firestore:
            return
        payload = dict(payload)
        payload["updatedAt"] = _now_ms()
        self.db.collection(TRAINING_JOBS_COLLECTION).document(job_id).set(payload, merge=True)

    def log_prediction(self, payload: Dict) -> None:
        if not self.has_firestore:
            return
        log_id = payload.get("id") or f"pred_{_now_ms()}"
        record = {**payload, "id": log_id, "requestedAt": int(payload.get("requestedAt") or _now_ms())}
        self.db.collection(PREDICTION_LOG_COLLECTION).document(log_id).set(record)

    def log_feedback(self, payload: Dict) -> None:
        if not self.has_firestore:
            return
        event_id = payload.get("id") or f"feedback_{_now_ms()}"
        record = {**payload, "id": event_id, "createdAt": int(payload.get("createdAt") or _now_ms())}
        self.db.collection(FEEDBACK_COLLECTION).document(event_id).set(record)

    def _cache_active_artifact(self, version_id: str, artifact_bytes: bytes, metadata: Dict) -> None:
        ARTIFACT_ROOT.mkdir(parents=True, exist_ok=True)
        DEFAULT_MODEL_PATH.write_bytes(artifact_bytes)
        active_manifest = {
            **metadata,
            "id": version_id,
            "artifactPath": str(DEFAULT_MODEL_PATH),
            "artifactFormat": "pickle",
            "status": "ACTIVE",
            "cachedAt": _now_ms(),
        }
        DEFAULT_MANIFEST_PATH.write_text(
            json.dumps(active_manifest, ensure_ascii=False, indent=2),
            encoding="utf-8"
        )

    def _read_local_manifest(self) -> Optional[Dict]:
        if not DEFAULT_MANIFEST_PATH.exists():
            return None
        return json.loads(DEFAULT_MANIFEST_PATH.read_text(encoding="utf-8"))

    def _get_active_firestore_metadata(self) -> Optional[Dict]:
        if not self.has_firestore:
            return None
        active_docs = [
            {**(snapshot.to_dict() or {}), "id": snapshot.id}
            for snapshot in self.db.collection(REGISTRY_COLLECTION).where("status", "==", "ACTIVE").stream()
        ]
        if not active_docs:
            return None
        return max(active_docs, key=lambda item: int(item.get("activatedAt") or item.get("trainedAt") or 0))

    def _archive_active_versions(self) -> None:
        if not self.has_firestore:
            return
        batch = self.db.batch()
        has_updates = False
        for snapshot in self.db.collection(REGISTRY_COLLECTION).where("status", "==", "ACTIVE").stream():
            batch.set(snapshot.reference, {"status": "ARCHIVED"}, merge=True)
            has_updates = True
        if has_updates:
            batch.commit()

    def _upload_artifact_bytes(self, version_id: str, artifact_bytes: bytes) -> None:
        if not self.has_firestore:
            return
        metadata_ref = self.db.collection(ARTIFACT_COLLECTION).document(version_id)
        metadata_ref.set({
            "versionId": version_id,
            "sizeBytes": len(artifact_bytes),
            "sha256": hashlib.sha256(artifact_bytes).hexdigest(),
            "chunkCount": (len(artifact_bytes) + CHUNK_SIZE_BYTES - 1) // CHUNK_SIZE_BYTES,
            "updatedAt": _now_ms(),
        })
        for offset in range(0, len(artifact_bytes), CHUNK_SIZE_BYTES):
            chunk_index = offset // CHUNK_SIZE_BYTES
            chunk = artifact_bytes[offset: offset + CHUNK_SIZE_BYTES]
            metadata_ref.collection("chunks").document(f"{chunk_index:04d}").set({
                "index": chunk_index,
                "payload": chunk,
            })

    def _download_artifact_bytes(self, version_id: str) -> Optional[bytes]:
        if not self.has_firestore:
            return None
        chunks = sorted(
            (
                chunk.to_dict() or {}
                for chunk in self.db.collection(ARTIFACT_COLLECTION).document(version_id).collection("chunks").stream()
            ),
            key=lambda item: int(item.get("index") or 0),
        )
        if not chunks:
            return None
        return b"".join(bytes(chunk.get("payload") or b"") for chunk in chunks)
