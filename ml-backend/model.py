import numpy as np
import pandas as pd
from sklearn.preprocessing import StandardScaler
from sklearn.ensemble import RandomForestClassifier
import pickle
import json
from typing import List, Dict, Tuple

class RecommendationModel:
    def __init__(self):
        self.model = None
        self.scaler = StandardScaler()
        self.feature_names = [
            'category_affinity', 'level_affinity', 'instructor_affinity',
            'rating', 'enrollment_count', 'lesson_count', 'recency_weight'
        ]
    
    def extract_features(self, user_profile: Dict, course: Dict, user_enrollments: List[str]) -> np.ndarray:
        """
        Extract features for a course given a user profile.
        
        user_profile: {categoryWeights, levelWeights, instructorWeights}
        course: {id, categoryId, level, instructorId, rating, enrollmentCount, lessonCount}
        """
        category_affinity = user_profile.get('categoryWeights', {}).get(course.get('categoryId'), 0.0)
        level_affinity = user_profile.get('levelWeights', {}).get(course.get('level'), 0.0)
        instructor_affinity = user_profile.get('instructorWeights', {}).get(course.get('instructorId'), 0.0)
        
        rating = course.get('rating', 0.0) / 5.0  # Normalize to [0, 1]
        enrollment_count = min(course.get('enrollmentCount', 0), 100) / 100.0  # Cap at 100
        lesson_count = min(course.get('lessonCount', 0), 100) / 100.0  # Cap at 100
        
        # If user is already enrolled, lower priority
        recency_weight = 0.0 if course.get('id') in user_enrollments else 1.0
        
        features = np.array([
            category_affinity,
            level_affinity,
            instructor_affinity,
            rating,
            enrollment_count,
            lesson_count,
            recency_weight
        ], dtype=float)
        
        return features
    
    def predict_scores(
        self,
        user_profile: Dict,
        courses: List[Dict],
        user_enrollments: List[str]
    ) -> Dict[str, float]:
        """
        Predict recommendation scores for courses.
        
        Returns: {courseId: score, ...}
        """
        if self.model is None:
            # Fallback: use heuristic when model not trained
            return self._heuristic_scores(user_profile, courses, user_enrollments)
        
        scores = {}
        for course in courses:
            features = self.extract_features(user_profile, course, user_enrollments)
            features_scaled = self.scaler.transform([features])[0]
            
            # Use model probability if available
            try:
                prob = self.model.predict_proba([features_scaled])[0]
                score = prob[1] if len(prob) > 1 else 0.5
                scores[course.get('id')] = float(score)
            except Exception:
                # Fallback to heuristic
                scores[course.get('id')] = self._calculate_heuristic(user_profile, course)
        
        return scores
    
    def _heuristic_scores(
        self,
        user_profile: Dict,
        courses: List[Dict],
        user_enrollments: List[str]
    ) -> Dict[str, float]:
        """Fallback heuristic scoring."""
        scores = {}
        for course in courses:
            score = self._calculate_heuristic(user_profile, course)
            scores[course.get('id')] = score
        return scores
    
    def _calculate_heuristic(self, user_profile: Dict, course: Dict) -> float:
        """Calculate heuristic score for a course."""
        category_weight = user_profile.get('categoryWeights', {}).get(course.get('categoryId'), 0.0)
        level_weight = user_profile.get('levelWeights', {}).get(course.get('level'), 0.0)
        instructor_weight = user_profile.get('instructorWeights', {}).get(course.get('instructorId'), 0.0)
        
        profile_score = (category_weight * 0.45) + (level_weight * 0.30) + (instructor_weight * 0.25)
        popularity_score = (course.get('rating', 0.0) / 5.0) * 0.5 + (min(course.get('enrollmentCount', 0), 100) / 100.0) * 0.5
        
        return (profile_score * 0.7) + (popularity_score * 0.3)
    
    def train(self, X_train: np.ndarray, y_train: np.ndarray):
        """Train the recommendation model."""
        # Normalize features
        X_scaled = self.scaler.fit_transform(X_train)
        
        # Train Random Forest
        self.model = RandomForestClassifier(
            n_estimators=100,
            max_depth=10,
            random_state=42,
            n_jobs=-1
        )
        self.model.fit(X_scaled, y_train)
    
    def save(self, filepath: str):
        """Save model to file."""
        with open(filepath, 'wb') as f:
            pickle.dump({'model': self.model, 'scaler': self.scaler}, f)
    
    def load(self, filepath: str):
        """Load model from file."""
        with open(filepath, 'rb') as f:
            data = pickle.load(f)
            self.model = data['model']
            self.scaler = data['scaler']
