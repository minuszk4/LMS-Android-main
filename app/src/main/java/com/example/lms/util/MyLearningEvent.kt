package com.example.lms.util

sealed class MyLearningEvent {
    data class ShowError(val message: String) : MyLearningEvent()
}

