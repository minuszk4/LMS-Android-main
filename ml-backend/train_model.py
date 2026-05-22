from __future__ import annotations

import argparse
import json
import math
import time
from pathlib import Path
from typing import Dict, List, Tuple

import numpy as np

from data_source import filter_data_by_window, load_runtime_data, resolve_seed_data_path
from model import RecommendationModel
from model_registry import ModelRegistry
from recommendation_dataset import (
    build_course_index,
    build_positive_interactions,
    build_progress_by_user,
    build_user_profile,
    list_student_ids,
)


DEFAULT_MODEL_PATH = Path(__file__).resolve().parent / "artifacts" / "recommendation_model.pkl"


def build_training_set(data: dict) -> Tuple[np.ndarray, np.ndarray, np.ndarray, Dict[str, Dict[str, float]]]:
    model = RecommendationModel()
    rng = np.random.default_rng(42)
    course_by_id = build_course_index(data)
    interactions_by_user = build_positive_interactions(data, course_by_id)
    progress_by_user = build_progress_by_user(data)
    student_ids = list_student_ids(data, interactions_by_user)
    published_courses = list(course_by_id.values())

    X_train: List[np.ndarray] = []
    y_train: List[int] = []
    sample_weights: List[float] = []

    for user_id in student_ids:
        interaction_scores = interactions_by_user.get(user_id, {})
        positive_course_ids = list(interaction_scores.keys())
        if not positive_course_ids:
            continue

        user_profile = build_user_profile(
            user_id=user_id,
            positive_course_ids=positive_course_ids,
            course_by_id=course_by_id,
            progress_records=progress_by_user.get(user_id, []),
        )

        positives = [course_by_id[course_id] for course_id in positive_course_ids if course_id in course_by_id]
        negatives = [course for course in published_courses if course.get("id") not in interaction_scores]

        max_negatives = min(len(negatives), max(len(positives) * 3, 6))
        sampled_negatives = []
        if max_negatives > 0:
            sampled_indices = rng.choice(len(negatives), size=max_negatives, replace=False)
            sampled_negatives = [negatives[index] for index in sorted(sampled_indices.tolist())]

        for course in positives:
            features = model.extract_features(user_profile, course, positive_course_ids)
            X_train.append(features)
            y_train.append(1)
            sample_weights.append(1.0 + float(interaction_scores.get(course.get("id"), 0.0)))

        for course in sampled_negatives:
            features = model.extract_features(user_profile, course, positive_course_ids)
            X_train.append(features)
            y_train.append(0)
            sample_weights.append(1.0)

    if not X_train:
        raise ValueError("No training samples were generated from the available dataset.")

    return (
        np.asarray(X_train, dtype=float),
        np.asarray(y_train, dtype=int),
        np.asarray(sample_weights, dtype=float),
        interactions_by_user,
    )


def evaluate_ranking_metrics(
    model: RecommendationModel,
    data: dict,
    interactions_by_user: Dict[str, Dict[str, float]],
    k_values: Tuple[int, int] = (5, 10),
) -> Dict[str, float]:
    course_by_id = build_course_index(data)
    progress_by_user = build_progress_by_user(data)
    published_courses = list(course_by_id.values())

    evaluable_users = [
        user_id
        for user_id, scores in interactions_by_user.items()
        if len(scores) >= 2
    ]

    if not evaluable_users:
        return {
            "evaluatedUsers": 0,
            "recallAt5": 0.0,
            "precisionAt5": 0.0,
            "ndcgAt5": 0.0,
            "recallAt10": 0.0,
            "precisionAt10": 0.0,
            "ndcgAt10": 0.0,
        }

    recall_totals = {k: 0.0 for k in k_values}
    precision_totals = {k: 0.0 for k in k_values}
    ndcg_totals = {k: 0.0 for k in k_values}

    for user_id in evaluable_users:
        ranked_positive_ids = sorted(
            interactions_by_user[user_id].items(),
            key=lambda item: item[1],
            reverse=True,
        )
        holdout_course_id = ranked_positive_ids[0][0]
        profile_course_ids = [course_id for course_id, _ in ranked_positive_ids[1:]]

        user_profile = build_user_profile(
            user_id=user_id,
            positive_course_ids=profile_course_ids,
            course_by_id=course_by_id,
            progress_records=progress_by_user.get(user_id, []),
        )

        candidate_courses = [
            course
            for course in published_courses
            if course.get("id") not in profile_course_ids
        ]
        scores = model.predict_scores(user_profile, candidate_courses, profile_course_ids)
        ranked_results = [
            course_id
            for course_id, _ in sorted(scores.items(), key=lambda item: item[1], reverse=True)
        ]

        for k in k_values:
            top_k = ranked_results[:k]
            hit = 1.0 if holdout_course_id in top_k else 0.0
            recall_totals[k] += hit
            precision_totals[k] += hit / float(k)
            if hit:
                rank = top_k.index(holdout_course_id)
                ndcg_totals[k] += 1.0 / math.log2(rank + 2.0)

    user_count = float(len(evaluable_users))
    metrics: Dict[str, float] = {"evaluatedUsers": len(evaluable_users)}
    for k in k_values:
        metrics[f"recallAt{k}"] = recall_totals[k] / user_count
        metrics[f"precisionAt{k}"] = precision_totals[k] / user_count
        metrics[f"ndcgAt{k}"] = ndcg_totals[k] / user_count
    return metrics


