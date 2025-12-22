// app/src/main/java/com/mathsnew/mathsnew/MathNode.kt
// AST节点定义

package com.mathsnew.mathsnew

/**
 * 数学表达式的抽象语法树节点
 */
sealed class MathNode {
    /**
     * 数字节点
     */
    data class Number(val value: Double) : MathNode() {
        override fun toString(): String = value.toString()
    }

    /**
     * 变量节点
     */
    data class Variable(val name: String) : MathNode() {
        override fun toString(): String = name
    }

    /**
     * 二元运算节点
     */
    data class BinaryOp(
        val operator: Operator,
        val left: MathNode,
        val right: MathNode
    ) : MathNode() {
        override fun toString(): String {
            return when (operator) {
                Operator.ADD -> "($left+$right)"
                Operator.SUBTRACT -> "($left-$right)"
                Operator.MULTIPLY -> "($left×$right)"
                Operator.DIVIDE -> "($left/$right)"  // ⚠️ 关键：这里必须是 /
                Operator.POWER -> "($left^$right)"
            }
        }
    }

    /**
     * 函数节点
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
enum class Operator(val symbol: String, val precedence: Int) {
    ADD("+", 1),
    SUBTRACT("-", 1),
    MULTIPLY("×", 2),
    DIVIDE("/", 2),  // ⚠️ 关键：符号是 /
    POWER("^", 3)
}