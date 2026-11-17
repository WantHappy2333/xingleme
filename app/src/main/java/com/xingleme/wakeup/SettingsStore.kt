package com.xingleme.wakeup

import android.content.Context
import android.content.SharedPreferences
import android.provider.Settings
import androidx.core.content.edit

/**
 * 所有设置只保存在本机 SharedPreferences 中。
 */
class SettingsStore(context: Context) {
    private val appContext = context.applicationContext
    private val prefs: SharedPreferences =
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    init {
        migrateThresholdToSeconds()
    }

    var checkIntervalSec: Int
        get() = prefs.getInt(KEY_INTERVAL, DEFAULT_INTERVAL_SEC).coerceIn(MIN_INTERVAL_SEC, MAX_INTERVAL_SEC)
        set(value) = prefs.edit { putInt(KEY_INTERVAL, value.coerceIn(MIN_INTERVAL_SEC, MAX_INTERVAL_SEC)) }

    /** 静止阈值统一用秒保存，自定义也以秒为单位。 */
    var thresholdSec: Int
        get() = prefs.getInt(KEY_THRESHOLD_SEC, DEFAULT_THRESHOLD_SEC).coerceIn(MIN_THRESHOLD_SEC, MAX_THRESHOLD_SEC)
        set(value) = prefs.edit { putInt(KEY_THRESHOLD_SEC, value.coerceIn(MIN_THRESHOLD_SEC, MAX_THRESHOLD_SEC)) }

    var vibrateEnabled: Boolean
        get() = prefs.getBoolean(KEY_VIBRATE, true)
        set(value) = prefs.edit { putBoolean(KEY_VIBRATE, value) }

    var vibrationIntensity: Int
        get() = prefs.getInt(KEY_INTENSITY, 2).coerceIn(1, 3)
        set(value) = prefs.edit { putInt(KEY_INTENSITY, value.coerceIn(1, 3)) }

    var soundEnabled: Boolean
        get() = prefs.getBoolean(KEY_SOUND, true)
        set(value) = prefs.edit { putBoolean(KEY_SOUND, value) }

    var soundVolume: Int
        get() = prefs.getInt(KEY_VOLUME, 70).coerceIn(0, 100)
        set(value) = prefs.edit { putInt(KEY_VOLUME, value.coerceIn(0, 100)) }

    var popupEnabled: Boolean
        get() = prefs.getBoolean(KEY_POPUP, true)
        set(value) = prefs.edit { putBoolean(KEY_POPUP, value) }

    /** 屏幕熄灭后是否继续计时并提醒。默认关闭，避免打扰已经准备休息的人。 */
    var remindWhenScreenOff: Boolean
        get() = prefs.getBoolean(KEY_SCREEN_OFF, false)
        set(value) = prefs.edit { putBoolean(KEY_SCREEN_OFF, value) }

    /** 是否只在指定时段内守夜。 */
    var scheduleEnabled: Boolean
        get() = prefs.getBoolean(KEY_SCHEDULE, false)
        set(value) = prefs.edit { putBoolean(KEY_SCHEDULE, value) }

    /** 0–1439 分钟，表示一天中的时刻。 */
    var guardStartMinute: Int
        get() = prefs.getInt(KEY_START_MINUTE, DEFAULT_START_MINUTE).coerceIn(0, 1439)
        set(value) = prefs.edit { putInt(KEY_START_MINUTE, value.coerceIn(0, 1439)) }

    var guardEndMinute: Int
        get() = prefs.getInt(KEY_END_MINUTE, DEFAULT_END_MINUTE).coerceIn(0, 1439)
        set(value) = prefs.edit { putInt(KEY_END_MINUTE, value.coerceIn(0, 1439)) }

    /** 熄屏监测快照：服务被系统重启后据此恢复计时，而不是重新开始。 */
    var screenOffLastInteractionWall: Long
        get() = prefs.getLong(KEY_SCREEN_OFF_LAST_WALL, 0L)
        set(value) = prefs.edit { putLong(KEY_SCREEN_OFF_LAST_WALL, value) }

    var screenOffSessionStartWall: Long
        get() = prefs.getLong(KEY_SCREEN_OFF_SESSION_WALL, 0L)
        set(value) = prefs.edit { putLong(KEY_SCREEN_OFF_SESSION_WALL, value) }

