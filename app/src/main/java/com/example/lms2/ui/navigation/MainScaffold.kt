package com.example.lms2.ui.navigation

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.navigation.NavDestination
import androidx.navigation.NavHostController
import com.example.lms2.ui.component.AdminBottomBar
import com.example.lms2.ui.component.InstructorBottomBar
import com.example.lms2.ui.component.StudentBottomBar

/**
 * Khai báo điều hướng hoặc khung điều hướng trong MainScaffold.
 * File này tập trung các route, NavHost hoặc scaffold dùng chung để điều phối luồng màn hình của ứng dụng.
 * Việc mô tả rõ lớp điều hướng giúp đối chiếu tài liệu use case và luồng chuyển màn hình thuận tiện hơn.
 */

@Composable
fun StudentMainScaffold(
    navController: NavHostController,
    currentDestination: NavDestination?,
    content: @Composable (PaddingValues) -> Unit
) {
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            StudentBottomBar(
                navController = navController,
                currentDestination = currentDestination
            )
        }
    ) { innerPadding ->
        content(innerPadding)
    }
}

@Composable
fun InstructorMainScaffold(
    navController: NavHostController,
    currentDestination: NavDestination?,
    content: @Composable (PaddingValues) -> Unit
) {
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            InstructorBottomBar(
                navController = navController,
                currentDestination = currentDestination
            )
        }
    ) { innerPadding ->
        content(innerPadding)
    }
}

@Composable
fun AdminMainScaffold(
    navController: NavHostController,
    currentDestination: NavDestination?,
    content: @Composable (PaddingValues) -> Unit
) {
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            AdminBottomBar(
                navController = navController,
                currentDestination = currentDestination
            )
        }
    ) { innerPadding ->
        content(innerPadding)
    }
}

