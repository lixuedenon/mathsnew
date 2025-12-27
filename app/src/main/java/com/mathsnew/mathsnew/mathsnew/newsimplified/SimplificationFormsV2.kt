// app/src/main/java/com/mathsnew/mathsnew/newsimplified/SimplificationFormsV2.kt
// 多形式化简结果数据类 V2

package com.mathsnew.mathsnew.newsimplified

import com.mathsnew.mathsnew.*
import kotlin.math.abs

/**
 * 多形式化简结果 V2
 *
 * 支持的形式类型:
 * - FACTORED: 因式形式
 * - EXPANDED: 完全展开
 * - GROUPED: 分组形式
 * - STRUCTURAL: 保留结构
 */
data class SimplificationForms(
    val forms: List<SimplifiedForm>
) {
    /**
     * 获取用于显示的表达式列表（已去重）
     *
     * @return 去重后的形式列表
     */
    fun getDisplayForms(): List<SimplifiedForm> {
        return forms.distinctBy { canonicalize(it.expression) }
    }

    /**
     * 标准化表达式（用于去重比较）
     *
     * @param node 表达式节点
     * @return 标准化字符串
     */
    private fun canonicalize(node: MathNode): String {
        return when (node) {
            is MathNode.Number -> {
                val value = node.value
                if (abs(value - value.toInt()) < 1e-10) {
                    value.toInt().toString()
                } else {
                    String.format("%.6f", value).trimEnd('0').trimEnd('.')
                }
            }
            is MathNode.Variable -> node.name
            is MathNode.BinaryOp -> {
                val left = canonicalize(node.left)
                val right = canonicalize(node.right)
                val op = when (node.operator) {
                    Operator.ADD -> "+"
                    Operator.SUBTRACT -> "-"
                    Operator.MULTIPLY -> "*"
                    Operator.DIVIDE -> "/"
                    Operator.POWER -> "^"
                }
                "($left$op$right)"
            }
            is MathNode.Function -> {
                val arg = canonicalize(node.argument)
                "${node.name}($arg)"
            }
        }
    }
}

/**
 * 单个化简形式 V2
 *
 * @param expression 化简后的表达式
 * @param type 化简类型
 * @param description 可选的描述文本（用于UI标注）
 */
data class SimplifiedForm(
    val expression: MathNode,
    val type: SimplificationType,
    val description: String? = null
)