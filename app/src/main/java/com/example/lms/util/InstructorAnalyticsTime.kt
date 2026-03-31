package com.example.lms.util

fun InstructorTimeRange.toStartAtMillis(now: Long): Long? {
    val daysToSubtract = days ?: return null
    return now - daysToSubtract * 24L * 60L * 60L * 1000L
}

