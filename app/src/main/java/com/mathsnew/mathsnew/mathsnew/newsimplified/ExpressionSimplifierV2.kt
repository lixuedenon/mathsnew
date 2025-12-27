// app/src/main/java/com/mathsnew/mathsnew/newsimplified/ExpressionSimplifierV2.kt
// 表达式简化器 V2 - 完全重构版

package com.mathsnew.mathsnew.newsimplified

import com.mathsnew.mathsnew.*
import android.util.Log

/**
 * 表达式简化器 V2
 *
 * 全新架构:
 * 1. 使用 ExpressionCanonicalizer 保证正确性
 * 2. 使用 FormGenerator 生成多样性
 * 3. 使用 FormSelector 提供智能选择
 *
 * 这个类是对外的统一接口
 */
class ExpressionSimplifierV2 {

    companion object {
        private const val TAG = "SimplifierV2"
    }

    private val canonicalizer = ExpressionCanonicalizer()
    private val formGenerator = FormGenerator()
    private val formSelector = FormSelector()

    /**
     * 生成多种化简形式
     *
     * @param node 待化简的AST节点
     * @return 包含多种形式的 SimplificationForms
     */
    fun simplifyToMultipleForms(node: MathNode): SimplificationForms {
        Log.d(TAG, "========================================")
        Log.d(TAG, "SimplifierV2: 开始化简")
        Log.d(TAG, "输入: $node")

        try {
            val forms = formGenerator.generateAllForms(node)

            Log.d(TAG, "生成了 ${forms.forms.size} 种形式:")
            forms.forms.forEachIndexed { index, form ->
                val stats = formSelector.getFormStatistics(form.expression)
                Log.d(TAG, "  形式${index + 1} [${form.type}]: ${form.expression}")
                Log.d(TAG, "    $stats")
            }

            Log.d(TAG, "========================================")
            return forms

        } catch (e: Exception) {
            Log.e(TAG, "化简失败: ${e.message}", e)
            return SimplificationForms(listOf(
                SimplifiedForm(node, SimplificationType.STRUCTURAL, "原始形式")
            ))
        }
    }

    /**
     * 为求导选择最优形式
     *
     * @param forms 多种形式
     * @return 最适合求导的形式
     */
    fun selectBestForDifferentiation(forms: SimplificationForms): MathNode {
        val selected = formSelector.selectBestForDifferentiation(forms.forms)

        Log.d(TAG, "为求导选择的形式: $selected")
        val stats = formSelector.getFormStatistics(selected)
        Log.d(TAG, "  $stats")

        return selected
    }

    /**
     * 简化接口: 直接返回规范形式
     *
     * @param node 待化简的节点
     * @return 规范化后的节点
     */
    fun simplify(node: MathNode): MathNode {
        return canonicalizer.canonicalize(node)
    }
}