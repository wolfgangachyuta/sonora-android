package com.sonora.player

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import java.io.InputStream

/** User-imported TSV dictionary packs remain fully local and searchable offline. */
object OfflineDictionary {
    fun importPack(context: Context, input: InputStream): Int {
        val database = SQLiteDatabase.openDatabase(
            context.getDatabasePath("athenaeum_dictionary.db").absolutePath,
            null,
            SQLiteDatabase.OPEN_READWRITE or SQLiteDatabase.CREATE_IF_NECESSARY
        )
        var imported = 0
        try {
            database.execSQL("CREATE TABLE IF NOT EXISTS entries (word TEXT PRIMARY KEY COLLATE NOCASE, definition TEXT NOT NULL)")
            database.beginTransaction()
            val statement = database.compileStatement("INSERT OR REPLACE INTO entries(word, definition) VALUES(?, ?)")
            input.bufferedReader(Charsets.UTF_8).useLines { lines ->
                lines.forEachIndexed { index, raw ->
                    val line = raw.removePrefix("\uFEFF").trim()
                    if (line.isBlank() || line.startsWith("#") || (index == 0 && line.startsWith("word", true))) return@forEachIndexed
                    val split = line.indexOf('\t').takeIf { it > 0 } ?: line.indexOf('|').takeIf { it > 0 } ?: line.indexOf(',').takeIf { it > 0 } ?: return@forEachIndexed
                    val word = line.substring(0, split).trim().lowercase()
                    val definition = line.substring(split + 1).trim()
                    if (word.isBlank() || definition.isBlank() || word.length > 100) return@forEachIndexed
                    statement.clearBindings()
                    statement.bindString(1, word)
                    statement.bindString(2, definition)
                    statement.executeInsert()
                    imported++
                }
            }
            database.setTransactionSuccessful()
        } finally {
            if (database.inTransaction()) database.endTransaction()
            database.close()
        }
        return imported
    }

    fun lookup(context: Context, word: String): String? {
        val clean = word.trim().lowercase()
        if (clean.isBlank()) return null
        val file = context.getDatabasePath("athenaeum_dictionary.db")
        if (!file.exists()) return null
        val database = SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
        return try {
            database.rawQuery("SELECT definition FROM entries WHERE word = ? LIMIT 1", arrayOf(clean)).use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        } finally {
            database.close()
        }
    }

    fun entryCount(context: Context): Int {
        val file = context.getDatabasePath("athenaeum_dictionary.db")
        if (!file.exists()) return 0
        val database = SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
        return try {
            database.rawQuery("SELECT COUNT(*) FROM entries", null).use { cursor ->
                if (cursor.moveToFirst()) cursor.getInt(0) else 0
            }
        } finally {
            database.close()
        }
    }
}
