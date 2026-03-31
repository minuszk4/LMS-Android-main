# Software Requirements Specification (SRS)
## LMS Android - Detailed Project Scope

Version: 1.0
Date: 2026-03-31
Author: Project Team
Status: Baseline Draft

---

## 1. Purpose and Scope

### 1.1 Purpose
This document defines the detailed scope and software requirements of the LMS Android project. It establishes a shared baseline for Product, Engineering, QA, and stakeholders to align on what is included, what is excluded, and what quality level is expected.

### 1.2 Product Scope Summary
LMS Android is a mobile learning platform for students and instructors, including:
- Authentication and role-based access.
- Course catalog, search, and detail viewing.
- Enrollment and learning progress tracking.
- Lesson and quiz learning flow.
- Shopping cart, payment flow, and order tracking.
- Instructor course management and analytics.
- AI assistant chatbot with course tools and navigation actions.

### 1.3 Scope Baseline Type
This SRS describes:
- Functional scope (features and flows).
- Non-functional scope (performance, security, reliability, usability).
- Data and integration scope.
- Explicit out-of-scope boundaries.

---

## 2. Stakeholders and User Classes

### 2.1 Stakeholders
- Product Owner: defines business goals and release priorities.
- Engineering Team: implements mobile app and backend integration logic.
- QA Team: validates requirements and release quality.
- End Users: students and instructors.
- Administrators (future): platform governance and moderation.

### 2.2 User Classes
- Guest user: unauthenticated browsing (limited).
- Student: enrolls and studies courses.
- Instructor: creates and manages own courses and curriculum.

---

## 3. Business Goals

- Increase course discovery and conversion to enrollment.
- Improve learning completion through progress visibility.
- Provide self-service support via AI chatbot.
- Enable instructors to publish/manage content and track outcomes.
- Ensure stable operation on Android devices with acceptable latency.

---

## 4. In-Scope Features

## 4.1 Identity and Access
- Login, register, forgot password, check-email flow.
- Session persistence.
- Role-based navigation for student and instructor.

## 4.2 Student Experience
- Home feed of courses.
- Search and filter courses.
- Course detail with curriculum preview.
- Add to cart and cart management.
- Payment flow and payment success screen.
- My Learning dashboard and progress indicators.
- Notifications screen.

## 4.3 Learning Flow
- Enrollment creation.
- Lesson player for course content.
- Quiz attempt workflow.
- Quiz result and quiz review.
- Course-level and lesson-level progress updates.

## 4.4 Instructor Experience
- Instructor home and profile pages.
- Create/edit course form.
- Curriculum management: lessons and quizzes.
- My Courses management list.
- Instructor statistics and course analytics screens.

## 4.5 AI Chatbot Experience
- Chat session creation and session switching.
- User and bot message history persistence.
- AI-assisted actions with tool-driven responses:
  - Search courses.
  - Get learning summary.
  - Recommend courses.
  - Show course details.
  - Add course to cart.
- Structured message rendering:
  - Text message.
  - Course card.
  - Course list.
  - Progress chart.
- In-chat action buttons with direct navigation:
  - Go to course detail.
  - Open cart.
  - Go to instructor profile.
  - Go to My Learning.
  - Quick payment (direct checkout route).

## 4.6 Recommendations
- Course recommendation repository using vector-based similarity and ranking.
- Personalized recommendations for students.
- Fallback behavior for sparse user history.

---

## 5. Out-of-Scope (Current Release)

- Web application and iOS application.
- Multi-language localization beyond current app language.
- Enterprise SSO (SAML/OAuth enterprise providers).
- Offline-first full learning mode with conflict resolution.
- Manual admin moderation console.
- Full cloud ML training pipeline for recommendation model.
- Proctored exam and anti-cheat mechanisms.
- Refund/dispute lifecycle management for payments.

---

## 6. Assumptions and Dependencies

### 6.1 Assumptions
- Firebase services are available and configured.
- Mobile network is available for most user actions.
- API keys are managed via local properties/build config for development.

### 6.2 External Dependencies
- Firebase Auth.
- Cloud Firestore.
- Firebase Storage.
- AI provider endpoint (OpenRouter/Gemini integration path in repository logic).
- Third-party media/image libraries (as configured in build dependencies).

---

## 7. Functional Requirements

### FR-01 Authentication
- The system shall allow user registration and login.
- The system shall support password reset via email flow.
- The system shall route user to role-appropriate main flow after login.

### FR-02 Course Discovery
- The system shall provide course listing for students.
- The system shall provide search capability by keyword.
- The system shall open course detail from list/search/chat actions.

### FR-03 Course Enrollment and Learning
- The system shall allow enrollment into available courses.
- The system shall present lessons and quizzes according to curriculum order.
- The system shall track progress at course and lesson granularity.

### FR-04 Quiz Lifecycle
- The system shall allow users to start a quiz attempt.
- The system shall store and evaluate quiz answers.
- The system shall display quiz result and review flow.

### FR-05 Cart and Payment
- The system shall allow adding/removing courses in cart.
- The system shall support payment screen with selected course IDs.
- The system shall persist order and order item records after successful checkout.

