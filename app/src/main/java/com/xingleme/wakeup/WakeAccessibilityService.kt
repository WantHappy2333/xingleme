package com.xingleme.wakeup

import android.accessibilityservice.AccessibilityService
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent

/**
 * 只做一件事：接收“手指触摸屏幕”的开始事件，刷新“最后一次操作时间”。
 * 通知、弹窗、页面切换等不会触发操作判定；不读取窗口内容，不截图，不存储事件。
 */
class WakeAccessibilityService : AccessibilityService() {

    private val windowManager by lazy {
        getSystemService(WINDOW_SERVICE) as WindowManager
    }

    private var overlayView: View? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        // 服务刚连接时给一个干净的起点。
        MonitorState.markInteraction()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // 只统计触屏产生的交互。通知到达、窗口切换、弹窗出现不算操作。
        // TYPE_TOUCH_INTERACTION_START 是原始触屏开始；其余几个是部分 ROM 下更可靠的触屏结果事件。
        val type = event?.eventType ?: return
        if (isPhysicalInteraction(type)) {
            runCatching { MonitorState.markInteraction() }
        }
    }

    private fun isPhysicalInteraction(type: Int): Boolean = when (type) {
        AccessibilityEvent.TYPE_TOUCH_INTERACTION_START,
        AccessibilityEvent.TYPE_TOUCH_INTERACTION_END,
        AccessibilityEvent.TYPE_GESTURE_DETECTION_START,
        AccessibilityEvent.TYPE_TOUCH_EXPLORATION_GESTURE_START,
        AccessibilityEvent.TYPE_VIEW_CLICKED,
        AccessibilityEvent.TYPE_VIEW_LONG_CLICKED,
        AccessibilityEvent.TYPE_VIEW_SCROLLED,
        AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> true
        else -> false
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        hideOverlay()
        if (instance === this) {
            instance = null
            GuardianService.notifyAccessibilityUnavailable()
        }
        super.onDestroy()
    }

    /**
     * 通过无障碍悬浮层盖在当前应用上，不需要额外的悬浮窗权限。
     * 返回 false 时由守夜服务改用高优先级通知。
     */
    fun showOverlay(view: View): Boolean {
        hideOverlay()
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
            title = "醒了么 · 提神提醒"
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN
        }
        view.isFocusableInTouchMode = true
        view.setOnKeyListener { _, keyCode, event ->
            keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP
        }
        return try {
            windowManager.addView(view, params)
            overlayView = view
            view.requestFocus()
            true
        } catch (t: Throwable) {
            runCatching { windowManager.removeViewImmediate(view) }
            overlayView = null
            false
        }
    }

    fun hideOverlay() {
        val view = overlayView ?: return
        overlayView = null
        runCatching { windowManager.removeViewImmediate(view) }
    }

    fun hasOverlay(): Boolean = overlayView != null


    companion object {
        @Volatile
        var instance: WakeAccessibilityService? = null
            private set
    }
}
