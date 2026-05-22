import json
import os
import urllib.error
import urllib.request


def main() -> None:
    service_url = str(os.getenv("RECOMMENDATION_SERVICE_URL", "")).strip().rstrip("/")
    admin_token = str(os.getenv("MODEL_ADMIN_TOKEN", "")).strip()
    if not service_url:
        raise RuntimeError("RECOMMENDATION_SERVICE_URL is required")
    if not admin_token:
        raise RuntimeError("MODEL_ADMIN_TOKEN is required")

    payload = {
        "dataSource": str(os.getenv("RETRAIN_DATA_SOURCE", "auto")).strip().lower(),
        "windowDays": int(os.getenv("RETRAIN_WINDOW_DAYS", "90") or "90"),
        "activateIfBetter": str(os.getenv("RETRAIN_ACTIVATE_IF_BETTER", "true")).strip().lower() not in {"false", "0", "no"},
        "triggerSource": "cron",
    }

    request = urllib.request.Request(
        url=f"{service_url}/jobs/retrain",
        data=json.dumps(payload).encode("utf-8"),
        method="POST",
        headers={
            "Content-Type": "application/json",
            "X-Model-Admin-Token": admin_token,
        },
    )

    try:
        with urllib.request.urlopen(request, timeout=1800) as response:
            print(response.read().decode("utf-8"))
    except urllib.error.HTTPError as exc:
        body = exc.read().decode("utf-8", errors="replace")
        raise RuntimeError(f"Retrain request failed with status {exc.code}: {body}") from exc


if __name__ == "__main__":
    main()
