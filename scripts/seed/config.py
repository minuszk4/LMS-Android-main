"""
Cấu hình tham số seed synthetic data cho LMS Android
Quy định số lượng, tỷ lệ, và các rule sinh dữ liệu
"""

# ============================================
# PROFILE SEED (Chọn một để triển khai)
# ============================================

SEED_PROFILES = {
    "quick": {
        "description": "Demo nhanh, tối thiểu data để test UI",
        "num_categories": 3,
        "num_instructors": 2,
        "num_courses_per_instructor": 2,
        "num_students": 5,
        "lessons_per_course_range": (2, 4),
        "quizzes_per_course_range": (1, 2),
        "reviews_per_course_range": (1, 3),
        "enrollment_rate": 0.6,
        "cart_rate": 0.3,
        "days_lookback": 30,
    },
    "standard": {
        "description": "Demo đầy đủ, đủ data cho màn hình list, filter",
        "num_categories": 5,
        "num_instructors": 5,
        "num_courses_per_instructor": 3,
        "num_students": 20,
        "lessons_per_course_range": (3, 8),
        "quizzes_per_course_range": (1, 3),
        "reviews_per_course_range": (2, 8),
        "enrollment_rate": 0.5,
        "cart_rate": 0.2,
        "days_lookback": 180,
    },
    "heavy": {
        "description": "Demo tải cao, test performance pagination",
        "num_categories": 10,
        "num_instructors": 15,
        "num_courses_per_instructor": 5,
        "num_students": 50,
        "lessons_per_course_range": (5, 12),
        "quizzes_per_course_range": (2, 5),
        "reviews_per_course_range": (5, 20),
        "enrollment_rate": 0.4,
        "cart_rate": 0.15,
        "days_lookback": 365,
    },
}

# ============================================
# THAM SỐ CHUNG
# ============================================

# Random seed cố định để tái tạo dữ liệu giống nhau
RANDOM_SEED = 42

# Mốc thời gian (timestamp milliseconds)
# Dữ liệu sẽ trải dài từ NOW - days_lookback đến NOW
BASE_TIMESTAMP_MS = int(__import__('time').time() * 1000)

# ============================================
# DỮ LIỆU DANH MỤC (HARDCODED)
# ============================================

CATEGORIES = [
    {"name": "Lập trình Android"},
    {"name": "Lập trình Web"},
    {"name": "Lập trình Python"},
    {"name": "Mobile Dev"},
    {"name": "UI/UX Design"},
    {"name": "Data Science"},
    {"name": "Devops"},
    {"name": "Blockchain"},
    {"name": "Machine Learning"},
    {"name": "Cloud Computing"},
]

# ============================================
# ENUM/STATUS
# ============================================

COURSE_LEVELS = ["BEGINNER", "INTERMEDIATE", "ADVANCED"]
CART_STATUS = ["ACTIVE", "CHECKED_OUT", "ABANDONED"]
CHAT_SESSION_STATUS = ["ACTIVE", "ARCHIVED", "CLOSED"]
CHAT_SENDER = ["USER", "BOT", "SYSTEM"]
PAYMENT_METHOD = ["E_WALLET", "BANK_TRANSFER"]
PAYMENT_STATUS = ["SUCCESS", "PENDING", "FAILED", "CANCELED"]
NOTIFICATION_TYPES = [
    "PURCHASE_SUCCESS",
    "STUDY_REMINDER",
    "COURSE_UPDATED",
    "NEW_LESSON",
    "QUIZ_AVAILABLE",
    "SYSTEM",
]

BANK_CATALOG = [
    {"name": "Vietcombank", "code": "970436"},
    {"name": "BIDV", "code": "970418"},
    {"name": "VietinBank", "code": "970415"},
    {"name": "Techcombank", "code": "970407"},
    {"name": "MBBank", "code": "970422"},
]

# ============================================
# THAM SỐ SINH DỮ LIỆU
# ============================================

# Range giá khóa học (VND)
COURSE_PRICE_RANGE = (49000, 999000)

# Range số học viên enroll
ENROLLMENT_COUNT_RANGE = (0, 500)

# Range rating khóa học
RATING_RANGE = (3.0, 5.0)

# Range review rating
REVIEW_RATING_RANGE = (1, 5)

