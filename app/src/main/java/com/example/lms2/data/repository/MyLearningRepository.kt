package com.example.lms2.data.repository

import com.example.lms2.data.cache.RepositoryCache
import com.example.lms2.data.cache.CacheTTL
import com.example.lms2.data.model.Enrollment
import com.example.lms2.data.model.CurriculumItem
import com.example.lms2.data.model.MyLearningData
import com.example.lms2.data.model.MyLearningItem
import com.example.lms2.data.paging.PageRequest
import com.example.lms2.data.paging.PageResult
import com.example.lms2.util.ResultState
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.tasks.await
import kotlin.math.roundToInt

class MyLearningRepository {

	private val firestore = FirebaseFirestore.getInstance()
	private val enrollmentsCollection = firestore.collection("enrollments")

	private val courseRepository = CourseRepository()
	private val progressRepository = ProgressRepository()
	private val categoryRepository = CategoryRepository()
	private val curriculumRepository = CurriculumRepository()

	suspend fun getMyLearningPaged(
		userId: String,
		pageRequest: PageRequest
	): ResultState<PageResult<MyLearningItem>> {
		if (userId.isBlank()) return ResultState.Error("Thiếu thông tin người dùng")

		val cacheKey = "myLearning:$userId:${pageRequest.normalizedPageSize}:${pageRequest.cursor ?: "first"}"
		if (pageRequest.useCache && !pageRequest.refresh) {
			RepositoryCache.get<PageResult<MyLearningItem>>(cacheKey)?.let {
				return ResultState.Success(it.copy(fromCache = true))
			}
		}

		return try {
			val enrollments = enrollmentsCollection
				.whereEqualTo("userId", userId)
				.orderBy("enrolledAt", Query.Direction.DESCENDING)
				.get()
				.await()
				.toObjects(Enrollment::class.java)

			if (enrollments.isEmpty()) {
				val emptyResult = PageResult(
					items = emptyList<MyLearningItem>(),
					nextCursor = null,
					hasMore = false,
					totalCount = 0,
					fromCache = false
				)
				return ResultState.Success(emptyResult)
			}

			val categoryMap = when (val categoryResult = categoryRepository.getCategories()) {
				is ResultState.Success -> categoryResult.data.associate { it.id to it.name }
				else -> emptyMap()
			}

			val start = pageRequest.cursor?.toIntOrNull() ?: 0
			val end = (start + pageRequest.normalizedPageSize).coerceAtMost(enrollments.size)
			val paginatedEnrollments = enrollments.subList(start, end)

			val items = coroutineScope {
				paginatedEnrollments.map { enrollment ->
					async {
						buildLearningItem(
							userId = userId,
							enrollment = enrollment,
							categoryMap = categoryMap
						)
					}
				}.awaitAll().filterNotNull()
			}

			val sortedItems = items
				.sortedWith(compareBy<MyLearningItem> { !it.isCompleted }
					.thenByDescending { sortTime(it) })
				.let { enrichLatestInProgressLessonMeta(it) }

			val result = PageResult(
				items = sortedItems,
				hasMore = end < enrollments.size,
				nextCursor = if (end < enrollments.size) end.toString() else null,
				totalCount = enrollments.size
			)
			RepositoryCache.put(cacheKey, result, CacheTTL.MEDIUM)
			ResultState.Success(result)
		} catch (e: Exception) {
			ResultState.Error(e.message ?: "Tải danh sách khóa học của bạn thất bại")
		}
	}

	suspend fun getMyLearning(userId: String): ResultState<MyLearningData> {
		return try {
			val enrollments = enrollmentsCollection
				.whereEqualTo("userId", userId)
				.get()
				.await()
				.toObjects(Enrollment::class.java)

			if (enrollments.isEmpty()) {
				return ResultState.Success(MyLearningData())
			}

			val categoryMap = when (val categoryResult = categoryRepository.getCategories()) {
				is ResultState.Success -> categoryResult.data.associate { it.id to it.name }
				else -> emptyMap()
			}

			val items = coroutineScope {
				enrollments.map { enrollment ->
					async {
						buildLearningItem(
							userId = userId,
							enrollment = enrollment,
							categoryMap = categoryMap
						)
					}
				}.awaitAll().filterNotNull()
			}

			val inProgress = items
				.filter { !it.isCompleted }
				.sortedByDescending { sortTime(it) }
				.let { enrichLatestInProgressLessonMeta(it) }

			val completed = items
				.filter { it.isCompleted }
				.sortedByDescending { sortTime(it) }

			ResultState.Success(
				MyLearningData(
					inProgress = inProgress,
					completed = completed
				)
			)
		} catch (e: Exception) {
			ResultState.Error(e.message ?: "Tải danh sách khóa học của bạn thất bại")
		}
	}

	private suspend fun buildLearningItem(
		userId: String,
		enrollment: Enrollment,
		categoryMap: Map<String, String>
	): MyLearningItem? {
		val (courseResult, progressResult) = coroutineScope {
			val courseDeferred = async { courseRepository.getCourseById(enrollment.courseId) }
			val progressDeferred = async { progressRepository.getProgress(userId, enrollment.courseId) }
			Pair(courseDeferred.await(), progressDeferred.await())
		}

		val course = (courseResult as? ResultState.Success)?.data ?: return null
		val categoryName = categoryMap[course.categoryId].orEmpty().ifBlank { "Khóa học" }

		val progress = (progressResult as? ResultState.Success)?.data
		val lastLessonId = progress?.lastLessonId.orEmpty()

		val totalLessons = course.lessonCount.coerceAtLeast(0)
		val completedLessons = (progress?.completedLessons ?: 0).coerceIn(0, totalLessons.coerceAtLeast(1))

		val isCompleted = when {
			progress?.isCompleted == true -> true
			totalLessons in 1..completedLessons -> true
			else -> false
		}

		val progressPercent = when {
			isCompleted -> 100
			totalLessons <= 0 -> 0
			else -> ((completedLessons.toFloat() / totalLessons.toFloat()) * 100f)
				.roundToInt()
				.coerceIn(0, 99)
		}

		return MyLearningItem(
			course = course,
			categoryName = categoryName,
			lastLessonTitle = "",
			lastLessonOrderIndex = -1,
			progressPercent = progressPercent,
			completedLessons = completedLessons,
			totalLessons = totalLessons,
			isCompleted = isCompleted,
			lastLessonId = lastLessonId,
			lastAccessedAt = progress?.lastAccessedAt ?: 0L,
			enrolledAt = enrollment.enrolledAt
		)
	}

	private fun sortTime(item: MyLearningItem): Long {
		return if (item.lastAccessedAt > 0L) item.lastAccessedAt else item.enrolledAt
	}

	private suspend fun enrichLatestInProgressLessonMeta(items: List<MyLearningItem>): List<MyLearningItem> {
		if (items.isEmpty()) return items

		val latest = items.first()
		if (latest.lastLessonId.isBlank()) return items

		val lastLessonMeta = when (val curriculumResult = curriculumRepository.getCurriculum(latest.course.id)) {
			is ResultState.Success -> curriculumResult.data
				.filterIsInstance<CurriculumItem.LessonItem>()
				.firstOrNull { it.lesson.id == latest.lastLessonId }
				?.lesson
			else -> null
		}

		if (lastLessonMeta == null) return items

		val updatedFirst = latest.copy(
			lastLessonTitle = lastLessonMeta.title,
			lastLessonOrderIndex = lastLessonMeta.orderIndex
		)

		return buildList(items.size) {
			add(updatedFirst)
			addAll(items.drop(1))
		}
	}
}
