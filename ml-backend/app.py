"""Dịch vụ Flask phục vụ recommendation và model-ops cho hệ thống LMS.

File này là điểm vào chính của ML backend. Nó đồng thời đảm nhiệm ba nhóm vai trò:
1. Phục vụ endpoint suy luận recommendation cho ứng dụng Android.
2. Cung cấp các endpoint quản trị model như reload, liệt kê version và retrain.
3. Ghi nhận telemetry như prediction log và feedback event để phục vụ đánh giá mô hình.
"""

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

# Nạp cấu hình từ `.env` để backend có thể đọc token quản trị,
# data source, cổng chạy và các tham số model-ops khác.
load_dotenv()

app = Flask(__name__)
# Cho phép app Android hoặc các dashboard nội bộ gọi API từ domain khác.
CORS(app)


class RuntimeDataCache:
    """Cache snapshot dữ liệu runtime để giảm chi phí đọc dữ liệu cho mỗi request.

    Thay vì mỗi lần suy luận lại truy vấn Firestore hoặc nguồn dữ liệu gốc,
    backend sẽ nạp một snapshot gồm courses, enrollments, progress, feedback...
    rồi dùng lại trong một khoảng thời gian ngắn. Cách này giúp:
    - giảm độ trễ của endpoint `/recommendations`,
    - giảm tải cho nguồn dữ liệu phía sau,
    - vẫn cho phép refresh theo chu kỳ hoặc theo yêu cầu quản trị.
    """

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
        """Trả về dữ liệu cache, đồng thời tự refresh khi cache hết hạn.

        Nếu `force_refresh=True`, hàm sẽ bỏ qua dữ liệu cũ và nạp lại ngay.
        Nếu việc nạp mới thất bại nhưng cache cũ vẫn còn, backend sẽ giữ lại
        snapshot cũ để service tiếp tục hoạt động thay vì lỗi toàn bộ.
        """
        now_ms = int(time.time() * 1000)
        cache_age_ms = now_ms - self.loaded_at_ms
        should_refresh = (
            force_refresh
            or self.payload is None
            or cache_age_ms >= self.refresh_seconds * 1000
        )

        if should_refresh:
            try:
                # Dữ liệu runtime là snapshot tổng hợp của courses, enrollments,
                # progress và các nguồn liên quan, được nạp một lần rồi tái sử dụng
                # cho nhiều request suy luận liên tiếp.
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
                # Nếu đã từng có cache hợp lệ thì giữ lại cache cũ để API tiếp tục phục vụ,
                # tránh làm hỏng toàn bộ luồng recommendation chỉ vì một lần refresh lỗi.
                if self.payload is None:
                    raise

        return self.payload or {}


runtime_cache = RuntimeDataCache()
registry = ModelRegistry()
# `model` là instance phục vụ suy luận đang sống trong memory của process hiện tại.
model = RecommendationModel()
# `model_state` là trạng thái runtime đang được endpoint health/model-ops trả ra.
# Nó không phải artifact model, mà là ảnh chụp nhanh cho biết:
# - model version nào đang được nạp,
# - nạp thành công hay thất bại,
# - artifact đang ở định dạng nào,
# - có lỗi gì khi load hay không.
model_state: Dict[str, object] = {
    "versionId": "",
    "status": "NOT_LOADED",
    "artifactFormat": "",
    "loadedAt": 0,
    "source": "",
    "error": "",
}


def _parse_bool(value, default: bool = False) -> bool:
    """Phân tích giá trị boolean từ JSON hoặc biến môi trường.

    Hàm này cho phép backend chấp nhận nhiều kiểu biểu diễn như:
    `true/false`, `1/0`, `yes/no`, `on/off`, giúp endpoint quản trị
    bền hơn khi nhận dữ liệu từ nhiều nguồn gọi khác nhau.
    """
    if value is None:
        return default
    if isinstance(value, bool):
        return value
    return str(value).strip().lower() not in {"false", "0", "no", "off", ""}


def _admin_token() -> str:
    """Đọc admin token nội bộ dùng để bảo vệ endpoint quản lý model."""
    return str(os.getenv("MODEL_ADMIN_TOKEN", "")).strip()


def _request_admin_token() -> str:
    """Lấy admin token từ request header.

    Backend hỗ trợ nhiều tên header để thuận tiện khi gọi từ các môi trường
    hoặc công cụ quản trị khác nhau.
    """
    return str(
        request.headers.get("X-Model-Admin-Token")
        or request.headers.get("X-Admin-Token")
        or ""
    ).strip()


def _authorize_admin_request():
    """Bảo vệ endpoint quản trị model khỏi caller công khai.

    Chỉ các request có admin token hợp lệ mới được reload model, retrain
    hoặc truy cập các chức năng model-ops nhạy cảm.
    """
    expected = _admin_token()
    if not expected:
        return False, jsonify({"error": "MODEL_ADMIN_TOKEN is not configured"}), 503
    if _request_admin_token() != expected:
        return False, jsonify({"error": "Unauthorized"}), 401
    return True, None, None


