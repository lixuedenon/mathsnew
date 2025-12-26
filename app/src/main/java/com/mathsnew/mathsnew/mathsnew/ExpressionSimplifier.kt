// app/src/main/java/com/mathsnew/mathsnew/ExpressionSimplifier.kt
// 表达式简化器（多形式版本）

package com.mathsnew.mathsnew

import android.util.Log
import kotlin.math.abs
import kotlin.math.round

/**
 * 表达式简化器
 *
 * 提供两种化简策略：
 * 1. 因式化简（FACTORED）：保留有意义的因式结构，只展开数字括号
 * 2. 展开化简（EXPANDED）：完全展开，合并所有同类项
 *
 * 化简原则：
 * ✅ 消除冗余（0+x, 1×x）
 * ✅ 合并纯数字运算
 * ✅ 标准化负数表示
 * ✅ 浮点数容差处理
 *
 * 因式化简额外规则：
 * ✅ 只展开数字×括号（2(x+1) → 2x+2）
 * ✅ 保留因式×因式（(x+1)(x+2) 不变）
 * ✅ 合并相同项（2x + 3x → 5x）
 *
 * 展开化简额外规则：
 * ✅ 展开所有括号
 * ✅ 全面合并同类项（2x+3x+4x → 9x）
 * ✅ 分数约分（6x/4 → 3x/2）
 * ✅ 幂的合并（x × x → x²）
 */
class ExpressionSimplifier {

    companion object {
        private const val EPSILON = 1e-10
    }

    /**
     * 生成多种化简形式
     *
     * @param node 待化简的AST节点
     * @return 包含多种形式的SimplificationForms对象
     */
    fun simplifyToMultipleForms(node: MathNode): SimplificationForms {
        val forms = mutableListOf<SimplifiedForm>()

        val factored = simplifyFactored(node)
        forms.add(SimplifiedForm(
            expression = factored,
            type = SimplificationType.FACTORED
        ))

        val expanded = simplifyExpanded(node)
        forms.add(SimplifiedForm(
            expression = expanded,
            type = SimplificationType.EXPANDED
        ))

        return SimplificationForms(forms)
    }

    /**
     * 因式化简（保留结构）
     *
     * 只展开数字×括号，保留其他因式结构
     *
     * @param node 待化简的AST节点
     * @return 化简后的AST节点
     */
    private fun simplifyFactored(node: MathNode): MathNode {
        var current = node
        var previous: MathNode
        var iterations = 0
        val maxIterations = 15

        do {
            previous = current
            current = simplifyFactoredOnce(current)
            iterations++
        } while (current != previous && iterations < maxIterations)

        return current
    }

    /**
     * 因式化简单轮迭代
     */
    private fun simplifyFactoredOnce(node: MathNode): MathNode {
        return when (node) {
            is MathNode.Number -> node
            is MathNode.Variable -> node
            is MathNode.Function -> {
                MathNode.Function(node.name, simplifyFactoredOnce(node.argument))
            }
            is MathNode.BinaryOp -> {
                val left = simplifyFactoredOnce(node.left)
                val right = simplifyFactoredOnce(node.right)
                simplifyBinaryOpFactored(node.operator, left, right)
            }
        }
    }

    /**
     * 二元运算符化简（因式模式）
     */
    private fun simplifyBinaryOpFactored(
        op: Operator,
        left: MathNode,
        right: MathNode
    ): MathNode {
        return when (op) {
            Operator.ADD -> simplifyAdditionFactored(left, right)
            Operator.SUBTRACT -> simplifySubtractionFactored(left, right)
            Operator.MULTIPLY -> simplifyMultiplicationFactored(left, right)
            Operator.DIVIDE -> simplifyDivisionFactored(left, right)
            Operator.POWER -> simplifyPowerFactored(left, right)
        }
    }

