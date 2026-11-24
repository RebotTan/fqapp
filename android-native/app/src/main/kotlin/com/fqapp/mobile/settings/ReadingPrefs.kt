package com.fqapp.mobile.settings

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * 阅读与外观设置。只有三个值，用 SharedPreferences 足够；Compose 侧靠状态读，
 * 改完立刻生效、下次启动仍在。
 */
object ReadingPrefs {

    private const val NAME = "reading"
    private const val KEY_FONT = "font_scale"
    private const val KEY_DARK = "dark_override"

    private var prefs: android.content.SharedPreferences? = null

    var fontScale by mutableFloatStateOf(1f)
        private set

    /** null = 跟随系统，true/false = 强制。 */
    var darkOverride by mutableStateOf<Boolean?>(null)
        private set

    fun init(context: Context) {
        val store = context.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)
        prefs = store
        fontScale = store.getFloat(KEY_FONT, 1f).coerceIn(MIN_SCALE, MAX_SCALE)
        darkOverride = if (store.contains(KEY_DARK)) store.getBoolean(KEY_DARK, false) else null
    }

    fun updateFontScale(value: Float) {
        fontScale = value.coerceIn(MIN_SCALE, MAX_SCALE)
        prefs?.edit()?.putFloat(KEY_FONT, fontScale)?.apply()
    }

    fun updateDarkOverride(value: Boolean?) {
        darkOverride = value
        val editor = prefs?.edit() ?: return
        if (value == null) editor.remove(KEY_DARK) else editor.putBoolean(KEY_DARK, value)
        editor.apply()
    }

    const val MIN_SCALE = 0.85f
    const val MAX_SCALE = 1.4f
}