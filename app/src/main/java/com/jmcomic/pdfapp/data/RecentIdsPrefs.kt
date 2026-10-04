package com.jmcomic.pdfapp.data

import android.content.Context
import org.json.JSONArray

/**
 * 最近输入的漫画 ID 历史（SharedPreferences，JSON 数组存储）。
 * 去重置顶，上限 8 条。
 */
object RecentIdsPrefs {

    private const val PREFS = "recent_ids_prefs"
    private const val KEY_IDS = "recent_ids"
    private const val MAX = 8

    fun load(context: Context): List<String> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_IDS, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { arr.getString(it) }
        }.getOrDefault(emptyList())
    }

    /** 新增 ID：去重置顶，超出上限截断。 */
    fun add(context: Context, id: String) {
        val current = load(context).filterNot { it == id }
        save(context, (listOf(id) + current).take(MAX))
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().remove(KEY_IDS).apply()
    }

    private fun save(context: Context, ids: List<String>) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_IDS, JSONArray(ids).toString()).apply()
    }
}