    /**
     * 加法化简（因式模式）
     *
     * 只合并完全相同底数的项，不展开
     */
    private fun simplifyAdditionFactored(left: MathNode, right: MathNode): MathNode {
        if (left is MathNode.Number && abs(left.value) < EPSILON) return right
        if (right is MathNode.Number && abs(right.value) < EPSILON) return left

        if (left is MathNode.Number && right is MathNode.Number) {
            return MathNode.Number(left.value + right.value)
        }

        if (left == right) {
            return MathNode.BinaryOp(Operator.MULTIPLY, MathNode.Number(2.0), left)
        }

        val leftCoeff = extractCoefficient(left)
        val rightCoeff = extractCoefficient(right)
        val leftBase = extractBase(left)
        val rightBase = extractBase(right)

        if (leftBase.toString() == rightBase.toString()) {
            val newCoeff = leftCoeff + rightCoeff
            return when {
                abs(newCoeff) < EPSILON -> MathNode.Number(0.0)
                abs(newCoeff - 1.0) < EPSILON -> leftBase
                else -> MathNode.BinaryOp(Operator.MULTIPLY, MathNode.Number(newCoeff), leftBase)
            }
        }

        return MathNode.BinaryOp(Operator.ADD, left, right)
    }

    /**
     * 减法化简（因式模式）
     */
    private fun simplifySubtractionFactored(left: MathNode, right: MathNode): MathNode {
        if (right is MathNode.Number && abs(right.value) < EPSILON) return left

        if (left is MathNode.Number && abs(left.value) < EPSILON) {
            return MathNode.BinaryOp(Operator.MULTIPLY, MathNode.Number(-1.0), right)
        }

        if (left is MathNode.Number && right is MathNode.Number) {
            return MathNode.Number(left.value - right.value)
        }

        if (left == right) {
            return MathNode.Number(0.0)
        }

        val leftCoeff = extractCoefficient(left)
        val rightCoeff = extractCoefficient(right)
        val leftBase = extractBase(left)
        val rightBase = extractBase(right)

        if (leftBase.toString() == rightBase.toString()) {
            val newCoeff = leftCoeff - rightCoeff
            return when {
                abs(newCoeff) < EPSILON -> MathNode.Number(0.0)
                abs(newCoeff - 1.0) < EPSILON -> leftBase
                abs(newCoeff + 1.0) < EPSILON -> MathNode.BinaryOp(
                    Operator.MULTIPLY,
                    MathNode.Number(-1.0),
                    leftBase
                )
                else -> MathNode.BinaryOp(Operator.MULTIPLY, MathNode.Number(newCoeff), leftBase)
            }
        }

        return MathNode.BinaryOp(Operator.SUBTRACT, left, right)
    }

    /**
     * 乘法化简（因式模式）
     *
     * 只展开数字×括号，保留因式×因式
     */
    private fun simplifyMultiplicationFactored(left: MathNode, right: MathNode): MathNode {
        if (left is MathNode.Number && abs(left.value) < EPSILON) return MathNode.Number(0.0)
        if (right is MathNode.Number && abs(right.value) < EPSILON) return MathNode.Number(0.0)

        if (left is MathNode.Number && abs(left.value - 1.0) < EPSILON) return right
        if (right is MathNode.Number && abs(right.value - 1.0) < EPSILON) return left

        if (left is MathNode.Number && abs(left.value + 1.0) < EPSILON) {
            return MathNode.BinaryOp(Operator.SUBTRACT, MathNode.Number(0.0), right)
        }
        if (right is MathNode.Number && abs(right.value + 1.0) < EPSILON) {
            return MathNode.BinaryOp(Operator.SUBTRACT, MathNode.Number(0.0), left)
        }

        if (left is MathNode.Number && isAdditionOrSubtraction(right)) {
            return expandDistributiveFactored(left, right)
        }
        if (right is MathNode.Number && isAdditionOrSubtraction(left)) {
            return expandDistributiveFactored(right, left)
        }

        if (left is MathNode.Number && right is MathNode.Number) {
            return MathNode.Number(left.value * right.value)
        }

        val allFactors = collectMultiplicationFactors(left) + collectMultiplicationFactors(right)
        val numberFactors = allFactors.filterIsInstance<MathNode.Number>()
        val otherFactors = allFactors.filter { it !is MathNode.Number }

        val coefficient = if (numberFactors.isEmpty()) {
            1.0
        } else {
            numberFactors.map { it.value }.reduce { acc, d -> acc * d }
        }

        val mergedFactors = mergePowerFactors(otherFactors)

        return when {
            abs(coefficient) < EPSILON -> MathNode.Number(0.0)

            abs(coefficient - 1.0) < EPSILON -> {
                when {
                    mergedFactors.isEmpty() -> MathNode.Number(1.0)
                    mergedFactors.size == 1 -> mergedFactors[0]
                    else -> buildMultiplication(mergedFactors)
                }
            }

            abs(coefficient + 1.0) < EPSILON -> {
                val product = when {
                    mergedFactors.isEmpty() -> MathNode.Number(1.0)
                    mergedFactors.size == 1 -> mergedFactors[0]
                    else -> buildMultiplication(mergedFactors)
                }
                MathNode.BinaryOp(Operator.SUBTRACT, MathNode.Number(0.0), product)
            }

            mergedFactors.isEmpty() -> MathNode.Number(coefficient)

            else -> {
                val product = if (mergedFactors.size == 1) mergedFactors[0]
                             else buildMultiplication(mergedFactors)

                if (abs(coefficient - 1.0) < EPSILON) {
                    product
                } else if (abs(coefficient + 1.0) < EPSILON) {
                    MathNode.BinaryOp(Operator.SUBTRACT, MathNode.Number(0.0), product)
                } else {
                    MathNode.BinaryOp(Operator.MULTIPLY, MathNode.Number(coefficient), product)
                }
            }
        }
    }

