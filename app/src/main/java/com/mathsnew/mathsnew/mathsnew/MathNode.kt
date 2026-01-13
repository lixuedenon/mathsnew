// app/src/main/java/com/mathsnew/mathsnew/MathNode.kt
// AST节点定义（修复 +-1 负号问题）

package com.mathsnew.mathsnew

import android.util.Log
import kotlin.math.abs

sealed class MathNode {
    data class Number(val value: Double) : MathNode() {
        constructor(value: Int) : this(value.toDouble()) {
            Log.d("MathNode.Number", "📊 创建(Int构造): value=$value → ${value.toDouble()}")
        }

        init {
            Log.d("MathNode.Number", "📊 创建(主构造): value=$value")
        }

        override fun toString(): String {
            val result = if (value == value.toLong().toDouble()) {
                value.toLong().toString()
            } else {
                value.toString()
            }

            if (result.contains(".")) {
                Log.e("MathNode.Number", "⚠️ toString产生小数: value=$value → result='$result'")
            } else if (value.toString().contains(".")) {
                Log.d("MathNode.Number", "✅ toString整数化: value=$value → result='$result'")
            }

            return result
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
            // ✅ 处理 -1× 的情况
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
                    when {
                        // ✅ 右边是 -1×expr：a+(-1×b) → a-b
                        right is BinaryOp &&
                        right.operator == Operator.MULTIPLY &&
                        right.left is Number &&
                        abs(right.left.value + 1.0) < 1e-10 -> {
                            val innerRight = formatChild(right.right, isLeft = false)
                            "$leftStr-$innerRight"
                        }
                        // 右边是负数：a+(-5) → a-5
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
                                "-($rightStr)"
                            } else {
                                "-$rightStr"
                            }
                        }
                        // 右边是负数：a-(-5) → a+5
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