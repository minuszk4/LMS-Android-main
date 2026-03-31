package com.example.lms2.util

sealed class InstructorPublicProfileEvent {
    data class ShowError(val message: String) : InstructorPublicProfileEvent()
}

