package com.xingleme.wakeup

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import com.google.android.material.button.MaterialButton

/**
 * 负责显示确认弹窗、开始/停止震动与铃声，并处理确认回调。
 */
class AlertController(
    private val context: Context,
    private val settings: SettingsStore,
    private val onAcknowledged: () -> Unit,
    private val onStopRequested: () -> Unit
) {
    private var feedback: FeedbackController? = null
    private var overlayView: View? = null
    private var idleTextForAlert: String = ""
    private val handler = Handler(Looper.getMainLooper())
    private val autoDismissRunnable = Runnable {
        if (isShowing) {
            finishInternal()
            MonitorState.markInteraction()
            onAcknowledged()
        }
    }

    var isShowing: Boolean = false
        private set

    fun show(idleMillis: Long) {
        if (isShowing) return
        isShowing = true
        MonitorState.alertActive = true

        feedback = FeedbackController(context, settings).also { it.start() }

        val idleText = TimeUtils.human(context, idleMillis)
        idleTextForAlert = idleText
        val notificationsAvailable = PermissionHelper.alertNotificationsAvailable(context)

        // 如果用户关闭了弹窗、通知又不可见，就强制用系统级悬浮层，避免只剩声音却没法确认。
        val forceOverlay = !notificationsAvailable
        if (settings.popupEnabled || forceOverlay) {
            val palette = Palettes.byId(settings.themeId)
            val themed = ContextThemeWrapper(context, palette.themeRes)
            val view = LayoutInflater.from(themed).inflate(R.layout.view_alert, null)
            bindOverlay(view, idleText)
            val host = WakeAccessibilityService.instance
            if (host != null && host.showOverlay(view)) {
                overlayView = view
                view.alpha = 0f
                view.animate().alpha(1f).setDuration(160L).start()
                view.findViewById<View>(R.id.alertCard)?.apply {
                    scaleX = 0.94f
                    scaleY = 0.94f
                    animate().scaleX(1f).scaleY(1f).setDuration(220L).start()
                }
                // 息屏时悬浮层可能被锁屏盖住，再发一条通知，保证有可确认的入口。
                if (!MonitorState.screenOn) {
                    if (notificationsAvailable) {
                        NotificationHelper.showAlert(context, idleText)
                    } else {
                        handler.postDelayed(autoDismissRunnable, 90_000L)
                    }
                }
                return
            }
        }

        if (notificationsAvailable) {
            NotificationHelper.showAlert(context, idleText)
        } else {
            // 极端情况下既没有悬浮层也没有可见通知，最多响 90 秒后自动收尾，避免无限打扰。
            handler.postDelayed(autoDismissRunnable, 90_000L)
        }
    }

    private fun bindOverlay(view: View, idleText: String) {
        val palette = Palettes.byId(settings.themeId)
        val start = androidx.core.content.ContextCompat.getColor(context, palette.gradientStartRes)
        val end = androidx.core.content.ContextCompat.getColor(context, palette.gradientEndRes)
        view.findViewById<FrameLayout>(R.id.alertIconBadge)?.background =
            GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(start, end)).apply {
                shape = GradientDrawable.OVAL
            }

        view.findViewById<TextView>(R.id.alertIdle)?.text =
            context.getString(R.string.alert_idle, idleText)

        view.findViewById<MaterialButton>(R.id.btnAck)?.setOnClickListener {
            acknowledge()
        }
        view.findViewById<MaterialButton>(R.id.btnStopAlert)?.setOnClickListener {
            stopGuardian()
        }
    }

    /** 息屏时悬浮层可能被锁屏盖住，补一条可确认的通知。 */
    fun ensureNotificationForScreenOff() {
        if (!isShowing) return
        if (PermissionHelper.alertNotificationsAvailable(context)) {
            NotificationHelper.showAlert(context, idleTextForAlert)
        }
    }

    fun acknowledge() {
        if (!isShowing) return
        finishInternal()
        MonitorState.markInteraction()
        onAcknowledged()
    }

    fun stopGuardian() {
        if (!isShowing) return
        finishInternal()
        onStopRequested()
    }

    /** 屏幕熄灭：停止打扰，等下次亮屏重新计时。 */
    fun dismissForScreenOff() {
        if (!isShowing) return
        finishInternal()
        MonitorState.markInteraction()
        onAcknowledged()
    }

    /** 服务被系统或用户结束时释放所有资源，不触发确认回调。 */
    fun shutdown() {
        if (!isShowing && feedback == null && overlayView == null) return
        finishInternal()
    }

    private fun finishInternal() {
        isShowing = false
        feedback?.stop()
        feedback = null
        overlayView?.let { view ->
            WakeAccessibilityService.instance?.hideOverlay()
            // 如果无障碍服务已断开，视图可能仍挂在旧窗口上，这里只做空引用。
            view.alpha = 1f
        }
        overlayView = null
        handler.removeCallbacks(autoDismissRunnable)
        NotificationHelper.cancelAlert(context)
        MonitorState.alertActive = false
    }
}
