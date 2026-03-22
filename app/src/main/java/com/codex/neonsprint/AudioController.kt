package com.codex.neonsprint

import android.media.AudioManager
import android.media.ToneGenerator

class AudioController {
    private var toneGenerator: ToneGenerator? = null
    private var soundEnabled = true
    private var volume = 75

    fun applyOptions(options: GameOptions) {
        val changed = options.masterVolume != volume || options.soundEnabled != soundEnabled
        soundEnabled = options.soundEnabled
        volume = options.masterVolume
        if (changed) {
            recreateToneGenerator()
        }
    }

    fun playMenu() = playTone(ToneGenerator.TONE_PROP_BEEP, 80)
    fun playBoost() = playTone(ToneGenerator.TONE_CDMA_ABBR_INTERCEPT, 110)
    fun playCrash() = playTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 180)

    private fun playTone(tone: Int, durationMs: Int) {
        if (!soundEnabled || volume <= 0) return
        val generator = toneGenerator ?: recreateToneGenerator()
        generator?.startTone(tone, durationMs)
    }

    private fun recreateToneGenerator(): ToneGenerator? {
        toneGenerator?.release()
        toneGenerator = if (soundEnabled && volume > 0) {
            ToneGenerator(AudioManager.STREAM_MUSIC, volume)
        } else {
            null
        }
        return toneGenerator
    }

    fun release() {
        toneGenerator?.release()
        toneGenerator = null
    }
}