    /**
     * 判断节点是否是加法或减法
     */
    private fun isAdditionOrSubtraction(node: MathNode): Boolean {
        return node is MathNode.BinaryOp &&
               (node.operator == Operator.ADD || node.operator == Operator.SUBTRACT)
    }

    /**
     * 展开分配律（因式模式）
     *
     * 只展开数字×(加减法)
     */
    private fun expandDistributiveFactored(
        coefficient: MathNode.Number,
        expr: MathNode
    ): MathNode {
        if (expr !is MathNode.BinaryOp) {
            return MathNode.BinaryOp(Operator.MULTIPLY, coefficient, expr)
        }

        return when (expr.operator) {
            Operator.ADD -> {
                val left = simplifyMultiplicationFactored(coefficient, expr.left)
                val right = simplifyMultiplicationFactored(coefficient, expr.right)
                simplifyAdditionFactored(left, right)
            }
            Operator.SUBTRACT -> {
                val left = simplifyMultiplicationFactored(coefficient, expr.left)
                val right = simplifyMultiplicationFactored(coefficient, expr.right)
                simplifySubtractionFactored(left, right)
            }
            else -> MathNode.BinaryOp(Operator.MULTIPLY, coefficient, expr)
        }
    }

    /**
     * 除法化简（因式模式）
     */
    private fun simplifyDivisionFactored(left: MathNode, right: MathNode): MathNode {
        if (right is MathNode.Number && abs(right.value - 1.0) < EPSILON) {
            return left
        }

        if (left is MathNode.Number && abs(left.value) < EPSILON) {
            return MathNode.Number(0.0)
        }

        if (left is MathNode.Number && right is MathNode.Number) {
            return MathNode.Number(left.value / right.value)
        }

        if (left == right) {
            return MathNode.Number(1.0)
        }

        return MathNode.BinaryOp(Operator.DIVIDE, left, right)
    }

    /**
     * 幂运算化简（因式模式）
     */
    private fun simplifyPowerFactored(left: MathNode, right: MathNode): MathNode {
        if (right is MathNode.Number && abs(right.value) < EPSILON) {
            return MathNode.Number(1.0)
        }

        if (right is MathNode.Number && abs(right.value - 1.0) < EPSILON) {
            return left
        }

        if (left is MathNode.Number && abs(left.value - 1.0) < EPSILON) {
            return MathNode.Number(1.0)
        }

        if (left is MathNode.Number && right is MathNode.Number) {
            return MathNode.Number(Math.pow(left.value, right.value))
        }

        if (left is MathNode.BinaryOp &&
            left.operator == Operator.POWER &&
            left.right is MathNode.Number &&
            right is MathNode.Number) {
            val newExponent = left.right.value * right.value
            return MathNode.BinaryOp(Operator.POWER, left.left, MathNode.Number(newExponent))
        }

        return MathNode.BinaryOp(Operator.POWER, left, right)
    }

    /**
     * 展开化简（完全展开）
     *
     * 展开所有括号，合并所有同类项
     *
     * @param node 待化简的AST节点
     * @return 化简后的AST节点
     */
    private fun simplifyExpanded(node: MathNode): MathNode {
        var current = node
        var previous: MathNode
        var iterations = 0
        val maxIterations = 20

        do {
            previous = current
            current = simplifyExpandedOnce(current)
            iterations++
        } while (current != previous && iterations < maxIterations)

        return current
    }