def _load_active_model() -> bool:
    """Nạp artifact model active hiện tại vào instance `model` trong bộ nhớ.

    Luồng nạp model được tách riêng thành một hàm để có thể tái sử dụng ở:
    - thời điểm backend khởi động,
    - endpoint `/model/reload`,
    - sau khi retrain thành công.

    Hàm này cũng cập nhật `model_state` để các endpoint giám sát có thể biết
    backend đang chạy bằng model nào, trạng thái gì và có lỗi hay không.
    """
    bundle = registry.get_active_model_bundle()
    if not bundle:
        # Không có artifact không có nghĩa backend phải dừng;
        # service vẫn có thể chạy ở chế độ heuristic-only.
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
        # Registry có thể trả về:
        # - bytes đã tải và ghép lại từ Firestore chunk storage,
        # - hoặc đường dẫn local tới artifact đã cache trên đĩa.
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
    """Biến danh sách candidate course ID thành payload course đầy đủ.

    App chỉ gửi lên danh sách ID ứng viên, còn backend sẽ tự ánh xạ sang
    dữ liệu course thật từ snapshot runtime để phục vụ bước scoring.
    Nếu request có `category_id`, hàm này cũng lọc lại candidate theo danh mục.
    """
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
    """Xây dựng hồ sơ học tập runtime của user cho bước suy luận.

    Kết quả trả về gồm:
    - `user_profile`: profile đã tổng hợp các tín hiệu sở thích,
    - `positive_course_ids`: các course có tín hiệu tương tác dương để model
      biết khóa nào user đã thực sự quan tâm hoặc từng học.
    """
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
    """Ghi prediction log theo kiểu best-effort, không làm hỏng luồng API.

    Nếu ghi log lỗi, request recommendation vẫn được xem là thành công.
    Điều này giúp telemetry hữu ích nhưng không trở thành điểm single point of failure.
    """
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
    """Tạo response fallback khi pipeline model chính gặp lỗi.

    Thay vì trả lỗi ngay cho app, backend sẽ dựng lại profile user, lấy candidate
    từ cache runtime và dùng một `RecommendationModel` mới ở chế độ heuristic-only
    để tính điểm. Nhờ đó home screen vẫn có recommendation thay vì bị rỗng.
    """
    # Fallback này tải lại user profile và candidate courses từ runtime cache,
    # sau đó dùng một RecommendationModel mới ở chế độ heuristic-only để score.
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


# Khi service khởi động, thử nạp ngay model active để request đầu tiên không phải chờ.
_load_active_model()
try:
    # Đồng thời warm-up dữ liệu runtime để endpoint recommendation có cache sẵn.
    runtime_cache.get(force_refresh=True)
except Exception as exc:
    runtime_cache.last_error = str(exc)


@app.route("/health", methods=["GET"])
def health_check():
    """Health check kèm metadata về trạng thái serving hiện tại.

    Endpoint này không chỉ trả `status=ok` mà còn cho biết:
    - model nào đang được nạp,
    - có đang load model thành công hay không,
    - dữ liệu runtime được lấy từ nguồn nào,
    - cache runtime được nạp lần cuối khi nào.
    """
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
    """Trả về manifest của model active cùng runtime state hiện tại."""
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
    """Liệt kê các version model gần đây để admin kiểm tra và đối chiếu."""
    limit = int(request.args.get("limit", 20) or 20)
    return jsonify({
        "versions": registry.list_model_versions(limit=limit),
    }), 200


@app.route("/metrics/recommendation", methods=["GET"])
def get_recommendation_metrics():
    """Trả về metric của version model đang active.

    Đây là endpoint thuận tiện để theo dõi nhanh chất lượng mô hình hiện tại
    mà không cần mở trực tiếp registry hoặc artifact manifest.
    """
    active_model_metadata = registry.get_active_model_metadata() or {}
    return jsonify({
        "modelVersion": active_model_metadata.get("id") or model_state.get("versionId", ""),
        "metrics": active_model_metadata.get("metrics") or model_state.get("metrics") or {},
        "runtimeModelState": model_state,
    }), 200


@app.route("/model/reload", methods=["POST"])
def reload_model():
    """Nạp lại artifact model active và tùy chọn refresh dữ liệu runtime.

    Endpoint này hữu ích khi:
    - vừa promote model mới lên registry,
    - muốn ép backend đọc lại artifact đang active,
    - muốn đồng thời refresh snapshot dữ liệu runtime.
    """
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
    """Kích hoạt một lần retrain đồng bộ từ admin surface.

    Request này sẽ tạo training job record, chạy workflow train/register,
    sau đó cập nhật lại trạng thái job để admin có thể theo dõi tiến trình.
    """
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
        # Train và register được gom thành một workflow:
        # tạo dataset -> train -> đánh giá -> lưu artifact -> có thể activate.
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
    """Đọc trạng thái chi tiết của một training job theo ID."""
    job = registry.get_training_job(job_id)
    if not job:
        return jsonify({"error": "Training job not found"}), 404
    return jsonify(job), 200


@app.route("/recommendation-feedback", methods=["POST"])
def recommendation_feedback():
    """Lưu feedback event online phát sinh từ app hoặc payment flow.

    Đây là đầu vào quan trọng cho các bài toán:
    - đo CTR / engagement của recommendation,
    - huấn luyện lại mô hình ở các vòng sau,
    - đối chiếu prediction nào đã dẫn tới hành vi thật của người dùng.
    """
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
    """Endpoint suy luận chính được lớp recommendation của Android gọi tới.

    Request đầu vào gồm `userId`, `candidateCourseIds`, `limit` và tùy chọn `categoryId`.
    Backend sẽ:
    1. dựng profile user từ runtime data,
    2. ánh xạ candidate ID sang course payload thật,
    3. tính score bằng model hiện tại,
    4. trả lại danh sách course đã xếp hạng cùng metadata vận hành.
    """
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
        # Profile user và candidate courses được xây ngay tại backend
        # để model chỉ tập trung vào scoring, không phụ thuộc app phải gửi quá nhiều state.
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

        # Recommendation response luôn kèm metadata vận hành để app và admin biết
        # version model nào đang chạy, có fallback hay không, và latency request.
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
            # Nếu scoring pipeline lỗi giữa đường, API vẫn cố trả recommendation
            # bằng heuristic để tránh làm home screen của app bị rỗng.
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
