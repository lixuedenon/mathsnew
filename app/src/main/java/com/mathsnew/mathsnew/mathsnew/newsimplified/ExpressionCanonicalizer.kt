// app/src/main/java/com/mathsnew/mathsnew/newsimplified/ExpressionCanonicalizer.kt
// 表达式规范化引擎 - 保证完全展开和合并同类项

package com.mathsnew.mathsnew.newsimplified

import com.mathsnew.mathsnew.*
import android.util.Log
import kotlin.math.abs

class ExpressionCanonicalizer {

    companion object {
        private const val TAG = "ExpressionCanonicalizer"
        private const val EPSILON = 1e-10
    }

    fun canonicalize(node: MathNode): MathNode {
        Log.d(TAG, "========== 开始规范化 ==========")
        Log.d(TAG, "输入: $node")

        // 🔧 关键修复：先处理分式
        if (node is MathNode.BinaryOp && node.operator == Operator.DIVIDE) {
            Log.d(TAG, "检测到分式，分别规范化分子和分母")
            val numerator = canonicalizeNonFraction(node.left)
            val denominator = canonicalizeNonFraction(node.right)
            val result = MathNode.BinaryOp(Operator.DIVIDE, numerator, denominator)
            Log.d(TAG, "分式规范化完成: $result")
            Log.d(TAG, "========== 规范化完成 ==========")
            return result
        }

        // 非分式，正常规范化
        val result = canonicalizeNonFraction(node)
        Log.d(TAG, "========== 规范化完成 ==========")
        return result
    }

    private fun canonicalizeNonFraction(node: MathNode): MathNode {
        val expanded = fullyExpand(node)
        Log.d(TAG, "展开后: $expanded")

        val terms = extractTerms(expanded)
        Log.d(TAG, "提取了 ${terms.size} 个项")

        val merged = mergeTerms(terms)
        Log.d(TAG, "合并后剩 ${merged.size} 个项")

        val sorted = sortTerms(merged)
        val result = buildExpression(sorted)

        Log.d(TAG, "最终结果: $result")

        return result
    }

    private fun fullyExpand(node: MathNode): MathNode {
        return when (node) {
            is MathNode.Number, is MathNode.Variable -> node

            is MathNode.Function -> {
                MathNode.Function(node.name, fullyExpand(node.argument))
            }

            is MathNode.BinaryOp -> {
                when (node.operator) {
                    Operator.ADD, Operator.SUBTRACT -> {
                        val left = fullyExpand(node.left)
                        val right = fullyExpand(node.right)
                        MathNode.BinaryOp(node.operator, left, right)
                    }

                    Operator.MULTIPLY -> {
                        val left = fullyExpand(node.left)
                        val right = fullyExpand(node.right)
                        expandMultiplication(left, right)
                    }

                    Operator.DIVIDE -> {
                        // 分式在展开时保持不变
                        val left = fullyExpand(node.left)
                        val right = fullyExpand(node.right)
                        MathNode.BinaryOp(Operator.DIVIDE, left, right)
                    }

                    Operator.POWER -> {
                        val base = fullyExpand(node.left)
                        expandPower(base, node.right)
                    }
                }
            }
        }
    }

    private fun expandMultiplication(left: MathNode, right: MathNode): MathNode {
        val leftIsSum = left is MathNode.BinaryOp && (left.operator == Operator.ADD || left.operator == Operator.SUBTRACT)
        val rightIsSum = right is MathNode.BinaryOp && (right.operator == Operator.ADD || right.operator == Operator.SUBTRACT)

        return when {
            leftIsSum && rightIsSum -> {
                val leftTerms = flattenSum(left)
                val rightTerms = flattenSum(right)

                val products = mutableListOf<MathNode>()
                for (lt in leftTerms) {
                    for (rt in rightTerms) {
                        products.add(multiplySimpleTerms(lt, rt))
                    }
                }

                buildSum(products)
            }

            leftIsSum -> {
                val leftTerms = flattenSum(left)
                val products = leftTerms.map { term ->
                    multiplySimpleTerms(term, right)
                }
                buildSum(products)
            }

            rightIsSum -> {
                val rightTerms = flattenSum(right)
                val products = rightTerms.map { term ->
                    multiplySimpleTerms(left, term)
                }
                buildSum(products)
            }

            else -> {
                multiplySimpleTerms(left, right)
            }
        }
    }

