package com.jmcomic.pdfapp.data

import android.content.Context
import com.jmcomic.pdfapp.ui.theme.ThemeMode

/**
 * 主题模式持久化（SharedPreferences）。
 */
object ThemePrefs {

    private const val PREFS = "theme_prefs"
    private const val KEY_MODE = "theme_mode"

    fun load(context: Context): ThemeMode {
        val name = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_MODE, null) ?: return ThemeMode.SYSTEM
        return runCatching { ThemeMode.valueOf(name) }.getOrDefault(ThemeMode.SYSTEM)
    }

    fun save(context: Context, mode: ThemeMode) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_MODE, mode.name).apply()
    }

    /** 循环切换：跟随系统 → 浅色 → 深色 → 跟随系统。 */
    fun next(mode: ThemeMode): ThemeMode = when (mode) {
        ThemeMode.SYSTEM -> ThemeMode.LIGHT
        ThemeMode.LIGHT -> ThemeMode.DARK
        ThemeMode.DARK -> ThemeMode.SYSTEM
    }
}
