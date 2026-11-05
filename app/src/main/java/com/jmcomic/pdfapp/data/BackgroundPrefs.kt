package com.jmcomic.pdfapp.data

import android.content.Context
import java.io.File

/**
 * 背景配置：
 *  - path：背景图片路径（null = 未设置）
 *  - opacity：背景图片自身透明度（0.15~1）
 *  - componentAlpha：页面组件（卡片/底栏等）透明度（0.3~1），
 *    调低后背景图能透过组件显示
 */
data class BackgroundConfig(
    val path: String? = null,
    val opacity: Float = 1f,
    val componentAlpha: Float = 0.95f,
)

/**
 * 背景图片设置持久化（SharedPreferences）。
 */
object BackgroundPrefs {

    private const val PREFS = "background_prefs"
    private const val KEY_PATH = "bg_path"
    private const val KEY_OPACITY = "bg_opacity"
    private const val KEY_COMPONENT_ALPHA = "bg_component_alpha"

    fun load(context: Context): BackgroundConfig {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        var path = prefs.getString(KEY_PATH, null)
        // 自愈：路径存在但文件已丢失 → 视为未设置
        if (path != null && !File(path).exists()) {
            path = null
            prefs.edit().remove(KEY_PATH).apply()
        }
        val opacity = prefs.getFloat(KEY_OPACITY, 1f).coerceIn(0.15f, 1f)
        val componentAlpha = prefs.getFloat(KEY_COMPONENT_ALPHA, 0.95f).coerceIn(0.3f, 1f)
        return BackgroundConfig(path, opacity, componentAlpha)
    }

    fun save(context: Context, path: String?, opacity: Float, componentAlpha: Float) {
        val editor = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
        if (path != null) editor.putString(KEY_PATH, path) else editor.remove(KEY_PATH)
        editor.putFloat(KEY_OPACITY, opacity.coerceIn(0.15f, 1f))
        editor.putFloat(KEY_COMPONENT_ALPHA, componentAlpha.coerceIn(0.3f, 1f))
        editor.apply()
    }
}
