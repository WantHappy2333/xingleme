package com.xingleme.wakeup

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import java.util.concurrent.TimeUnit

/**
 * 前台守夜服务：定时检查最后一次真实触屏时间，超时后触发确认提醒。
 * 支持“息屏后继续提醒”和“只在指定时段守夜”。
 */
class GuardianService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var settings: SettingsStore
    private var alertController: AlertController? = null
    private var receiverRegistered = false
    private var wakeLock: PowerManager.WakeLock? = null
    private var lastScheduleActive: Boolean? = null

    private val checkRunnable = Runnable { checkNow() }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    val wasOn = MonitorState.screenOn
                    MonitorState.screenOn = false
                    if (settings.remindWhenScreenOff) {
                        saveScreenOffSnapshot()
                        // 息屏也提醒：继续计时，并保持 CPU 可运行，保证到点能叫醒。
                        updateWakeLock()
                        alertController?.ensureNotificationForScreenOff()
                        scheduleCheck(250L)
                    } else {
                        if (alertController?.isShowing == true) {
                            alertController?.dismissForScreenOff()
                            alertController = null
                        }
                        releaseWakeLock()
                        scheduleCheck(intervalMs())
                    }
                    if (wasOn) updateForegroundNotification()
                }

                Intent.ACTION_SCREEN_ON, Intent.ACTION_USER_PRESENT -> {
                    val wasOff = !MonitorState.screenOn
                    MonitorState.screenOn = true
                    settings.clearScreenOffSnapshot()
                    releaseWakeLock()
                    // 只有“熄屏暂停”模式下才把亮屏当作新会话起点；
                    // 息屏提醒模式必须严格等待真实触屏，避免通知亮屏也被误判为操作。
                    if (wasOff && !MonitorState.alertActive && !settings.remindWhenScreenOff) {
                        MonitorState.markInteraction()
                    }
                    scheduleCheck(300L)
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        running = true
        current = this
        settings = SettingsStore(this)
        settings.wasRunning = true
        settings.ensureAtLeastOneAlert()

        NotificationHelper.ensureChannels(this)
        val pm = getSystemService(PowerManager::class.java)
        val screenInteractive = pm?.isInteractive ?: true
        MonitorState.screenOn = screenInteractive
        MonitorState.alertActive = false
        if (!screenInteractive && settings.remindWhenScreenOff) {
            val nowWall = System.currentTimeMillis()
            val lastWall = settings.screenOffLastInteractionWall
            if (lastWall in 1L until nowWall) {
                val idle = nowWall - lastWall
                val sessionStart = settings.screenOffSessionStartWall
                val sessionAge = if (sessionStart in 1L until nowWall) {
                    nowWall - sessionStart
                } else {
                    idle
                }
                MonitorState.restoreSnapshot(idle, sessionAge)
            } else {
                MonitorState.beginSession()
                saveScreenOffSnapshot()
            }
        } else {
            MonitorState.beginSession()
            settings.clearScreenOffSnapshot()
        }
        lastScheduleActive = ScheduleUtils.isWithinNow(settings)

        val notification = buildGuardianNotification()
        try {
            startForegroundCompat(notification)
        } catch (t: Throwable) {
            Log.e(TAG, "无法启动前台守夜服务", t)
            running = false
            current = null
            stopSelf()
            return
        }

        registerScreenReceiver()
        updateWakeLock()
        updateScheduleAlarm()
        scheduleCheck(700L)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopEverything()
                return START_NOT_STICKY
            }

            ACTION_ACK -> {
                alertController?.acknowledge()
                return START_STICKY
            }

            ACTION_REFRESH -> {
                if (!PermissionHelper.isAccessibilityEnabled(this)) {
                    handleAccessibilityUnavailable()
                    return START_NOT_STICKY
                }
                refreshAfterSettingsChanged()
                return START_STICKY
            }

            ACTION_SCHEDULE_TICK -> {
                updateWakeLock()
                updateScheduleAlarm()
                updateForegroundNotification()
                scheduleCheck(250L)
                return START_STICKY
            }

            ACTION_WAKE_CHECK -> {
                updateWakeLock()
                updateForegroundNotification()
                scheduleCheck(250L)
                return START_STICKY
            }
        }

        if (!PermissionHelper.isAccessibilityEnabled(this)) {
            Toast.makeText(this, R.string.toast_need_accessibility, Toast.LENGTH_LONG).show()
            stopEverything()
            return START_NOT_STICKY
        }

        updateForegroundNotification()
        scheduleCheck(500L)
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        running = false
        if (current === this) current = null
        handler.removeCallbacksAndMessages(null)
        unregisterScreenReceiver()
        cancelScheduleAlarm()
        cancelWakeCheckAlarm()
        releaseWakeLock()
        settings.clearScreenOffSnapshot()
        alertController?.shutdown()
        alertController = null
        MonitorState.alertActive = false
        NotificationHelper.cancelAlert(this)
        settings.wasRunning = false
        super.onDestroy()
    }

    private fun refreshAfterSettingsChanged() {
        if (!settings.remindWhenScreenOff) {
            settings.clearScreenOffSnapshot()
        } else if (!MonitorState.screenOn) {
            saveScreenOffSnapshot()
        }
        if (!settings.remindWhenScreenOff && !MonitorState.screenOn) {
            if (alertController?.isShowing == true) {
                alertController?.dismissForScreenOff()
                alertController = null
            }
        }
        updateWakeLock()
        updateScheduleAlarm()
        updateForegroundNotification()
        scheduleCheck(120L)
    }

    private fun checkNow() {
        if (!running) return
        if (!PermissionHelper.isAccessibilityEnabled(this)) {
            handleAccessibilityUnavailable()
            return
        }

        refreshScreenStateFromPowerManager()

        if (!refreshScheduleState()) {
            releaseWakeLock()
            updateForegroundNotification()
            val delay = minOf(
                intervalMs(),
                ScheduleUtils.millisUntilNextBoundary(
                    settings.guardStartMinute,
                    settings.guardEndMinute
                )
            ).coerceAtLeast(500L)
            scheduleCheck(delay)
            return
        }

        if (!MonitorState.screenOn && !settings.remindWhenScreenOff) {
            releaseWakeLock()
            scheduleCheck(intervalMs())
            return
        }

        updateWakeLock()

        if (alertController?.isShowing == true) {
            scheduleCheck(intervalMs())
            return
        }

        val now = MonitorState.now()
        val idle = MonitorState.idleMs(now)
        val thresholdMs = TimeUnit.SECONDS.toMillis(settings.thresholdSec.toLong())

        if (idle >= thresholdMs) {
            triggerAlert(idle)
            scheduleCheck(intervalMs())
        } else {
            val remaining = thresholdMs - idle
            // 平时按用户设定频率检查；临近阈值时精确补一次，保证提醒不迟到。
            val delay = minOf(intervalMs(), remaining).coerceAtLeast(250L)
            scheduleCheck(delay)
        }
    }

    /**
     * 返回当前是否处于可守夜时段。跨入时段时重新开始一次干净的计时。
     */
    private fun refreshScheduleState(): Boolean {
        if (!settings.scheduleEnabled) {
            if (lastScheduleActive != true) {
                lastScheduleActive = true
                MonitorState.beginSession()
                updateForegroundNotification()
            }
            return true
        }

        val active = ScheduleUtils.isWithinNow(settings)
        if (active) {
            if (lastScheduleActive != true) {
                lastScheduleActive = true
                MonitorState.beginSession()
                updateForegroundNotification()
            }
        } else {
            if (lastScheduleActive != false) {
                lastScheduleActive = false
                if (alertController?.isShowing == true) {
                    alertController?.shutdown()
                    alertController = null
                    MonitorState.alertActive = false
                }
                updateForegroundNotification()
            }
        }
        return active
    }

    private fun triggerAlert(idleMillis: Long) {
        if (alertController?.isShowing == true) return
        val controller = AlertController(
            context = this,
            settings = settings,
            onAcknowledged = { onAlertAcknowledged() },
            onStopRequested = { stopEverything() }
        )
        alertController = controller
        MonitorState.alertActive = true
        controller.show(idleMillis)
        updateForegroundNotification()
    }

    private fun onAlertAcknowledged() {
        alertController = null
        MonitorState.alertActive = false
        if (!MonitorState.screenOn && settings.remindWhenScreenOff) {
            saveScreenOffSnapshot()
        }
        updateForegroundNotification()
        scheduleCheck(400L)
    }

    private fun saveScreenOffSnapshot() {
        val nowWall = System.currentTimeMillis()
        settings.screenOffLastInteractionWall = nowWall - MonitorState.idleMs()
        settings.screenOffSessionStartWall = nowWall - MonitorState.sessionMs()
    }

    private fun scheduleCheck(delayMillis: Long) {
        handler.removeCallbacks(checkRunnable)
        handler.postDelayed(checkRunnable, delayMillis.coerceAtLeast(50L))
        // 用系统闹钟做兜底：即使屏幕状态广播漏掉或 Handler 被系统延迟，也能叫醒服务检查。
        if (!settings.remindWhenScreenOff) {
            cancelWakeCheckAlarm()
        } else {
            scheduleWakeCheckAlarmIfNeeded()
        }
    }

    private fun refreshScreenStateFromPowerManager() {
        val interactive = getSystemService(PowerManager::class.java)?.isInteractive
            ?: return
        if (interactive == MonitorState.screenOn) return
        if (interactive) {
            MonitorState.screenOn = true
            settings.clearScreenOffSnapshot()
            releaseWakeLock()
        } else {
            MonitorState.screenOn = false
            if (settings.remindWhenScreenOff) {
                saveScreenOffSnapshot()
                updateWakeLock()
                alertController?.ensureNotificationForScreenOff()
            }
        }
        updateForegroundNotification()
    }

    private fun scheduleWakeCheckAlarmIfNeeded() {
        val alarmManager = getSystemService(AlarmManager::class.java) ?: return
        val pendingIntent = wakeCheckPendingIntent()
        runCatching { alarmManager.cancel(pendingIntent) }
        val needed = running &&
            settings.remindWhenScreenOff &&
            ScheduleUtils.isWithinNow(settings) &&
            alertController?.isShowing != true
        if (!needed) return
        val remaining = TimeUnit.SECONDS.toMillis(settings.thresholdSec.toLong()) - MonitorState.idleMs()
        val triggerAt = System.currentTimeMillis() + remaining.coerceAtLeast(1500L) + 1000L
        runCatching {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
        }
    }

    private fun cancelWakeCheckAlarm() {
        val alarmManager = getSystemService(AlarmManager::class.java) ?: return
        runCatching { alarmManager.cancel(wakeCheckPendingIntent()) }
    }

    private fun wakeCheckPendingIntent(): PendingIntent = PendingIntent.getForegroundService(
        this,
        202,
        Intent(this, GuardianService::class.java).setAction(ACTION_WAKE_CHECK),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun intervalMs(): Long =
        TimeUnit.SECONDS.toMillis(settings.checkIntervalSec.toLong()).coerceAtLeast(1000L)

    private fun buildGuardianNotification(): Notification {
        val scheduleEnabled = settings.scheduleEnabled
        val activeNow = ScheduleUtils.isWithinNow(settings)
        val waiting = scheduleEnabled && !activeNow
        val scheduleText = if (scheduleEnabled) {
            ScheduleUtils.windowLabel(settings.guardStartMinute, settings.guardEndMinute)
        } else {
            null
        }
        return NotificationHelper.buildGuardian(
            context = this,
            intervalText = TimeUtils.spanShort(this, settings.checkIntervalSec),
            thresholdText = TimeUtils.spanShort(this, settings.thresholdSec),
            scheduleText = scheduleText,
            waitingForWindow = waiting,
            waitingStartText = if (waiting) ScheduleUtils.formatTime(settings.guardStartMinute) else null,
            screenOffMonitoring = !MonitorState.screenOn && settings.remindWhenScreenOff
        )
    }

    @SuppressLint("MissingPermission")
    private fun updateForegroundNotification() {
        if (!running) return
        runCatching {
            NotificationManagerCompat.from(this).notify(
                NotificationHelper.ID_GUARDIAN,
                buildGuardianNotification()
            )
        }
    }

    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceCompat.startForeground(
                this,
                NotificationHelper.ID_GUARDIAN,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            ServiceCompat.startForeground(
                this,
                NotificationHelper.ID_GUARDIAN,
                notification,
                0
            )
        }
    }

    private fun registerScreenReceiver() {
        if (receiverRegistered) return
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        ContextCompat.registerReceiver(
            this,
            screenReceiver,
            filter,
            ContextCompat.RECEIVER_EXPORTED
        )
        receiverRegistered = true
    }

    private fun unregisterScreenReceiver() {
        if (!receiverRegistered) return
        runCatching { unregisterReceiver(screenReceiver) }
        receiverRegistered = false
    }

    private fun updateWakeLock() {
        val need = running &&
            !MonitorState.screenOn &&
            settings.remindWhenScreenOff &&
            ScheduleUtils.isWithinNow(settings)
        if (!need) {
            releaseWakeLock()
            return
        }
        val lock = wakeLock ?: runCatching {
            getSystemService(PowerManager::class.java)
                ?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "xingleme:screen_off_guard")
                ?.apply { setReferenceCounted(false) }
        }.getOrNull()?.also { wakeLock = it } ?: return
        // 每次检查时续期，超时后系统也能自动释放，避免异常情况下长期耗电。
        if (lock.isHeld) runCatching { lock.release() }
        runCatching { lock.acquire(15 * 60 * 1000L) }
    }

    private fun releaseWakeLock() {
        wakeLock?.let { lock ->
            if (lock.isHeld) runCatching { lock.release() }
        }
        wakeLock = null
    }

    private fun updateScheduleAlarm() {
        if (!running) return
        val alarmManager = getSystemService(AlarmManager::class.java) ?: return
        val pendingIntent = scheduleAlarmPendingIntent()
        runCatching { alarmManager.cancel(pendingIntent) }
        if (!settings.scheduleEnabled || settings.guardStartMinute == settings.guardEndMinute) return
        val triggerAt = ScheduleUtils.nextBoundaryAtMillis(
            settings.guardStartMinute,
            settings.guardEndMinute,
            System.currentTimeMillis()
        ) + 500L
        runCatching {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
        }
    }

    private fun cancelScheduleAlarm() {
        val alarmManager = getSystemService(AlarmManager::class.java) ?: return
        runCatching { alarmManager.cancel(scheduleAlarmPendingIntent()) }
    }

    private fun scheduleAlarmPendingIntent(): PendingIntent = PendingIntent.getService(
        this,
        201,
        Intent(this, GuardianService::class.java).setAction(ACTION_SCHEDULE_TICK),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun handleAccessibilityUnavailable() {
        if (!running) return
        Toast.makeText(this, R.string.toast_accessibility_lost, Toast.LENGTH_LONG).show()
        stopEverything()
    }

    private fun stopEverything() {
        running = false
        settings.wasRunning = false
        handler.removeCallbacksAndMessages(null)
        cancelScheduleAlarm()
        cancelWakeCheckAlarm()
        releaseWakeLock()
        settings.clearScreenOffSnapshot()
        alertController?.shutdown()
        alertController = null
        MonitorState.alertActive = false
        NotificationHelper.cancelAlert(this)
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    companion object {
        private const val TAG = "XinglemeGuardian"
        const val ACTION_START = "com.xingleme.wakeup.action.START"
        const val ACTION_STOP = "com.xingleme.wakeup.action.STOP"
        const val ACTION_REFRESH = "com.xingleme.wakeup.action.REFRESH"
        const val ACTION_ACK = "com.xingleme.wakeup.action.ACK"
        const val ACTION_SCHEDULE_TICK = "com.xingleme.wakeup.action.SCHEDULE_TICK"
        const val ACTION_WAKE_CHECK = "com.xingleme.wakeup.action.WAKE_CHECK"

        @Volatile
        var running: Boolean = false
            private set

        private var current: GuardianService? = null

        fun start(context: Context) {
            val intent = Intent(context, GuardianService::class.java).setAction(ACTION_START)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, GuardianService::class.java))
        }

        fun refresh(context: Context) {
            if (!running) return
            val intent = Intent(context, GuardianService::class.java).setAction(ACTION_REFRESH)
            runCatching { context.startService(intent) }
        }

        fun acknowledge(context: Context) {
            if (!running) return
            val intent = Intent(context, GuardianService::class.java).setAction(ACTION_ACK)
            runCatching { context.startService(intent) }
        }

        fun notifyAccessibilityUnavailable() {
            current?.handleAccessibilityUnavailable()
        }
    }
}
