package com.example.news_alarm.worker

import android.content.Context

fun initializeDefaultPrefs(context: Context) {
    val prefs = context.getSharedPreferences("news_prefs", Context.MODE_PRIVATE)
    if (!prefs.getBoolean("initialized", false)) {
        prefs.edit()
            .putStringSet("interests", setOf("AI", "로봇", "테크"))
            .putLong("check_interval_minutes", 15L)
            .putBoolean("initialized", true)
            .apply()
    }
}
