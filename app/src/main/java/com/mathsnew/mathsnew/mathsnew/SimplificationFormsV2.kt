// app/src/main/java/com/mathsnew/mathsnew/newsimplified/SimplificationFormsV2.kt
// V2 版本的多形式化简结果数据类

package com.mathsnew.mathsnew.newsimplified

import com.mathsnew.mathsnew.*
import kotlin.math.abs

data class SimplificationFormsV2(
    val forms: List<SimplifiedForm>
) {
    fun getDisplayForms(): List<SimplifiedForm> {
        val unique = mutableListOf<SimplifiedForm>()
        val seen = mutableSetOf<String>()

        for (form in forms) {
            val key = canonicalize(form.expression)
            if (key !in seen) {
                unique.add(form)
                seen.add(key)
            }
        }

        return unique
    }

    private fun canonicalize(node: MathNode): String {
        return when (node) {
            is MathNode.Number -> {
                val value = node.value
                if (abs(value - value.toInt()) < 1e-10) {
                    value.toInt().toString()
                } else {
                    value.toString()
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