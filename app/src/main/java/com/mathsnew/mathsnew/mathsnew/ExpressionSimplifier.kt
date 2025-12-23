// app/src/main/java/com/mathsnew/mathsnew/ExpressionSimplifier.kt
// 表达式简化器（完整版：同类项合并 + 分数约分 + 幂合并）

package com.mathsnew.mathsnew

import android.util.Log
import kotlin.math.abs
import kotlin.math.round

/**
 * 表达式简化器
 *
 * 化简原则：
 * ✅ 消除冗余（0+x, 1×x）
 * ✅ 合并纯数字运算
 * ✅ 标准化负数表示
 * ✅ 展开嵌套乘法并合并数字系数
 * ✅ 合并同类项（6x + 6x → 12x）
 * ✅ 分数约分（6x/4 → 3x/2）
 * ✅ 幂的合并（x × x → x²）
 * ✅ 双重负号消除（-(-x) → x）
 * ❌ 不展开分配律
 * ❌ 不因式分解
 */
class ExpressionSimplifier {

    fun simplify(node: MathNode): MathNode {
        var current = node
        var previous: MathNode
        var iterations = 0
        val maxIterations = 15  // 增加迭代次数

        do {
            previous = current
            current = simplifyOnce(current)
            iterations++
        } while (current != previous && iterations < maxIterations)

        return current
    }

    private fun simplifyOnce(node: MathNode): MathNode {
        return when (node) {
            is MathNode.Number -> node
            is MathNode.Variable -> node
            is MathNode.Function -> {
                MathNode.Function(node.name, simplifyOnce(node.argument))
            }
            is MathNode.BinaryOp -> {
                val left = simplifyOnce(node.left)
                val right = simplifyOnce(node.right)
                simplifyBinaryOp(node.operator, left, right)
            }
        }
    }

    private fun simplifyBinaryOp(op: Operator, left: MathNode, right: MathNode): MathNode {
        return when (op) {
            Operator.ADD -> simplifyAddition(left, right)
            Operator.SUBTRACT -> simplifySubtraction(left, right)
            Operator.MULTIPLY -> simplifyMultiplication(left, right)
            Operator.DIVIDE -> simplifyDivision(left, right)
            Operator.POWER -> simplifyPower(left, right)
        }
    }

    /**
     * 加法化简（支持同类项合并）
     */
    private fun simplifyAddition(left: MathNode, right: MathNode): MathNode {
        // 0 + x = x
        if (left is MathNode.Number && left.value == 0.0) return right
        if (right is MathNode.Number && right.value == 0.0) return left

        // 数字相加
        if (left is MathNode.Number && right is MathNode.Number) {
            return MathNode.Number(left.value + right.value)
        }

        // 同类项合并
        val leftCoeff = extractCoefficient(left)
        val rightCoeff = extractCoefficient(right)
        val leftBase = extractBase(left)
        val rightBase = extractBase(right)

        if (areEqualBases(leftBase, rightBase)) {
            val newCoeff = leftCoeff + rightCoeff
            return when {
                newCoeff == 0.0 -> MathNode.Number(0.0)
                newCoeff == 1.0 -> leftBase
                else -> MathNode.BinaryOp(Operator.MULTIPLY, MathNode.Number(newCoeff), leftBase)
            }
        }

        return MathNode.BinaryOp(Operator.ADD, left, right)
    }

