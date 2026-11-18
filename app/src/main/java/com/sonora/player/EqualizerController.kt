package com.sonora.player

import android.media.audiofx.Equalizer

class EqualizerController(audioSessionId: Int) {
    private val effect = runCatching { Equalizer(0, audioSessionId) }.getOrNull()
    val enabled: Boolean get() = effect?.enabled == true
    val bands: Int get() = effect?.numberOfBands?.toInt() ?: 0
    val range: IntArray get() { val r = effect?.bandLevelRange; return if (r == null) intArrayOf(-1500, 1500) else intArrayOf(r[0].toInt(), r[1].toInt()) }
    val presetNames: List<String> get() = effect?.let { eq -> (0 until eq.numberOfPresets).map { eq.getPresetName(it.toShort()).toString() } } ?: emptyList()
    fun level(band: Int): Int = runCatching { effect?.getBandLevel(band.toShort())?.toInt() ?: 0 }.getOrDefault(0)
    fun frequency(band: Int): Int = runCatching { effect?.getCenterFreq(band.toShort())?.div(1000) ?: 0 }.getOrDefault(0)
    fun setEnabled(enabled: Boolean) { effect?.enabled = enabled }
    fun setBandLevel(band: Int, level: Int) { effect?.setBandLevel(band.toShort(), level.toShort()) }
    fun usePreset(index: Int) { effect?.usePreset(index.toShort()) }
    fun release() { runCatching { effect?.release() } }
}