    /**
     * 展开化简单轮迭代
     */
    private fun simplifyExpandedOnce(node: MathNode): MathNode {
        return when (node) {
            is MathNode.Number -> node
            is MathNode.Variable -> node
            is MathNode.Function -> {
                MathNode.Function(node.name, simplifyExpandedOnce(node.argument))
            }
            is MathNode.BinaryOp -> {
                val left = simplifyExpandedOnce(node.left)
                val right = simplifyExpandedOnce(node.right)
                simplifyBinaryOpExpanded(node.operator, left, right)
            }
        }
    }

    /**
     * 二元运算符化简（展开模式）
     */
    private fun simplifyBinaryOpExpanded(
        op: Operator,
        left: MathNode,
        right: MathNode
    ): MathNode {
        return when (op) {
            Operator.ADD -> simplifyAdditionExpanded(left, right)
            Operator.SUBTRACT -> simplifySubtractionExpanded(left, right)
            Operator.MULTIPLY -> simplifyMultiplicationExpanded(left, right)
            Operator.DIVIDE -> simplifyDivisionExpanded(left, right)
            Operator.POWER -> simplifyPowerFactored(left, right)
        }
    }

    /**
     * 加法化简（展开模式）
     *
     * 收集所有加法项并合并同类项
     */
    private fun simplifyAdditionExpanded(left: MathNode, right: MathNode): MathNode {
        val allTerms = collectAdditionTerms(left) + collectAdditionTerms(right)
        val mergedTerms = mergeTerms(allTerms)

        if (mergedTerms.size == 1) {
            return mergedTerms[0]
        }

        return buildAddition(mergedTerms)
    }

    /**
     * 收集所有加法项（展平嵌套结构）
     */
    private fun collectAdditionTerms(node: MathNode): List<MathNode> {
        return when (node) {
            is MathNode.BinaryOp -> {
                if (node.operator == Operator.ADD) {
                    collectAdditionTerms(node.left) + collectAdditionTerms(node.right)
                } else {
                    listOf(node)
                }
            }
            else -> listOf(node)
        }
    }

    /**
     * 合并同类项
     */
    private fun mergeTerms(terms: List<MathNode>): List<MathNode> {
        if (terms.isEmpty()) return listOf(MathNode.Number(0.0))
        if (terms.size == 1) return terms

        val termsByBase = mutableMapOf<String, MutableList<MathNode>>()

        for (term in terms) {
            val base = extractBase(term)
            val baseKey = base.toString()

            if (!termsByBase.containsKey(baseKey)) {
                termsByBase[baseKey] = mutableListOf()
            }
            termsByBase[baseKey]!!.add(term)
        }

        val result = mutableListOf<MathNode>()

        for ((_, groupTerms) in termsByBase) {
            val base = extractBase(groupTerms[0])
            val totalCoefficient = groupTerms.sumOf { extractCoefficient(it) }

            when {
                abs(totalCoefficient) < EPSILON -> {
                }
                base is MathNode.Number && abs(base.value - 1.0) < EPSILON -> {
                    result.add(MathNode.Number(totalCoefficient))
                }
                abs(totalCoefficient - 1.0) < EPSILON -> {
                    result.add(base)
                }
                else -> {
                    result.add(MathNode.BinaryOp(Operator.MULTIPLY, MathNode.Number(totalCoefficient), base))
                }
            }
        }

        return if (result.isEmpty()) listOf(MathNode.Number(0.0)) else result
    }

    /**
     * 构建加法表达式
     */
    private fun buildAddition(terms: List<MathNode>): MathNode {
        if (terms.isEmpty()) return MathNode.Number(0.0)
        if (terms.size == 1) return terms[0]

        var result = terms[0]
        for (i in 1 until terms.size) {
            result = MathNode.BinaryOp(Operator.ADD, result, terms[i])
        }
        return result
    }

    /**
     * 减法化简（展开模式）
     */
    private fun simplifySubtractionExpanded(left: MathNode, right: MathNode): MathNode {
        if (right is MathNode.Number && abs(right.value) < EPSILON) return left

        if (left is MathNode.Number && abs(left.value) < EPSILON) {
            return MathNode.BinaryOp(Operator.MULTIPLY, MathNode.Number(-1.0), right)
        }

        if (left is MathNode.Number && right is MathNode.Number) {
            return MathNode.Number(left.value - right.value)
        }

        if (left == right) {
            return MathNode.Number(0.0)
        }

        val negativeRight = MathNode.BinaryOp(Operator.MULTIPLY, MathNode.Number(-1.0), right)
        return simplifyAdditionExpanded(left, negativeRight)
    }

