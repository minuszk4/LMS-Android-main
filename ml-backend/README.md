# ML Recommendation Backend

Python Flask backend for course recommendations with ML model.

## Architecture

- **Model**: Lightweight linear scorer trained on user-course interaction patterns
- **Fallback**: Heuristic scoring when model is not trained or unavailable
- **Data source**: Local `scripts/seed/seed_data.json` during development and training

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

Copy `.env.example` to `.env` if you still want to use Firebase in other parts of the repo.

### Running

```bash
python app.py
```

Server starts on `http://localhost:5000`

### Train Model

Train the model from local seed data and save the weight file for later experiments:

```bash
python train_model.py --seed-data ../scripts/seed/seed_data.json --output artifacts/recommendation_model.json
```

## API Endpoints

### Health Check

```
GET /health
```

Response:
```json
{
  "status": "ok",
  "message": "ML Recommendation Backend is running"
}
```

### Get Recommendations

```
POST /recommendations
```

Request:
```json
{
  "userId": "user_123",
  "limit": 5,
  "candidateCourseIds": ["course_1", "course_2", "course_3"],
  "categoryId": "category_1"  // optional
}
```

Response:
```json
{
  "recommendations": [
    {"courseId": "course_1", "score": 0.85},
    {"courseId": "course_2", "score": 0.72},
    {"courseId": "course_3", "score": 0.65}
  ]
}
```

## Deployment on Render

### Prerequisites

1. GitHub account with this repo pushed
2. Render account
3. No Firebase credentials are required for the temporary seed-data deployment

### Steps

1. Create a new Blueprint on Render and connect this repository
2. Render will use `render.yaml` at the repo root
3. Deploy the `lms-ml-backend` service

If you prefer to create a manual Web Service instead of a Blueprint:

1. Set the root directory to `ml-backend`
2. Set the build command to `pip install -r requirements.txt`
3. Set the start command to `gunicorn app:app`
4. Deploy

### Getting Render URL

After deployment, your backend will be available at:
```
https://your-service-name.onrender.com
```

Add this to Android app's `local.properties`:
```properties
RECOMMENDATION_API_URL=https://your-service-name.onrender.com
```

## Development Notes

### Current Status

- Model can be trained from `seed_data.json` for offline experiments
- Runtime API still uses heuristic scoring only
- The saved artifact is temporary and not loaded automatically

### Future Improvements

- Add model training endpoint to retrain periodically
- Add feature engineering for better ranking
- Implement model versioning (A/B testing)
- Monitor prediction accuracy metrics
