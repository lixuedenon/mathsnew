// app/src/main/java/com/mathsnew/mathsnew/newsimplified/ExpressionSimplifierV2.kt
// 表达式简化器 V2 - 完全重构版

package com.mathsnew.mathsnew.newsimplified

import com.mathsnew.mathsnew.*
import android.util.Log

class ExpressionSimplifierV2 {

    companion object {
        private const val TAG = "SimplifierV2"
    }

    private val canonicalizer = ExpressionCanonicalizer()
    private val formGenerator = FormGenerator()

    fun simplifyToMultipleForms(node: MathNode): SimplificationFormsV2 {
        Log.d(TAG, "========================================")
        Log.d(TAG, "SimplifierV2: 开始化简")
        Log.d(TAG, "输入: $node")

        try {
            val forms = formGenerator.generateAllForms(node)

            Log.d(TAG, "生成了 ${forms.forms.size} 种形式:")
            forms.forms.forEachIndexed { index, form ->
                val stats = FormSelector.getFormStatistics(form.expression)
                Log.d(TAG, "  形式${index + 1} [${form.type}]: ${form.expression}")
                Log.d(TAG, "    $stats")
            }

            Log.d(TAG, "========================================")
            return forms

        } catch (e: Exception) {
            Log.e(TAG, "化简失败: ${e.message}", e)
            return SimplificationFormsV2(listOf(
                SimplifiedForm(node, SimplificationType.STRUCTURAL, "原始形式")
            ))
        }
    }

    fun selectBestForDifferentiation(forms: SimplificationFormsV2): MathNode {
        val selected = FormSelector.selectBestForDifferentiation(forms.forms)

        Log.d(TAG, "为求导选择的形式: ${selected.expression}")
        val stats = FormSelector.getFormStatistics(selected.expression)
        Log.d(TAG, "  $stats")

        return selected.expression
    }

    fun simplify(node: MathNode): MathNode {
        return canonicalizer.canonicalize(node)
    }
}