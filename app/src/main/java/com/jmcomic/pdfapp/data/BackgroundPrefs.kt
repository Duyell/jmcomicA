package com.jmcomic.pdfapp.data

import android.content.Context
import java.io.File

/** 背景配置：图片路径（null = 未设置）+ 透明度。 */
data class BackgroundConfig(
    val path: String? = null,
    val opacity: Float = 1f,
)

/**
 * 背景图片设置持久化（SharedPreferences）。
 */
object BackgroundPrefs {

    private const val PREFS = "background_prefs"
    private const val KEY_PATH = "bg_path"
    private const val KEY_OPACITY = "bg_opacity"

    fun load(context: Context): BackgroundConfig {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        var path = prefs.getString(KEY_PATH, null)
        // 自愈：路径存在但文件已丢失 → 视为未设置
        if (path != null && !File(path).exists()) {
            path = null
            prefs.edit().remove(KEY_PATH).apply()
        }
        val opacity = prefs.getFloat(KEY_OPACITY, 1f).coerceIn(0.15f, 1f)
        return BackgroundConfig(path, opacity)
    }

    fun save(context: Context, path: String?, opacity: Float) {
        val editor = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
        if (path != null) editor.putString(KEY_PATH, path) else editor.remove(KEY_PATH)
        editor.putFloat(KEY_OPACITY, opacity.coerceIn(0.15f, 1f))
        editor.apply()
    }
}