def build_model_version_id() -> str:
    return time.strftime("reco_%Y%m%d_%H%M%S", time.localtime())


def train_and_register(
    data_source: str = "auto",
    window_days: int = 90,
    output_path: Path = DEFAULT_MODEL_PATH,
    activate_if_better: bool = True,
) -> Dict:
    registry = ModelRegistry()
    runtime_data, source_name = load_runtime_data(
        source=data_source,
        seed_data_path=resolve_seed_data_path(),
    )
    runtime_data = filter_data_by_window(runtime_data, window_days=window_days)

    X_train, y_train, sample_weights, interactions_by_user = build_training_set(runtime_data)
    if len(set(y_train.tolist())) < 2:
        raise ValueError("Training data must contain at least two classes.")

    model = RecommendationModel()
    model.train_with_sample_weights(X_train, y_train, sample_weight=sample_weights)
    metrics = evaluate_ranking_metrics(model, runtime_data, interactions_by_user)

    artifact_bytes = model.dump_bytes()
    output_path.parent.mkdir(parents=True, exist_ok=True)
    output_path.write_bytes(artifact_bytes)

    version_id = build_model_version_id()
    metadata = {
        "modelType": "random_forest_classifier",
        "trainedAt": int(time.time() * 1000),
        "status": "READY",
        "dataSource": source_name,
        "trainingSampleCount": int(len(y_train)),
        "positiveSampleCount": int((y_train == 1).sum()),
        "negativeSampleCount": int((y_train == 0).sum()),
        "metrics": metrics,
        "windowDays": window_days,
        "featureNames": model.feature_names,
    }

    active_model_metadata = registry.get_active_model_metadata()
    activation_decision = (
        registry.should_activate_model(metrics, active_model_metadata)
        if activate_if_better
        else {
            "shouldActivate": False,
            "reason": "activateIfBetter=false, leaving model in READY state",
            "currentMetrics": (active_model_metadata or {}).get("metrics") or {},
            "candidateMetrics": metrics,
            "checks": {},
        }
    )
    should_activate = bool(activation_decision.get("shouldActivate"))
    metadata["status"] = "ACTIVE" if should_activate else "READY"
    metadata["activationDecision"] = activation_decision

    saved_metadata = registry.register_model_version(
        version_id=version_id,
        artifact_bytes=artifact_bytes,
        metadata=metadata,
        activate=should_activate,
    )

    report = {
        "versionId": version_id,
        "dataSource": source_name,
        "artifactPath": str(output_path),
        "metrics": metrics,
        "trainingSampleCount": int(len(y_train)),
        "positiveSampleCount": int((y_train == 1).sum()),
        "negativeSampleCount": int((y_train == 0).sum()),
        "activationDecision": activation_decision,
        "registry": saved_metadata,
    }
    return report


def main() -> None:
    parser = argparse.ArgumentParser(description="Train and register the recommendation model.")
    parser.add_argument(
        "--data-source",
        default="auto",
        choices=["auto", "seed", "firestore"],
        help="Where the training data should come from",
    )
    parser.add_argument(
        "--window-days",
        type=int,
        default=90,
        help="Reserved for future windowed training runs",
    )
    parser.add_argument(
        "--output",
        type=Path,
        default=DEFAULT_MODEL_PATH,
        help="Path to cache the trained model artifact locally",
    )
    parser.add_argument(
        "--activate-if-better",
        default="true",
        help="Whether the trained model should become the active model immediately",
    )
    args = parser.parse_args()

    activate_if_better = str(args.activate_if_better).strip().lower() not in {"false", "0", "no"}
    report = train_and_register(
        data_source=args.data_source,
        window_days=args.window_days,
        output_path=args.output,
        activate_if_better=activate_if_better,
    )
    print(json.dumps(report, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
