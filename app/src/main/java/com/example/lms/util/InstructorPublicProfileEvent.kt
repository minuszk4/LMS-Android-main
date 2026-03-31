package com.example.lms.util

sealed class InstructorPublicProfileEvent {
    data class ShowError(val message: String) : InstructorPublicProfileEvent()
}