### FR-06 Instructor Course Management
- The system shall allow instructors to create and update course data.
- The system shall allow instructors to manage lessons/quizzes in curriculum.
- The system shall provide instructor analytics views.

### FR-07 Chatbot Core
- The system shall persist chat sessions and chat messages.
- The system shall support structured bot response types via metadata.
- The system shall support tool-like chatbot actions for learning workflows.

### FR-08 Chatbot Navigation Actions
- The system shall provide in-chat action buttons for direct app navigation.
- The system shall navigate to course detail when course ID is available.
- The system shall navigate to cart, instructor profile, My Learning, and payment routes from chat actions.

### FR-09 Recommendation
- The system shall return recommended courses based on user behavior and course features.
- The system shall avoid low-quality recommendations in sparse-history scenarios through fallback ranking.

---

## 8. Non-Functional Requirements

### NFR-01 Performance
- Common screen navigation should complete within acceptable mobile UX latency.
- Chat UI interactions shall remain responsive during API calls.
- List rendering shall handle realistic demo datasets without UI freeze.

### NFR-02 Reliability
- The app shall handle transient network errors gracefully.
- The app shall display user-friendly error messages for failed operations.
- Data updates shall preserve consistency for critical flows (cart/payment/enrollment/progress).

### NFR-03 Security and Privacy
- API keys shall not be hardcoded in source control.
- Sensitive user data shall be protected by Firebase security rules and least-privilege access.
- Authentication state shall be validated before protected operations.

### NFR-04 Usability
- Student and instructor flows shall be clearly separated by role.
- Chat actions shall be understandable and discoverable.
- Critical tasks (search, detail, cart, payment, learning) should be reachable in minimal steps.

### NFR-05 Maintainability
- Code should remain modular by repository, viewmodel, and screen boundaries.
- New chatbot actions should be extensible without rewriting core chat architecture.
- Requirements changes should map cleanly to affected modules.

---

## 9. Data Scope

Primary logical entities in project scope include:
- users
- instructors
- categories
- courses
- lessons
- quizzes
- enrollments
- progress
- lessonProgress
- reviews
- cartItems
- carts
- orders
- orderItems
- notifications
- chatSessions
- chatMessages

Data quality constraints in scope:
- Referential integrity at application layer for key relationships.
- Composite uniqueness where required by business flows (enrollment/review/cart item patterns).
- Valid state values for enum-like fields (payment status, cart status, role, level).

---

## 10. Interface Scope

### 10.1 Internal Interfaces
- ViewModel to Repository contracts for each domain module.
- Chatbot repository to recommendation/course/cart/progress repositories.

### 10.2 External Interfaces
- Firebase SDK interfaces for auth/firestore/storage.
- AI API interface for chatbot response generation and tool-loop behavior.

### 10.3 Navigation Interfaces
- Route-based navigation for student/instructor/chat/payment/learning/quiz screens.
- Deep-link-like route parameters for course ID, quiz ID, payment inputs.

---

## 11. Constraints

- Android SDK and dependency versions as defined in project gradle config.
- Free-tier cloud quotas may limit large write operations in test/demo environments.
- Mobile-only release context for current project phase.

---

## 12. Risks and Mitigation

### Risk-01: AI Service Variability
- Risk: upstream model/API behavior changes can affect chatbot consistency.
- Mitigation: keep deterministic tool metadata path and fallback messaging.

### Risk-02: Quota Limits in Demo Data
- Risk: write quotas can interrupt bulk seeding.
- Mitigation: staged upload, resume mode, max-write guard per run.

### Risk-03: Navigation Drift in Chat Actions
- Risk: route contract changes can break in-chat actions.
- Mitigation: centralize route constants and run regression checks on chatbot navigation.

---

## 13. Acceptance Scope (Release-Level)

A release is in-scope complete when:
- Student core flows are usable end-to-end.
- Instructor content management flows are usable end-to-end.
- Chatbot can return structured course/progress responses and trigger direct navigation actions.
- Payment, enrollment, and progress pathways are functionally coherent.
- Known out-of-scope items are not silently introduced as implicit commitments.

---

## 14. Traceability Overview

Scope-to-module mapping (high level):
- Authentication: auth screens + auth viewmodel + Firebase Auth.
- Student learning: student screens + course/enrollment/progress repositories.
- Instructor management: instructor screens + curriculum/course repositories.
- Chatbot: chatbot screen + chatbot viewmodel + OpenRouter/Gemini repository logic.
- Recommendation: recommendation repository + course/user behavior data.
- Payment/cart: cart/payment screens + cart/order repositories.

---

## 15. Change Control

Any future scope change should define:
- Requirement ID(s) impacted.
- Modules and routes impacted.
- Migration impact on data model.
- QA regression checklist updates.

---

## 16. Appendix A - Glossary

- LMS: Learning Management System.
- SRS: Software Requirements Specification.
- In-scope: committed features for this release baseline.
- Out-of-scope: explicitly excluded features.
- Chat action: user-triggered quick action button inside chatbot message UI.

---

End of document.
