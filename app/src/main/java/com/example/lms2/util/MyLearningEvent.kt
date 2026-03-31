package com.example.lms2.util

sealed class MyLearningEvent {
    data class ShowError(val message: String) : MyLearningEvent()
}

