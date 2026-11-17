package com.xingleme.wakeup

import android.os.SystemClock

/**
 * 无障碍服务与守夜服务共享的轻量运行状态。
 * 全部使用 elapsedRealtime，避免系统时间变化造成误判。
 */
object MonitorState {
    @Volatile
    var lastInteractionElapsed: Long = SystemClock.elapsedRealtime()
        private set

    @Volatile
    var sessionStartedElapsed: Long = 0L
        private set

    @Volatile
    var alertActive: Boolean = false

    @Volatile
    var screenOn: Boolean = true

    fun now(): Long = SystemClock.elapsedRealtime()

    fun markInteraction() {
        lastInteractionElapsed = now()
    }

    fun beginSession() {
        val now = now()
        sessionStartedElapsed = now
        lastInteractionElapsed = now
    }

    /** 服务被系统重建后，从熄屏快照恢复“已经多久没触屏”。 */
    fun restoreSnapshot(idleMs: Long, sessionMs: Long) {
        val current = now()
        val safeIdle = idleMs.coerceAtLeast(0L)
        lastInteractionElapsed = current - safeIdle
        sessionStartedElapsed = current - sessionMs.coerceAtLeast(0L)
    }

    fun idleMs(now: Long = now()): Long = (now - lastInteractionElapsed).coerceAtLeast(0L)

    fun sessionMs(now: Long = now()): Long {
        if (sessionStartedElapsed <= 0L) return 0L
        return (now - sessionStartedElapsed).coerceAtLeast(0L)
    }
}