    /**
     * 减法化简（支持同类项合并）
     */
    private fun simplifySubtraction(left: MathNode, right: MathNode): MathNode {
        // x - 0 = x
        if (right is MathNode.Number && right.value == 0.0) return left

        // 0 - x = -x
        if (left is MathNode.Number && left.value == 0.0) {
            return MathNode.BinaryOp(
                Operator.MULTIPLY,
                MathNode.Number(-1.0),
                right
            )
        }

        // 数字相减
        if (left is MathNode.Number && right is MathNode.Number) {
            return MathNode.Number(left.value - right.value)
        }

        // x - x = 0
        if (left == right) {
            return MathNode.Number(0.0)
        }

        // 双重负号：x - (-y) = x + y
        if (right is MathNode.BinaryOp &&
            right.operator == Operator.MULTIPLY &&
            right.left is MathNode.Number &&
            right.left.value == -1.0) {
            return simplifyAddition(left, right.right)
        }

        // 同类项相减
        val leftCoeff = extractCoefficient(left)
        val rightCoeff = extractCoefficient(right)
        val leftBase = extractBase(left)
        val rightBase = extractBase(right)

        if (areEqualBases(leftBase, rightBase)) {
            val newCoeff = leftCoeff - rightCoeff
            return when {
                newCoeff == 0.0 -> MathNode.Number(0.0)
                newCoeff == 1.0 -> leftBase
                newCoeff == -1.0 -> MathNode.BinaryOp(
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
     * 乘法化简（增强版：支持幂的合并）
     */
    private fun simplifyMultiplication(left: MathNode, right: MathNode): MathNode {
        // 0 × x = 0
        if (left is MathNode.Number && left.value == 0.0) return MathNode.Number(0.0)
        if (right is MathNode.Number && right.value == 0.0) return MathNode.Number(0.0)

        // 1 × x = x
        if (left is MathNode.Number && left.value == 1.0) return right
        if (right is MathNode.Number && right.value == 1.0) return left

        // 双重负号：(-1) × (-1) × x = x
        // 这个由数字合并自动处理

        // 展开嵌套乘法，收集所有因子
        val allFactors = collectMultiplicationFactors(left) + collectMultiplicationFactors(right)

        // 分离数字因子和非数字因子
        val numberFactors = mutableListOf<Double>()
        val otherFactors = mutableListOf<MathNode>()

        for (factor in allFactors) {
            if (factor is MathNode.Number) {
                numberFactors.add(factor.value)
            } else {
                otherFactors.add(factor)
            }
        }

        // 合并所有数字
        val coefficient = if (numberFactors.isEmpty()) {
            1.0
        } else {
            numberFactors.reduce { acc, d -> acc * d }
        }

        // 合并相同底数的幂：x × x → x², x² × x → x³
        val mergedFactors = mergePowerFactors(otherFactors)

        // 根据系数值构建结果
        return when {
            coefficient == 0.0 -> MathNode.Number(0.0)

            coefficient == 1.0 -> {
                when {
                    mergedFactors.isEmpty() -> MathNode.Number(1.0)
                    mergedFactors.size == 1 -> mergedFactors[0]
                    else -> buildMultiplication(mergedFactors)
                }
            }

            coefficient == -1.0 -> {
                val product = when {
                    mergedFactors.isEmpty() -> MathNode.Number(1.0)
                    mergedFactors.size == 1 -> mergedFactors[0]
                    else -> buildMultiplication(mergedFactors)
                }
                MathNode.BinaryOp(Operator.SUBTRACT, MathNode.Number(0.0), product)
            }

            mergedFactors.isEmpty() -> MathNode.Number(coefficient)

            else -> {
                val product = if (mergedFactors.size == 1) {
                    mergedFactors[0]
                } else {
                    buildMultiplication(mergedFactors)
                }
                MathNode.BinaryOp(Operator.MULTIPLY, MathNode.Number(coefficient), product)
            }
        }
    }

    /**
     * 合并相同底数的幂
     *
     * 例如：
     * - [x, x] → [x²]
     * - [x², x] → [x³]
     * - [sin(x), sin(x)] → [sin²(x)]
     */
    private fun mergePowerFactors(factors: List<MathNode>): List<MathNode> {
        if (factors.isEmpty()) return factors

        // 统计每个底数的指数
        val baseExponents = mutableMapOf<String, Double>()
        val nonPowerFactors = mutableListOf<MathNode>()

        for (factor in factors) {
            val (base, exponent) = extractBaseAndExponent(factor)
            val baseKey = base.toString()

            if (base is MathNode.Number && base.value == 1.0) {
                // 底数是1，忽略
                continue
            }

            if (canMergePower(base)) {
                baseExponents[baseKey] = (baseExponents[baseKey] ?: 0.0) + exponent
            } else {
                nonPowerFactors.add(factor)
            }
        }

        // 重建因子列表
        val result = mutableListOf<MathNode>()

        for ((baseKey, totalExponent) in baseExponents) {
            if (totalExponent == 0.0) continue

            // 找到对应的底数
            val base = factors.firstOrNull {
                extractBaseAndExponent(it).first.toString() == baseKey
            }?.let { extractBaseAndExponent(it).first } ?: continue

            result.add(when {
                totalExponent == 1.0 -> base
                else -> MathNode.BinaryOp(Operator.POWER, base, MathNode.Number(totalExponent))
            })
        }

        result.addAll(nonPowerFactors)
        return result
    }

    /**
     * 提取底数和指数
     *
     * 例如：
     * - x → (x, 1)
     * - x² → (x, 2)
     * - sin(x) → (sin(x), 1)
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
     *
     * 只有简单的变量和函数可以合并
     */
    private fun canMergePower(base: MathNode): Boolean {
        return when (base) {
            is MathNode.Variable -> true
            is MathNode.Function -> true
            else -> false
        }
    }

    /**
     * 除法化简（增强版：支持约分）
     */
    private fun simplifyDivision(left: MathNode, right: MathNode): MathNode {
        Log.d("Simplifier", "简化除法: $left / $right")

        // x / 1 = x
        if (right is MathNode.Number && right.value == 1.0) {
            Log.d("Simplifier", "  规则: x/1 = x")
            return left
        }

        // 0 / x = 0
        if (left is MathNode.Number && left.value == 0.0) {
            Log.d("Simplifier", "  规则: 0/x = 0")
            return MathNode.Number(0.0)
        }

        // 数字相除
        if (left is MathNode.Number && right is MathNode.Number) {
            Log.d("Simplifier", "  规则: 数字相除")
            return MathNode.Number(left.value / right.value)
        }

        // x / x = 1
        if (left == right) {
            Log.d("Simplifier", "  规则: x/x = 1")
            return MathNode.Number(1.0)
        }

        // 约分
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
     *
     * 处理以下情况：
     * 1. 系数约分：6x/4 → 3x/2
     * 2. 变量约分：(6x)/(2x) → 3
     * 3. 幂约分：(6x²)/(2x) → 3x
     */
    private fun simplifyFraction(numerator: MathNode, denominator: MathNode): MathNode? {
        // 提取分子分母的系数和底数
        val numCoeff = extractCoefficient(numerator)
        val denCoeff = extractCoefficient(denominator)
        val numBase = extractBase(numerator)
        val denBase = extractBase(denominator)

        Log.d("Simplifier", "  分子系数: $numCoeff, 底数: $numBase")
        Log.d("Simplifier", "  分母系数: $denCoeff, 底数: $denBase")

        // 计算系数的GCD并约分
        val coeffGcd = gcd(abs(numCoeff), abs(denCoeff))
        val newNumCoeff = numCoeff / coeffGcd
        val newDenCoeff = denCoeff / coeffGcd

        Log.d("Simplifier", "  GCD: $coeffGcd")
        Log.d("Simplifier", "  约分后系数: $newNumCoeff / $newDenCoeff")

        // 提取底数的指数
        val (numPowerBase, numExponent) = extractBaseAndExponent(numBase)
        val (denPowerBase, denExponent) = extractBaseAndExponent(denBase)

        // 检查底数是否相同
        val basesEqual = numPowerBase.toString() == denPowerBase.toString()

        return when {
            // 情况1：底数相同，可以约分指数
            basesEqual -> {
                val resultExponent = numExponent - denExponent

                when {
                    // 完全约掉，只剩系数
                    resultExponent == 0.0 -> {
                        if (newDenCoeff == 1.0) {
                            MathNode.Number(newNumCoeff)
                        } else {
                            MathNode.BinaryOp(
                                Operator.DIVIDE,
                                MathNode.Number(newNumCoeff),
                                MathNode.Number(newDenCoeff)
                            )
                        }
                    }

                    // 分子指数更大：结果在分子
                    resultExponent > 0.0 -> {
                        val resultBase = if (resultExponent == 1.0) {
                            numPowerBase
                        } else {
                            MathNode.BinaryOp(Operator.POWER, numPowerBase, MathNode.Number(resultExponent))
                        }

                        val numeratorPart = if (newNumCoeff == 1.0) {
                            resultBase
                        } else {
                            MathNode.BinaryOp(Operator.MULTIPLY, MathNode.Number(newNumCoeff), resultBase)
                        }

                        if (newDenCoeff == 1.0) {
                            numeratorPart
                        } else {
                            MathNode.BinaryOp(Operator.DIVIDE, numeratorPart, MathNode.Number(newDenCoeff))
                        }
                    }

                    // 分母指数更大：结果在分母
                    else -> {
                        val resultBase = if (resultExponent == -1.0) {
                            numPowerBase
                        } else {
                            MathNode.BinaryOp(Operator.POWER, numPowerBase, MathNode.Number(-resultExponent))
                        }

                        val denominatorPart = if (newDenCoeff == 1.0) {
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

            // 情况2：底数不同，只约分系数
            coeffGcd > 1.0 -> {
                val newNumerator = if (newNumCoeff == 1.0) {
                    numBase
                } else {
                    MathNode.BinaryOp(Operator.MULTIPLY, MathNode.Number(newNumCoeff), numBase)
                }

                val newDenominator = if (newDenCoeff == 1.0) {
                    denBase
                } else {
                    MathNode.BinaryOp(Operator.MULTIPLY, MathNode.Number(newDenCoeff), denBase)
                }

                MathNode.BinaryOp(Operator.DIVIDE, newNumerator, newDenominator)
            }

            // 无法约分
            else -> null
        }
    }

    /**
     * 计算最大公约数（GCD）
     * 使用欧几里得算法
     */
    private fun gcd(a: Double, b: Double): Double {
        // 转换为整数处理（假设都是整数或可以转为整数）
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

    private fun simplifyPower(left: MathNode, right: MathNode): MathNode {
        // x⁰ = 1
        if (right is MathNode.Number && right.value == 0.0) {
            return MathNode.Number(1.0)
        }

        // x¹ = x
        if (right is MathNode.Number && right.value == 1.0) {
            return left
        }

        // 1ⁿ = 1
        if (left is MathNode.Number && left.value == 1.0) {
            return MathNode.Number(1.0)
        }

        // 数字的幂
        if (left is MathNode.Number && right is MathNode.Number) {
            return MathNode.Number(Math.pow(left.value, right.value))
        }

        // (xᵃ)ᵇ = xᵃᵇ
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
     * 判断两个底数是否相等
     */
    private fun areEqualBases(base1: MathNode, base2: MathNode): Boolean {
        return base1.toString() == base2.toString()
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