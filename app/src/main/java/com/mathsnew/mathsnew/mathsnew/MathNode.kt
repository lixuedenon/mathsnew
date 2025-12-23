// app/src/main/java/com/mathsnew/mathsnew/MathNode.kt
// AST节点定义（智能括号版本）

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
     * 二元运算节点（智能括号版本）
     */
    data class BinaryOp(
        val operator: Operator,
        val left: MathNode,
        val right: MathNode
    ) : MathNode() {
        override fun toString(): String {
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

        /**
         * 格式化子节点（根据优先级决定是否需要括号）
         */
        private fun formatChild(child: MathNode, isLeft: Boolean): String {
            return if (needsParentheses(child, isLeft)) {
                "($child)"
            } else {
                child.toString()
            }
        }

        /**
         * 判断子节点是否需要括号
         *
         * 规则：
         * 1. 子节点优先级低于当前运算符 → 需要括号
         * 2. 减法/除法的右子节点，如果是加减法 → 需要括号
         */
        private fun needsParentheses(child: MathNode, isLeft: Boolean): Boolean {
            if (child !is BinaryOp) return false

            val parentPrecedence = operator.precedence
            val childPrecedence = child.operator.precedence

            return when {
                // 子节点优先级低 → 需要括号
                childPrecedence < parentPrecedence -> true

                // 减法的右子节点是加减法 → 需要括号
                // 例如：a - (b + c)
                !isLeft && operator == Operator.SUBTRACT &&
                childPrecedence == Operator.ADD.precedence -> true

                // 除法的右子节点是乘除法 → 需要括号
                // 例如：a / (b × c), a / (b / c)
                !isLeft && operator == Operator.DIVIDE &&
                childPrecedence == Operator.MULTIPLY.precedence -> true

                // 其他情况不需要括号
                else -> false
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
 * 运算符枚举（带优先级）
 */
enum class Operator(val symbol: String, val precedence: Int) {
    ADD("+", 1),        // 优先级最低
    SUBTRACT("-", 1),
    MULTIPLY("×", 2),
    DIVIDE("/", 2),
    POWER("^", 3)       // 优先级最高
}