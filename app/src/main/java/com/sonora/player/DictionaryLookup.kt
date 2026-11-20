package com.sonora.player

import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL

object DictionaryLookup {
    fun define(term: String): String? {
        val word = term.trim().lowercase()
        if (word.isBlank() || word.any { !it.isLetter() } || word.length > 40) return null
        val connection = (URL("https://api.dictionaryapi.dev/api/v2/entries/en/$word").openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 7_000
            readTimeout = 7_000
        }
        return try {
            if (connection.responseCode !in 200..299) return null
            val entries = JSONArray(connection.inputStream.bufferedReader().use { it.readText() })
            val meanings = entries.optJSONObject(0)?.optJSONArray("meanings") ?: return null
            buildList {
                for (i in 0 until meanings.length()) {
                    val meaning = meanings.optJSONObject(i) ?: continue
                    val part = meaning.optString("partOfSpeech")
                    val definitions = meaning.optJSONArray("definitions") ?: continue
                    for (j in 0 until minOf(definitions.length(), 2)) {
                        val definition = definitions.optJSONObject(j)?.optString("definition").orEmpty()
                        if (definition.isNotBlank()) add((if (part.isBlank()) "" else "$part · ") + definition)
                    }
                }
            }.take(4).joinToString("\n\n").ifBlank { null }
        } finally {
            connection.disconnect()
        }
    }
}