    fun clearScreenOffSnapshot() {
        prefs.edit {
            remove(KEY_SCREEN_OFF_LAST_WALL)
            remove(KEY_SCREEN_OFF_SESSION_WALL)
        }
    }

    var ringtoneUri: String?
        get() = prefs.getString(KEY_RINGTONE, null)
        set(value) = prefs.edit { putString(KEY_RINGTONE, value) }

    var themeId: String
        get() = prefs.getString(KEY_THEME, Palettes.lime.id) ?: Palettes.lime.id
        set(value) = prefs.edit { putString(KEY_THEME, value) }

    var welcomed: Boolean
        get() = prefs.getBoolean(KEY_WELCOMED, false)
        set(value) = prefs.edit { putBoolean(KEY_WELCOMED, value) }

    /** 通知栏快捷停止时使用，便于重启后恢复。 */
    var wasRunning: Boolean
        get() = prefs.getBoolean(KEY_WAS_RUNNING, false)
        set(value) = prefs.edit { putBoolean(KEY_WAS_RUNNING, value) }

    /**
     * 至少保留一种提醒方式。返回 true 表示被自动修正。
     */
    fun ensureAtLeastOneAlert(): Boolean {
        if (!vibrateEnabled && !soundEnabled && !popupEnabled) {
            popupEnabled = true
            return true
        }
        return false
    }

    fun defaultRingtoneUri(): String? {
        val uri = Settings.System.DEFAULT_ALARM_ALERT_URI
            ?: Settings.System.DEFAULT_NOTIFICATION_URI
        return uri?.toString()
    }

    fun effectiveRingtoneUri(): String? = ringtoneUri ?: defaultRingtoneUri()

    private fun migrateThresholdToSeconds() {
        if (prefs.contains(KEY_THRESHOLD_SEC)) return
        if (!prefs.contains(KEY_THRESHOLD_MIN_LEGACY)) return
        val oldMinutes = prefs.getInt(KEY_THRESHOLD_MIN_LEGACY, DEFAULT_THRESHOLD_SEC / 60)
        prefs.edit {
            putInt(KEY_THRESHOLD_SEC, (oldMinutes * 60).coerceIn(MIN_THRESHOLD_SEC, MAX_THRESHOLD_SEC))
            remove(KEY_THRESHOLD_MIN_LEGACY)
        }
    }

    companion object {
        private const val PREFS_NAME = "xingleme_settings"
        private const val KEY_INTERVAL = "check_interval_sec"
        private const val KEY_THRESHOLD_SEC = "threshold_sec"
        private const val KEY_THRESHOLD_MIN_LEGACY = "threshold_minutes"
        private const val KEY_VIBRATE = "vibrate_enabled"
        private const val KEY_INTENSITY = "vibration_intensity"
        private const val KEY_SOUND = "sound_enabled"
        private const val KEY_VOLUME = "sound_volume"
        private const val KEY_POPUP = "popup_enabled"
        private const val KEY_SCREEN_OFF = "remind_when_screen_off"
        private const val KEY_SCREEN_OFF_LAST_WALL = "screen_off_last_interaction_wall"
        private const val KEY_SCREEN_OFF_SESSION_WALL = "screen_off_session_start_wall"
        private const val KEY_SCHEDULE = "schedule_enabled"
        private const val KEY_START_MINUTE = "guard_start_minute"
        private const val KEY_END_MINUTE = "guard_end_minute"
        private const val KEY_RINGTONE = "ringtone_uri"
        private const val KEY_THEME = "theme_id"
        private const val KEY_WELCOMED = "welcomed"
        private const val KEY_WAS_RUNNING = "was_running"

        const val DEFAULT_INTERVAL_SEC = 30
        const val MIN_INTERVAL_SEC = 5
        const val MAX_INTERVAL_SEC = 300
        const val DEFAULT_THRESHOLD_SEC = 180
        const val MIN_THRESHOLD_SEC = 10
        const val MAX_THRESHOLD_SEC = 3600
        const val DEFAULT_START_MINUTE = 22 * 60
        const val DEFAULT_END_MINUTE = 6 * 60
    }
}
