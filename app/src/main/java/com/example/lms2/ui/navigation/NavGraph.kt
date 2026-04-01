package com.example.lms2.ui.navigation

import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.lms2.data.model.UserRole
import com.example.lms2.util.CheckoutSource
import com.example.lms2.ui.screen.auth.CheckEmailScreen
import com.example.lms2.ui.screen.auth.ForgotPasswordScreen
import com.example.lms2.ui.screen.auth.LoginScreen
import com.example.lms2.ui.screen.auth.RegisterScreen
import com.example.lms2.ui.screen.admin.AdminCoursesScreen
import com.example.lms2.ui.screen.admin.AdminCategoriesScreen
import com.example.lms2.ui.screen.admin.AdminHomeRoute
import com.example.lms2.ui.screen.admin.AdminInstructorApprovalScreen
import com.example.lms2.ui.screen.admin.AdminUsersScreen
import com.example.lms2.ui.screen.auth.SplashScreen
import com.example.lms2.ui.screen.instructor.InstructorHomeRoute
import com.example.lms2.ui.screen.instructor.stats.InstructorStatisticsRoute
import com.example.lms2.ui.screen.instructor.stats.CourseAnalyticsRoute
import com.example.lms2.ui.screen.instructor.stats.CourseAnalyticsSearchRoute
import com.example.lms2.ui.screen.instructor.course.CourseFormScreen
import com.example.lms2.ui.screen.instructor.course.MyCoursesScreen
import com.example.lms2.ui.screen.instructor.curriculum.CurriculumScreen
import com.example.lms2.ui.screen.instructor.curriculum.LessonFormScreen
import com.example.lms2.ui.screen.instructor.curriculum.QuizFormScreen
import com.example.lms2.ui.screen.instructor.profile.InstructorPersonalInfoRoute
import com.example.lms2.ui.screen.instructor.profile.InstructorProfileScreen
import com.example.lms2.ui.screen.student.CourseDetailScreen
import com.example.lms2.ui.screen.student.ChatbotRoute
import com.example.lms2.ui.screen.student.InstructorPublicProfileRoute
import com.example.lms2.ui.screen.student.CartRoute
import com.example.lms2.ui.screen.student.LessonPlayerScreen
import com.example.lms2.ui.screen.student.MyLearningRoute
import com.example.lms2.ui.screen.student.NotificationRoute
import com.example.lms2.ui.screen.student.PaymentRoute
import com.example.lms2.ui.screen.student.PaymentSuccessScreen
import com.example.lms2.ui.screen.student.QuizAttemptRoute
import com.example.lms2.ui.screen.student.QuizResultRoute
import com.example.lms2.ui.screen.student.QuizReviewRoute
import com.example.lms2.ui.screen.student.SearchScreen
import com.example.lms2.ui.screen.student.StudentHomeRoute
import com.example.lms2.ui.screen.student.StudentProfileEditScreen
import com.example.lms2.ui.screen.student.StudentProfileScreen
import com.example.lms2.ui.screen.student.StudentSettingsScreen
import com.example.lms2.viewmodel.*

