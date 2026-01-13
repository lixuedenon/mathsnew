// app/src/main/java/com/mathsnew/mathsnew/newsimplified/SimplificationFormsV2.kt
// 多形式化简结果数据类 V2

package com.mathsnew.mathsnew.newsimplified

import com.mathsnew.mathsnew.*
import kotlin.math.abs

data class SimplificationFormsV2(
    val forms: List<SimplifiedForm>
) {
    /**
     * 获取要显示的形式
     *
     * 策略：
     * 1. 按规范化表达式分组
     * 2. 每组选择优先级最高的形式（因式分解 > 标准型）
     */
    fun getDisplayForms(): List<SimplifiedForm> {
        if (forms.isEmpty()) return emptyList()

        // 按规范化表达式分组
        val groups = forms.groupBy { canonicalize(it.expression) }

        // 每组选择优先级最高的
        return groups.map { (_, formsInGroup) ->
            formsInGroup.maxByOrNull { getPriority(it.type) } ?: formsInGroup.first()
        }
    }

    /**
     * 获取形式的优先级（数值越大优先级越高）
     */
    private fun getPriority(type: SimplificationType): Int {
        return when (type) {
            SimplificationType.FACTORED -> 100
            SimplificationType.GROUPED -> 50
            SimplificationType.EXPANDED -> 10
            SimplificationType.STRUCTURAL -> 5
        }
    }

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

data class SimplifiedForm(
    val expression: MathNode,
    val type: SimplificationType,
    val description: String? = null
)

enum class SimplificationType {
    FACTORED,
    EXPANDED,
    GROUPED,
    STRUCTURAL
}