package com.sonora.player

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class BookMetadata(
    val title: String,
    val authors: String,
    val publisher: String,
    val publishedDate: String,
    val description: String,
    val categories: String,
    val language: String
)

object BookMetadataLookup {
    fun lookup(isbn: String): BookMetadata? {
        val clean = isbn.filter { it.isDigit() || it == 'X' || it == 'x' }
        if (clean.length !in 10..13) return null
        val connection = (URL("https://www.googleapis.com/books/v1/volumes?q=isbn:$clean&maxResults=1").openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 8_000
            readTimeout = 8_000
            setRequestProperty("Accept", "application/json")
        }
        return try {
            if (connection.responseCode !in 200..299) return null
            val root = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            val volume = root.optJSONArray("items")?.optJSONObject(0)?.optJSONObject("volumeInfo") ?: return null
            BookMetadata(
                title = volume.optString("title"),
                authors = volume.optJSONArray("authors")?.let { array -> (0 until array.length()).joinToString(", ") { array.optString(it) } }.orEmpty(),
                publisher = volume.optString("publisher"),
                publishedDate = volume.optString("publishedDate"),
                description = volume.optString("description"),
                categories = volume.optJSONArray("categories")?.let { array -> (0 until array.length()).joinToString(", ") { array.optString(it) } }.orEmpty(),
                language = volume.optString("language")
            )
        } finally {
            connection.disconnect()
        }
    }
}
