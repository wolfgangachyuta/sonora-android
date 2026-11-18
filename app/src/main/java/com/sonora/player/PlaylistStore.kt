package com.sonora.player

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class PlaylistStore(context: Context) {
    private val prefs = context.getSharedPreferences("sonora_playlists", Context.MODE_PRIVATE)
    fun load(): List<LocalPlaylist> = runCatching {
        val array = JSONArray(prefs.getString("items", "[]"))
        (0 until array.length()).map { i ->
            val item = array.getJSONObject(i)
            val ids = item.optJSONArray("trackIds") ?: JSONArray()
            LocalPlaylist(item.optString("id"), item.optString("name"), (0 until ids.length()).map { ids.optString(it) })
        }
    }.getOrDefault(emptyList())
    fun save(playlists: List<LocalPlaylist>) {
        val array = JSONArray()
        playlists.forEach { playlist ->
            val ids = JSONArray()
            playlist.trackIds.forEach { ids.put(it) }
            array.put(JSONObject().put("id", playlist.id).put("name", playlist.name).put("trackIds", ids))
        }
        prefs.edit().putString("items", array.toString()).apply()
    }
}
data class LocalPlaylist(val id: String, val name: String, val trackIds: List<String>)
