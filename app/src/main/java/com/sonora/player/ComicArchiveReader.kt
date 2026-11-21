package com.sonora.player

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.util.zip.ZipInputStream
import com.github.junrar.Junrar
import java.io.File

object ComicArchiveReader {
    fun read(resolver: ContentResolver, uri: Uri, rar: Boolean = false): List<Bitmap> {
        val pages = mutableListOf<Pair<String, Bitmap>>()
        if (rar) {
            val input = resolver.openInputStream(uri) ?: error("Could not open comic archive")
            val temporary = File.createTempFile("athenaeum-cbr-", "").apply { delete(); mkdirs() }
            try {
                Junrar.extract(input, temporary)
                temporary.walkTopDown().filter { it.isFile && it.extension.lowercase() in setOf("jpg", "jpeg", "png", "webp", "gif") }
                    .take(2_000).forEach { file ->
                        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        BitmapFactory.decodeFile(file.absolutePath, bounds)
                        var sample = 1
                        while (bounds.outWidth / sample > 2_048 || bounds.outHeight / sample > 3_072) sample *= 2
                        val options = BitmapFactory.Options().apply { inSampleSize = sample.coerceAtLeast(1) }
                        BitmapFactory.decodeFile(file.absolutePath, options)?.let { pages += file.relativeTo(temporary).invariantSeparatorsPath to it }
                    }
            } finally {
                input.close()
                temporary.deleteRecursively()
            }
            return pages.sortedBy { it.first.lowercase() }.map { it.second }
        }
        resolver.openInputStream(uri)?.use { input ->
            ZipInputStream(input).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    val name = entry.name
                    if (!entry.isDirectory && name.substringAfterLast('.', "").lowercase() in setOf("jpg", "jpeg", "png", "webp", "gif")) {
                        if (pages.size < 2_000) {
                            val bytes = zip.readBytes()
                            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                            var sample = 1
                            while (bounds.outWidth / sample > 2_048 || bounds.outHeight / sample > 3_072) sample *= 2
                            val options = BitmapFactory.Options().apply { inSampleSize = sample.coerceAtLeast(1) }
                            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)?.let { pages += name to it }
                        }
                    }
                    zip.closeEntry()
                }
            }
        }
        return pages.sortedBy { it.first.lowercase() }.map { it.second }
    }
}
