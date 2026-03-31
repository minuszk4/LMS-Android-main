package com.example.lms.util

sealed class InstructorPersonalInfoEvent {
    data class ShowError(val message: String) : InstructorPersonalInfoEvent()
    data class ShowSuccess(val message: String) : InstructorPersonalInfoEvent()
}

