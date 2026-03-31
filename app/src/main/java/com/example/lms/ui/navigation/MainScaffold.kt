package com.example.lms.ui.navigation

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.navigation.NavDestination
import androidx.navigation.NavHostController
import com.example.lms.ui.component.InstructorBottomBar
import com.example.lms.ui.component.StudentBottomBar

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