    /**
     * 乘法化简（展开模式）
     */
    private fun simplifyMultiplicationExpanded(left: MathNode, right: MathNode): MathNode {
        if (left is MathNode.Number && abs(left.value) < EPSILON) return MathNode.Number(0.0)
        if (right is MathNode.Number && abs(right.value) < EPSILON) return MathNode.Number(0.0)

        if (left is MathNode.Number && abs(left.value - 1.0) < EPSILON) return right
        if (right is MathNode.Number && abs(right.value - 1.0) < EPSILON) return left

        if (left is MathNode.Number && abs(left.value + 1.0) < EPSILON) {
            return MathNode.BinaryOp(Operator.SUBTRACT, MathNode.Number(0.0), right)
        }
        if (right is MathNode.Number && abs(right.value + 1.0) < EPSILON) {
            return MathNode.BinaryOp(Operator.SUBTRACT, MathNode.Number(0.0), left)
        }

        if (left is MathNode.Number && isAdditionOrSubtraction(right)) {
            return expandDistributiveExpanded(left, right)
        }
        if (right is MathNode.Number && isAdditionOrSubtraction(left)) {
            return expandDistributiveExpanded(right, left)
        }

        val allFactors = collectMultiplicationFactors(left) + collectMultiplicationFactors(right)
        val numberFactors = allFactors.filterIsInstance<MathNode.Number>()
        val otherFactors = allFactors.filter { it !is MathNode.Number }

        val coefficient = if (numberFactors.isEmpty()) {
            1.0
        } else {
            numberFactors.map { it.value }.reduce { acc, d -> acc * d }
        }

        val mergedFactors = mergePowerFactors(otherFactors)

        return when {
            abs(coefficient) < EPSILON -> MathNode.Number(0.0)

            abs(coefficient - 1.0) < EPSILON -> {
                when {
                    mergedFactors.isEmpty() -> MathNode.Number(1.0)
                    mergedFactors.size == 1 -> mergedFactors[0]
                    else -> buildMultiplication(mergedFactors)
                }
            }

            abs(coefficient + 1.0) < EPSILON -> {
                val product = when {
                    mergedFactors.isEmpty() -> MathNode.Number(1.0)
                    mergedFactors.size == 1 -> mergedFactors[0]
                    else -> buildMultiplication(mergedFactors)
                }
                MathNode.BinaryOp(Operator.SUBTRACT, MathNode.Number(0.0), product)
            }

            mergedFactors.isEmpty() -> MathNode.Number(coefficient)

            else -> {
                val product = if (mergedFactors.size == 1) mergedFactors[0]
                             else buildMultiplication(mergedFactors)

                if (abs(coefficient - 1.0) < EPSILON) {
                    product
                } else if (abs(coefficient + 1.0) < EPSILON) {
                    MathNode.BinaryOp(Operator.SUBTRACT, MathNode.Number(0.0), product)
                } else {
                    MathNode.BinaryOp(Operator.MULTIPLY, MathNode.Number(coefficient), product)
                }
            }
        }
    }

    /**
     * 展开分配律（展开模式）
     */
    private fun expandDistributiveExpanded(
        coefficient: MathNode.Number,
        expr: MathNode
    ): MathNode {
        if (expr !is MathNode.BinaryOp) {
            return MathNode.BinaryOp(Operator.MULTIPLY, coefficient, expr)
        }

        return when (expr.operator) {
            Operator.ADD -> {
                val left = simplifyMultiplicationExpanded(coefficient, expr.left)
                val right = simplifyMultiplicationExpanded(coefficient, expr.right)
                simplifyAdditionExpanded(left, right)
            }
            Operator.SUBTRACT -> {
                val left = simplifyMultiplicationExpanded(coefficient, expr.left)
                val right = simplifyMultiplicationExpanded(coefficient, expr.right)
                simplifySubtractionExpanded(left, right)
            }
            else -> MathNode.BinaryOp(Operator.MULTIPLY, coefficient, expr)
        }
    }

