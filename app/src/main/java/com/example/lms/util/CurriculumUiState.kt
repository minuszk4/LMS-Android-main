package com.example.lms.util

import com.example.lms.data.model.CurriculumItem

sealed class CurriculumUiState {
    object Idle : CurriculumUiState()
    object Loading : CurriculumUiState()
    data class Success(val items: List<CurriculumItem>) : CurriculumUiState()
    data class Error(val message: String) : CurriculumUiState()
}