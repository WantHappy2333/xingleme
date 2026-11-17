package com.xingleme.wakeup

import java.time.Instant
import java.time.ZoneId
import java.util.Calendar
import java.util.Locale
import kotlin.math.max

/** 守夜时段的跨午夜计算。时间用“当天第几分钟”表示。 */
object ScheduleUtils {
    fun formatTime(minuteOfDay: Int): String {
        val minute = minuteOfDay.coerceIn(0, 1439)
        return String.format(Locale.CHINA, "%02d:%02d", minute / 60, minute % 60)
    }

    fun windowLabel(startMinute: Int, endMinute: Int): String =
        "${formatTime(startMinute)} – ${formatTime(endMinute)}"

    fun isWithin(startMinute: Int, endMinute: Int, nowMinute: Int): Boolean {
        if (startMinute == endMinute) return true
        val minute = nowMinute.coerceIn(0, 1439)
        return if (startMinute < endMinute) {
            minute in startMinute until endMinute
        } else {
            minute >= startMinute || minute < endMinute
        }
    }

    fun isWithinNow(settings: SettingsStore): Boolean {
        if (!settings.scheduleEnabled) return true
        val cal = Calendar.getInstance()
        val nowMinute = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        return isWithin(settings.guardStartMinute, settings.guardEndMinute, nowMinute)
    }

    fun nextBoundaryAtMillis(startMinute: Int, endMinute: Int, nowMillis: Long): Long {
        if (startMinute == endMinute) return nowMillis + 24 * 60 * 60 * 1000L
        val zone = ZoneId.systemDefault()
        val now = Instant.ofEpochMilli(nowMillis).atZone(zone)
        var best = Long.MAX_VALUE
        for (dayOffset in 0L..2L) {
            val date = now.toLocalDate().plusDays(dayOffset)
            for (minute in intArrayOf(startMinute, endMinute)) {
                val candidate = date.atStartOfDay(zone)
                    .plusMinutes(minute.toLong())
                    .toInstant()
                    .toEpochMilli()
                if (candidate > nowMillis + 1000L && candidate < best) {
                    best = candidate
                }
            }
        }
        return if (best == Long.MAX_VALUE) nowMillis + 60_000L else best
    }

    fun millisUntilNextBoundary(startMinute: Int, endMinute: Int, nowMillis: Long = System.currentTimeMillis()): Long =
        max(1000L, nextBoundaryAtMillis(startMinute, endMinute, nowMillis) - nowMillis)
}
