package com.sonora.player

import android.content.Context

class PlaybackHistory(context: Context) {
    private val prefs = context.getSharedPreferences("sonora_history", Context.MODE_PRIVATE)
    fun recent(): List<String> = prefs.getString("ids", "").orEmpty().split(",").filter(String::isNotBlank)
    fun record(id: String) {
        val list = (listOf(id) + recent().filterNot { it == id }).take(100)
        prefs.edit().putString("ids", list.joinToString(",")).apply()
    }
    fun clear() { prefs.edit().remove("ids").apply() }
}