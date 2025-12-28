// app/src/main/java/com/mathsnew/mathsnew/newsimplified/SimplificationFormsV2.kt
// 多形式化简结果数据类 V2

package com.mathsnew.mathsnew.newsimplified

import com.mathsnew.mathsnew.*
import kotlin.math.abs

data class SimplificationFormsV2(
    val forms: List<SimplifiedForm>
) {
    fun getDisplayForms(): List<SimplifiedForm> {
        return forms.distinctBy { canonicalize(it.expression) }
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