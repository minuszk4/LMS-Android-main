package com.example.lms2.util

import android.content.Context

object AppSettingsStore {
    private const val PREF_NAME = "lms_app_settings"
    private const val KEY_PUSH_NOTIFICATION_ENABLED = "push_notification_enabled"
    private const val KEY_EMAIL_NOTIFICATION_ENABLED = "email_notification_enabled"
    private const val KEY_DARK_MODE_ENABLED = "dark_mode_enabled"

    private fun prefs(context: Context) = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    fun isPushNotificationEnabled(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_PUSH_NOTIFICATION_ENABLED, true)
    }

    fun setPushNotificationEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_PUSH_NOTIFICATION_ENABLED, enabled).apply()
    }

    fun isEmailNotificationEnabled(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_EMAIL_NOTIFICATION_ENABLED, false)
    }

    fun setEmailNotificationEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_EMAIL_NOTIFICATION_ENABLED, enabled).apply()
    }

    fun isDarkModeEnabled(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_DARK_MODE_ENABLED, false)
    }

    fun setDarkModeEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_DARK_MODE_ENABLED, enabled).apply()
    }
}