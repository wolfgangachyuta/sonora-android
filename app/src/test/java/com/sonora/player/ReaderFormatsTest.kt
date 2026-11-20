package com.sonora.player

import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderFormatsTest {
    @Test
    fun markdownProducesSemanticHeadingsAndEmphasis() {
        val html = PlainTextFormats.markdownToHtml("# Chapter\nA **useful** passage.")
        assertTrue(html.contains("<h1>Chapter</h1>"))
        assertTrue(html.contains("<strong>useful</strong>"))
    }

    @Test
    fun rtfParagraphsBecomeReadableText() {
        val html = PlainTextFormats.rtfToHtml("{\\rtf1\\ansi First paragraph\\par Second paragraph}")
        assertTrue(html.contains("First paragraph"))
        assertTrue(html.contains("Second paragraph"))
    }

    @Test
    fun palmDatabaseMobiWithoutCompressionCanBeRead() {
        val bytes = ByteArray(114)
        fun put16(offset: Int, value: Int) {
            bytes[offset] = (value ushr 8).toByte()
            bytes[offset + 1] = value.toByte()
        }
        fun put32(offset: Int, value: Int) {
            bytes[offset] = (value ushr 24).toByte()
            bytes[offset + 1] = (value ushr 16).toByte()
            bytes[offset + 2] = (value ushr 8).toByte()
            bytes[offset + 3] = value.toByte()
        }
        put16(76, 2)
        put32(78, 94)
        put32(86, 110)
        put16(94, 1) // no compression
        put32(98, 4) // text length
        put16(102, 1) // one text record
        "Test".toByteArray(Charsets.US_ASCII).copyInto(bytes, 110)
        assertEquals("Test", MobiParser.read(bytes))
    }
}
