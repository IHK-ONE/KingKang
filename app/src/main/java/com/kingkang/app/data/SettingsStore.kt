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
     * True while the app's theme choice has not reached dsh. The next connection writes
     * it instead of adopting the Harness theme, so a local change always wins over an
     * older Harness value.
     */
    var themeSyncPending: Boolean
        get() = prefs.getBoolean(KEY_THEME_PENDING, false)
        set(value) = prefs.edit().putBoolean(KEY_THEME_PENDING, value).apply()

    /**
     * npm registry for installing/updating dsh and for plugin installs. Blank means the
     * app's default (Aliyun), which is also what a fresh dsh profile gets.
     */
    var npmRegistry: String
        get() = prefs.getString(KEY_NPM_REGISTRY, "") ?: ""
        set(value) = prefs.edit().putString(KEY_NPM_REGISTRY, value.trim()).apply()

    /**
     * Include prerelease (rc/alpha) dsh versions when checking for updates. Off by default:
     * the npm `latest` tag stays a stable channel for most users.
     */
    var prereleaseUpdates: Boolean
        get() = prefs.getBoolean(KEY_PRERELEASE_UPDATES, false)
        set(value) = prefs.edit().putBoolean(KEY_PRERELEASE_UPDATES, value).apply()

    /** Workspace headers the user folded in the drawer; the list outlives the drawer. */
    var collapsedWorkspaceIds: Set<String>
        get() = prefs.getStringSet(KEY_COLLAPSED_WORKSPACES, null)?.toSet().orEmpty()
        set(value) = prefs.edit().putStringSet(KEY_COLLAPSED_WORKSPACES, value).apply()

    /**
     * True once the user picked the new-chat permission default in KingKang. Until then the
     * app keeps steering a fresh profile to full access, even if dsh materialised its own
     * default while the profile was created.
     */
    var permissionDefaultChosen: Boolean
        get() = prefs.getBoolean(KEY_PERMISSION_DEFAULT_CHOSEN, false)
        set(value) = prefs.edit().putBoolean(KEY_PERMISSION_DEFAULT_CHOSEN, value).apply()

    /** Directories picked recently in a picker, most recent first. */
    var recentDirectories: List<String>
        get() = prefs.getString(KEY_RECENT_DIRECTORIES, null)
            ?.split('\n')
            ?.filter { it.isNotBlank() }
            .orEmpty()
        set(value) = prefs.edit()
            .putString(KEY_RECENT_DIRECTORIES, value.distinct().take(8).joinToString("\n"))
            .apply()

    private companion object {
        const val KEY_ROOT_MODE = "root_mode"
        const val KEY_ONBOARDING = "onboarding"
        const val KEY_THEME = "theme_mode"
        const val KEY_THEME_PENDING = "theme_sync_pending"
        const val KEY_PERMISSION_DEFAULT_CHOSEN = "permission_default_chosen"
        const val KEY_NPM_REGISTRY = "npm_registry"
        const val KEY_PRERELEASE_UPDATES = "prerelease_updates"
        const val KEY_COLLAPSED_WORKSPACES = "collapsed_workspaces"
        const val KEY_RECENT_DIRECTORIES = "recent_directories"
        const val THEME_SYSTEM = "system"
    }
}
