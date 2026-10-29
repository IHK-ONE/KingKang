package com.kingkang.app.data

import android.content.Context

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("kingkang_settings", Context.MODE_PRIVATE)

    var rootMode: Boolean
        get() = prefs.getBoolean(KEY_ROOT_MODE, false)
        set(value) = prefs.edit().putBoolean(KEY_ROOT_MODE, value).apply()

    var hasCompletedOnboarding: Boolean
        get() = prefs.getBoolean(KEY_ONBOARDING, false)
        set(value) = prefs.edit().putBoolean(KEY_ONBOARDING, value).apply()

    /** "system" | "light" | "dark"; the same value is written to dsh's ui-theme setting. */
    var themeMode: String
        get() = prefs.getString(KEY_THEME, THEME_SYSTEM) ?: THEME_SYSTEM
        set(value) = prefs.edit().putString(KEY_THEME, value).apply()

    /**
     * npm registry for installing/updating dsh and for plugin installs. Blank means the
     * app's default (Aliyun), which is also what a fresh dsh profile gets.
     */
    var npmRegistry: String
        get() = prefs.getString(KEY_NPM_REGISTRY, "") ?: ""
        set(value) = prefs.edit().putString(KEY_NPM_REGISTRY, value.trim()).apply()

    private companion object {
        const val KEY_ROOT_MODE = "root_mode"
        const val KEY_ONBOARDING = "onboarding"
        const val KEY_THEME = "theme_mode"
        const val KEY_NPM_REGISTRY = "npm_registry"
        const val THEME_SYSTEM = "system"
    }
}
