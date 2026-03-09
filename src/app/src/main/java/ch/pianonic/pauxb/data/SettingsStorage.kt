package ch.pianonic.pauxb.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class ThemeMode { SYSTEM, LIGHT, DARK }

class SettingsStorage(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("pauxb_settings", Context.MODE_PRIVATE)

    private val _themeMode = MutableStateFlow(loadThemeMode())
    val themeMode: StateFlow<ThemeMode> = _themeMode

    private val _dynamicColor = MutableStateFlow(loadDynamicColor())
    val dynamicColor: StateFlow<Boolean> = _dynamicColor

    private val _defaultVncPort = MutableStateFlow(loadDefaultVncPort())
    val defaultVncPort: StateFlow<Int> = _defaultVncPort

    private val _defaultResolution = MutableStateFlow(loadDefaultResolution())
    val defaultResolution: StateFlow<String> = _defaultResolution

    private val _keepScreenOn = MutableStateFlow(loadKeepScreenOn())
    val keepScreenOn: StateFlow<Boolean> = _keepScreenOn

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit().putString("theme_mode", mode.name).apply()
        _themeMode.value = mode
    }

    fun setDynamicColor(enabled: Boolean) {
        prefs.edit().putBoolean("dynamic_color", enabled).apply()
        _dynamicColor.value = enabled
    }

    fun setDefaultVncPort(port: Int) {
        prefs.edit().putInt("default_vnc_port", port).apply()
        _defaultVncPort.value = port
    }

    fun setDefaultResolution(resolution: String) {
        prefs.edit().putString("default_resolution", resolution).apply()
        _defaultResolution.value = resolution
    }

    fun setKeepScreenOn(enabled: Boolean) {
        prefs.edit().putBoolean("keep_screen_on", enabled).apply()
        _keepScreenOn.value = enabled
    }

    private fun loadThemeMode(): ThemeMode {
        val name = prefs.getString("theme_mode", ThemeMode.SYSTEM.name)
        return try { ThemeMode.valueOf(name!!) } catch (_: Exception) { ThemeMode.SYSTEM }
    }

    private fun loadDynamicColor(): Boolean = prefs.getBoolean("dynamic_color", true)
    private fun loadDefaultVncPort(): Int = prefs.getInt("default_vnc_port", 5910)
    private fun loadDefaultResolution(): String = prefs.getString("default_resolution", "1280x720") ?: "1280x720"
    private fun loadKeepScreenOn(): Boolean = prefs.getBoolean("keep_screen_on", true)
}