    private fun multiplySimpleTerms(left: MathNode, right: MathNode): MathNode {
        // 特殊情况：快速处理
        if (left is MathNode.Number && right is MathNode.Number) {
            return MathNode.Number(left.value * right.value)
        }

        if (left is MathNode.Number && abs(left.value) < EPSILON) {
            return MathNode.Number(0.0)
        }
        if (right is MathNode.Number && abs(right.value) < EPSILON) {
            return MathNode.Number(0.0)
        }

        if (left is MathNode.Number && abs(left.value - 1.0) < EPSILON) {
            return right
        }
        if (right is MathNode.Number && abs(right.value - 1.0) < EPSILON) {
            return left
        }

        // 使用 MathTerm 来处理复杂乘法
        try {
            val leftTerm = MathTerm.fromNode(left)
            val rightTerm = MathTerm.fromNode(right)

            val newCoefficient = leftTerm.coefficient * rightTerm.coefficient

            val newVariables = mutableMapOf<String, Double>()
            for ((varName, exponent) in leftTerm.variables) {
                newVariables[varName] = exponent
            }
            for ((varName, exponent) in rightTerm.variables) {
                newVariables[varName] = (newVariables[varName] ?: 0.0) + exponent
            }

            val newFunctions = leftTerm.functions + rightTerm.functions
            val newNested = leftTerm.nestedExpressions + rightTerm.nestedExpressions

            val resultTerm = MathTerm(newCoefficient, newVariables, newFunctions, newNested)

            return resultTerm.toNode()

        } catch (e: Exception) {
            return MathNode.BinaryOp(Operator.MULTIPLY, left, right)
        }
    }

    private fun expandPower(base: MathNode, exponent: MathNode): MathNode {
        if (exponent !is MathNode.Number) {
            return MathNode.BinaryOp(Operator.POWER, base, exponent)
        }

        val n = exponent.value

        if (base is MathNode.Variable) {
            return MathNode.BinaryOp(Operator.POWER, base, exponent)
        }

        if (base is MathNode.Number) {
            return MathNode.Number(Math.pow(base.value, n))
        }

        if (n != n.toInt().toDouble() || n < 0 || n > 10) {
            return MathNode.BinaryOp(Operator.POWER, base, exponent)
        }

        val intN = n.toInt()

        return when {
            intN == 0 -> MathNode.Number(1.0)
            intN == 1 -> base
            base is MathNode.BinaryOp && (base.operator == Operator.ADD || base.operator == Operator.SUBTRACT) -> {
                var result = base
                repeat(intN - 1) {
                    result = expandMultiplication(result, base)
                }
                result
            }
            else -> {
                MathNode.BinaryOp(Operator.POWER, base, exponent)
            }
        }
    }

    private fun flattenSum(node: MathNode): List<MathNode> {
        return when (node) {
            is MathNode.BinaryOp -> {
                when (node.operator) {
                    Operator.ADD -> flattenSum(node.left) + flattenSum(node.right)

                    Operator.SUBTRACT -> {
                        flattenSum(node.left) + flattenSum(node.right).map { term ->
                            negateTerm(term)
                        }
                    }

                    else -> listOf(node)
                }
            }
            else -> listOf(node)
        }
    }

    private fun negateTerm(term: MathNode): MathNode {
        return when (term) {
            is MathNode.Number -> MathNode.Number(-term.value)
            is MathNode.BinaryOp -> {
                if (term.operator == Operator.MULTIPLY && term.left is MathNode.Number) {
                    MathNode.BinaryOp(
                        Operator.MULTIPLY,
                        MathNode.Number(-term.left.value),
                        term.right
                    )
                } else {
                    MathNode.BinaryOp(Operator.MULTIPLY, MathNode.Number(-1.0), term)
                }
            }
            else -> MathNode.BinaryOp(Operator.MULTIPLY, MathNode.Number(-1.0), term)
        }
    }

    private fun buildSum(terms: List<MathNode>): MathNode {
        if (terms.isEmpty()) return MathNode.Number(0.0)
        if (terms.size == 1) return terms[0]

        var result = terms[0]
        for (i in 1 until terms.size) {
            result = MathNode.BinaryOp(Operator.ADD, result, terms[i])
        }
        return result
    }

    private fun extractTerms(node: MathNode): List<MathTerm> {
        val termNodes = flattenSum(node)
        return termNodes.map { MathTerm.fromNode(it) }
    }

    private fun mergeTerms(terms: List<MathTerm>): List<MathTerm> {
        if (terms.isEmpty()) return emptyList()

        val groups = mutableMapOf<String, MutableList<MathTerm>>()

        for (term in terms) {
            val key = term.getBaseKey()
            if (!groups.containsKey(key)) {
                groups[key] = mutableListOf()
            }
            groups[key]!!.add(term)
        }

        val merged = mutableListOf<MathTerm>()

        for ((_, group) in groups) {
            var combined = group[0]
            for (i in 1 until group.size) {
                combined = combined.mergeWith(group[i]) ?: combined
            }

            if (!combined.isZero()) {
                merged.add(combined)
            }
        }

        return merged
    }

    private fun sortTerms(terms: List<MathTerm>): List<MathTerm> {
        return terms.sortedWith(compareBy(
            { it.isConstant() },
            { -getTotalDegree(it) },
            { it.getBaseKey() }
        ))
    }

    private fun getTotalDegree(term: MathTerm): Double {
        return term.variables.values.sum()
    }

    private fun buildExpression(terms: List<MathTerm>): MathNode {
        if (terms.isEmpty()) return MathNode.Number(0.0)

        val nodes = terms.map { it.toNode() }

        if (nodes.size == 1) return nodes[0]

        var result = nodes[0]
        for (i in 1 until nodes.size) {
            result = MathNode.BinaryOp(Operator.ADD, result, nodes[i])
        }

        return result
    }
}