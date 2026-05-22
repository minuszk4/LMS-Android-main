import os
import time
from typing import Dict, Optional

from dotenv import load_dotenv
from flask import Flask, jsonify, request
from flask_cors import CORS

from data_source import load_runtime_data, resolve_seed_data_path
from model import RecommendationModel
from model_registry import ModelRegistry
from recommendation_dataset import (
    build_course_index,
    build_positive_interactions,
    build_progress_by_user,
    build_user_profile,
)
from train_model import train_and_register

load_dotenv()

app = Flask(__name__)
CORS(app)


class RuntimeDataCache:
    def __init__(self):
        self.payload: Optional[dict] = None
        self.source_name: str = "UNINITIALIZED"
        self.loaded_at_ms: int = 0
        self.last_error: str = ""

    @property
    def refresh_seconds(self) -> int:
        return int(os.getenv("DATA_REFRESH_SECONDS", "300") or "300")

    @property
    def configured_source(self) -> str:
        return str(os.getenv("RECOMMENDATION_DATA_SOURCE", "auto")).strip().lower()

    def get(self, force_refresh: bool = False) -> dict:
        now_ms = int(time.time() * 1000)
        cache_age_ms = now_ms - self.loaded_at_ms
        should_refresh = (
            force_refresh
            or self.payload is None
            or cache_age_ms >= self.refresh_seconds * 1000
        )

        if should_refresh:
            try:
                data, source_name = load_runtime_data(
                    source=self.configured_source,
                    seed_data_path=resolve_seed_data_path(),
                )
                self.payload = data
                self.source_name = source_name
                self.loaded_at_ms = now_ms
                self.last_error = ""
            except Exception as exc:
                self.last_error = str(exc)
                if self.payload is None:
                    raise

        return self.payload or {}


runtime_cache = RuntimeDataCache()
registry = ModelRegistry()
model = RecommendationModel()
model_state: Dict[str, object] = {
    "versionId": "",
    "status": "NOT_LOADED",
    "artifactFormat": "",
    "loadedAt": 0,
    "source": "",
    "error": "",
}


def _parse_bool(value, default: bool = False) -> bool:
    if value is None:
        return default
    if isinstance(value, bool):
        return value
    return str(value).strip().lower() not in {"false", "0", "no", "off", ""}


def _admin_token() -> str:
    return str(os.getenv("MODEL_ADMIN_TOKEN", "")).strip()


def _request_admin_token() -> str:
    return str(
        request.headers.get("X-Model-Admin-Token")
        or request.headers.get("X-Admin-Token")
        or ""
    ).strip()


def _authorize_admin_request():
    expected = _admin_token()
    if not expected:
        return False, jsonify({"error": "MODEL_ADMIN_TOKEN is not configured"}), 503
    if _request_admin_token() != expected:
        return False, jsonify({"error": "Unauthorized"}), 401
    return True, None, None


def _load_active_model() -> bool:
    bundle = registry.get_active_model_bundle()
    if not bundle:
        model_state.update({
            "versionId": "",
            "status": "HEURISTIC_ONLY",
            "artifactFormat": "",
            "loadedAt": int(time.time() * 1000),
            "source": "NONE",
            "error": "No active model artifact found",
        })
        return False

    metadata = bundle.get("metadata") or {}
    artifact_format = str(bundle.get("artifact_format") or "pickle").lower()
    try:
        if bundle.get("artifact_bytes") is not None:
            model.load_bytes(bundle["artifact_bytes"])
        else:
            model.load(str(bundle.get("artifact_path")))
        model_state.update({
            "versionId": str(metadata.get("id") or metadata.get("versionId") or ""),
            "status": str(metadata.get("status") or "ACTIVE"),
            "artifactFormat": artifact_format,
            "loadedAt": int(time.time() * 1000),
            "source": str(metadata.get("source") or metadata.get("dataSource") or "REGISTRY"),
            "error": "",
            "metrics": metadata.get("metrics") or {},
        })
        return True
    except Exception as exc:
        model_state.update({
            "status": "LOAD_FAILED",
            "loadedAt": int(time.time() * 1000),
            "error": str(exc),
        })
        return False


def _build_candidate_courses(runtime_data: dict, candidate_course_ids: list, category_id: Optional[str]) -> list:
    course_by_id = build_course_index(runtime_data)
    courses = []
    for course_id in candidate_course_ids:
        course = course_by_id.get(course_id)
        if not course:
            continue
        if category_id and str(course.get("categoryId") or "") != str(category_id):
            continue
        courses.append(course)
    return courses


def _build_runtime_user_profile(runtime_data: dict, user_id: str) -> tuple[dict, list[str]]:
    course_by_id = build_course_index(runtime_data)
    interactions_by_user = build_positive_interactions(runtime_data, course_by_id)
    progress_by_user = build_progress_by_user(runtime_data)
    positive_course_ids = list(interactions_by_user.get(user_id, {}).keys())
    user_profile = build_user_profile(
        user_id=user_id,
        positive_course_ids=positive_course_ids,
        course_by_id=course_by_id,
        progress_records=progress_by_user.get(user_id, []),
    )
    return user_profile, positive_course_ids


