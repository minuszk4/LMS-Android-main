package com.example.lms.data.repository

import com.example.lms.data.model.Review
import com.example.lms.util.ResultState
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await

class ReviewRepository {

	private val firestore = FirebaseFirestore.getInstance()
	private val reviewsCollection = firestore.collection("reviews")
	private val coursesCollection = firestore.collection("courses")

	suspend fun upsertReview(
		courseId: String,
		userId: String,
		userName: String,
		userAvatarUrl: String,
		rating: Int,
		content: String
	): ResultState<Review> {
		if (rating !in 1..5) return ResultState.Error("Đánh giá phải từ 1 đến 5 sao")
		if (content.isBlank()) return ResultState.Error("Nội dung đánh giá không được để trống")

		return try {
			val reviewId = buildReviewId(userId = userId, courseId = courseId)
			val reviewRef = reviewsCollection.document(reviewId)
			val now = System.currentTimeMillis()

			val savedReview = firestore.runTransaction { transaction ->
				val current = transaction.get(reviewRef).toObject(Review::class.java)
				val newReview = Review(
					id = reviewId,
					courseId = courseId,
					userId = userId,
					userName = userName,
					userAvatarUrl = userAvatarUrl,
					rating = rating,
					content = content.trim(),
					isEdited = current != null,
					isHidden = current?.isHidden ?: false,
					createdAt = current?.createdAt ?: now,
					updatedAt = now
				)
				transaction.set(reviewRef, newReview)
				newReview
			}.await()

			refreshCourseRating(courseId)
			ResultState.Success(savedReview)
		} catch (e: Exception) {
			ResultState.Error(e.message ?: "Lưu đánh giá thất bại")
		}
	}

	suspend fun getCourseReviews(courseId: String): ResultState<List<Review>> {
		return try {
			val snapshot = reviewsCollection
				.whereEqualTo("courseId", courseId)
				.whereEqualTo("isHidden", false)
				.orderBy("updatedAt", Query.Direction.DESCENDING)
				.get()
				.await()
			ResultState.Success(snapshot.toObjects(Review::class.java))
		} catch (e: Exception) {
			ResultState.Error(e.message ?: "Lấy danh sách đánh giá thất bại")
		}
	}

	suspend fun getMyReview(courseId: String, userId: String): ResultState<Review?> {
		return try {
			val snapshot = reviewsCollection
				.document(buildReviewId(userId = userId, courseId = courseId))
				.get()
				.await()
			ResultState.Success(snapshot.toObject(Review::class.java))
		} catch (e: Exception) {
			ResultState.Error(e.message ?: "Lấy đánh giá của bạn thất bại")
		}
	}


	suspend fun deleteReview(courseId: String, userId: String): ResultState<Unit> {
		return try {
			reviewsCollection
				.document(buildReviewId(userId = userId, courseId = courseId))
				.delete()
				.await()

			refreshCourseRating(courseId)
			ResultState.Success(Unit)
		} catch (e: Exception) {
			ResultState.Error(e.message ?: "Xóa đánh giá thất bại")
		}
	}

	private suspend fun refreshCourseRating(courseId: String) {
		val snapshot = reviewsCollection
			.whereEqualTo("courseId", courseId)
			.whereEqualTo("isHidden", false)
			.get()
			.await()

		val reviews = snapshot.toObjects(Review::class.java)
		val reviewCount = reviews.size
		val averageRating = if (reviewCount == 0) 0.0 else reviews.map { it.rating }.average()

		coursesCollection.document(courseId)
			.update(
				mapOf(
					"rating" to averageRating,
					"reviewCount" to reviewCount,
					"updatedAt" to System.currentTimeMillis()
				)
			)
			.await()
	}

	private fun buildReviewId(userId: String, courseId: String): String = "${userId}_${courseId}"
}