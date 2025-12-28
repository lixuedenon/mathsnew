// app/src/main/java/com/mathsnew/mathsnew/ExpressionSimplifier.kt
// 表达式简化器（多形式版本 - 修复分配律展开）

package com.mathsnew.mathsnew

import android.util.Log
import com.mathsnew.mathsnew.newsimplified.SimplificationFormsV2
import com.mathsnew.mathsnew.newsimplified.SimplifiedForm
import com.mathsnew.mathsnew.newsimplified.SimplificationType
import kotlin.math.abs
import kotlin.math.round

class ExpressionSimplifier {

    companion object {
        private const val EPSILON = 1e-10
        private const val TAG = "SimplifierDebug"
    }

    fun simplifyToMultipleForms(node: MathNode): SimplificationFormsV2 {
        Log.d(TAG, "========================================")
        Log.d(TAG, "simplifyToMultipleForms 开始")
        Log.d(TAG, "输入表达式: $node")

        val forms = mutableListOf<SimplifiedForm>()

        val factored = simplifyFactored(node)
        Log.d(TAG, "FACTORED 结果: $factored")
        forms.add(SimplifiedForm(
            expression = factored,
            type = SimplificationType.FACTORED
        ))

        val expanded = simplifyExpanded(node)
        Log.d(TAG, "EXPANDED 结果: $expanded")
        forms.add(SimplifiedForm(
            expression = expanded,
            type = SimplificationType.EXPANDED
        ))

        val standard = simplifyStandard(node)
        Log.d(TAG, "STANDARD 结果: $standard")
        forms.add(SimplifiedForm(
            expression = standard,
            type = SimplificationType.STRUCTURAL
        ))

        Log.d(TAG, "========================================")
        return SimplificationFormsV2(forms)
    }

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

    private fun simplifyAdditionFactored(left: MathNode, right: MathNode): MathNode {
        if (left is MathNode.Number && abs(left.value) < EPSILON) return right
        if (right is MathNode.Number && abs(right.value) < EPSILON) return left

        if (left is MathNode.Number && right is MathNode.Number) {
            return MathNode.Number(left.value + right.value)
        }

        if (left == right) {
            return MathNode.BinaryOp(Operator.MULTIPLY, MathNode.Number(2.0), left)
        }

        val (leftCoeff, leftBase) = extractAllCoefficients(left)
        val (rightCoeff, rightBase) = extractAllCoefficients(right)

        val leftKey = computeBaseKey(leftBase)
        val rightKey = computeBaseKey(rightBase)

        if (leftKey == rightKey) {
            val newCoeff = leftCoeff + rightCoeff
            return when {
                abs(newCoeff) < EPSILON -> MathNode.Number(0.0)
                abs(newCoeff - 1.0) < EPSILON -> leftBase
                else -> MathNode.BinaryOp(Operator.MULTIPLY, MathNode.Number(newCoeff), leftBase)
            }
        }

        return MathNode.BinaryOp(Operator.ADD, left, right)
    }

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

        val (leftCoeff, leftBase) = extractAllCoefficients(left)
        val (rightCoeff, rightBase) = extractAllCoefficients(right)

        val leftKey = computeBaseKey(leftBase)
        val rightKey = computeBaseKey(rightBase)