# Range quiz passing score
QUIZ_PASSING_SCORE_RANGE = (60, 85)

# Range quiz duration (phút)
QUIZ_DURATION_RANGE = (10, 60)

# Range quiz questions per quiz
QUESTIONS_PER_QUIZ_RANGE = (5, 20)

# Range quiz attempts
QUIZ_ATTEMPTS_RANGE = (1, 5)

# Progress completion cho dữ liệu demo
PROGRESS_COMPLETION_RATES = [0, 0.25, 0.5, 0.75, 1.0]  # Tỷ lệ hoàn thành

# ============================================
# VALIDATION RULES
# ============================================

# Kiểm tra khi seed
VALIDATION_RULES = {
    "check_no_orphan_records": True,  # Không có record FK mà PK ko tồn tại
    "check_unique_constraints": True,  # userId_courseId unique
    "check_enum_values": True,  # Status enum hợp lệ
    "min_courses_per_category": 1,  # Mỗi category ít nhất 1 course
}

# ============================================
# NAMING TEMPLATES (tiếng Việt)
# ============================================

VIETNAMESE_NAMES = [
    "Nguyễn Văn A", "Trần Thị B", "Lê Văn C", "Phạm Thị D", "Hoàng Văn E",
    "Vũ Thị F", "Đặng Văn G", "Bùi Thị H", "Đinh Văn I", "Ngoài Thị K",
    "Tạ Văn L", "Trịnh Thị M", "Võ Văn N", "Lý Thị O", "Đỗ Văn P",
]

COURSE_TITLES_BY_CATEGORY = {
    "Lập trình Android": [
        "Kotlin cơ bản cho Android",
        "Architecture Pattern trong Android",
        "Jetpack Compose từ A đến Z",
        "Firebase + Android Advanced",
    ],
    "Lập trình Web": [
        "React.js từ Beginner đến Pro",
        "Node.js + Express API",
        "Vue 3 Composition API",
        "Next.js Full Stack",
    ],
    "Lập trình Python": [
        "Python cơ bản",
        "Django Web Framework",
        "FastAPI REST API",
        "Automation với Python",
    ],
    "Mobile Dev": [
        "React Native Crash Course",
        "Flutter Advanced",
        "Cross-platform Development",
    ],
    "UI/UX Design": [
        "Figma Advanced",
        "Design System từ A-Z",
        "User Research Essentials",
    ],
    "Data Science": [
        "Pandas + NumPy Master",
        "Data Visualization",
        "Statistical Analysis",
    ],
    "Devops": [
        "Docker & Kubernetes",
        "CI/CD Pipeline",
        "AWS Cloud Essentials",
    ],
    "Blockchain": [
        "Solidity Smart Contract",
        "Web3.js Integration",
    ],
    "Machine Learning": [
        "ML Fundamentals",
        "Deep Learning TensorFlow",
    ],
    "Cloud Computing": [
        "AWS Solutions Architect",
        "Google Cloud Platform",
        "Azure Administration",
    ],
}

LESSON_TITLES_TEMPLATE = [
    "Giới thiệu {topic}",
    "Cài đặt và Setup {topic}",
    "Khái niệm cơ bản {topic}",
    "Ví dụ thực hành {topic}",
    "Best Practices {topic}",
    "Debugging & Troubleshooting",
    "Performance Optimization",
    "Real-world Project",
]

QUIZ_TITLES_TEMPLATE = [
    "Quiz Chương 1: Nền tảng",
    "Quiz Chương 2: Advanced",
    "Quiz Chương 3: Practice",
    "Final Quiz",
]

REVIEW_CONTENTS = [
    "Khóa học rất hữu ích, giảng viên giải thích rõ ràng.",
    "Nội dung chi tiết, có bài tập thực hành tốt.",
    "Recommend cho ai muốn học từ cơ bản đến nâng cao.",
    "Giảng viên thân thiện, hỗ trợ tốt.",
    "Khóa học đáng giá tiền, sẽ tiếp tục học khóa khác.",
    "Rất tuyệt vời, học được nhiều điều mới.",
    "Nội dung được cập nhật liên tục.",
    "Thời lượng khóa học hợp lý, không quá dài hoặc quá ngắn.",
]
