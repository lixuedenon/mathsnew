// app/src/main/java/com/mathsnew/mathsnew/MathNode.kt
// AST节点定义（智能括号版本）

package com.mathsnew.mathsnew

import kotlin.math.abs

sealed class MathNode {
    data class Number(val value: Double) : MathNode() {
        override fun toString(): String = value.toString()
    }

    data class Variable(val name: String) : MathNode() {
        override fun toString(): String = name
    }

    data class BinaryOp(
        val operator: Operator,
        val left: MathNode,
        val right: MathNode
    ) : MathNode() {
        override fun toString(): String {
            if (operator == Operator.MULTIPLY) {
                if (left is Number && abs(left.value + 1.0) < 1e-10) {
                    return "-${formatChild(right, isLeft = false)}"
                }

                if (left is Number && abs(left.value - 1.0) < 1e-10) {
                    return formatChild(right, isLeft = false)
                }
            }

            val leftStr = formatChild(left, isLeft = true)
            val rightStr = formatChild(right, isLeft = false)

            return when (operator) {
                Operator.ADD -> "$leftStr+$rightStr"
                Operator.SUBTRACT -> "$leftStr-$rightStr"
                Operator.MULTIPLY -> "$leftStr×$rightStr"
                Operator.DIVIDE -> "$leftStr/$rightStr"
                Operator.POWER -> "$leftStr^$rightStr"
            }
        }

        private fun formatChild(child: MathNode, isLeft: Boolean): String {
            return if (needsParentheses(child, isLeft)) {
                "($child)"
            } else {
                child.toString()
            }
        }

        private fun needsParentheses(child: MathNode, isLeft: Boolean): Boolean {
            if (child !is BinaryOp) return false

            val parentPrecedence = operator.precedence
            val childPrecedence = child.operator.precedence

            return when {
                childPrecedence < parentPrecedence -> true

                !isLeft && operator == Operator.SUBTRACT &&
                childPrecedence == Operator.ADD.precedence -> true

                !isLeft && operator == Operator.DIVIDE &&
                childPrecedence == Operator.MULTIPLY.precedence -> true

                else -> false
            }
        }
    }

    data class Function(
        val name: String,
        val argument: MathNode
    ) : MathNode() {
        override fun toString(): String = "$name($argument)"
    }
}

enum class Operator(val symbol: String, val precedence: Int) {
    ADD("+", 1),
    SUBTRACT("-", 1),
    MULTIPLY("×", 2),
    DIVIDE("/", 2),
    POWER("^", 3)
}