        if (leftKey == rightKey) {
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

    private fun isAdditionOrSubtraction(node: MathNode): Boolean {
        return node is MathNode.BinaryOp &&
               (node.operator == Operator.ADD || node.operator == Operator.SUBTRACT)
    }

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

    private fun simplifyExpanded(node: MathNode): MathNode {
        Log.d(TAG, "[EXPANDED] 开始化简: $node")

        var current = node
        var previous: MathNode
        var iterations = 0
        val maxIterations = 20

        do {
            previous = current
            current = simplifyExpandedOnce(current)
            iterations++
            Log.d(TAG, "[EXPANDED] 迭代 $iterations: $current")
        } while (current != previous && iterations < maxIterations)

        Log.d(TAG, "[EXPANDED] 最终结果: $current")
        return current
    }

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

    private fun simplifyAdditionExpanded(left: MathNode, right: MathNode): MathNode {
        val allTerms = collectAdditionTerms(left) + collectAdditionTerms(right)

        Log.d(TAG, "[ADD] 收集到 ${allTerms.size} 项")
        allTerms.forEachIndexed { i, term ->
            Log.d(TAG, "[ADD]   项 $i: $term")
        }

        val mergedTerms = mergeTermsImproved(allTerms)

        Log.d(TAG, "[ADD] 合并后 ${mergedTerms.size} 项")
        mergedTerms.forEachIndexed { i, term ->
            Log.d(TAG, "[ADD]   合并项 $i: $term")
        }

        if (mergedTerms.isEmpty()) {
            return MathNode.Number(0.0)
        }

        if (mergedTerms.size == 1) {
            return mergedTerms[0]
        }

        return buildAddition(mergedTerms)
    }

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

    private fun mergeTermsImproved(terms: List<MathNode>): List<MathNode> {
        if (terms.isEmpty()) return listOf(MathNode.Number(0.0))
        if (terms.size == 1) return terms

        Log.d(TAG, "[mergeTerms] 开始合并 ${terms.size} 项")

        val termsByKey = mutableMapOf<String, MutableList<Pair<Double, MathNode>>>()

        for (term in terms) {
            val (coefficient, base) = extractAllCoefficients(term)
            val baseKey = computeBaseKey(base)

            Log.d(TAG, "[mergeTerms] 项: $term")
            Log.d(TAG, "[mergeTerms]   系数: $coefficient")
            Log.d(TAG, "[mergeTerms]   底数: $base")
            Log.d(TAG, "[mergeTerms]   键: $baseKey")

            if (!termsByKey.containsKey(baseKey)) {
                termsByKey[baseKey] = mutableListOf()
            }
            termsByKey[baseKey]!!.add(Pair(coefficient, base))
        }

        Log.d(TAG, "[mergeTerms] 分组数: ${termsByKey.size}")

        val result = mutableListOf<MathNode>()

        for ((key, pairs) in termsByKey) {
            val totalCoefficient = pairs.sumOf { it.first }
            val base = pairs.first().second

            Log.d(TAG, "[mergeTerms] 键 $key: ${pairs.size} 项, 总系数 $totalCoefficient")

            when {
                abs(totalCoefficient) < EPSILON -> {
                    Log.d(TAG, "[mergeTerms]   -> 跳过(系数为0)")
                }
                key == "CONST" -> {
                    Log.d(TAG, "[mergeTerms]   -> 常数: $totalCoefficient")
                    result.add(MathNode.Number(totalCoefficient))
                }
                abs(totalCoefficient - 1.0) < EPSILON -> {
                    Log.d(TAG, "[mergeTerms]   -> 1×底数: $base")
                    result.add(base)
                }
                else -> {
                    Log.d(TAG, "[mergeTerms]   -> $totalCoefficient×底数")
                    result.add(MathNode.BinaryOp(Operator.MULTIPLY, MathNode.Number(totalCoefficient), base))
                }
            }
        }

        return if (result.isEmpty()) listOf(MathNode.Number(0.0)) else result
    }

    private fun computeBaseKey(node: MathNode): String {
        return when (node) {
            is MathNode.Number -> "CONST"

            is MathNode.Variable -> "VAR:${node.name}"

            is MathNode.BinaryOp -> {
                when (node.operator) {
                    Operator.POWER -> {
                        val baseKey = computeBaseKey(node.left)
                        val expKey = when (node.right) {
                            is MathNode.Number -> "CONST:${node.right.value}"
                            else -> computeBaseKey(node.right)
                        }
                        "POW:$baseKey:$expKey"
                    }
                    Operator.MULTIPLY -> {
                        val leftKey = computeBaseKey(node.left)
                        val rightKey = computeBaseKey(node.right)
                        if (leftKey < rightKey) {
                            "MUL:$leftKey:$rightKey"
                        } else {
                            "MUL:$rightKey:$leftKey"
                        }
                    }
                    Operator.DIVIDE -> {
                        val numKey = computeBaseKey(node.left)
                        val denKey = computeBaseKey(node.right)
                        "DIV:$numKey:$denKey"
                    }
                    Operator.ADD -> {
                        val leftKey = computeBaseKey(node.left)
                        val rightKey = computeBaseKey(node.right)
                        "ADD:$leftKey:$rightKey"
                    }
                    Operator.SUBTRACT -> {
                        val leftKey = computeBaseKey(node.left)
                        val rightKey = computeBaseKey(node.right)
                        "SUB:$leftKey:$rightKey"
                    }
                }
            }

            is MathNode.Function -> {
                val argKey = computeBaseKey(node.argument)
                "FUNC:${node.name}:$argKey"
            }
        }
    }

    private fun extractAllCoefficients(node: MathNode): Pair<Double, MathNode> {
        return when (node) {
            is MathNode.Number -> Pair(node.value, MathNode.Number(1.0))

            is MathNode.Variable -> Pair(1.0, node)

            is MathNode.BinaryOp -> {
                if (node.operator == Operator.MULTIPLY) {
                    val (leftCoeff, leftBase) = extractAllCoefficients(node.left)
                    val (rightCoeff, rightBase) = extractAllCoefficients(node.right)

                    val totalCoeff = leftCoeff * rightCoeff

                    val base = when {
                        leftBase is MathNode.Number && abs(leftBase.value - 1.0) < EPSILON -> rightBase
                        rightBase is MathNode.Number && abs(rightBase.value - 1.0) < EPSILON -> leftBase
                        else -> MathNode.BinaryOp(Operator.MULTIPLY, leftBase, rightBase)
                    }

                    Pair(totalCoeff, base)
                } else {
                    Pair(1.0, node)
                }
            }

            is MathNode.Function -> Pair(1.0, node)
        }
    }

    private fun buildAddition(terms: List<MathNode>): MathNode {
        if (terms.isEmpty()) return MathNode.Number(0.0)
        if (terms.size == 1) return terms[0]

        var result = terms[0]
        for (i in 1 until terms.size) {
            result = MathNode.BinaryOp(Operator.ADD, result, terms[i])
        }
        return result
    }

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
        val expandedNegativeRight = simplifyExpandedOnce(negativeRight)
        return simplifyAdditionExpanded(left, expandedNegativeRight)
    }

