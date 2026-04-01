from flask import Flask, jsonify, request
from flask_cors import CORS
import firebase_admin
from firebase_admin import credentials, firestore
import os
import json
from model import RecommendationModel
from dotenv import load_dotenv

load_dotenv()

app = Flask(__name__)
CORS(app)

# Initialize Firebase
firebase_key_path = os.getenv('FIREBASE_KEY_PATH', 'serviceAccountKey.json')
if os.path.exists(firebase_key_path):
    cred = credentials.Certificate(firebase_key_path)
    firebase_admin.initialize_app(cred)
else:
    # Try to load from env var
    firebase_config = {
        "type": "service_account",
        "project_id": os.getenv("FIREBASE_PROJECT_ID"),
        "private_key_id": os.getenv("FIREBASE_PRIVATE_KEY_ID"),
        "private_key": os.getenv("FIREBASE_PRIVATE_KEY", "").replace("\\n", "\n"),
        "client_email": os.getenv("FIREBASE_CLIENT_EMAIL"),
        "client_id": os.getenv("FIREBASE_CLIENT_ID"),
        "auth_uri": "https://accounts.google.com/o/oauth2/auth",
        "token_uri": "https://oauth2.googleapis.com/token",
        "auth_provider_x509_cert_url": "https://www.googleapis.com/oauth2/v1/certs",
        "client_x509_cert_url": os.getenv("FIREBASE_CLIENT_X509_CERT_URL")
    }
    cred = credentials.Certificate(firebase_config)
    firebase_admin.initialize_app(cred)

db = firestore.client()
model = RecommendationModel()

@app.route('/health', methods=['GET'])
def health_check():
    """Health check endpoint."""
    return jsonify({'status': 'ok', 'message': 'ML Recommendation Backend is running'}), 200

@app.route('/recommendations', methods=['POST'])
def get_recommendations():
    """
    Get course recommendations for a user.
    
    Request body:
    {
        "userId": "user_id",
        "limit": 5,
        "candidateCourseIds": ["course_1", "course_2", ...],
        "categoryId": "optional_category_id"
    }
    
    Response:
    {
        "recommendations": [
            {"courseId": "course_1", "score": 0.85},
            ...
        ]
    }
    """
    try:
        data = request.json
        user_id = data.get('userId')
        limit = data.get('limit', 5)
        candidate_course_ids = data.get('candidateCourseIds', [])
        category_id = data.get('categoryId')
        
        if not user_id:
            return jsonify({'error': 'userId is required'}), 400
        
        if not candidate_course_ids:
            return jsonify({'error': 'candidateCourseIds is required'}), 400
        
        # Fetch user enrollments
        enrollments_ref = db.collection('enrollments')
        user_enrollments_docs = enrollments_ref.where('userId', '==', user_id).stream()
        user_enrollment_ids = [doc.get('courseId') for doc in user_enrollments_docs if doc.get('courseId')]
        
        # Fetch enrolled courses to build user profile
        courses_ref = db.collection('courses')
        enrolled_courses = []
        
        for course_id in user_enrollment_ids:
            course_doc = courses_ref.document(course_id).get()
            if course_doc.exists:
                course_data = course_doc.to_dict()
                course_data['id'] = course_id
                enrolled_courses.append(course_data)
        
        # Build user profile from enrolled courses
        user_profile = _build_user_profile(user_id, enrolled_courses)
        
        # Fetch candidate courses
        candidate_courses = []
        for course_id in candidate_course_ids:
            course_doc = courses_ref.document(course_id).get()
            if course_doc.exists:
                course_data = course_doc.to_dict()
                course_data['id'] = course_id
                candidate_courses.append(course_data)
        
        if not candidate_courses:
            return jsonify({'recommendations': []}), 200
        
        # Get scores from model
        scores = model.predict_scores(user_profile, candidate_courses, user_enrollment_ids)
        
        # Sort by score and return top N
        recommendations = [
            {'courseId': course_id, 'score': float(score)}
            for course_id, score in sorted(scores.items(), key=lambda x: x[1], reverse=True)
        ][:limit]
        
        return jsonify({'recommendations': recommendations}), 200
    
    except Exception as e:
        return jsonify({'error': str(e)}), 500

def _build_user_profile(user_id: str, enrolled_courses: list) -> dict:
    """Build user preference profile from enrolled courses."""
    if not enrolled_courses:
        return {
            'categoryWeights': {},
            'levelWeights': {},
            'instructorWeights': {}
        }
    
    category_weights = {}
    level_weights = {}
    instructor_weights = {}
    
    # Get progress data
    progress_ref = db.collection('progress')
    progress_docs = progress_ref.where('userId', '==', user_id).stream()
    progress_weights = {}
    
    for doc in progress_docs:
        course_id = doc.get('courseId')
        completed_lessons = doc.get('completedLessons', 0)
        
        # Find lesson count for this course
        for course in enrolled_courses:
            if course.get('id') == course_id:
                total_lessons = course.get('lessonCount', 1)
                if total_lessons > 0:
                    progress_weight = max(0.5, (completed_lessons / total_lessons) * 2.0)
                else:
                    progress_weight = 0.5
                progress_weights[course_id] = progress_weight
                break
    
    # Calculate weights from enrolled courses
    for course in enrolled_courses:
        weight = progress_weights.get(course.get('id'), 0.5)
        
        category = course.get('categoryId')
        if category:
            category_weights[category] = category_weights.get(category, 0.0) + weight
        
        level = course.get('level')
        if level:
            level_weights[level] = level_weights.get(level, 0.0) + weight
        
        instructor = course.get('instructorId')
        if instructor:
            instructor_weights[instructor] = instructor_weights.get(instructor, 0.0) + weight
    
    # Normalize weights
    total_weight = len(enrolled_courses)
    if total_weight > 0:
        for key in category_weights:
            category_weights[key] /= total_weight
        for key in level_weights:
            level_weights[key] /= total_weight
        for key in instructor_weights:
            instructor_weights[key] /= total_weight
    
    return {
        'categoryWeights': category_weights,
        'levelWeights': level_weights,
        'instructorWeights': instructor_weights
    }

if __name__ == '__main__':
    port = int(os.getenv('PORT', 5000))
    app.run(host='0.0.0.0', port=port, debug=False)
