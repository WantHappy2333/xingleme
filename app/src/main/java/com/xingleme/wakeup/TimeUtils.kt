package com.xingleme.wakeup

import android.content.Context
import java.util.Locale
import kotlin.math.max

object TimeUtils {
    /** 倒计时格式 mm:ss，最多支持到 99 分钟。 */
    fun clock(millis: Long): String {
        val totalSec = max(0L, millis / 1000L)
        val minutes = totalSec / 60L
        val seconds = totalSec % 60L
        return String.format(Locale.CHINA, "%02d:%02d", minutes, seconds)
    }

    /** 适合读屏的时长，例如 1 分 23 秒。 */
    fun human(context: Context, millis: Long): String {
        val totalSec = max(0L, millis / 1000L)
        val hours = totalSec / 3600L
        val minutes = (totalSec % 3600L) / 60L
        val seconds = totalSec % 60L
        return when {
            hours > 0 -> context.getString(R.string.duration_hours_minutes, hours, minutes)
            minutes > 0 && seconds > 0 -> context.getString(R.string.duration_minutes_seconds, minutes, seconds)
            minutes > 0 -> context.getString(R.string.duration_minutes, minutes)
            else -> context.getString(R.string.duration_seconds, seconds)
        }
    }

    /** 简写：30 秒 / 1 分钟 / 1 分 30 秒。 */
    fun spanShort(context: Context, seconds: Int): String {
        return when {
            seconds < 60 -> context.getString(R.string.duration_seconds, seconds)
            seconds % 60 == 0 -> context.getString(R.string.duration_minutes, seconds / 60)
            else -> context.getString(R.string.duration_minutes_seconds, seconds / 60, seconds % 60)
        }
    }

    fun minutesShort(context: Context, minutes: Int): String =
        context.getString(R.string.duration_minutes, minutes)

    fun ago(context: Context, idleMillis: Long): String {
        val sec = max(0L, idleMillis / 1000L)
        return when {
            sec < 2 -> context.getString(R.string.time_just_now)
            sec < 60 -> context.getString(R.string.time_seconds_ago, sec)
            else -> context.getString(R.string.time_minutes_ago, sec / 60)
        }
    }
}
