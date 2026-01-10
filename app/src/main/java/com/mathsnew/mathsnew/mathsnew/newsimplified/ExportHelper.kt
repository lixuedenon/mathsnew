// app/src/main/java/com/mathsnew/mathsnew/newsimplified/ExportHelper.kt
// 导出助手 - 集成 HandwrittenExporter 到现有系统

package com.mathsnew.mathsnew.newsimplified

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast

/**
 * 导出助手
 *
 * 功能：
 * 1. 将计算结果导出为手写格式
 * 2. 复制到剪贴板
 * 3. 显示提示信息
 */
class ExportHelper(private val context: Context) {

    private val exporter = HandwrittenExporter()
    private val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    /**
     * 导出并复制到剪贴板
     *
     * @param result 计算结果
     * @param originalExpression 原始表达式
     */
    fun exportAndCopy(result: CalculationResult.Success, originalExpression: String) {
        try {
            // 生成手写格式文本（直接从 AST）
            val handwrittenText = exporter.exportCalculationResult(
                original = originalExpression,
                firstDerivativeForms = result.forms,
                secondDerivativeForms = result.secondDerivativeForms
            )

            // 复制到剪贴板
            val clip = ClipData.newPlainText("导数计算结果", handwrittenText)
            clipboardManager.setPrimaryClip(clip)

            // 显示提示
            Toast.makeText(context, "已复制到剪贴板", Toast.LENGTH_SHORT).show()

            // 可选：打印到日志（方便调试）
            android.util.Log.d("ExportHelper", "导出内容:\n$handwrittenText")

        } catch (e: Exception) {
            android.util.Log.e("ExportHelper", "导出失败", e)
            Toast.makeText(context, "导出失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 仅生成文本，不复制
     */
    fun exportToText(result: CalculationResult.Success, originalExpression: String): String {
        return exporter.exportCalculationResult(
            original = originalExpression,
            firstDerivativeForms = result.forms,
            secondDerivativeForms = result.secondDerivativeForms
        )
    }
}