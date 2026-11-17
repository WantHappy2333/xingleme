package com.xingleme.wakeup

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.core.widget.NestedScrollView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.slider.Slider
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat

class MainActivity : AppCompatActivity() {

    private lateinit var settings: SettingsStore
    private val uiHandler = Handler(Looper.getMainLooper())

    private lateinit var scroll: NestedScrollView
    private lateinit var heroBackground: FrameLayout
    private lateinit var logoBadge: FrameLayout
    private lateinit var heroStatus: TextView
    private lateinit var heroSession: TextView
    private lateinit var heroLabel: TextView
    private lateinit var heroTime: TextView
    private lateinit var heroSub: TextView
    private lateinit var btnGuardian: MaterialButton
    private lateinit var permissionBanner: MaterialCardView
    private lateinit var sectionThemeTitle: TextView

    private lateinit var chipGroupInterval: ChipGroup
    private lateinit var chipGroupThreshold: ChipGroup
    private lateinit var chipIntervalCustom: Chip
    private lateinit var chipThresholdCustom: Chip
    private lateinit var valueInterval: TextView
    private lateinit var valueThreshold: TextView

    private lateinit var switchVibrate: SwitchMaterial
    private lateinit var switchSound: SwitchMaterial
    private lateinit var switchPopup: SwitchMaterial
    private lateinit var switchScreenOff: SwitchMaterial
    private lateinit var rowVibrate: View
    private lateinit var rowSound: View
    private lateinit var rowPopup: View
    private lateinit var rowScreenOff: View
    private lateinit var rowRingtone: View
    private lateinit var vibrationExtra: View
    private lateinit var soundExtra: View
    private lateinit var sliderVibration: Slider
    private lateinit var sliderVolume: Slider
    private lateinit var valueVibration: TextView
    private lateinit var valueVolume: TextView
    private lateinit var ringtoneName: TextView
    private lateinit var btnTestRingtone: MaterialButton

    private lateinit var statusAccessibility: TextView
    private lateinit var statusNotification: TextView
    private lateinit var statusBattery: TextView
    private lateinit var themePickerContainer: LinearLayout
    private lateinit var switchSchedule: SwitchMaterial
    private lateinit var rowSchedule: View
    private lateinit var scheduleEditor: View
    private lateinit var scheduleNote: View
    private lateinit var btnStartTime: View
    private lateinit var btnEndTime: View
    private lateinit var startTimeValue: TextView
    private lateinit var endTimeValue: TextView
    private lateinit var scheduleStatus: TextView

    private var suppressSwitches = false
    private var suppressChips = false
    private var pendingStartAfterNotification = false
    private var pendingStartAfterAccessibility = false
    private var awaitingAccessibilityReturn = false
    private var previewPlayer: MediaPlayer? = null
    private var firstResume = true
    private var lastAccessibilityEnabled = false

    private val intervalPresetIds = linkedMapOf(
        15 to R.id.chipInterval15,
        30 to R.id.chipInterval30,
        60 to R.id.chipInterval60,
        120 to R.id.chipInterval120
    )
    private val thresholdPresetIds = linkedMapOf(
        60 to R.id.chipThreshold1,
        120 to R.id.chipThreshold2,
        180 to R.id.chipThreshold3,
        300 to R.id.chipThreshold5,
        600 to R.id.chipThreshold10
    )

    private val ticker = object : Runnable {
        override fun run() {
            renderState()
            uiHandler.postDelayed(this, 500L)
        }
    }

    private val stopPreviewRunnable = Runnable { stopPreview() }

