// app/src/main/java/com/mathsnew/mathsnew/newsimplified/ExpressionCanonicalizer.kt
// 表达式规范化引擎 - 保证完全展开和合并同类项（修复嵌套分式版本）

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

        if (node is MathNode.BinaryOp && node.operator == Operator.DIVIDE) {
            Log.d(TAG, "检测到分式，分别规范化分子和分母")
            val numerator = canonicalizeNonFraction(node.left)
            val denominator = canonicalizeNonFraction(node.right)
            val result = MathNode.BinaryOp(Operator.DIVIDE, numerator, denominator)
            Log.d(TAG, "分式规范化完成: $result")
            Log.d(TAG, "========== 规范化完成 ==========")
            return result
        }

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
                        val left = fullyExpand(node.left)
                        val right = fullyExpand(node.right)

                        // ✅✅✅ 新增：规范化分母 ✅✅✅
                        val canonicalizedDenominator = canonicalizeDenominator(right)

                        MathNode.BinaryOp(Operator.DIVIDE, left, canonicalizedDenominator)
                    }

                    Operator.POWER -> {
                        val base = fullyExpand(node.left)
                        val exponent = fullyExpand(node.right)
                        simplifyPower(base, exponent)
                    }
                }
            }
        }
    }

    // ✅✅✅ 新增方法：规范化分母 ✅✅✅
    private fun canonicalizeDenominator(denominator: MathNode): MathNode {
        return when {
            // 如果分母是加减法，规范化它
            denominator is MathNode.BinaryOp &&
            (denominator.operator == Operator.ADD || denominator.operator == Operator.SUBTRACT) -> {
                Log.d(TAG, "🔍 规范化分母: $denominator")

                val terms = extractTerms(denominator)
                Log.d(TAG, "  分母提取了 ${terms.size} 个项")

                val merged = mergeTerms(terms)
                Log.d(TAG, "  分母合并后 ${merged.size} 个项")

                val sorted = sortTerms(merged)
                val result = buildExpression(sorted)

                Log.d(TAG, "✅ 规范化后的分母: $result")
                result
            }

            // 如果分母本身是除法，递归规范化
            denominator is MathNode.BinaryOp &&
            denominator.operator == Operator.DIVIDE -> {
                val left = canonicalizeDenominator(denominator.left)
                val right = canonicalizeDenominator(denominator.right)
                MathNode.BinaryOp(Operator.DIVIDE, left, right)
            }

            // 其他情况保持不变
            else -> denominator
        }
    }

    private fun simplifyPower(base: MathNode, exponent: MathNode): MathNode {
        if (base is MathNode.BinaryOp && base.operator == Operator.POWER
            && base.right is MathNode.Number && exponent is MathNode.Number) {
            val innerExponent = base.right.value
            val outerExponent = exponent.value
            val newExponent = innerExponent * outerExponent

            Log.d(TAG, "简化嵌套幂: (${base.left})^$innerExponent^$outerExponent -> (${base.left})^$newExponent")

            return MathNode.BinaryOp(Operator.POWER, base.left, MathNode.Number(newExponent))
        }

        if (exponent is MathNode.Number) {
            return expandPower(base, exponent)
        }

        return MathNode.BinaryOp(Operator.POWER, base, exponent)
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

            val newFunctions = mutableMapOf<String, Double>()
            for ((funcKey, exponent) in leftTerm.functions) {
                newFunctions[funcKey] = exponent
            }
            for ((funcKey, exponent) in rightTerm.functions) {
                newFunctions[funcKey] = (newFunctions[funcKey] ?: 0.0) + exponent
            }

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

        for ((key, group) in groups) {
            var combined = group[0]
            for (i in 1 until group.size) {
                val next = group[i]
                val mergeResult = combined.mergeWith(next)
                if (mergeResult != null) {
                    combined = mergeResult
                    Log.d(TAG, "合并同类项: $key")
                } else {
                    Log.d(TAG, "无法合并（不是同类项）: ${combined.getBaseKey()} 和 ${next.getBaseKey()}")
                }
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