    private fun simplifyMultiplicationExpanded(left: MathNode, right: MathNode): MathNode {
        Log.d(TAG, "[MUL] 乘法化简: $left × $right")

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

        if (isAdditionOrSubtraction(right)) {
            Log.d(TAG, "[MUL] 展开分配律: $left × (加减法)")
            return distributeMultiplication(left, right)
        }
        if (isAdditionOrSubtraction(left)) {
            Log.d(TAG, "[MUL] 展开分配律: (加减法) × $right")
            return distributeMultiplication(right, left)
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

    private fun distributeMultiplication(factor: MathNode, addSubExpr: MathNode): MathNode {
        Log.d(TAG, "[DIST] 分配: $factor × $addSubExpr")

        if (addSubExpr !is MathNode.BinaryOp) {
            return MathNode.BinaryOp(Operator.MULTIPLY, factor, addSubExpr)
        }

        return when (addSubExpr.operator) {
            Operator.ADD -> {
                val left = simplifyMultiplicationExpanded(factor, addSubExpr.left)
                val right = simplifyMultiplicationExpanded(factor, addSubExpr.right)
                Log.d(TAG, "[DIST] 加法展开: $left + $right")
                simplifyAdditionExpanded(left, right)
            }
            Operator.SUBTRACT -> {
                val left = simplifyMultiplicationExpanded(factor, addSubExpr.left)
                val right = simplifyMultiplicationExpanded(factor, addSubExpr.right)
                Log.d(TAG, "[DIST] 减法展开: $left - $right")
                simplifySubtractionExpanded(left, right)
            }
            else -> MathNode.BinaryOp(Operator.MULTIPLY, factor, addSubExpr)
        }
    }

    private fun simplifyDivisionExpanded(left: MathNode, right: MathNode): MathNode {
        Log.d(TAG, "[DIV] 除法化简: $left / $right")

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

        val expandedNumerator = fullyExpandNumerator(left)
        val expandedDenominator = fullyExpandNumerator(right)

        Log.d(TAG, "[DIV] 展开后分子: $expandedNumerator")
        Log.d(TAG, "[DIV] 展开后分母: $expandedDenominator")

        val simplified = simplifyFraction(expandedNumerator, expandedDenominator)
        if (simplified != null) {
            Log.d(TAG, "[DIV] 约分结果: $simplified")
            return simplified
        }

        return MathNode.BinaryOp(Operator.DIVIDE, expandedNumerator, expandedDenominator)
    }

    private fun fullyExpandNumerator(node: MathNode): MathNode {
        var current = node
        var previous: MathNode
        var iterations = 0
        val maxIterations = 10

        do {
            previous = current
            current = expandOnce(current)
            iterations++
        } while (current != previous && iterations < maxIterations)

        return current
    }

    private fun expandOnce(node: MathNode): MathNode {
        return when (node) {
            is MathNode.Number -> node
            is MathNode.Variable -> node
            is MathNode.Function -> {
                MathNode.Function(node.name, expandOnce(node.argument))
            }
            is MathNode.BinaryOp -> {
                when (node.operator) {
                    Operator.ADD, Operator.SUBTRACT -> {
                        val left = expandOnce(node.left)
                        val right = expandOnce(node.right)
                        if (node.operator == Operator.ADD) {
                            simplifyAdditionExpanded(left, right)
                        } else {
                            simplifySubtractionExpanded(left, right)
                        }
                    }
                    Operator.MULTIPLY -> {
                        val left = expandOnce(node.left)
                        val right = expandOnce(node.right)
                        simplifyMultiplicationExpanded(left, right)
                    }
                    else -> {
                        val left = expandOnce(node.left)
                        val right = expandOnce(node.right)
                        MathNode.BinaryOp(node.operator, left, right)
                    }
                }
            }
        }
    }

    private fun simplifyFraction(numerator: MathNode, denominator: MathNode): MathNode? {
        val (numCoeff, numBase) = extractAllCoefficients(numerator)
        val (denCoeff, denBase) = extractAllCoefficients(denominator)

        val coeffGcd = gcd(abs(numCoeff), abs(denCoeff))
        val newNumCoeff = numCoeff / coeffGcd
        val newDenCoeff = denCoeff / coeffGcd

        val (numPowerBase, numExponent) = extractBaseAndExponent(numBase)
        val (denPowerBase, denExponent) = extractBaseAndExponent(denBase)

        val numKey = computeBaseKey(numPowerBase)
        val denKey = computeBaseKey(denPowerBase)
        val basesEqual = numKey == denKey

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

            coeffGcd > 1.0 + EPSILON -> {
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

    private fun simplifyStandard(node: MathNode): MathNode {
        return simplifyExpanded(node)
    }

    private fun mergePowerFactors(factors: List<MathNode>): List<MathNode> {
        if (factors.isEmpty()) return factors

        val baseExponents = mutableMapOf<String, Double>()
        val baseNodes = mutableMapOf<String, MathNode>()
        val nonPowerFactors = mutableListOf<MathNode>()

        for (factor in factors) {
            val (base, exponent) = extractBaseAndExponent(factor)
            val baseKey = computeBaseKey(base)

            if (base is MathNode.Number && abs(base.value - 1.0) < EPSILON) {
                continue
            }

            if (canMergePower(base)) {
                baseExponents[baseKey] = (baseExponents[baseKey] ?: 0.0) + exponent
                if (!baseNodes.containsKey(baseKey)) {
                    baseNodes[baseKey] = base
                }
            } else {
                nonPowerFactors.add(factor)
            }
        }

        val result = mutableListOf<MathNode>()

        for ((key, totalExponent) in baseExponents) {
            if (abs(totalExponent) < EPSILON) continue

            val base = baseNodes[key] ?: continue

            result.add(when {
                abs(totalExponent - 1.0) < EPSILON -> base
                else -> MathNode.BinaryOp(Operator.POWER, base, MathNode.Number(totalExponent))
            })
        }

        result.addAll(nonPowerFactors)
        return result
    }

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

    private fun canMergePower(base: MathNode): Boolean {
        return when (base) {
            is MathNode.Variable -> true
            is MathNode.Function -> true
            else -> false
        }
    }

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