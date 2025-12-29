// app/src/main/java/com/mathsnew/mathsnew/newsimplified/FormSelector.kt
// 形式选择器 - 选择最佳显示形式

package com.mathsnew.mathsnew.newsimplified

import com.mathsnew.mathsnew.*
import android.util.Log
import kotlin.math.abs

object FormSelector {

    private const val TAG = "FormSelector"

    fun selectBestForDifferentiation(forms: List<SimplifiedForm>): SimplifiedForm {
        if (forms.isEmpty()) {
            throw IllegalArgumentException("表单列表不能为空")
        }

        if (forms.size == 1) {
            return forms[0]
        }

        Log.d(TAG, "从 ${forms.size} 个形式中选择最佳")

        val scored = forms.map { form ->
            val score = calculateScore(form.expression)
            Log.d(TAG, "形式 [${form.type}]: 得分=$score, 表达式=${form.expression}")
            Pair(form, score)
        }

        val best = scored.minByOrNull { it.second }?.first ?: forms[0]

        Log.d(TAG, "选择: [${best.type}] ${best.expression}")

        return best
    }

    private fun calculateScore(node: MathNode): Int {
        var score = 0

        score += countNodes(node) * 1

        score += countDivisions(node) * 5

        score += countPowers(node) * 3

        score += countFunctions(node) * 2

        return score
    }

    private fun countNodes(node: MathNode): Int {
        return when (node) {
            is MathNode.Number, is MathNode.Variable -> 1
            is MathNode.Function -> 1 + countNodes(node.argument)
            is MathNode.BinaryOp -> 1 + countNodes(node.left) + countNodes(node.right)
        }
    }

    private fun countDivisions(node: MathNode): Int {
        return when (node) {
            is MathNode.BinaryOp -> {
                val count = if (node.operator == Operator.DIVIDE) 1 else 0
                count + countDivisions(node.left) + countDivisions(node.right)
            }
            is MathNode.Function -> countDivisions(node.argument)
            else -> 0
        }
    }

    private fun countPowers(node: MathNode): Int {
        return when (node) {
            is MathNode.BinaryOp -> {
                val count = if (node.operator == Operator.POWER) 1 else 0
                count + countPowers(node.left) + countPowers(node.right)
            }
            is MathNode.Function -> countPowers(node.argument)
            else -> 0
        }
    }

    private fun countFunctions(node: MathNode): Int {
        return when (node) {
            is MathNode.Function -> 1 + countFunctions(node.argument)
            is MathNode.BinaryOp -> countFunctions(node.left) + countFunctions(node.right)
            else -> 0
        }
    }

    fun getFormStatistics(node: MathNode): String {
        val nodes = countNodes(node)
        val divisions = countDivisions(node)
        val powers = countPowers(node)
        val functions = countFunctions(node)
        val score = calculateScore(node)

        return "节点:$nodes, 除法:$divisions, 幂:$powers, 函数:$functions, 得分:$score"
    }
}