@Composable
fun AppNavGraph(
    onDarkModeChanged: (Boolean) -> Unit = {}
) {
    val navController = rememberNavController()

    val authViewModel: AuthViewModel = viewModel()
    val courseViewModel: CourseViewModel = viewModel()
    val curriculumViewModel: CurriculumViewModel = viewModel()
    val lessonViewModel: LessonViewModel = viewModel()
    val quizViewModel: QuizViewModel = viewModel()
    val quizAttemptViewModel: QuizAttemptViewModel = viewModel()
    val myLearningViewModel: MyLearningViewModel = viewModel()
    val cartViewModel: CartViewModel = viewModel()
    val paymentViewModel: PaymentViewModel = viewModel()
    val notificationViewModel: NotificationViewModel = viewModel()
    val chatbotViewModel: ChatbotViewModel = viewModel()
    val instructorPublicProfileViewModel: InstructorPublicProfileViewModel = viewModel()
    val instructorHomeAnalyticsViewModel: InstructorAnalyticsViewModel = viewModel(key = "instructor_home_analytics_vm")
    val instructorStatsAnalyticsViewModel: InstructorAnalyticsViewModel = viewModel(key = "instructor_stats_analytics_vm")
    val courseAnalyticsViewModel: CourseAnalyticsViewModel = viewModel()
    val instructorPersonalInfoViewModel: InstructorPersonalInfoViewModel = viewModel()
    val adminApprovalViewModel: AdminApprovalViewModel = viewModel()
    val adminManagementViewModel: AdminManagementViewModel = viewModel()

    val authUiState by authViewModel.uiState.collectAsStateWithLifecycle()
    val user = authUiState.currentUser


    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    NavHost(
        navController = navController,
        startDestination = Routes.SPLASH
    ) {
            composable(Routes.SPLASH) {
                var hasNavigated by rememberSaveable { mutableStateOf(false) }
                SplashScreen(
                    onProgressComplete = {
                        if (hasNavigated) return@SplashScreen
                        hasNavigated = true

                        val target = if (authViewModel.isUserLoggedIn()) Routes.HOME else Routes.LOGIN
                        navController.navigate(target) {
                            popUpTo(Routes.SPLASH) { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                )
            }

            // Auth Routes
            composable(Routes.LOGIN) { LoginScreen(navController, authViewModel) }
            composable(Routes.REGISTER) { RegisterScreen(navController) }

            navigation(route = "forgot_pwd_flow", startDestination = Routes.FORGOT_PASSWORD) {
                composable(Routes.FORGOT_PASSWORD) { entry ->
                    val vm: AuthViewModel = viewModel(remember(entry) { navController.getBackStackEntry("forgot_pwd_flow") })
                    ForgotPasswordScreen(navController, vm)
                }
                composable(Routes.CHECK_EMAIL) { entry ->
                    val vm: AuthViewModel = viewModel(remember(entry) { navController.getBackStackEntry("forgot_pwd_flow") })
                    CheckEmailScreen(navController, vm)
                }
            }

            composable(Routes.HOME) {
                if (authUiState.isLoading || user == null) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    if (user.role == UserRole.ADMIN) {
                        AdminMainScaffold(navController, currentDestination) { innerPadding ->
                            Box(modifier = Modifier.padding(innerPadding)) {
                                AdminHomeRoute(
                                    viewModel = adminManagementViewModel,
                                    onOpenApprovals = {
                                        navController.navigate(Routes.ADMIN_APPROVALS) { launchSingleTop = true }
                                    },
                                    onOpenUsers = {
                                        navController.navigate(Routes.ADMIN_USERS) { launchSingleTop = true }
                                    },
                                    onOpenCourses = {
                                        navController.navigate(Routes.ADMIN_COURSES) { launchSingleTop = true }
                                    },
                                    onOpenCategories = {
                                        navController.navigate(Routes.ADMIN_CATEGORIES) { launchSingleTop = true }
                                    }
                                )
                            }
                        }
                    } else if (user.role == UserRole.INSTRUCTOR) {
                        InstructorMainScaffold(navController, currentDestination) { innerPadding ->
                            Box(modifier = Modifier.padding(innerPadding)) {
                                InstructorHomeRoute(
                                    instructorId = authViewModel.getCurrentUserId(),
                                    instructorName = user.fullName,
                                    viewModel = instructorHomeAnalyticsViewModel,
                                    onMyCoursesClick = {
                                        navController.navigate(Routes.MY_COURSES) {
                                            popUpTo(navController.graph.findStartDestination().id) {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    },
                                    onCreateCourseClick = {
                                        courseViewModel.onCourseSelected(null)
                                        navController.navigate(Routes.COURSE_FORM)
                                    },
                                    onViewStatisticsClick = {
                                        navController.navigate(Routes.INSTRUCTOR_STATS) {
                                            popUpTo(navController.graph.findStartDestination().id) {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    },
                                    onTopCourseClick = { courseId ->
                                        val encodedCourseId = Uri.encode(courseId)
                                        navController.navigate("${Routes.COURSE_ANALYTICS}/$encodedCourseId") {
                                            launchSingleTop = true
                                        }
                                    }
                                )
                            }
                        }
                    } else {
                        StudentMainScaffold(navController, currentDestination) { innerPadding ->
                            Box(modifier = Modifier.padding(innerPadding)) {
                                StudentHomeRoute(
                                    authViewModel = authViewModel,
                                    courseViewModel = courseViewModel,
                                    myLearningViewModel = myLearningViewModel,
                                    onSearchClick = { navController.navigate(Routes.SEARCH) },
                                    onCartClick = { navController.navigate(Routes.CART) },
                                    onSeeAllClick = {
                                        navController.navigate(Routes.MY_LEARNING) {
                                            popUpTo(navController.graph.findStartDestination().id) {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    },
                                    onContinueClick = { item ->
                                        val lastLessonId = item.lastLessonId
                                        if (lastLessonId.isNotBlank()) {
                                            navController.navigate("${Routes.LESSON_PLAYER}/${item.course.id}/$lastLessonId")
                                        } else {
                                            navController.navigate("${Routes.COURSE_DETAIL}/${item.course.id}")
                                        }
                                    },
                                    onMyCourseClick = { item ->
                                        navController.navigate("${Routes.COURSE_DETAIL}/${item.course.id}")
                                    },
                                    onSuggestedCourseClick = { course ->
                                        navController.navigate("${Routes.COURSE_DETAIL}/${course.id}")
                                    },
                                    onInstructorClick = { instructorId, instructorName ->
                                        val encodedInstructorId = Uri.encode(instructorId)
                                        val encodedInstructorName = Uri.encode(instructorName)
                                        navController.navigate(
                                            "${Routes.INSTRUCTOR_PUBLIC_PROFILE}/$encodedInstructorId?instructorName=$encodedInstructorName"
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
            }

            composable(Routes.PROFILE) {
                if (user?.role == UserRole.ADMIN) {
                    AdminMainScaffold(navController, currentDestination) { innerPadding ->
                        Box(modifier = Modifier.padding(innerPadding)) {
                            InstructorProfileScreen(
                                authViewModel = authViewModel,
                                onAccountInfoClick = {
                                    navController.navigate(Routes.ACCOUNT_INFO)
                                },
                                onPersonalInfoClick = {
                                    navController.navigate(Routes.ACCOUNT_INFO)
                                },
                                onLogoutClick = {
                                    authViewModel.logout()
                                    navController.navigate(Routes.LOGIN) {
                                        popUpTo(0) { inclusive = true }
                                    }
                                }
                            )
                        }
                    }
                } else if (user?.role == UserRole.INSTRUCTOR) {
                    InstructorMainScaffold(navController, currentDestination) { innerPadding ->
                        Box(modifier = Modifier.padding(innerPadding)) {
                            InstructorProfileScreen(
                                authViewModel = authViewModel,
                                onAccountInfoClick = {
                                    navController.navigate(Routes.ACCOUNT_INFO)
                                },
                                onPersonalInfoClick = {
                                    navController.navigate(Routes.INSTRUCTOR_PERSONAL_INFO)
                                },
                                onLogoutClick = {
                                    authViewModel.logout()
                                    navController.navigate(Routes.LOGIN) {
                                        popUpTo(0) { inclusive = true }
                                    }
                                }
                            )
                        }
                    }
                } else {
                    StudentMainScaffold(navController, currentDestination) { innerPadding ->
                        Box(modifier = Modifier.padding(innerPadding)) {
                            StudentProfileScreen(
                                authViewModel = authViewModel,
                                onAccountInfoClick = {
                                    navController.navigate(Routes.ACCOUNT_INFO)
                                },
                                onCartClick = {
                                    navController.navigate(Routes.CART)
                                },
                                onChatbotClick = {
                                    navController.navigate(Routes.CHATBOT)
                                },
                                onSettingsClick = {
                                    navController.navigate(Routes.STUDENT_SETTINGS)
                                },
                                onLogoutClick = {
                                    authViewModel.logout()
                                    navController.navigate(Routes.LOGIN) {
                                        popUpTo(0) { inclusive = true }
                                    }
                                }
                            )
                        }
                    }
                }
            }

            composable(Routes.ADMIN_APPROVALS) {
                AdminMainScaffold(navController, currentDestination) { innerPadding ->
                    Box(modifier = Modifier.padding(innerPadding)) {
                        AdminInstructorApprovalScreen(
                            adminUid = authViewModel.getCurrentUserId(),
                            viewModel = adminApprovalViewModel,
                            onLogoutClick = {
                                authViewModel.logout()
                                navController.navigate(Routes.LOGIN) {
                                    popUpTo(0) { inclusive = true }
                                }
                            }
                        )
                    }
                }
            }

            composable(Routes.ADMIN_USERS) {
                AdminMainScaffold(navController, currentDestination) { innerPadding ->
                    Box(modifier = Modifier.padding(innerPadding)) {
                        AdminUsersScreen(
                            adminUid = authViewModel.getCurrentUserId(),
                            viewModel = adminManagementViewModel
                        )
                    }
                }
            }

            composable(Routes.ADMIN_COURSES) {
                AdminMainScaffold(navController, currentDestination) { innerPadding ->
                    Box(modifier = Modifier.padding(innerPadding)) {
                        AdminCoursesScreen(viewModel = adminManagementViewModel)
                    }
                }
            }

            composable(Routes.ADMIN_CATEGORIES) {
                AdminMainScaffold(navController, currentDestination) { innerPadding ->
                    Box(modifier = Modifier.padding(innerPadding)) {
                        AdminCategoriesScreen(viewModel = adminManagementViewModel)
                    }
                }
            }

            composable(Routes.ACCOUNT_INFO) {
                StudentProfileEditScreen(
                    authViewModel = authViewModel,
                    onBackClick = { navController.popBackStack() }
                )
            }

            composable(Routes.STUDENT_SETTINGS) {
                StudentSettingsScreen(
                    onDarkModeChanged = onDarkModeChanged,
                    onBackClick = { navController.popBackStack() }
                )
            }

            composable(Routes.CHATBOT) {
                ChatbotRoute(
                    userId = authViewModel.getCurrentUserId(),
                    userAvatarUrl = authUiState.currentUser?.avatarUrl,
                    viewModel = chatbotViewModel,
                    onNavigateToCourseDetail = { courseId ->
                        navController.navigate("${Routes.COURSE_DETAIL}/$courseId")
                    },
                    onNavigateToCart = {
                        navController.navigate(Routes.CART)
                    },
                    onNavigateToInstructor = { instructorId ->
                        navController.navigate("${Routes.INSTRUCTOR_PUBLIC_PROFILE}/${Uri.encode(instructorId)}")
                    },
                    onNavigateToMyLearning = {
                        navController.navigate(Routes.MY_LEARNING)
                    },
                    onNavigateToPayment = { courseId ->
                        val encodedIds = Uri.encode(courseId)
                        navController.navigate("${Routes.PAYMENT}?courseIds=$encodedIds&source=direct")
                    },
                    onBackClick = { navController.popBackStack() }
                )
            }

            composable(Routes.INSTRUCTOR_PERSONAL_INFO) {
                InstructorPersonalInfoRoute(
                    instructorId = authViewModel.getCurrentUserId(),
                    viewModel = instructorPersonalInfoViewModel,
                    onBackClick = { navController.popBackStack() }
                )
            }

            // Student Flow
            composable(Routes.SEARCH) {
                SearchScreen(
                    navController = navController,
                    viewModel = courseViewModel,
                    onCourseClick = { course ->
                        navController.navigate("${Routes.COURSE_DETAIL}/${course.id}")
                    },
                    onInstructorClick = { instructorId, instructorName ->
                        val encodedInstructorId = Uri.encode(instructorId)
                        val encodedInstructorName = Uri.encode(instructorName)
                        navController.navigate(
                            "${Routes.INSTRUCTOR_PUBLIC_PROFILE}/$encodedInstructorId?instructorName=$encodedInstructorName"
                        )
                    }
                )
            }

            composable(
                route = "${Routes.COURSE_DETAIL}/{courseId}",
                arguments = listOf(navArgument("courseId") { type = NavType.StringType })
            ) { backStackEntry ->
                val courseId = backStackEntry.arguments?.getString("courseId") ?: ""
                val courseDetailViewModel: CourseDetailViewModel = viewModel()
                CourseDetailScreen(
                    navController = navController,
                    viewModel = courseDetailViewModel,
                    courseId = courseId,
                    userId = authViewModel.getCurrentUserId(),
                    onLessonClick = { itemId ->
                        navController.navigate("${Routes.LESSON_PLAYER}/$courseId/$itemId")
                    }
                )
            }

            composable(
                route = "${Routes.INSTRUCTOR_PUBLIC_PROFILE}/{instructorId}?instructorName={instructorName}&instructorAvatarUrl={instructorAvatarUrl}",
                arguments = listOf(
                    navArgument("instructorId") { type = NavType.StringType },
                    navArgument("instructorName") {
                        type = NavType.StringType
                        defaultValue = ""
                    },
                    navArgument("instructorAvatarUrl") {
                        type = NavType.StringType
                        defaultValue = ""
                    }
                )
            ) { backStackEntry ->
                val instructorId = Uri.decode(backStackEntry.arguments?.getString("instructorId").orEmpty())
                val instructorName = Uri.decode(backStackEntry.arguments?.getString("instructorName").orEmpty())
                val instructorAvatarUrl = Uri.decode(backStackEntry.arguments?.getString("instructorAvatarUrl").orEmpty())

                InstructorPublicProfileRoute(
                    instructorId = instructorId,
                    instructorName = instructorName,
                    instructorAvatarUrl = instructorAvatarUrl,
                    viewModel = instructorPublicProfileViewModel,
                    onBackClick = { navController.popBackStack() }
                )
            }

            composable(
                route = "${Routes.LESSON_PLAYER}/{courseId}/{itemId}",
                arguments = listOf(
                    navArgument("courseId") { type = NavType.StringType },
                    navArgument("itemId") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val courseId = backStackEntry.arguments?.getString("courseId") ?: ""
                val itemId = backStackEntry.arguments?.getString("itemId") ?: ""
                val lessonPlayerViewModel: LessonPlayerViewModel = viewModel()
                LessonPlayerScreen(
                    navController = navController,
                    viewModel = lessonPlayerViewModel,
                    courseId = courseId,
                    itemId = itemId,
                    userId = authViewModel.getCurrentUserId(),
                    onStartQuiz = { quizId ->
                        navController.navigate("${Routes.QUIZ_ATTEMPT}/$courseId/$quizId")
                    }
                )
            }

            composable(
                route = "${Routes.QUIZ_ATTEMPT}/{courseId}/{quizId}",
                arguments = listOf(
                    navArgument("courseId") { type = NavType.StringType },
                    navArgument("quizId") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val courseId = backStackEntry.arguments?.getString("courseId") ?: ""
                val quizId = backStackEntry.arguments?.getString("quizId") ?: ""
                QuizAttemptRoute(
                    courseId = courseId,
                    quizId = quizId,
                    userId = authViewModel.getCurrentUserId(),
                    viewModel = quizAttemptViewModel,
                    onBackClick = { navController.popBackStack() },
                    onNavigateToResult = {
                        navController.navigate("${Routes.QUIZ_RESULT}/$courseId/$quizId")
                    }
                )
            }

            composable(
                route = "${Routes.QUIZ_RESULT}/{courseId}/{quizId}",
                arguments = listOf(
                    navArgument("courseId") { type = NavType.StringType },
                    navArgument("quizId") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val courseId = backStackEntry.arguments?.getString("courseId") ?: ""
                val quizId = backStackEntry.arguments?.getString("quizId") ?: ""
                QuizResultRoute(
                    viewModel = quizAttemptViewModel,
                    studentName = user?.fullName ?: "Sinh vien",
                    onContinueClick = {
                        val lessonPlayerRoute = "${Routes.LESSON_PLAYER}/$courseId/$quizId"
                        val didPopToLessonPlayer = navController.popBackStack(lessonPlayerRoute, inclusive = false)
                        if (!didPopToLessonPlayer) {
                            navController.navigate(lessonPlayerRoute) {
                                popUpTo("${Routes.QUIZ_ATTEMPT}/$courseId/$quizId") { inclusive = true }
                                launchSingleTop = true
                            }
                        }
                    },
                    onReviewAnswerClick = {
                        navController.navigate("${Routes.QUIZ_REVIEW}/$courseId/$quizId")
                    },
                    onRetakeClick = {
                        quizAttemptViewModel.retakeQuiz()
                        navController.popBackStack("${Routes.QUIZ_ATTEMPT}/$courseId/$quizId", inclusive = false)
                    }
                )
            }

            composable(
                route = "${Routes.QUIZ_REVIEW}/{courseId}/{quizId}",
                arguments = listOf(
                    navArgument("courseId") { type = NavType.StringType },
                    navArgument("quizId") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val courseId = backStackEntry.arguments?.getString("courseId") ?: ""
                val quizId = backStackEntry.arguments?.getString("quizId") ?: ""
                QuizReviewRoute(
                    viewModel = quizAttemptViewModel,
                    onBackClick = { navController.popBackStack() },
                    onContinueClick = {
                        val lessonPlayerRoute = "${Routes.LESSON_PLAYER}/$courseId/$quizId"
                        val didPopToLessonPlayer = navController.popBackStack(lessonPlayerRoute, inclusive = false)
                        if (!didPopToLessonPlayer) {
                            navController.navigate(lessonPlayerRoute) {
                                popUpTo("${Routes.QUIZ_ATTEMPT}/$courseId/$quizId") { inclusive = true }
                                launchSingleTop = true
                            }
                        }
                    }
                )
            }


            composable(Routes.MY_LEARNING) {
                StudentMainScaffold(navController, currentDestination) { innerPadding ->
                    Box(modifier = Modifier.padding(innerPadding)) {
                        MyLearningRoute(
                            userId = authViewModel.getCurrentUserId(),
                            viewModel = myLearningViewModel,
                            onCourseClick = { courseId ->
                                navController.navigate("${Routes.COURSE_DETAIL}/$courseId")
                            },
                            onInstructorClick = { instructorId, instructorName ->
                                val encodedInstructorId = Uri.encode(instructorId)
                                val encodedInstructorName = Uri.encode(instructorName)
                                navController.navigate(
                                    "${Routes.INSTRUCTOR_PUBLIC_PROFILE}/$encodedInstructorId?instructorName=$encodedInstructorName"
                                )
                            },
                            onExploreCoursesClick = {
                                navController.navigate(Routes.SEARCH) {
                                    launchSingleTop = true
                                }
                            }
                        )
                    }
                }
            }
            composable(Routes.NOTIFICATIONS) {
                StudentMainScaffold(navController, currentDestination) { innerPadding ->
                    Box(modifier = Modifier.padding(innerPadding)) {
                        NotificationRoute(
                            userId = authViewModel.getCurrentUserId(),
                            viewModel = notificationViewModel
                        )
                    }
                }
            }
            composable(Routes.CART) {
                CartRoute(
                    userId = authViewModel.getCurrentUserId(),
                    viewModel = cartViewModel,
                    onBackClick = { navController.popBackStack() },
                    onNavigateToPayment = { selectedCourseIds ->
                        val encodedIds = Uri.encode(selectedCourseIds.joinToString(","))
                        navController.navigate("${Routes.PAYMENT}?courseIds=$encodedIds&source=cart") {
                            launchSingleTop = true
                        }
                    },
                    onExploreCoursesClick = {
                        navController.navigate(Routes.SEARCH) {
                            launchSingleTop = true
                        }
                    }
                )
            }

            composable(
                route = "${Routes.PAYMENT}?courseIds={courseIds}&source={source}",
                arguments = listOf(
                    navArgument("courseIds") {
                        type = NavType.StringType
                        defaultValue = ""
                    },
                    navArgument("source") {
                        type = NavType.StringType
                        defaultValue = "cart"
                    }
                )
            ) { backStackEntry ->
                val courseIdsArg = backStackEntry.arguments?.getString("courseIds").orEmpty()
                val selectedCourseIds = Uri.decode(courseIdsArg)
                    .split(",")
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                val sourceArg = backStackEntry.arguments?.getString("source").orEmpty()
                val checkoutSource = if (sourceArg.equals("direct", ignoreCase = true)) {
                    CheckoutSource.DIRECT
                } else {
                    CheckoutSource.CART
                }

                PaymentRoute(
                    userId = authViewModel.getCurrentUserId(),
                    selectedCourseIds = selectedCourseIds,
                    checkoutSource = checkoutSource,
                    viewModel = paymentViewModel,
                    onBackClick = { navController.popBackStack() },
                    onCheckoutSuccess = { orderId ->
                        val encodedOrderId = Uri.encode(orderId)
                        navController.navigate("${Routes.PAYMENT_SUCCESS}?orderId=$encodedOrderId") {
                            popUpTo(Routes.PAYMENT) { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                )
            }

            composable(
                route = "${Routes.PAYMENT_SUCCESS}?orderId={orderId}",
                arguments = listOf(
                    navArgument("orderId") {
                        type = NavType.StringType
                        defaultValue = ""
                    }
                )
            ) {
                PaymentSuccessScreen(
                    onBackClick = { navController.popBackStack() },
                    onGoToMyLearning = {
                        navController.navigate(Routes.MY_LEARNING) {
                            popUpTo(Routes.CART) { inclusive = true }
                            launchSingleTop = true
                        }
                    },
                    onGoToHome = {
                        navController.navigate(Routes.HOME) {
                            popUpTo(Routes.CART) { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                )
            }

            // Instructor Flow
            composable(Routes.MY_COURSES) {
                InstructorMainScaffold(navController, currentDestination) { innerPadding ->
                    Box(modifier = Modifier.padding(innerPadding)) {
                        MyCoursesScreen(
                            instructorId = authViewModel.getCurrentUserId(),
                            viewModel = courseViewModel,
                            onNavigateToAddCourse = {
                                courseViewModel.onCourseSelected(null)
                                navController.navigate(Routes.COURSE_FORM)
                            },
                            onNavigateToEditCourse = { course ->
                                courseViewModel.onCourseSelected(course)
                                navController.navigate(Routes.COURSE_FORM)
                            },
                            onManageContent = { course ->
                                courseViewModel.onCourseSelected(course)
                                curriculumViewModel.setCourseId(course.id)
                                navController.navigate(Routes.CURRICULUM)
                            }
                        )
                    }
                }
            }

            composable(Routes.INSTRUCTOR_STATS) {
                InstructorMainScaffold(navController, currentDestination) { innerPadding ->
                    Box(modifier = Modifier.padding(innerPadding)) {
                        InstructorStatisticsRoute(
                            instructorId = authViewModel.getCurrentUserId(),
                            viewModel = instructorStatsAnalyticsViewModel,
                            onOpenCourseAnalyticsSearch = {
                                navController.navigate(Routes.COURSE_ANALYTICS_SEARCH) {
                                    launchSingleTop = true
                                }
                            }
                        )
                    }
                }
            }

            composable(Routes.COURSE_ANALYTICS_SEARCH) {
                CourseAnalyticsSearchRoute(
                    instructorId = authViewModel.getCurrentUserId(),
                    courseViewModel = courseViewModel,
                    onBackClick = { navController.popBackStack() },
                    onCourseClick = { courseId ->
                        val encodedCourseId = Uri.encode(courseId)
                        navController.navigate("${Routes.COURSE_ANALYTICS}/$encodedCourseId") {
                            launchSingleTop = true
                        }
                    }
                )
            }

            composable(
                route = "${Routes.COURSE_ANALYTICS}/{courseId}",
                arguments = listOf(navArgument("courseId") { type = NavType.StringType })
            ) { backStackEntry ->
                val encodedCourseId = backStackEntry.arguments?.getString("courseId").orEmpty()
                val courseId = Uri.decode(encodedCourseId)

                CourseAnalyticsRoute(
                    courseId = courseId,
                    viewModel = courseAnalyticsViewModel,
                    onBackClick = { navController.popBackStack() }
                )
            }

            composable(Routes.COURSE_FORM) {
                CourseFormScreen(
                    instructorId = authViewModel.getCurrentUserId(),
                    authViewModel = authViewModel,
                    viewModel = courseViewModel,
                    onBackClick = { navController.popBackStack() }
                )
            }

            composable(Routes.CURRICULUM) {
                CurriculumScreen(
                    courseViewModel = courseViewModel,
                    viewModel = curriculumViewModel,
                    onBackClick = { navController.popBackStack() },
                    onAddLesson = { courseId ->
                        lessonViewModel.initWith(null, courseId)
                        navController.navigate(Routes.LESSON_FORM)
                    },
                    onAddQuiz = { courseId ->
                        quizViewModel.initWith(null, courseId)
                        navController.navigate(Routes.QUIZ_FORM)
                    },
                    onEditLesson = { lessonItem, courseId ->
                        lessonViewModel.initWith(lessonItem.lesson, courseId)
                        navController.navigate(Routes.LESSON_FORM)
                    },
                    onEditQuiz = { quizItem, courseId ->
                        quizViewModel.initWith(quizItem.quiz, courseId)
                        navController.navigate(Routes.QUIZ_FORM)
                    }
                )
            }

            composable(Routes.LESSON_FORM) {
                LessonFormScreen(
                    viewModel = lessonViewModel,
                    onBackClick = {
                        navController.popBackStack()
                        curriculumViewModel.loadCurriculum()
                    }
                )
            }

            composable(Routes.QUIZ_FORM) {
                QuizFormScreen(
                    viewModel = quizViewModel,
                    onBackClick = {
                        navController.popBackStack()
                        curriculumViewModel.loadCurriculum()
                    }
                )
            }
        }
}


