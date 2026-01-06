// app/src/main/java/com/mathsnew/mathsnew/utils/CalculationHistoryManager.kt
// 计算历史记录管理器 - 最多保存5条

package com.mathsnew.mathsnew.utils

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray

class CalculationHistoryManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "calculus_history"
        private const val KEY_HISTORY = "history_list"
        private const val MAX_HISTORY_SIZE = 5
    }

    /**
     * 保存表达式到历史记录（循环队列，最多5条）
     */
    fun saveExpression(expression: String) {
        if (expression.isEmpty()) return

        val history = getHistory().toMutableList()

        // 如果已存在相同表达式，先移除
        history.remove(expression)

        // 添加到队首
        history.add(0, expression)

        // 保持最多5条
        if (history.size > MAX_HISTORY_SIZE) {
            history.removeAt(history.size - 1)
        }

        // 保存到 SharedPreferences
        val jsonArray = JSONArray()
        for (expr in history) {
            jsonArray.put(expr)
        }

        prefs.edit().putString(KEY_HISTORY, jsonArray.toString()).apply()
    }

    /**
     * 获取历史记录列表（最新的在前）
     */
    fun getHistory(): List<String> {
        val jsonString = prefs.getString(KEY_HISTORY, null) ?: return emptyList()

        return try {
            val jsonArray = JSONArray(jsonString)
            val list = mutableListOf<String>()
            for (i in 0 until jsonArray.length()) {
                list.add(jsonArray.getString(i))
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * 清除所有历史记录
     */
    fun clearHistory() {
        prefs.edit().remove(KEY_HISTORY).apply()
    }
}