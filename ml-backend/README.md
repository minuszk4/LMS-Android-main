# ML Recommendation Backend

Python Flask backend for course recommendations with a trainable ML model, runtime model loading, Firestore-aware data ingestion, and scheduled retraining support.

## What Changed

- Runtime now attempts to load the active trained model on startup.
- Backend can train from `seed_data.json` or live Firestore data.
- Model versions are cached locally and can also be mirrored to Firestore.
- `/jobs/retrain` lets you retrain and activate a model remotely.
- Render cron can call `trigger_retrain.py` every 2 days by default.
- Prediction and feedback events can be logged to Firestore.

## Local Development

### Setup

```bash
python -m venv venv
# On Windows:
venv\Scripts\activate
# On macOS/Linux:
source venv/bin/activate

pip install -r requirements.txt
```

### Environment Variables

Copy `.env.example` to `.env` and configure:

- Firebase service-account variables if you want live Firestore data.
- `RECOMMENDATION_DATA_SOURCE=auto|seed|firestore`
- `MODEL_ADMIN_TOKEN` for `/model/reload` and `/jobs/retrain`
- `DATA_REFRESH_SECONDS` to control runtime cache refresh

If Firebase envs are missing, the backend automatically falls back to `scripts/seed/seed_data.json`.

### Running the API

```bash
python app.py
```

Server starts on `http://localhost:5000`.

### Train a Model Manually

From seed data:

```bash
python train_model.py --data-source seed --output artifacts/recommendation_model.pkl
```

From Firestore:

```bash
python train_model.py --data-source firestore --output artifacts/recommendation_model.pkl
```

### Trigger Retrain via API

```bash
curl -X POST http://localhost:5000/jobs/retrain ^
  -H "Content-Type: application/json" ^
  -H "X-Model-Admin-Token: your_token" ^
  -d "{\"dataSource\":\"auto\",\"windowDays\":90,\"activateIfBetter\":true}"
```

## API Endpoints

### `GET /health`

Returns service health, current model state and runtime data-source information.

### `GET /model/active`

Returns the active model metadata and runtime state.

### `GET /model/versions`

Returns recent model versions so you can inspect `ACTIVE` and `READY` candidates.

### `GET /metrics/recommendation`

Returns the metrics of the active model currently serving traffic.

### `POST /model/reload`

Reloads the active model from the registry/local cache.

Headers:

- `X-Model-Admin-Token: <MODEL_ADMIN_TOKEN>`

### `POST /jobs/retrain`

Trains a new recommendation model and activates it immediately when requested.

Headers:

- `X-Model-Admin-Token: <MODEL_ADMIN_TOKEN>`

Body:

```json
{
  "dataSource": "auto",
  "windowDays": 90,
  "activateIfBetter": true,
  "triggerSource": "manual"
}
```

If `activateIfBetter=true`, the candidate model is only activated when:

- `ndcg@10` improves over the current active model,
- `recall@10` does not drop by more than `0.01`.

### `POST /recommendations`

Request:

```json
{
  "userId": "user_123",
  "limit": 5,
  "candidateCourseIds": ["course_1", "course_2", "course_3"],
  "categoryId": "optional_category_id",
  "source": "home"
}
```

Response:

```json
{
  "recommendations": [
    {"courseId": "course_1", "score": 0.85},
    {"courseId": "course_2", "score": 0.72}
  ],
  "modelVersion": "reco_20260520_020000",
  "fallbackUsed": false,
  "latencyMs": 184,
  "dataSource": "FIRESTORE"
}
```

### `POST /recommendation-feedback`

Logs feedback such as `CLICK`, `ADD_TO_CART`, `PURCHASE`, `ENROLL`, `COMPLETE`.

### `GET /jobs/retrain/<jobId>`

Returns the current status of a stored training job when Firestore registry is configured.

## Deployment on Render

### Blueprint

`render.yaml` now contains:

- one web service for inference,
- one cron service that calls `trigger_retrain.py` every 2 days.

### Required Environment Variables on Render

- `MODEL_ADMIN_TOKEN`
- `RECOMMENDATION_SERVICE_URL=https://your-service-name.onrender.com`
- `RECOMMENDATION_DATA_SOURCE=auto`
- Firebase service-account env vars if you want live Firestore data

### Android Client

Add this to `local.properties`:

```properties
RECOMMENDATION_API_URL=https://your-service-name.onrender.com
```

## Notes

- If no trained model is found, the backend still falls back safely to heuristic scoring.
- The existing legacy JSON artifact is still readable for backward compatibility.
- Firestore artifact mirroring is best-effort and mainly intended for lightweight persistence of model versions/metrics within the current architecture.