def _log_prediction(payload: Dict) -> None:
    try:
        registry.log_prediction(payload)
    except Exception:
        pass


def _build_heuristic_fallback_response(
    user_id: str,
    candidate_course_ids: list,
    limit: int,
    category_id: Optional[str],
    source: str,
    request_started_at: int,
) -> Dict:
    runtime_data = runtime_cache.get(force_refresh=False)
    user_profile, positive_course_ids = _build_runtime_user_profile(runtime_data, user_id)
    fallback_model = RecommendationModel()
    candidate_courses = _build_candidate_courses(runtime_data, candidate_course_ids, category_id)
    scores = fallback_model.predict_scores(user_profile, candidate_courses, positive_course_ids)
    recommendations = [
        {"courseId": course_id, "score": float(score)}
        for course_id, score in sorted(scores.items(), key=lambda item: item[1], reverse=True)
    ][:limit]
    latency_ms = int(time.time() * 1000) - request_started_at

    response = {
        "recommendations": recommendations,
        "modelVersion": model_state.get("versionId", ""),
        "fallbackUsed": True,
        "latencyMs": latency_ms,
        "dataSource": runtime_cache.source_name,
    }
    _log_prediction({
        "userId": user_id,
        "candidateCourseIds": candidate_course_ids,
        "recommendedCourseIds": [item["courseId"] for item in recommendations],
        "modelVersion": model_state.get("versionId", ""),
        "requestedAt": request_started_at,
        "latencyMs": latency_ms,
        "fallbackUsed": True,
        "source": source,
    })
    return response


_load_active_model()
try:
    runtime_cache.get(force_refresh=True)
except Exception as exc:
    runtime_cache.last_error = str(exc)


@app.route("/health", methods=["GET"])
def health_check():
    return jsonify({
        "status": "ok",
        "message": "ML Recommendation Backend is running",
        "modelVersion": model_state.get("versionId", ""),
        "modelLoaded": bool(model.is_trained),
        "modelStatus": model_state.get("status"),
        "dataSource": runtime_cache.source_name,
        "dataLoadedAt": runtime_cache.loaded_at_ms,
        "dataLastError": runtime_cache.last_error,
    }), 200


@app.route("/model/active", methods=["GET"])
def active_model():
    bundle = registry.get_active_model_bundle()
    metadata = (bundle or {}).get("metadata") or {}
    response = {
        "activeModel": metadata,
        "runtimeModelState": model_state,
        "runtimeDataSource": runtime_cache.source_name,
        "runtimeDataLoadedAt": runtime_cache.loaded_at_ms,
    }
    return jsonify(response), 200


@app.route("/model/versions", methods=["GET"])
def list_model_versions():
    limit = int(request.args.get("limit", 20) or 20)
    return jsonify({
        "versions": registry.list_model_versions(limit=limit),
    }), 200


@app.route("/metrics/recommendation", methods=["GET"])
def get_recommendation_metrics():
    active_model_metadata = registry.get_active_model_metadata() or {}
    return jsonify({
        "modelVersion": active_model_metadata.get("id") or model_state.get("versionId", ""),
        "metrics": active_model_metadata.get("metrics") or model_state.get("metrics") or {},
        "runtimeModelState": model_state,
    }), 200


@app.route("/model/reload", methods=["POST"])
def reload_model():
    authorized, response, status = _authorize_admin_request()
    if not authorized:
        return response, status

    reload_data = _parse_bool((request.get_json(silent=True) or {}).get("reloadData"), default=False)
    if reload_data:
        runtime_cache.get(force_refresh=True)
    loaded = _load_active_model()
    return jsonify({
        "reloaded": loaded,
        "modelState": model_state,
        "dataSource": runtime_cache.source_name,
    }), 200 if loaded else 500


@app.route("/jobs/retrain", methods=["POST"])
def retrain_job():
    authorized, response, status = _authorize_admin_request()
    if not authorized:
        return response, status

    payload = request.get_json(silent=True) or {}
    data_source = str(payload.get("dataSource") or os.getenv("RECOMMENDATION_DATA_SOURCE", "auto")).strip().lower()
    window_days = int(payload.get("windowDays") or 90)
    activate_if_better = _parse_bool(payload.get("activateIfBetter"), default=True)

    job_id = registry.create_training_job({
        "jobType": "FULL_RETRAIN",
        "status": "RUNNING",
        "triggerSource": str(payload.get("triggerSource") or "manual").upper(),
        "windowDays": window_days,
        "startedAt": int(time.time() * 1000),
    })

    try:
        report = train_and_register(
            data_source=data_source,
            window_days=window_days,
            activate_if_better=activate_if_better,
        )
        _load_active_model()
        runtime_cache.get(force_refresh=True)
        registry.update_training_job(job_id, {
            "status": "SUCCESS",
            "finishedAt": int(time.time() * 1000),
            "modelVersion": report.get("versionId"),
            "metrics": report.get("metrics"),
            "artifactPath": report.get("artifactPath"),
            "dataSource": report.get("dataSource"),
        })
        return jsonify({
            "jobId": job_id,
            "status": "SUCCESS",
            "report": report,
        }), 200
    except Exception as exc:
        registry.update_training_job(job_id, {
            "status": "FAILED",
            "finishedAt": int(time.time() * 1000),
            "errorMessage": str(exc),
        })
        return jsonify({
            "jobId": job_id,
            "status": "FAILED",
            "error": str(exc),
        }), 500