    /**
     * 除法化简（展开模式）
     */
    private fun simplifyDivisionExpanded(left: MathNode, right: MathNode): MathNode {
        Log.d("Simplifier", "简化除法: $left / $right")

        if (right is MathNode.Number && abs(right.value - 1.0) < EPSILON) {
            Log.d("Simplifier", "  规则: x/1 = x")
            return left
        }

        if (left is MathNode.Number && abs(left.value) < EPSILON) {
            Log.d("Simplifier", "  规则: 0/x = 0")
            return MathNode.Number(0.0)
        }

        if (left is MathNode.Number && right is MathNode.Number) {
            Log.d("Simplifier", "  规则: 数字相除")
            return MathNode.Number(left.value / right.value)
        }

        if (left == right) {
            Log.d("Simplifier", "  规则: x/x = 1")
            return MathNode.Number(1.0)
        }

        val simplified = simplifyFraction(left, right)
        if (simplified != null) {
            Log.d("Simplifier", "  约分: $left/$right → $simplified")
            return simplified
        }

        Log.d("Simplifier", "  无简化，保持除法")
        return MathNode.BinaryOp(Operator.DIVIDE, left, right)
    }

    /**
     * 分数约分
     */
    private fun simplifyFraction(numerator: MathNode, denominator: MathNode): MathNode? {
        val numCoeff = extractCoefficient(numerator)
        val denCoeff = extractCoefficient(denominator)
        val numBase = extractBase(numerator)
        val denBase = extractBase(denominator)

        Log.d("Simplifier", "  分子系数: $numCoeff, 底数: $numBase")
        Log.d("Simplifier", "  分母系数: $denCoeff, 底数: $denBase")

        val coeffGcd = gcd(abs(numCoeff), abs(denCoeff))
        val newNumCoeff = numCoeff / coeffGcd
        val newDenCoeff = denCoeff / coeffGcd

        Log.d("Simplifier", "  GCD: $coeffGcd")
        Log.d("Simplifier", "  约分后系数: $newNumCoeff / $newDenCoeff")

        val (numPowerBase, numExponent) = extractBaseAndExponent(numBase)
        val (denPowerBase, denExponent) = extractBaseAndExponent(denBase)

        val basesEqual = numPowerBase.toString() == denPowerBase.toString()

        return when {
            basesEqual -> {
                val resultExponent = numExponent - denExponent

                when {
                    abs(resultExponent) < EPSILON -> {
                        if (abs(newDenCoeff - 1.0) < EPSILON) {
                            MathNode.Number(newNumCoeff)
                        } else {
                            MathNode.BinaryOp(
                                Operator.DIVIDE,
                                MathNode.Number(newNumCoeff),
                                MathNode.Number(newDenCoeff)
                            )
                        }
                    }

                    resultExponent > 0.0 -> {
                        val resultBase = if (abs(resultExponent - 1.0) < EPSILON) {
                            numPowerBase
                        } else {
                            MathNode.BinaryOp(Operator.POWER, numPowerBase, MathNode.Number(resultExponent))
                        }

                        val numeratorPart = if (abs(newNumCoeff - 1.0) < EPSILON) {
                            resultBase
                        } else {
                            MathNode.BinaryOp(Operator.MULTIPLY, MathNode.Number(newNumCoeff), resultBase)
                        }

                        if (abs(newDenCoeff - 1.0) < EPSILON) {
                            numeratorPart
                        } else {
                            MathNode.BinaryOp(Operator.DIVIDE, numeratorPart, MathNode.Number(newDenCoeff))
                        }
                    }

                    else -> {
                        val resultBase = if (abs(resultExponent + 1.0) < EPSILON) {
                            numPowerBase
                        } else {
                            MathNode.BinaryOp(Operator.POWER, numPowerBase, MathNode.Number(-resultExponent))
                        }

                        val denominatorPart = if (abs(newDenCoeff - 1.0) < EPSILON) {
                            resultBase
                        } else {
                            MathNode.BinaryOp(Operator.MULTIPLY, MathNode.Number(newDenCoeff), resultBase)
                        }

                        MathNode.BinaryOp(
                            Operator.DIVIDE,
                            MathNode.Number(newNumCoeff),
                            denominatorPart
                        )
                    }
                }
            }

            coeffGcd > 1.0 -> {
                val newNumerator = if (abs(newNumCoeff - 1.0) < EPSILON) {
                    numBase
                } else {
                    MathNode.BinaryOp(Operator.MULTIPLY, MathNode.Number(newNumCoeff), numBase)
                }

                val newDenominator = if (abs(newDenCoeff - 1.0) < EPSILON) {
                    denBase
                } else {
                    MathNode.BinaryOp(Operator.MULTIPLY, MathNode.Number(newDenCoeff), denBase)
                }

                MathNode.BinaryOp(Operator.DIVIDE, newNumerator, newDenominator)
            }

            else -> null
        }
    }

