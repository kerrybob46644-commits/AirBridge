package com.airbridge.app

import android.content.Context

/**
 * App 级主题选择（浅色 / 深色 / 跟随系统）。
 *
 * 与 [LocaleHelper] 类似，选择持久化在同一个 "airbridge_prefs" 文件里；
 * 不同的是主题切换不需要 recreate Activity，[CastController] 把当前
 * 取值放进 UiState，Compose 订阅后可即时重组生效。
 */
object ThemeHelper {

    const val KEY_THEME_MODE = "theme_mode"

    const val THEME_SYSTEM = "system"
    const val THEME_LIGHT = "light"
    const val THEME_DARK = "dark"

    fun getThemeMode(context: Context): String =
        context.getSharedPreferences(CastController.PREFS, Context.MODE_PRIVATE)
            .getString(KEY_THEME_MODE, THEME_SYSTEM) ?: THEME_SYSTEM

    fun setThemeMode(context: Context, mode: String) {
        val normalized = when (mode) {
            THEME_LIGHT, THEME_DARK -> mode
            else -> THEME_SYSTEM
        }
        context.getSharedPreferences(CastController.PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_THEME_MODE, normalized)
            .apply()
    }
}
