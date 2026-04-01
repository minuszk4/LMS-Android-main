# ML Recommendation Backend

Python Flask backend for course recommendations with ML model.

## Architecture

- **Model**: Random Forest classifier trained on user-course interaction patterns
- **Fallback**: Heuristic scoring when model is not trained or unavailable
- **Database**: Firebase Firestore for user profiles and course data

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

Copy `.env.example` to `.env` and fill in Firebase credentials:

```bash
cp .env.example .env
```

### Running

```bash
python app.py
```

Server starts on `http://localhost:5000`

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
3. Firebase service account with Firestore permissions

### Steps

1. Create new Web Service on Render
2. Connect GitHub repo
3. Set build command: `pip install -r ml-backend/requirements.txt`
4. Set start command: `python ml-backend/app.py`
5. Add environment variables (from Firebase):
   - `FIREBASE_PROJECT_ID`
   - `FIREBASE_PRIVATE_KEY_ID`
   - `FIREBASE_PRIVATE_KEY`
   - `FIREBASE_CLIENT_EMAIL`
   - `FIREBASE_CLIENT_ID`
   - `FIREBASE_CLIENT_X509_CERT_URL`
6. Deploy

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

- Model is **not trained** - using heuristic fallback
- Ready to deploy as-is, app will use heuristic scoring
- Once you have sufficient training data:
  1. Collect user enrollment + course interaction data
  2. Run model training script
  3. Model will automatically use trained weights

### Future Improvements

- Add model training endpoint to retrain periodically
- Add feature engineering for better ranking
- Implement model versioning (A/B testing)
- Monitor prediction accuracy metrics
