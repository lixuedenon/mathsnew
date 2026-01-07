// app/src/main/java/com/mathsnew/mathsnew/MathNode.kt
// AST节点定义（调试版 - 带详细日志）

package com.mathsnew.mathsnew

import android.util.Log
import kotlin.math.abs

sealed class MathNode {
    data class Number(val value: Double) : MathNode() {
        // ✅ 次构造函数：接受Int
        constructor(value: Int) : this(value.toDouble()) {
            Log.d("MathNode.Number", "📊 创建(Int构造): value=$value → ${value.toDouble()}")
        }

        // ✅ 主构造函数会自动调用（data class特性）
        init {
            Log.d("MathNode.Number", "📊 创建(主构造): value=$value")
        }

        override fun toString(): String {
            val result = if (value == value.toLong().toDouble()) {
                value.toLong().toString()
            } else {
                value.toString()
            }

            // 🔍 关键日志：追踪哪些数字被格式化
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