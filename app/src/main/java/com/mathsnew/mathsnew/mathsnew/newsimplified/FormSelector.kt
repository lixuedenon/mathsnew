// app/src/main/java/com/mathsnew/mathsnew/newsimplified/FormSelector.kt
// 智能形式选择器 - 为下一步求导选择最优形式

package com.mathsnew.mathsnew.newsimplified

import com.mathsnew.mathsnew.*
import kotlin.math.max

/**
 * 智能形式选择器
 *
 * 职责: 从多种形式中选择最适合求导的形式
 *
 * 启发式规则:
 * 1. 多项式优先 (容易求导)
 * 2. 项数少的优先
 * 3. 嵌套少的优先
 * 4. 因式分解的形式次优 (链式法则也不难)
 */
class FormSelector {

    /**
     * 选择最适合求导的形式
     *
     * @param forms 形式列表
     * @return 最优形式
     */
    fun selectBestForDifferentiation(forms: List<SimplifiedForm>): MathNode {
        if (forms.isEmpty()) {
            throw IllegalArgumentException("No forms available")
        }

        if (forms.size == 1) {
            return forms[0].expression
        }

        val scored = forms.map { form ->
            form to estimateDifferentiationComplexity(form.expression)
        }

        val best = scored.minByOrNull { it.second }!!

        return best.first.expression
    }

    /**
     * 估算求导复杂度
     *
     * 返回值越小，越容易求导
     *
     * @param node 表达式节点
     * @return 复杂度分数
     */
    private fun estimateDifferentiationComplexity(node: MathNode): Int {
        return when (node) {
            is MathNode.Number -> 0
            is MathNode.Variable -> 1

            is MathNode.Function -> {
                3 + estimateDifferentiationComplexity(node.argument)
            }

            is MathNode.BinaryOp -> {
                when (node.operator) {
                    Operator.ADD, Operator.SUBTRACT -> {
                        estimateDifferentiationComplexity(node.left) +
                        estimateDifferentiationComplexity(node.right) + 1
                    }

                    Operator.MULTIPLY -> {
                        (estimateDifferentiationComplexity(node.left) +
                         estimateDifferentiationComplexity(node.right)) * 2
                    }

                    Operator.DIVIDE -> {
                        (estimateDifferentiationComplexity(node.left) +
                         estimateDifferentiationComplexity(node.right)) * 3
                    }

                    Operator.POWER -> {
                        val baseComplexity = estimateDifferentiationComplexity(node.left)
                        val expComplexity = estimateDifferentiationComplexity(node.right)

                        if (node.right is MathNode.Number) {
                            baseComplexity * 2 + 2
                        } else {
                            (baseComplexity + expComplexity) * 4
                        }
                    }
                }
            }
        }
    }

    /**
     * 获取表达式的统计信息（用于调试）
     *
     * @param node 表达式节点
     * @return 统计信息
     */
    fun getFormStatistics(node: MathNode): FormStatistics {
        return FormStatistics(
            termCount = countTerms(node),
            maxNestingDepth = getMaxDepth(node),
            variableCount = countVariables(node),
            functionCount = countFunctions(node),
            complexity = estimateDifferentiationComplexity(node)
        )
    }

    /**
     * 计数项数
     *
     * @param node 表达式节点
     * @return 项数
     */
    private fun countTerms(node: MathNode): Int {
        return when (node) {
            is MathNode.BinaryOp -> {
                if (node.operator == Operator.ADD || node.operator == Operator.SUBTRACT) {
                    countTerms(node.left) + countTerms(node.right)
                } else {
                    1
                }
            }
            else -> 1
        }
    }

    /**
     * 获取最大嵌套深度
     *
     * @param node 表达式节点
     * @return 最大深度
     */
    private fun getMaxDepth(node: MathNode): Int {
        return when (node) {
            is MathNode.Number, is MathNode.Variable -> 0

            is MathNode.Function -> 1 + getMaxDepth(node.argument)

            is MathNode.BinaryOp -> {
                1 + max(getMaxDepth(node.left), getMaxDepth(node.right))
            }
        }
    }

    /**
     * 计数变量出现次数
     *
     * @param node 表达式节点
     * @return 变量出现次数
     */
    private fun countVariables(node: MathNode): Int {
        return when (node) {
            is MathNode.Variable -> 1

            is MathNode.Function -> countVariables(node.argument)

            is MathNode.BinaryOp -> {
                countVariables(node.left) + countVariables(node.right)
            }

            else -> 0
        }
    }

    /**
     * 计数函数出现次数
     *
     * @param node 表达式节点
     * @return 函数出现次数
     */
    private fun countFunctions(node: MathNode): Int {
        return when (node) {
            is MathNode.Function -> 1 + countFunctions(node.argument)

            is MathNode.BinaryOp -> {
                countFunctions(node.left) + countFunctions(node.right)
            }

            else -> 0
        }
    }
}

/**
 * 表达式统计信息
 */
data class FormStatistics(
    val termCount: Int,
    val maxNestingDepth: Int,
    val variableCount: Int,
    val functionCount: Int,
    val complexity: Int
) {
    override fun toString(): String {
        return "统计[项:$termCount, 深度:$maxNestingDepth, 变量:$variableCount, 函数:$functionCount, 复杂度:$complexity]"
    }
}