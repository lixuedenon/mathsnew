// app/src/main/java/com/mathsnew/mathsnew/SimplificationForms.kt
// 多形式化简结果数据类

package com.mathsnew.mathsnew

import kotlin.math.abs

/**
 * 多形式化简结果
 *
 * 包含表达式的多种等价形式（最多三种），用户可以同时查看：
 * - 因式形式（保留结构）
 * - 展开形式（合并同类项）
 * - 标准形式（标准多项式）
 *
 * 自动去重，只显示有差异的形式
 */
data class SimplificationForms(
    val forms: List<SimplifiedForm>
) {
    /**
     * 获取用于显示的表达式列表
     *
     * 改进的去重逻辑：
     * 1. 先对每个表达式进行标准化
     * 2. 再比较字符串表示
     * 3. 只保留真正不同的形式
     *
     * @return 去重后的化简形式列表
     */
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

    /**
     * 标准化表达式以进行比较
     *
     * 规则：
     * 1. 将浮点数标准化（2.0 → 2）
     * 2. 忽略括号的细微差异
     * 3. 统一运算符表示
     */
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

/**
 * 单个化简形式
 *
 * @param expression 化简后的AST节点
 * @param type 化简类型
 * @param description 可选的描述文本（用于UI标注）
 */
data class SimplifiedForm(
    val expression: MathNode,
    val type: SimplificationType,
    val description: String? = null
)

/**
 * 化简类型枚举
 */
enum class SimplificationType {
    FACTORED,    // 因式形式（保留结构，只展开数字括号）
    EXPANDED,    // 展开形式（完全展开，合并同类项）
    STANDARD     // 标准形式（标准多项式形式）
}