@app.route("/jobs/retrain/<job_id>", methods=["GET"])
def get_retrain_job(job_id: str):
    job = registry.get_training_job(job_id)
    if not job:
        return jsonify({"error": "Training job not found"}), 404
    return jsonify(job), 200


@app.route("/recommendation-feedback", methods=["POST"])
def recommendation_feedback():
    payload = request.get_json(silent=True) or {}
    event_type = str(payload.get("eventType") or "").strip().upper()
    user_id = str(payload.get("userId") or "").strip()
    course_id = str(payload.get("courseId") or "").strip()

    if not user_id or not course_id or not event_type:
        return jsonify({"error": "userId, courseId and eventType are required"}), 400

    try:
        registry.log_feedback({
            "userId": user_id,
            "courseId": course_id,
            "predictionLogId": str(payload.get("predictionLogId") or "").strip(),
            "eventType": event_type,
            "source": str(payload.get("source") or "UNKNOWN").strip().lower(),
            "modelVersion": str(payload.get("modelVersion") or model_state.get("versionId") or ""),
            "createdAt": int(time.time() * 1000),
        })
        return jsonify({"status": "logged"}), 200
    except Exception as exc:
        return jsonify({"error": str(exc)}), 500


@app.route("/recommendations", methods=["POST"])
def get_recommendations():
    request_started_at = int(time.time() * 1000)
    data = request.get_json(silent=True) or {}
    user_id = str(data.get("userId") or "").strip()
    limit = int(data.get("limit") or 5)
    candidate_course_ids = data.get("candidateCourseIds") or []
    category_id = data.get("categoryId")
    source = str(data.get("source") or "app").strip().lower()

    try:
        if not user_id:
            return jsonify({"error": "userId is required"}), 400

        if not candidate_course_ids:
            return jsonify({"error": "candidateCourseIds is required"}), 400

        runtime_data = runtime_cache.get(force_refresh=False)
        user_profile, positive_course_ids = _build_runtime_user_profile(runtime_data, user_id)
        candidate_courses = _build_candidate_courses(runtime_data, candidate_course_ids, category_id)

        if not candidate_courses:
            return jsonify({
                "recommendations": [],
                "modelVersion": model_state.get("versionId", ""),
                "fallbackUsed": not model.is_trained,
                "latencyMs": int(time.time() * 1000) - request_started_at,
                "dataSource": runtime_cache.source_name,
            }), 200

        scores = model.predict_scores(user_profile, candidate_courses, positive_course_ids)
        recommendations = [
            {"courseId": course_id, "score": float(score)}
            for course_id, score in sorted(scores.items(), key=lambda item: item[1], reverse=True)
        ][:limit]

        latency_ms = int(time.time() * 1000) - request_started_at
        response = {
            "recommendations": recommendations,
            "modelVersion": model_state.get("versionId", ""),
            "fallbackUsed": not model.is_trained,
            "latencyMs": latency_ms,
            "dataSource": runtime_cache.source_name,
        }

        _log_prediction({
            "userId": user_id,
            "candidateCourseIds": candidate_course_ids,
            "recommendedCourseIds": [item["courseId"] for item in recommendations],
            "modelVersion": model_state.get("versionId", ""),
            "requestedAt": request_started_at,
            "latencyMs": latency_ms,
            "fallbackUsed": not model.is_trained,
            "source": source,
        })

        return jsonify(response), 200
    except Exception as exc:
        try:
            if user_id and candidate_course_ids:
                fallback_response = _build_heuristic_fallback_response(
                    user_id=user_id,
                    candidate_course_ids=candidate_course_ids,
                    limit=limit,
                    category_id=category_id,
                    source=source,
                    request_started_at=request_started_at,
                )
                fallback_response["warning"] = f"Backend model fallback activated: {exc}"
                return jsonify(fallback_response), 200
        except Exception:
            pass
        return jsonify({"error": str(exc)}), 500


if __name__ == "__main__":
    port = int(os.getenv("PORT", 5000))
    app.run(host="0.0.0.0", port=port, debug=False)
