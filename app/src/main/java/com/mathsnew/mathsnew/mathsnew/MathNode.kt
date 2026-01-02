// app/src/main/java/com/mathsnew/mathsnew/MathNode.kt
// AST节点定义（智能括号版本 + 负号优化）

package com.mathsnew.mathsnew

import kotlin.math.abs

sealed class MathNode {
    data class Number(val value: Double) : MathNode() {
        override fun toString(): String {
            // ✅ 修复：如果是整数，不显示小数点
            return if (value == value.toLong().toDouble()) {
                value.toLong().toString()  // 3, 6, 12
            } else {
                value.toString()  // 3.14, 2.5
            }
        }
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
                Operator.ADD -> {
                    // ✅ 优化：处理 +- 情况
                    when {
                        // 右边是负数：a+(-b) → a-b
                        right is Number && right.value < 0 -> {
                            "$leftStr-${abs(right.value)}"
                        }
                        // 右边字符串以负号开头：a+(-expr) → a-expr
                        rightStr.startsWith("-") -> {
                            "$leftStr$rightStr"
                        }
                        else -> {
                            "$leftStr+$rightStr"
                        }
                    }
                }

                Operator.SUBTRACT -> {
                    when {
                        // 左边是0：0-expr → -expr
                        left is Number && abs(left.value) < 1e-10 -> {
                            if (right is BinaryOp && (right.operator == Operator.ADD ||
                                right.operator == Operator.SUBTRACT)) {
                                "-($rightStr)"  // 0-(a+b) → -(a+b)
                            } else {
                                "-$rightStr"  // 0-a → -a
                            }
                        }
                        // 右边是负数：a-(-b) → a+b
                        right is Number && right.value < 0 -> {
                            "$leftStr+${abs(right.value)}"
                        }
                        else -> {
                            "$leftStr-$rightStr"
                        }
                    }
                }

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