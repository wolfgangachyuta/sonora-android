package com.sonora.player

import java.io.ByteArrayOutputStream
import java.nio.charset.Charset

/** Reader for legacy PalmDOC-compressed MOBI 6 files. KF8/AZW3 hybrid records are detected separately. */
object MobiParser {
    fun read(bytes: ByteArray): String {
        require(bytes.size >= 86) { "The MOBI file is too small or malformed." }
        val count = u16(bytes, 76)
        require(count > 1 && 78 + count * 8 <= bytes.size) { "Invalid Palm database record directory." }
        val offsets = (0 until count).map { i -> u32(bytes, 78 + i * 8) }
        val records = offsets.mapIndexed { i, start ->
            val end = if (i + 1 < offsets.size) offsets[i + 1] else bytes.size.toLong()
            require(start >= 0L && start < end && end <= bytes.size.toLong()) { "Invalid MOBI record offset." }
            bytes.copyOfRange(start.toInt(), end.toInt())
        }
        val header = records.first()
        val compression = u16(header, 0)
        val textLength = u32(header, 4)
        val textRecords = u16(header, 8)
        val encodingCode = if (header.size >= 32) u32(header, 28) else 1252
        val charset = if (encodingCode == 65001L) Charsets.UTF_8 else Charset.forName("windows-1252")
        require(compression == 1 || compression == 2) {
            if (compression == 17480) "This AZW/KF8 file uses unsupported Huff/CDIC compression." else "Unsupported MOBI compression: $compression"
        }
        val output = ByteArrayOutputStream()
        records.drop(1).take(textRecords).forEach { record ->
            val decoded = if (compression == 1) record else palmDoc(record)
            output.write(decoded)
        }
        val raw = output.toByteArray().let { if (textLength in 1L..it.size.toLong()) it.copyOf(textLength.toInt()) else it }
        return raw.toString(charset)
    }

    private fun palmDoc(input: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        var i = 0
        while (i < input.size) {
            val value = input[i].toInt() and 0xFF
            i++
            when {
                value == 0 -> out.write(0)
                value in 1..8 -> {
                    val end = (i + value).coerceAtMost(input.size)
                    out.write(input, i, end - i)
                    i = end
                }
                value in 9..0x7F -> out.write(value)
                value in 0x80..0xBF && i < input.size -> {
                    val next = input[i++].toInt() and 0xFF
                    val distance = ((value and 0x3F) shl 5) or (next ushr 3)
                    val length = (next and 7) + 3
                    val data = out.toByteArray()
                    if (distance > 0 && distance <= data.size) {
                        repeat(length) {
                            val current = out.toByteArray()
                            val source = current.size - distance
                            if (source >= 0) out.write(current[source].toInt() and 0xFF)
                        }
                    }
                }
                else -> { out.write(0x20); out.write(value xor 0x80) }
            }
        }
        return out.toByteArray()
    }

    private fun u16(bytes: ByteArray, offset: Int): Int =
        ((bytes[offset].toInt() and 0xFF) shl 8) or (bytes[offset + 1].toInt() and 0xFF)

    private fun u32(bytes: ByteArray, offset: Int): Long =
        ((bytes[offset].toLong() and 0xFF) shl 24) or
        ((bytes[offset + 1].toLong() and 0xFF) shl 16) or
        ((bytes[offset + 2].toLong() and 0xFF) shl 8) or
        (bytes[offset + 3].toLong() and 0xFF)
}
