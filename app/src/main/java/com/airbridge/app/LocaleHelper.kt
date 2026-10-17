package com.airbridge.app

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import java.util.Locale

/**
 * App-level language selection.
 *
 * The choice persists in the shared "airbridge_prefs" file (same file
 * CastController reads). Each Activity/Service applies it by overriding
 * attachBaseContext() with [applyLocale]; the currently visible UI then
 * switches language after the caller recreates the activity.
 */
object LocaleHelper {

    private const val KEY_LOCALE = "locale"

    const val LANG_ENGLISH = "en"
    const val LANG_CHINESE = "zh"

    fun getLocale(context: Context): Locale = when (getLangCode(context)) {
        LANG_CHINESE -> Locale.SIMPLIFIED_CHINESE
        else -> Locale.ENGLISH
    }

    fun getLangCode(context: Context): String =
        context.getSharedPreferences(CastController.PREFS, Context.MODE_PRIVATE)
            .getString(KEY_LOCALE, LANG_ENGLISH) ?: LANG_ENGLISH

    fun setLanguage(context: Context, langCode: String) {
        context.getSharedPreferences(CastController.PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LOCALE, langCode)
            .apply()
    }

    /** Wrap [base] with the stored language so its resources resolve in that locale. */
    fun applyLocale(base: Context): Context {
        val config = Configuration(base.resources.configuration)
        val locale = getLocale(base)
        config.setLocale(locale)
        if (Build.VERSION.SDK_INT >= 33) {
            config.setLocales(LocaleList(locale))
        }
        return base.createConfigurationContext(config)
    }
}