    private val ringtoneLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            @Suppress("DEPRECATION")
            val uri = result.data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            if (uri != null) {
                settings.ringtoneUri = uri.toString()
                refreshRingtoneName()
                Toast.makeText(this, R.string.toast_ringtone_saved, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        refreshPermissionRows()
        if (granted) {
            Toast.makeText(this, R.string.toast_notification_enabled, Toast.LENGTH_SHORT).show()
            if (pendingStartAfterNotification) {
                pendingStartAfterNotification = false
                startGuardianNow()
            }
        } else {
            pendingStartAfterNotification = false
            Toast.makeText(this, R.string.toast_notification_denied, Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        settings = SettingsStore(this)
        settings.ensureAtLeastOneAlert()
        setTheme(Palettes.byId(settings.themeId).themeRes)
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_main)

        bindViews()
        configureSystemBars()
        applyInsets()
        applyPalette()
        setupThemePicker()
        setupCadence()
        setupSchedule()
        setupAlertControls()
        setupPermissionRows()
        setupActions()

        refreshFromSettings()
        renderState()

        if (!settings.welcomed && !PermissionHelper.isAccessibilityEnabled(this)) {
            settings.welcomed = true
            uiHandler.postDelayed({
                if (!isFinishing && !isDestroyed) showWelcomeDialog()
            }, 650L)
        }
    }

    override fun onResume() {
        super.onResume()
        refreshFromSettings()
        refreshPermissionRows()
        renderState()
        uiHandler.removeCallbacks(ticker)
        uiHandler.post(ticker)

        val enabled = PermissionHelper.isAccessibilityEnabled(this)
        if (!firstResume && enabled && !lastAccessibilityEnabled) {
            Toast.makeText(this, R.string.toast_permission_granted, Toast.LENGTH_SHORT).show()
        }
        lastAccessibilityEnabled = enabled
        firstResume = false

        if (pendingStartAfterAccessibility) {
            pendingStartAfterAccessibility = false
            if (enabled) tryStartGuardian()
        }

        if (pendingStartAfterNotification && PermissionHelper.notificationsAllowed(this)) {
            pendingStartAfterNotification = false
            startGuardianNow()
        }

        if (awaitingAccessibilityReturn) {
            awaitingAccessibilityReturn = false
            uiHandler.postDelayed({
                if (isFinishing || isDestroyed) return@postDelayed
                refreshPermissionRows()
                if (!PermissionHelper.isAccessibilityEnabled(this)) {
                    showAccessibilityStillOffDialog()
                }
            }, 1200L)
        }
    }

    override fun onPause() {
        uiHandler.removeCallbacks(ticker)
        stopPreview()
        super.onPause()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        refreshFromSettings()
        refreshPermissionRows()
        renderState()
    }

    override fun onDestroy() {
        uiHandler.removeCallbacksAndMessages(null)
        stopPreview()
        super.onDestroy()
    }

    private fun bindViews() {
        scroll = findViewById(R.id.scroll)
        heroBackground = findViewById(R.id.heroBackground)
        logoBadge = findViewById(R.id.logoBadge)
        heroStatus = findViewById(R.id.heroStatus)
        heroSession = findViewById(R.id.heroSession)
        heroLabel = findViewById(R.id.heroLabel)
        heroTime = findViewById(R.id.heroTime)
        heroSub = findViewById(R.id.heroSub)
        btnGuardian = findViewById(R.id.btnGuardian)
        permissionBanner = findViewById(R.id.permissionBanner)
        sectionThemeTitle = findViewById(R.id.sectionThemeTitle)

        chipGroupInterval = findViewById(R.id.chipGroupInterval)
        chipGroupThreshold = findViewById(R.id.chipGroupThreshold)
        chipIntervalCustom = findViewById(R.id.chipIntervalCustom)
        chipThresholdCustom = findViewById(R.id.chipThresholdCustom)
        valueInterval = findViewById(R.id.valueInterval)
        valueThreshold = findViewById(R.id.valueThreshold)

        switchVibrate = findViewById(R.id.switchVibrate)
        switchSound = findViewById(R.id.switchSound)
        switchPopup = findViewById(R.id.switchPopup)
        switchScreenOff = findViewById(R.id.switchScreenOff)
        rowVibrate = findViewById(R.id.rowVibrate)
        rowSound = findViewById(R.id.rowSound)
        rowPopup = findViewById(R.id.rowPopup)
        rowScreenOff = findViewById(R.id.rowScreenOff)
        rowRingtone = findViewById(R.id.rowRingtone)
        vibrationExtra = findViewById(R.id.vibrationExtra)
        soundExtra = findViewById(R.id.soundExtra)
        sliderVibration = findViewById(R.id.sliderVibration)
        sliderVolume = findViewById(R.id.sliderVolume)
        valueVibration = findViewById(R.id.valueVibration)
        valueVolume = findViewById(R.id.valueVolume)
        ringtoneName = findViewById(R.id.ringtoneName)
        btnTestRingtone = findViewById(R.id.btnTestRingtone)

        statusAccessibility = findViewById(R.id.statusAccessibility)
        statusNotification = findViewById(R.id.statusNotification)
        statusBattery = findViewById(R.id.statusBattery)
        themePickerContainer = findViewById(R.id.themePickerContainer)

        switchSchedule = findViewById(R.id.switchSchedule)
        rowSchedule = findViewById(R.id.rowSchedule)
        scheduleEditor = findViewById(R.id.scheduleEditor)
        scheduleNote = findViewById(R.id.scheduleNote)
        btnStartTime = findViewById(R.id.btnStartTime)
        btnEndTime = findViewById(R.id.btnEndTime)
        startTimeValue = findViewById(R.id.startTimeValue)
        endTimeValue = findViewById(R.id.endTimeValue)
        scheduleStatus = findViewById(R.id.scheduleStatus)
    }

    private fun configureSystemBars() {
        val palette = Palettes.byId(settings.themeId)
        val page = ContextCompat.getColor(this, palette.pageRes)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = if (Build.VERSION.SDK_INT >= 27) page else Color.rgb(27, 31, 23)
        window.setBackgroundDrawable(ColorDrawable(page))

        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.isAppearanceLightStatusBars = true
        controller.isAppearanceLightNavigationBars = Build.VERSION.SDK_INT >= 27

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isStatusBarContrastEnforced = false
            window.isNavigationBarContrastEnforced = false
            window.decorView.setForceDarkAllowed(false)
        }
    }

    private fun applyInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(scroll) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(
                left = bars.left,
                top = bars.top,
                right = bars.right,
                bottom = bars.bottom
            )
            insets
        }
        ViewCompat.requestApplyInsets(scroll)
    }

    private fun applyPalette() {
        val palette = Palettes.byId(settings.themeId)
        val primary = ContextCompat.getColor(this, palette.primaryRes)
        val start = ContextCompat.getColor(this, palette.gradientStartRes)
        val end = ContextCompat.getColor(this, palette.gradientEndRes)

        heroBackground.background = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(start, end)
        ).apply {
            cornerRadius = dp(30f).toFloat()
        }
        heroBackground.clipToOutline = true
        logoBadge.background = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(start, end)
        ).apply {
            cornerRadius = dp(14f).toFloat()
        }
        btnGuardian.backgroundTintList = ColorStateList.valueOf(Color.WHITE)
        btnGuardian.setTextColor(primary)
        btnGuardian.iconTint = ColorStateList.valueOf(primary)
        valueInterval.setTextColor(primary)
        valueThreshold.setTextColor(primary)
        valueVibration.setTextColor(primary)
        valueVolume.setTextColor(primary)
        btnTestRingtone.setTextColor(primary)
        btnTestRingtone.iconTint = ColorStateList.valueOf(primary)
        btnTestRingtone.strokeColor = ColorStateList.valueOf(primary)
    }

    private fun setupThemePicker() {
        themePickerContainer.removeAllViews()
        val selected = Palettes.byId(settings.themeId)
        Palettes.all.forEach { palette ->
            val primary = ContextCompat.getColor(this, palette.primaryRes)
            val start = ContextCompat.getColor(this, palette.gradientStartRes)
            val end = ContextCompat.getColor(this, palette.gradientEndRes)
            val isSelected = palette.id == selected.id

            val item = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                isClickable = true
                isFocusable = true
                contentDescription = getString(palette.nameRes) + "，提神配色"
                val outValue = TypedValue()
                theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, outValue, true)
                if (outValue.resourceId != 0) setBackgroundResource(outValue.resourceId)
            }

            val outer = FrameLayout(this).apply {
                layoutParams = LinearLayout.LayoutParams(dp(48f), dp(48f))
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.TRANSPARENT)
                    if (isSelected) setStroke(dp(2f), primary)
                }
            }

            val inner = View(this).apply {
                layoutParams = FrameLayout.LayoutParams(dp(36f), dp(36f), Gravity.CENTER)
                background = GradientDrawable(
                    GradientDrawable.Orientation.TL_BR,
                    intArrayOf(start, end)
                ).apply { shape = GradientDrawable.OVAL }
            }
            outer.addView(inner)

            if (isSelected) {
                val check = ImageView(this).apply {
                    layoutParams = FrameLayout.LayoutParams(dp(18f), dp(18f), Gravity.CENTER)
                    setImageResource(R.drawable.ic_check)
                    imageTintList = ColorStateList.valueOf(Color.WHITE)
                    contentDescription = null
                }
                outer.addView(check)
            }

            val label = TextView(this).apply {
                text = getString(palette.nameRes)
                textSize = 10.5f
                gravity = Gravity.CENTER
                maxLines = 1
                setTextColor(if (isSelected) primary else ContextCompat.getColor(this@MainActivity, R.color.ink_muted))
                setPadding(0, dp(6f), 0, 0)
                typeface = android.graphics.Typeface.create(
                    "sans-serif",
                    if (isSelected) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL
                )
            }

            item.addView(outer)
            item.addView(label)
            item.setOnClickListener { selectTheme(palette) }
            themePickerContainer.addView(item)
        }
    }

    private fun selectTheme(palette: ThemePalette) {
        if (settings.themeId == palette.id) return
        settings.themeId = palette.id
        recreate()
    }

    private fun setupCadence() {
        chipGroupInterval.setOnCheckedStateChangeListener { _, checkedIds ->
            if (suppressChips) return@setOnCheckedStateChangeListener
            val id = checkedIds.firstOrNull() ?: return@setOnCheckedStateChangeListener
            if (id == R.id.chipIntervalCustom) {
                showCustomIntervalDialog()
                return@setOnCheckedStateChangeListener
            }
            intervalPresetIds.entries.firstOrNull { it.value == id }?.key?.let { seconds ->
                if (settings.checkIntervalSec != seconds) {
                    settings.checkIntervalSec = seconds
                    refreshCadenceChips()
                    notifyServiceSettingsChanged()
                }
            }
        }

        chipGroupThreshold.setOnCheckedStateChangeListener { _, checkedIds ->
            if (suppressChips) return@setOnCheckedStateChangeListener
            val id = checkedIds.firstOrNull() ?: return@setOnCheckedStateChangeListener
            if (id == R.id.chipThresholdCustom) {
                showCustomThresholdDialog()
                return@setOnCheckedStateChangeListener
            }
            thresholdPresetIds.entries.firstOrNull { it.value == id }?.key?.let { seconds ->
                if (settings.thresholdSec != seconds) {
                    settings.thresholdSec = seconds
                    refreshCadenceChips()
                    notifyServiceSettingsChanged()
                }
            }
        }

        // 已经处于自定义值时，再点一次可以直接修改。
        chipIntervalCustom.setOnClickListener {
            if (!suppressChips && intervalPresetIds[settings.checkIntervalSec] == null) {
                showCustomIntervalDialog()
            }
        }
        chipThresholdCustom.setOnClickListener {
            if (!suppressChips && thresholdPresetIds[settings.thresholdSec] == null) {
                showCustomThresholdDialog()
            }
        }
    }

    private fun refreshCadenceChips() {
        suppressChips = true
        chipGroupInterval.clearCheck()
        chipGroupThreshold.clearCheck()

        val intervalPreset = intervalPresetIds[settings.checkIntervalSec]
        if (intervalPreset != null) {
            findViewById<Chip>(intervalPreset).isChecked = true
            chipIntervalCustom.text = getString(R.string.chip_custom)
        } else {
            chipIntervalCustom.text = TimeUtils.spanShort(this, settings.checkIntervalSec)
            chipIntervalCustom.isChecked = true
        }

        val thresholdPreset = thresholdPresetIds[settings.thresholdSec]
        if (thresholdPreset != null) {
            findViewById<Chip>(thresholdPreset).isChecked = true
            chipThresholdCustom.text = getString(R.string.chip_custom)
        } else {
            chipThresholdCustom.text = TimeUtils.spanShort(this, settings.thresholdSec)
            chipThresholdCustom.isChecked = true
        }

        valueInterval.text = getString(
            R.string.interval_value,
            TimeUtils.spanShort(this, settings.checkIntervalSec)
        )
        valueThreshold.text = TimeUtils.spanShort(this, settings.thresholdSec)
        suppressChips = false
    }

    private fun showCustomIntervalDialog() {
        showCustomNumberDialog(
            titleRes = R.string.dialog_custom_interval_title,
            hintRes = R.string.dialog_custom_interval_hint,
            initial = settings.checkIntervalSec,
            min = SettingsStore.MIN_INTERVAL_SEC,
            max = SettingsStore.MAX_INTERVAL_SEC,
            errorRes = R.string.dialog_custom_interval_error,
            onSave = { value ->
                settings.checkIntervalSec = value
                refreshCadenceChips()
                notifyServiceSettingsChanged()
                Toast.makeText(this, R.string.toast_custom_saved, Toast.LENGTH_SHORT).show()
            }
        )
    }

    private fun showCustomThresholdDialog() {
        showCustomNumberDialog(
            titleRes = R.string.dialog_custom_threshold_title,
            hintRes = R.string.dialog_custom_threshold_hint,
            initial = settings.thresholdSec,
            min = SettingsStore.MIN_THRESHOLD_SEC,
            max = SettingsStore.MAX_THRESHOLD_SEC,
            errorRes = R.string.dialog_custom_threshold_error,
            onSave = { value ->
                settings.thresholdSec = value
                refreshCadenceChips()
                notifyServiceSettingsChanged()
                Toast.makeText(this, R.string.toast_custom_saved, Toast.LENGTH_SHORT).show()
            }
        )
    }

    private fun showCustomNumberDialog(
        titleRes: Int,
        hintRes: Int,
        initial: Int,
        min: Int,
        max: Int,
        errorRes: Int,
        onSave: (Int) -> Unit
    ) {
        val content = layoutInflater.inflate(R.layout.dialog_number_input, null)
        val inputLayout = content.findViewById<TextInputLayout>(R.id.inputLayout)
        val input = content.findViewById<TextInputEditText>(R.id.inputNumber)
        val hint = content.findViewById<TextView>(R.id.inputHint)
        inputLayout.hint = getString(hintRes)
        hint.text = getString(errorRes)
        input.setText(initial.toString())
        input.selectAll()

        var saved = false
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(titleRes)
            .setView(content)
            .setPositiveButton(R.string.dialog_save, null)
            .setNegativeButton(R.string.dialog_cancel, null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).setOnClickListener {
                val value = input.text?.toString()?.trim()?.toIntOrNull()
                if (value == null || value !in min..max) {
                    inputLayout.error = getString(errorRes)
                    return@setOnClickListener
                }
                inputLayout.error = null
                saved = true
                onSave(value)
                dialog.dismiss()
            }
            input.requestFocus()
            dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
        }
        dialog.setOnDismissListener {
            if (!saved) refreshCadenceChips()
        }
        dialog.show()
    }

    private fun setupAlertControls() {
        suppressSwitches = true
        switchVibrate.isChecked = settings.vibrateEnabled
        switchSound.isChecked = settings.soundEnabled
        switchPopup.isChecked = settings.popupEnabled
        switchScreenOff.isChecked = settings.remindWhenScreenOff
        sliderVibration.value = settings.vibrationIntensity.toFloat()
        sliderVolume.value = settings.soundVolume.toFloat()
        suppressSwitches = false

        updateVibrationValue(settings.vibrationIntensity)
        updateVolumeValue(settings.soundVolume)
        refreshRingtoneName()
        updateAlertSectionState()

        switchVibrate.setOnCheckedChangeListener { _, checked ->
            if (suppressSwitches) return@setOnCheckedChangeListener
            if (!checked && !switchSound.isChecked && !switchPopup.isChecked) {
                revertSwitch(switchVibrate)
                return@setOnCheckedChangeListener
            }
            settings.vibrateEnabled = checked
            updateAlertSectionState()
        }
        switchSound.setOnCheckedChangeListener { _, checked ->
            if (suppressSwitches) return@setOnCheckedChangeListener
            if (!checked && !switchVibrate.isChecked && !switchPopup.isChecked) {
                revertSwitch(switchSound)
                return@setOnCheckedChangeListener
            }
            settings.soundEnabled = checked
            updateAlertSectionState()
        }
        switchPopup.setOnCheckedChangeListener { _, checked ->
            if (suppressSwitches) return@setOnCheckedChangeListener
            if (!checked && !switchVibrate.isChecked && !switchSound.isChecked) {
                revertSwitch(switchPopup)
                return@setOnCheckedChangeListener
            }
            settings.popupEnabled = checked
            updateAlertSectionState()
        }

        switchScreenOff.setOnCheckedChangeListener { _, checked ->
            if (suppressSwitches) return@setOnCheckedChangeListener
            settings.remindWhenScreenOff = checked
            if (checked) {
                Toast.makeText(this, R.string.toast_screen_off_on, Toast.LENGTH_SHORT).show()
                if (!PermissionHelper.isIgnoringBatteryOptimizations(this)) {
                    uiHandler.postDelayed({
                        if (!isFinishing && !isDestroyed) showScreenOffReliabilityDialog()
                    }, 500L)
                }
            }
            GuardianService.refresh(this)
            renderState()
        }

        rowVibrate.setOnClickListener { switchVibrate.toggle() }
        rowSound.setOnClickListener { switchSound.toggle() }
        rowPopup.setOnClickListener { switchPopup.toggle() }
        rowScreenOff.setOnClickListener { switchScreenOff.toggle() }

        sliderVibration.addOnChangeListener { _, value, fromUser ->
            val intensity = value.toInt().coerceIn(1, 3)
            updateVibrationValue(intensity)
            if (fromUser) settings.vibrationIntensity = intensity
        }
        sliderVolume.addOnChangeListener { _, value, fromUser ->
            val volume = value.toInt().coerceIn(0, 100)
            updateVolumeValue(volume)
            if (fromUser) settings.soundVolume = volume
        }
    }

    private fun revertSwitch(switch: SwitchMaterial) {
        suppressSwitches = true
        switch.isChecked = true
        suppressSwitches = false
        Toast.makeText(this, R.string.toast_keep_one, Toast.LENGTH_SHORT).show()
    }

    private fun updateVibrationValue(intensity: Int) {
        valueVibration.text = when (intensity) {
            1 -> getString(R.string.vibration_light)
            2 -> getString(R.string.vibration_medium)
            else -> getString(R.string.vibration_strong)
        }
    }

    private fun updateVolumeValue(volume: Int) {
        valueVolume.text = getString(R.string.volume_percent, volume)
    }

    private fun updateAlertSectionState() {
        val vibrationOn = switchVibrate.isChecked
        val soundOn = switchSound.isChecked

        vibrationExtra.alpha = if (vibrationOn) 1f else 0.42f
        sliderVibration.isEnabled = vibrationOn

        soundExtra.alpha = if (soundOn) 1f else 0.42f
        sliderVolume.isEnabled = soundOn
        rowRingtone.alpha = if (soundOn) 1f else 0.42f
        rowRingtone.isEnabled = soundOn
        btnTestRingtone.alpha = if (soundOn) 1f else 0.42f
        btnTestRingtone.isEnabled = soundOn
        if (!soundOn) stopPreview()
    }

    private fun setupSchedule() {
        suppressSwitches = true
        switchSchedule.isChecked = settings.scheduleEnabled
        suppressSwitches = false
        refreshScheduleControls()

        switchSchedule.setOnCheckedChangeListener { _, checked ->
            if (suppressSwitches) return@setOnCheckedChangeListener
            settings.scheduleEnabled = checked
            refreshScheduleControls()
            GuardianService.refresh(this)
            renderState()
        }
        rowSchedule.setOnClickListener { switchSchedule.toggle() }
        btnStartTime.setOnClickListener { showTimePicker(isStart = true) }
        btnEndTime.setOnClickListener { showTimePicker(isStart = false) }
    }

    private fun showTimePicker(isStart: Boolean) {
        val current = if (isStart) settings.guardStartMinute else settings.guardEndMinute
        val picker = MaterialTimePicker.Builder()
            .setTimeFormat(TimeFormat.CLOCK_24H)
            .setHour(current / 60)
            .setMinute(current % 60)
            .setTitleText(if (isStart) R.string.dialog_pick_start else R.string.dialog_pick_end)
            .build()
        picker.addOnPositiveButtonClickListener {
            val selected = picker.hour * 60 + picker.minute
            if (isStart) settings.guardStartMinute = selected else settings.guardEndMinute = selected
            if (settings.guardStartMinute == settings.guardEndMinute) {
                Toast.makeText(this, R.string.toast_schedule_all_day, Toast.LENGTH_SHORT).show()
            }
            refreshScheduleControls()
            GuardianService.refresh(this)
            renderState()
        }
        picker.show(supportFragmentManager, if (isStart) "guard_start_time" else "guard_end_time")
    }

    private fun refreshScheduleControls() {
        suppressSwitches = true
        switchSchedule.isChecked = settings.scheduleEnabled
        suppressSwitches = false
        startTimeValue.text = ScheduleUtils.formatTime(settings.guardStartMinute)
        endTimeValue.text = ScheduleUtils.formatTime(settings.guardEndMinute)
        scheduleEditor.isVisible = settings.scheduleEnabled
        scheduleNote.isVisible = settings.scheduleEnabled
        updateScheduleStatus()
    }

    private fun updateScheduleStatus() {
        if (!settings.scheduleEnabled || !::scheduleStatus.isInitialized) return
        val active = ScheduleUtils.isWithinNow(settings)
        scheduleStatus.text = when {
            !GuardianService.running && active -> getString(R.string.schedule_status_ready_active)
            !GuardianService.running -> getString(
                R.string.schedule_status_ready_waiting,
                ScheduleUtils.formatTime(settings.guardStartMinute)
            )
            active -> getString(R.string.schedule_status_active)
            else -> getString(
                R.string.schedule_status_waiting,
                ScheduleUtils.formatTime(settings.guardStartMinute)
            )
        }
    }

    private fun setupPermissionRows() {
        findViewById<View>(R.id.rowAccessibility).setOnClickListener { showAccessibilityDialog() }
        permissionBanner.setOnClickListener { showAccessibilityDialog() }
        findViewById<View>(R.id.btnBannerPermission).setOnClickListener { showAccessibilityDialog() }
        findViewById<View>(R.id.rowNotification).setOnClickListener { onNotificationRowClick() }
        findViewById<View>(R.id.rowBattery).setOnClickListener { onBatteryRowClick() }
        findViewById<View>(R.id.rowPermissionHelp).setOnClickListener { showPermissionHelpDialog() }
    }

    private fun setupActions() {
        btnGuardian.setOnClickListener {
            when {
                !GuardianService.running -> tryStartGuardian()
                MonitorState.alertActive -> {
                    GuardianService.acknowledge(this)
                    Toast.makeText(this, R.string.toast_acknowledged, Toast.LENGTH_SHORT).show()
                }
                else -> stopGuardian()
            }
        }
        findViewById<View>(R.id.btnTheme).setOnClickListener { scrollToTheme() }
        rowRingtone.setOnClickListener {
            if (!switchSound.isChecked) return@setOnClickListener
            launchRingtonePicker()
        }
        btnTestRingtone.setOnClickListener { togglePreview() }
    }

    private fun refreshFromSettings() {
        refreshCadenceChips()
        suppressSwitches = true
        switchVibrate.isChecked = settings.vibrateEnabled
        switchSound.isChecked = settings.soundEnabled
        switchPopup.isChecked = settings.popupEnabled
        switchScreenOff.isChecked = settings.remindWhenScreenOff
        sliderVibration.value = settings.vibrationIntensity.toFloat()
        sliderVolume.value = settings.soundVolume.toFloat()
        suppressSwitches = false
        updateVibrationValue(settings.vibrationIntensity)
        updateVolumeValue(settings.soundVolume)
        refreshRingtoneName()
        updateAlertSectionState()
        refreshScheduleControls()
    }

    private fun refreshPermissionRows() {
        val accessibilityOn = PermissionHelper.isAccessibilityEnabled(this)
        permissionBanner.isVisible = !accessibilityOn
        setStatusText(
            statusAccessibility,
            accessibilityOn,
            getString(R.string.permission_on),
            getString(R.string.permission_off)
        )

        val notificationOn = PermissionHelper.notificationsAllowed(this)
        setStatusText(
            statusNotification,
            notificationOn,
            getString(R.string.permission_on),
            getString(R.string.permission_off)
        )

        val batteryOn = PermissionHelper.isIgnoringBatteryOptimizations(this)
        statusBattery.text = if (batteryOn) getString(R.string.permission_allowed) else getString(R.string.permission_advice)
        statusBattery.setTextColor(
            ContextCompat.getColor(
                this,
                if (batteryOn) R.color.success else R.color.ink_muted
            )
        )
    }

    private fun setStatusText(view: TextView, on: Boolean, onText: String, offText: String) {
        view.text = if (on) onText else offText
        view.setTextColor(
            ContextCompat.getColor(
                this,
                if (on) R.color.success else R.color.warn_text
            )
        )
    }

    private fun renderState() {
        val running = GuardianService.running
        val alerting = MonitorState.alertActive
        val thresholdMs = settings.thresholdSec * 1000L
        val idle = MonitorState.idleMs()
        val scheduleWaiting = settings.scheduleEnabled && !ScheduleUtils.isWithinNow(settings)

        if (!running) {
            heroStatus.text = getString(R.string.hero_status_idle)
            heroLabel.text = getString(R.string.hero_label_idle)
            heroTime.text = getString(R.string.hero_time_idle)
            heroSub.text = getString(R.string.hero_idle_sub)
            heroSession.isVisible = false
            btnGuardian.text = getString(R.string.start_guardian)
            btnGuardian.icon = AppCompatResources.getDrawable(this, R.drawable.ic_play)
            updateScheduleStatus()
            return
        }

        if (scheduleWaiting && !alerting) {
            heroStatus.text = getString(R.string.hero_status_waiting)
            heroLabel.text = getString(R.string.hero_label_waiting)
            heroTime.text = ScheduleUtils.formatTime(settings.guardStartMinute)
            heroSub.text = getString(
                R.string.hero_sub_waiting,
                ScheduleUtils.formatTime(settings.guardStartMinute)
            )
            heroSession.isVisible = true
            heroSession.text = getString(
                R.string.hero_session_waiting,
                ScheduleUtils.windowLabel(settings.guardStartMinute, settings.guardEndMinute)
            )
            btnGuardian.text = getString(R.string.stop_guardian)
            btnGuardian.icon = AppCompatResources.getDrawable(this, R.drawable.ic_stop)
            updateScheduleStatus()
            return
        }

        heroSession.isVisible = true
        heroSession.text = getString(R.string.hero_session, TimeUtils.clock(MonitorState.sessionMs()))

        if (alerting) {
            heroStatus.text = getString(R.string.hero_status_alert)
            heroLabel.text = getString(R.string.hero_alert_label)
            heroTime.text = getString(R.string.hero_time_alert)
            heroSub.text = getString(R.string.hero_alert_sub)
        } else {
            heroStatus.text = getString(R.string.hero_status_running)
            heroLabel.text = getString(R.string.hero_label_countdown)
            heroTime.text = TimeUtils.clock((thresholdMs - idle).coerceAtLeast(0L))
            heroSub.text = getString(R.string.hero_running_sub, TimeUtils.ago(this, idle))
        }

        if (alerting) {
            btnGuardian.text = getString(R.string.alert_ack)
            btnGuardian.icon = AppCompatResources.getDrawable(this, R.drawable.ic_check)
        } else {
            btnGuardian.text = getString(R.string.stop_guardian)
            btnGuardian.icon = AppCompatResources.getDrawable(this, R.drawable.ic_stop)
        }
        updateScheduleStatus()
    }

    private fun tryStartGuardian() {
        if (!PermissionHelper.isAccessibilityEnabled(this)) {
            pendingStartAfterAccessibility = true
            showAccessibilityDialog()
            return
        }
        if (!PermissionHelper.notificationsAllowed(this)) {
            showNotificationRationale(startAfterGrant = true)
            return
        }
        startGuardianNow()
    }

    private fun startGuardianNow() {
        if (GuardianService.running) {
            renderState()
            return
        }
        try {
            GuardianService.start(this)
            val waitingForWindow = settings.scheduleEnabled && !ScheduleUtils.isWithinNow(settings)
            if (waitingForWindow) {
                Toast.makeText(
                    this,
                    getString(R.string.toast_started_waiting, ScheduleUtils.formatTime(settings.guardStartMinute)),
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                Toast.makeText(this, R.string.toast_started, Toast.LENGTH_SHORT).show()
            }
            uiHandler.postDelayed({ renderState() }, 450L)
        } catch (t: Throwable) {
            Toast.makeText(this, R.string.unknown_error, Toast.LENGTH_LONG).show()
        }
    }

    private fun stopGuardian() {
        GuardianService.stop(this)
        Toast.makeText(this, R.string.toast_stopped, Toast.LENGTH_SHORT).show()
        uiHandler.postDelayed({ renderState() }, 350L)
    }

    private fun notifyServiceSettingsChanged() {
        GuardianService.refresh(this)
    }

    private fun showAccessibilityDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.dialog_accessibility_title)
            .setMessage(R.string.dialog_accessibility_message)
            .setPositiveButton(R.string.dialog_accessibility_confirm) { _, _ ->
                awaitingAccessibilityReturn = true
                PermissionHelper.openAccessibilitySettings(this)
            }
            .setNeutralButton(R.string.dialog_accessibility_neutral) { _, _ ->
                PermissionHelper.openAppDetailsSettings(this)
            }
            .setNegativeButton(R.string.dialog_later) { _, _ ->
                pendingStartAfterAccessibility = false
            }
            .setOnCancelListener {
                pendingStartAfterAccessibility = false
                awaitingAccessibilityReturn = false
            }
            .show()
    }

    private fun showScreenOffReliabilityDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.dialog_screen_off_reliability_title)
            .setMessage(R.string.dialog_screen_off_reliability_message)
            .setPositiveButton(R.string.dialog_battery_confirm) { _, _ ->
                PermissionHelper.requestIgnoreBatteryOptimizations(this)
            }
            .setNegativeButton(R.string.dialog_later, null)
            .show()
    }

    private fun showAccessibilityStillOffDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.accessibility_still_off_title)
            .setMessage(R.string.accessibility_still_off_message)
            .setPositiveButton(R.string.open_app_info) { _, _ ->
                PermissionHelper.openAppDetailsSettings(this)
            }
            .setNeutralButton(R.string.dialog_accessibility_confirm) { _, _ ->
                awaitingAccessibilityReturn = true
                PermissionHelper.openAccessibilitySettings(this)
            }
            .setNegativeButton(R.string.dialog_permission_confirm, null)
            .show()
    }

    private fun showWelcomeDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.dialog_welcome_title)
            .setMessage(R.string.dialog_welcome_message)
            .setPositiveButton(R.string.dialog_welcome_confirm) { _, _ -> showAccessibilityDialog() }
            .setNegativeButton(R.string.dialog_welcome_later, null)
            .show()
    }

    private fun showPermissionHelpDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.dialog_permission_title)
            .setMessage(R.string.dialog_permission_message)
            .setPositiveButton(R.string.dialog_permission_confirm, null)
            .show()
    }

    private fun onNotificationRowClick() {
        if (PermissionHelper.notificationsAllowed(this)) {
            PermissionHelper.openNotificationSettings(this)
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotificationRuntimePermission()) {
            showNotificationRationale(startAfterGrant = false)
        } else {
            showNotificationSettingsDialog()
        }
    }

    private fun showNotificationRationale(startAfterGrant: Boolean) {
        pendingStartAfterNotification = false
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.dialog_notification_title)
            .setMessage(R.string.dialog_notification_message)
            .setPositiveButton(R.string.dialog_notification_confirm) { _, _ ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotificationRuntimePermission()) {
                    pendingStartAfterNotification = startAfterGrant
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    pendingStartAfterNotification = startAfterGrant
                    PermissionHelper.openNotificationSettings(this)
                }
            }
            .setNegativeButton(if (startAfterGrant) R.string.dialog_start_anyway else R.string.dialog_cancel) { _, _ ->
                if (startAfterGrant) startGuardianNow()
            }
            .setOnCancelListener { pendingStartAfterNotification = false }
            .show()
    }

    private fun showNotificationSettingsDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.dialog_notification_title)
            .setMessage(R.string.dialog_notification_message)
            .setPositiveButton(R.string.dialog_permission_settings) { _, _ ->
                PermissionHelper.openNotificationSettings(this)
            }
            .setNegativeButton(R.string.dialog_cancel, null)
            .show()
    }

    private fun hasNotificationRuntimePermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

    private fun onBatteryRowClick() {
        if (PermissionHelper.isIgnoringBatteryOptimizations(this)) {
            Toast.makeText(this, R.string.toast_battery_ok, Toast.LENGTH_SHORT).show()
            return
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.dialog_battery_title)
            .setMessage(R.string.dialog_battery_message)
            .setPositiveButton(R.string.dialog_battery_confirm) { _, _ ->
                PermissionHelper.requestIgnoreBatteryOptimizations(this)
            }
            .setNegativeButton(R.string.dialog_later, null)
            .show()
    }

    private fun launchRingtonePicker() {
        val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
            putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
            putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, getString(R.string.ringtone_picker_title))
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
            putExtra(
                RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI,
                Settings.System.DEFAULT_ALARM_ALERT_URI
            )
            val current = settings.effectiveRingtoneUri()?.let { Uri.parse(it) }
            if (current != null) {
                putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, current)
            }
        }
        runCatching { ringtoneLauncher.launch(intent) }.onFailure {
            Toast.makeText(this, R.string.unknown_error, Toast.LENGTH_SHORT).show()
        }
    }

    private fun refreshRingtoneName() {
        val stored = settings.ringtoneUri
        if (stored == null) {
            ringtoneName.text = getString(R.string.ringtone_default)
            return
        }
        val title = runCatching {
            RingtoneManager.getRingtone(this, Uri.parse(stored))?.getTitle(this)
        }.getOrNull()
        ringtoneName.text = title?.takeIf { it.isNotBlank() } ?: getString(R.string.ringtone_unknown)
    }

    private fun togglePreview() {
        if (!switchSound.isChecked) return
        if (previewPlayer != null) {
            stopPreview()
            return
        }
        val player = SoundPlayer.play(
            context = this,
            uriString = settings.effectiveRingtoneUri(),
            volumePercent = settings.soundVolume,
            loop = false
        )
        if (player == null) {
            Toast.makeText(this, R.string.toast_test_failed, Toast.LENGTH_SHORT).show()
            return
        }
        previewPlayer = player
        btnTestRingtone.setText(R.string.stop_test)
        uiHandler.removeCallbacks(stopPreviewRunnable)
        uiHandler.postDelayed(stopPreviewRunnable, 8000L)
    }

    private fun stopPreview() {
        uiHandler.removeCallbacks(stopPreviewRunnable)
        SoundPlayer.stop(previewPlayer)
        previewPlayer = null
        if (::btnTestRingtone.isInitialized) {
            btnTestRingtone.setText(R.string.test_ringtone)
        }
    }

    private fun scrollToTheme() {
        val rect = Rect()
        sectionThemeTitle.getDrawingRect(rect)
        scroll.offsetDescendantRectToMyCoords(sectionThemeTitle, rect)
        scroll.smoothScrollTo(0, (rect.top - dp(18f)).coerceAtLeast(0))
    }

    private fun dp(value: Float): Int =
        (value * resources.displayMetrics.density + 0.5f).toInt()
}
