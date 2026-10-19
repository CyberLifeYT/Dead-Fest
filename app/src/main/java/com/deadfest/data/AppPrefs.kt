package com.deadfest.data

import android.app.ActivityManager
import android.content.Context
import android.provider.Settings
import androidx.core.content.edit
import com.deadfest.BuildConfig
import com.deadfest.domain.UpdateVariant
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

    /**
     * Which published build to update to.
     *
     * With nothing stored the choice follows the build actually running: a survivor on the full
     * build is offered the full build, one on R8 is offered R8. Nobody is moved onto a different
     * kind of build by an update they never asked for — and choosing explicitly overrides this for
     * good, so the answer is remembered from then on.
     */
    private val _updateVariant = MutableStateFlow(
        UpdateVariant.fromStored(prefs.getString(KEY_UPDATE_VARIANT, null))
            ?: if (BuildConfig.DEBUG) UpdateVariant.Standard else UpdateVariant.R8
    )
    val updateVariant: StateFlow<UpdateVariant> = _updateVariant.asStateFlow()

    fun setWheelAlerts(on: Boolean) = save(KEY_WHEEL, on, _wheelAlerts)
    fun setMarketAlerts(on: Boolean) = save(KEY_MARKET, on, _marketAlerts)
    fun setWastelandAlerts(on: Boolean) = save(KEY_WASTELAND, on, _wastelandAlerts)
    fun setPerformanceMode(on: Boolean) = save(KEY_PERFORMANCE, on, _performanceMode)

    fun setUpdateVariant(variant: UpdateVariant) {
        _updateVariant.value = variant
        prefs.edit { putString(KEY_UPDATE_VARIANT, variant.name) }
    }

    /**
     * The active app theme (Default / Halloween / Christmas / ...).
     *
     * Stored as the palette's display name. Absent on every install that predates v2.0.8, which is
     * why the default is resolved in [com.deadfest.ui.theme.paletteFor] rather than assumed.
     */
    private val _theme = MutableStateFlow(
        prefs.getString(KEY_THEME, null) ?: com.deadfest.ui.theme.DEFAULT_THEME_NAME
    )
    val theme: StateFlow<String> = _theme.asStateFlow()

    fun setTheme(name: String) {
        _theme.value = name
        prefs.edit { putString(KEY_THEME, name) }
    }

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
        /** Added in 2.0.7.2. Absent on every earlier install, which is why it must stay optional. */
        const val KEY_UPDATE_VARIANT = "pref_update_variant"
        /** Added in 2.0.8. Absent on earlier installs, so the theme falls back to the default. */
        const val KEY_THEME = "pref_app_theme"

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
