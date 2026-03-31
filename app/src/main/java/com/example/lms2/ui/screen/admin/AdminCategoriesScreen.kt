package com.example.lms2.ui.screen.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.lms2.data.model.Category
import com.example.lms2.viewmodel.AdminManagementEvent
import com.example.lms2.viewmodel.AdminManagementViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminCategoriesScreen(
    viewModel: AdminManagementViewModel
) {
    val uiState = viewModel.uiState.collectAsStateWithLifecycle().value
    val snackbarHostState = remember { SnackbarHostState() }
    var categoryName by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(Unit) {
        viewModel.loadCategories()
    }

    LaunchedEffect(Unit) {
        viewModel.event.collect { event ->
            when (event) {
                is AdminManagementEvent.ShowError -> snackbarHostState.showSnackbar(event.message)
                is AdminManagementEvent.ShowSuccess -> snackbarHostState.showSnackbar(event.message)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Quản lý danh mục", fontWeight = FontWeight.Bold) },
                actions = {
                    TextButton(onClick = { viewModel.loadCategories() }, enabled = !uiState.isLoadingCategories) {
                        Text("Làm mới")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            when {
                uiState.isLoadingCategories -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                else -> {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        item {
                            CreateCategoryCard(
                                categoryName = categoryName,
                                isProcessing = uiState.isProcessing,
                                onCategoryNameChange = { categoryName = it },
                                onCreateCategory = {
                                    viewModel.createCategory(categoryName)
                                    categoryName = ""
                                }
                            )
                        }

                        if (uiState.categories.isEmpty()) {
                            item {
                                Text("Chưa có danh mục")
                            }
                        }

                        items(uiState.categories, key = { it.id }) { category ->
                            CategoryManagementCard(
                                category = category,
                                disableDelete = uiState.isProcessing,
                                onDelete = { viewModel.deleteCategory(category) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CreateCategoryCard(
    categoryName: String,
    isProcessing: Boolean,
    onCategoryNameChange: (String) -> Unit,
    onCreateCategory: () -> Unit
) {
    val canSubmit = categoryName.trim().isNotEmpty()

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Tạo danh mục", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

            OutlinedTextField(
                value = categoryName,
                onValueChange = onCategoryNameChange,
                label = { Text("Tên danh mục") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Button(
                onClick = onCreateCategory,
                enabled = canSubmit && !isProcessing,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Tạo danh mục")
            }
        }
    }
}

@Composable
private fun CategoryManagementCard(
    category: Category,
    disableDelete: Boolean,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(category.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text("ID: ${category.id}", style = MaterialTheme.typography.bodySmall)

            TextButton(
                onClick = onDelete,
                enabled = !disableDelete,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Xóa danh mục")
            }
        }
    }
}
