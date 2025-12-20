// app/src/main/java/com/mathsnew/mathsnew/MathNode.kt
// 抽象语法树（AST）数据结构

package com.mathsnew.mathsnew

/**
 * 数学表达式的抽象语法树（AST）节点
 *
 * 例如：表达式 "x^2 + 3*x" 的AST：
 *
 *          ADD(+)
 *         /      \
 *      POW(^)   MULT(×)
 *      /   \     /    \
 *     x     2   3      x
 */
sealed class MathNode {

    /**
     * 数字节点：5, 3.14, -2
     */
    data class Number(val value: Double) : MathNode() {
        override fun toString(): String = if (value % 1.0 == 0.0) {
            value.toInt().toString()
        } else {
            value.toString()
        }
    }

    /**
     * 变量节点：x, y, t
     */
    data class Variable(val name: String) : MathNode() {
        override fun toString(): String = name
    }

    /**
     * 二元运算节点：+, -, ×, /, ^
     */
    data class BinaryOp(
        val operator: Operator,
        val left: MathNode,
        val right: MathNode
    ) : MathNode() {
        override fun toString(): String {
            val leftStr = if (left is BinaryOp && left.operator.priority < operator.priority) {
                "($left)"
            } else {
                left.toString()
            }

            val rightStr = if (right is BinaryOp && right.operator.priority < operator.priority) {
                "($right)"
            } else {
                right.toString()
            }

            return "$leftStr${operator.symbol}$rightStr"
        }
    }

    /**
     * 函数调用节点：sin(x), cos(x), ln(x)
     */
    data class Function(
        val name: String,
        val argument: MathNode
    ) : MathNode() {
        override fun toString(): String = "$name($argument)"
    }
}

/**
 * 运算符枚举
 */
enum class Operator(val symbol: String, val priority: Int) {
    ADD("+", 1),
    SUBTRACT("-", 1),
    MULTIPLY("×", 2),
    DIVIDE("/", 2),
    POWER("^", 3);

    companion object {
        fun fromSymbol(symbol: String): Operator? {
            return values().find { it.symbol == symbol }
        }
    }
}