    /**
     * 计算最大公约数（GCD）
     */
    private fun gcd(a: Double, b: Double): Double {
        val aInt = round(a).toLong()
        val bInt = round(b).toLong()

        if (aInt == 0L) return bInt.toDouble()
        if (bInt == 0L) return aInt.toDouble()

        var x = abs(aInt)
        var y = abs(bInt)

        while (y != 0L) {
            val temp = y
            y = x % y
            x = temp
        }

        return x.toDouble()
    }

    /**
     * 合并相同底数的幂
     */
    private fun mergePowerFactors(factors: List<MathNode>): List<MathNode> {
        if (factors.isEmpty()) return factors

        val baseExponents = mutableMapOf<String, Double>()
        val nonPowerFactors = mutableListOf<MathNode>()

        for (factor in factors) {
            val (base, exponent) = extractBaseAndExponent(factor)
            val baseKey = base.toString()

            if (base is MathNode.Number && abs(base.value - 1.0) < EPSILON) {
                continue
            }

            if (canMergePower(base)) {
                baseExponents[baseKey] = (baseExponents[baseKey] ?: 0.0) + exponent
            } else {
                nonPowerFactors.add(factor)
            }
        }

        val result = mutableListOf<MathNode>()

        for ((baseKey, totalExponent) in baseExponents) {
            if (abs(totalExponent) < EPSILON) continue

            val base = factors.firstOrNull {
                extractBaseAndExponent(it).first.toString() == baseKey
            }?.let { extractBaseAndExponent(it).first } ?: continue

            result.add(when {
                abs(totalExponent - 1.0) < EPSILON -> base
                else -> MathNode.BinaryOp(Operator.POWER, base, MathNode.Number(totalExponent))
            })
        }

        result.addAll(nonPowerFactors)
        return result
    }

    /**
     * 提取底数和指数
     */
    private fun extractBaseAndExponent(node: MathNode): Pair<MathNode, Double> {
        return when (node) {
            is MathNode.BinaryOp -> {
                if (node.operator == Operator.POWER && node.right is MathNode.Number) {
                    Pair(node.left, node.right.value)
                } else {
                    Pair(node, 1.0)
                }
            }
            else -> Pair(node, 1.0)
        }
    }

    /**
     * 判断是否可以合并幂
     */
    private fun canMergePower(base: MathNode): Boolean {
        return when (base) {
            is MathNode.Variable -> true
            is MathNode.Function -> true
            else -> false
        }
    }

    /**
     * 提取系数
     */
    private fun extractCoefficient(node: MathNode): Double {
        return when (node) {
            is MathNode.Number -> node.value
            is MathNode.Variable -> 1.0
            is MathNode.BinaryOp -> {
                if (node.operator == Operator.MULTIPLY && node.left is MathNode.Number) {
                    node.left.value
                } else {
                    1.0
                }
            }
            else -> 1.0
        }
    }

    /**
     * 提取底数
     */
    private fun extractBase(node: MathNode): MathNode {
        return when (node) {
            is MathNode.Number -> MathNode.Number(1.0)
            is MathNode.Variable -> node
            is MathNode.BinaryOp -> {
                if (node.operator == Operator.MULTIPLY && node.left is MathNode.Number) {
                    node.right
                } else {
                    node
                }
            }
            else -> node
        }
    }

    /**
     * 收集所有乘法因子
     */
    private fun collectMultiplicationFactors(node: MathNode): List<MathNode> {
        return when (node) {
            is MathNode.BinaryOp -> {
                if (node.operator == Operator.MULTIPLY) {
                    collectMultiplicationFactors(node.left) +
                    collectMultiplicationFactors(node.right)
                } else {
                    listOf(node)
                }
            }
            else -> listOf(node)
        }
    }

    /**
     * 构建乘法表达式
     */
    private fun buildMultiplication(factors: List<MathNode>): MathNode {
        if (factors.isEmpty()) return MathNode.Number(1.0)
        if (factors.size == 1) return factors[0]

        var result = factors[0]
        for (i in 1 until factors.size) {
            result = MathNode.BinaryOp(Operator.MULTIPLY, result, factors[i])
        }
        return result
    }
}