// app/src/main/java/com/mathsnew/mathsnew/newsimplified/FractionSimplifier.kt
// 分式化简器 - 处理分子分母约分（支持 FunctionKey）

package com.mathsnew.mathsnew.newsimplified

import com.mathsnew.mathsnew.*
import android.util.Log
import kotlin.math.abs

class FractionSimplifier {

    companion object {
        private const val TAG = "FractionSimplifier"
        private const val EPSILON = 1e-10
    }

    private val canonicalizer = ExpressionCanonicalizer()

    fun simplifyFraction(node: MathNode): MathNode {
        if (node !is MathNode.BinaryOp || node.operator != Operator.DIVIDE) {
            return node
        }

        Log.d(TAG, "开始化简分式: $node")

        val numerator = canonicalizer.canonicalize(node.left)
        val denominator = canonicalizer.canonicalize(node.right)

        Log.d(TAG, "规范化分子: $numerator")
        Log.d(TAG, "规范化分母: $denominator")

        val numFactors = extractAllFactors(numerator)
        val denFactors = extractAllFactors(denominator)

        Log.d(TAG, "分子因子: ${numFactors.size}个")
        Log.d(TAG, "分母因子: ${denFactors.size}个")

        val commonFactors = findCommonFactors(numFactors, denFactors)

        Log.d(TAG, "公因子: ${commonFactors.size}个")

        if (commonFactors.isEmpty()) {
            return MathNode.BinaryOp(Operator.DIVIDE, numerator, denominator)
        }

        val reducedNum = removeFactors(numFactors, commonFactors)
        val reducedDen = removeFactors(denFactors, commonFactors)

        val newNumerator = if (reducedNum.isEmpty()) {
            MathNode.Number(1.0)
        } else {
            buildProduct(reducedNum)
        }

        val newDenominator = if (reducedDen.isEmpty()) {
            MathNode.Number(1.0)
        } else {
            buildProduct(reducedDen)
        }

        Log.d(TAG, "化简后分子: $newNumerator")
        Log.d(TAG, "化简后分母: $newDenominator")

        return if (newDenominator is MathNode.Number && abs(newDenominator.value - 1.0) < EPSILON) {
            newNumerator
        } else {
            MathNode.BinaryOp(Operator.DIVIDE, newNumerator, newDenominator)
        }
    }

    private fun extractAllFactors(node: MathNode): List<MathNode> {
        return when (node) {
            is MathNode.BinaryOp -> {
                if (node.operator == Operator.MULTIPLY) {
                    extractAllFactors(node.left) + extractAllFactors(node.right)
                } else if (node.operator == Operator.ADD || node.operator == Operator.SUBTRACT) {
                    listOf(node)
                } else {
                    listOf(node)
                }
            }
            else -> listOf(node)
        }
    }

    private fun findCommonFactors(
        factors1: List<MathNode>,
        factors2: List<MathNode>
    ): List<MathNode> {
        val common = mutableListOf<MathNode>()
        val used2 = mutableSetOf<Int>()

        for (f1 in factors1) {
            for (i in factors2.indices) {
                if (i in used2) continue

                val f2 = factors2[i]

                if (areEquivalent(f1, f2)) {
                    common.add(f1)
                    used2.add(i)
                    break
                }
            }
        }

        return common
    }

    private fun areEquivalent(a: MathNode, b: MathNode): Boolean {
        return when {
            a is MathNode.Number && b is MathNode.Number -> {
                abs(a.value - b.value) < EPSILON
            }
            a is MathNode.Variable && b is MathNode.Variable -> {
                a.name == b.name
            }
            a is MathNode.BinaryOp && b is MathNode.BinaryOp -> {
                a.operator == b.operator &&
                areEquivalent(a.left, b.left) &&
                areEquivalent(a.right, b.right)
            }
            a is MathNode.Function && b is MathNode.Function -> {
                val keyA = FunctionKey.from(a)
                val keyB = FunctionKey.from(b)
                keyA == keyB
            }
            else -> false
        }
    }

    private fun removeFactors(
        allFactors: List<MathNode>,
        toRemove: List<MathNode>
    ): List<MathNode> {
        val result = allFactors.toMutableList()

        for (factor in toRemove) {
            val index = result.indexOfFirst { areEquivalent(it, factor) }
            if (index >= 0) {
                result.removeAt(index)
            }
        }

        return result
    }

    private fun buildProduct(factors: List<MathNode>): MathNode {
        if (factors.isEmpty()) return MathNode.Number(1.0)
        if (factors.size == 1) return factors[0]

        var result = factors[0]
        for (i in 1 until factors.size) {
            result = MathNode.BinaryOp(Operator.MULTIPLY, result, factors[i])
        }
        return result
    }
}