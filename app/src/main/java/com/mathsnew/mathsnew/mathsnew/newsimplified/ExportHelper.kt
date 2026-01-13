// app/src/main/java/com/mathsnew/mathsnew/newsimplified/ExportHelper.kt
// 导出助手 - 简化版，直接导出显示文本

package com.mathsnew.mathsnew.newsimplified

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import android.util.Log

/**
 * 导出助手（简化版）
 *
 * 功能：
 * 1. 将屏幕显示的文本复制到剪贴板
 * 2. 显示提示信息
 */
class ExportHelper(private val context: Context) {

    private val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    companion object {
        private const val TAG = "ExportHelper"
    }

    /**
     * 导出并复制到剪贴板
     *
     * @param displayText 屏幕显示的文本
     */
    fun exportAndCopy(displayText: String) {
        try {
            Log.d(TAG, "开始导出，文本长度: ${displayText.length}")

            // 复制到剪贴板
            val clip = ClipData.newPlainText("导数计算结果", displayText)
            clipboardManager.setPrimaryClip(clip)

            Toast.makeText(context, "已复制到剪贴板", Toast.LENGTH_SHORT).show()

            // 打印到日志（方便调试）
            Log.d(TAG, "导出内容:\n$displayText")

        } catch (e: Exception) {
            Log.e(TAG, "导出失败", e)
            Toast.makeText(context, "导出失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}