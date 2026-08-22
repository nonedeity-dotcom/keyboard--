package com.offline.ruenkeyboard

import android.content.Context
import org.json.JSONArray

/**
 * Локальное хранилище истории буфера обмена.
 * Данные хранятся только в SharedPreferences на устройстве, никуда не передаются.
 */
class ClipboardHistoryStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getHistory(): MutableList<String> {
        val raw = prefs.getString(KEY_HISTORY, null) ?: return mutableListOf()
        val result = mutableListOf<String>()
        val arr = JSONArray(raw)
        for (i in 0 until arr.length()) {
            result.add(arr.getString(i))
        }
        return result
    }

    fun addEntry(text: String) {
        if (text.isBlank()) return
        val history = getHistory()
        history.remove(text)
        history.add(0, text)
        while (history.size > MAX_ENTRIES) {
            history.removeAt(history.size - 1)
        }
        save(history)
    }

    fun removeEntry(text: String) {
        val history = getHistory()
        history.remove(text)
        save(history)
    }

    fun clear() {
        prefs.edit().remove(KEY_HISTORY).apply()
    }

    private fun save(history: List<String>) {
        val arr = JSONArray()
        history.forEach { arr.put(it) }
        prefs.edit().putString(KEY_HISTORY, arr.toString()).apply()
    }

    companion object {
        private const val PREFS_NAME = "clipboard_history"
        private const val KEY_HISTORY = "history"
        private const val MAX_ENTRIES = 30
    }
}
