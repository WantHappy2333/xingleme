package com.xingleme.wakeup

import android.content.Context
import android.media.MediaPlayer
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * 一次提醒的震动与铃声。确认或结束提醒时统一停止。
 */
class FeedbackController(
    private val context: Context,
    private val settings: SettingsStore
) {
    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var started = false

    fun start() {
        if (started) return
        started = true
        if (settings.vibrateEnabled) startVibration()
        if (settings.soundEnabled && settings.soundVolume > 0) {
            player = SoundPlayer.play(
                context = context,
                uriString = settings.effectiveRingtoneUri(),
                volumePercent = settings.soundVolume,
                loop = true
            )
        }
    }

    private fun startVibration() {
        val vib = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        } ?: return
        if (!vib.hasVibrator()) return
        vibrator = vib

        val timings = longArrayOf(0L, 520L, 360L, 520L, 900L)
        val amplitudes = when (settings.vibrationIntensity) {
            1 -> intArrayOf(0, 80, 0, 80, 0)
            2 -> intArrayOf(0, 165, 0, 165, 0)
            else -> intArrayOf(0, 255, 0, 255, 0)
        }
        val effect = if (vib.hasAmplitudeControl()) {
            VibrationEffect.createWaveform(timings, amplitudes, 0)
        } else {
            VibrationEffect.createWaveform(timings, 0)
        }
        runCatching { vib.vibrate(effect) }
    }

    fun stop() {
        started = false
        runCatching { vibrator?.cancel() }
        vibrator = null
        SoundPlayer.stop(player)
        player = null
    }
}
