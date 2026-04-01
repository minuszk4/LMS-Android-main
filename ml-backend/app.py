from flask import Flask, jsonify, request
from flask_cors import CORS
import os
import json
from model import RecommendationModel
from dotenv import load_dotenv
from pathlib import Path
from typing import Optional

load_dotenv()

app = Flask(__name__)
CORS(app)

model = RecommendationModel()
seed_data = None


def _load_seed_data() -> dict:
    """Load locally generated seed data instead of Firestore."""
    default_seed_path = Path(__file__).resolve().parent.parent / "scripts" / "seed" / "seed_data.json"
    seed_data_path = Path(os.getenv("SEED_DATA_PATH", str(default_seed_path)))

    if not seed_data_path.exists():
        raise FileNotFoundError(
            f"Seed data file not found: {seed_data_path}. Set SEED_DATA_PATH to a valid seed_data.json file."
        )

    with seed_data_path.open("r", encoding="utf-8") as file:
        return json.load(file)


def _get_collection(name: str) -> list:
    return seed_data.get(name, []) if seed_data else []


def _get_course_by_id(course_id: str) -> Optional[dict]:
    for course in _get_collection("courses"):
        if course.get("id") == course_id:
            return course
    return None


def _build_course_list(course_ids: list) -> list:
    courses = []
    for course_id in course_ids:
        course = _get_course_by_id(course_id)
        if course:
            courses.append(course)
    return courses


seed_data = _load_seed_data()

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
        user_enrollment_ids = [
            enrollment.get('courseId')
            for enrollment in _get_collection('enrollments')
            if enrollment.get('userId') == user_id and enrollment.get('courseId')
        ]

        enrolled_courses = _build_course_list(user_enrollment_ids)
        
        # Build user profile from enrolled courses
        user_profile = _build_user_profile(user_id, enrolled_courses)
        
        # Fetch candidate courses
        candidate_courses = _build_course_list(candidate_course_ids)
        
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
    progress_weights = {}
    
    for doc in _get_collection('progress'):
        if doc.get('userId') != user_id:
            continue

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
