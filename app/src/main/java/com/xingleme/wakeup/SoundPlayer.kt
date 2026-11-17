package com.xingleme.wakeup

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.util.Log

/**
 * 应用内音量控制的铃声播放器。使用闹钟音频通道，避免被媒体静音影响。
 */
object SoundPlayer {
    private const val TAG = "XinglemeSound"

    fun play(
        context: Context,
        uriString: String?,
        volumePercent: Int,
        loop: Boolean
    ): MediaPlayer? {
        if (volumePercent <= 0 || uriString.isNullOrBlank()) return null
        val uri = runCatching { Uri.parse(uriString) }.getOrNull() ?: return null
        val player = MediaPlayer()
        return try {
            player.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            player.setDataSource(context.applicationContext, uri)
            player.isLooping = loop
            val volume = (volumePercent.coerceIn(0, 100) / 100f)
            player.setVolume(volume, volume)
            player.setOnErrorListener { _, what, extra ->
                Log.w(TAG, "MediaPlayer error: $what / $extra")
                player.release()
                true
            }
            player.prepare()
            player.start()
            player
        } catch (t: Throwable) {
            Log.w(TAG, "无法播放铃声", t)
            runCatching { player.release() }
            null
        }
    }

    fun stop(player: MediaPlayer?) {
        if (player == null) return
        runCatching { if (player.isPlaying) player.stop() }
        runCatching { player.release() }
    }
}
