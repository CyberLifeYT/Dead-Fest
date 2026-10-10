package com.deadfest.data

import android.app.ActivityManager
import android.content.Context
import android.provider.Settings
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Local device preferences, exposed as flows so the UI updates the moment a toggle flips. */
class AppPrefs(context: Context) {
    private val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    private val _wheelAlerts = MutableStateFlow(prefs.getBoolean(KEY_WHEEL, true))
    val wheelAlerts: StateFlow<Boolean> = _wheelAlerts.asStateFlow()

    private val _marketAlerts = MutableStateFlow(prefs.getBoolean(KEY_MARKET, true))
    val marketAlerts: StateFlow<Boolean> = _marketAlerts.asStateFlow()

    private val _wastelandAlerts = MutableStateFlow(prefs.getBoolean(KEY_WASTELAND, true))
    val wastelandAlerts: StateFlow<Boolean> = _wastelandAlerts.asStateFlow()

    // Performance mode defaults to ON for low-RAM phones or when system animations are off.
    private val _performanceMode = MutableStateFlow(
        if (prefs.contains(KEY_PERFORMANCE)) prefs.getBoolean(KEY_PERFORMANCE, false)
        else isLowEndDevice(context)
    )
    val performanceMode: StateFlow<Boolean> = _performanceMode.asStateFlow()

    /** The first-launch "keep Dead-Fest awake" prompt has been shown (asked once only). */
    var batteryPromptShown: Boolean
        get() = prefs.getBoolean(KEY_BATTERY_PROMPT, false)
        set(value) = prefs.edit { putBoolean(KEY_BATTERY_PROMPT, value) }

    fun setWheelAlerts(on: Boolean) = save(KEY_WHEEL, on, _wheelAlerts)
    fun setMarketAlerts(on: Boolean) = save(KEY_MARKET, on, _marketAlerts)
    fun setWastelandAlerts(on: Boolean) = save(KEY_WASTELAND, on, _wastelandAlerts)
    fun setPerformanceMode(on: Boolean) = save(KEY_PERFORMANCE, on, _performanceMode)

    private fun save(key: String, value: Boolean, flow: MutableStateFlow<Boolean>) {
        flow.value = value
        prefs.edit { putBoolean(key, value) }
    }

    companion object {
        // Same file/keys the app already used, so existing choices carry over.
        const val FILE = "deadfest_optimizations"
        const val KEY_WHEEL = "pref_wheel_spin"
        const val KEY_MARKET = "pref_black_market"
        const val KEY_WASTELAND = "pref_wasteland_alerts"
        const val KEY_PERFORMANCE = "pref_performance_mode"
        const val KEY_BATTERY_PROMPT = "battery_prompt_shown"

        fun isLowEndDevice(context: Context): Boolean {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            val lowRam = am?.isLowRamDevice == true || (am?.memoryClass ?: 256) <= 128
            val animationsOff = try {
                Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
            } catch (_: Exception) {
                false
            }
            return lowRam || animationsOff
        }
    }
}
