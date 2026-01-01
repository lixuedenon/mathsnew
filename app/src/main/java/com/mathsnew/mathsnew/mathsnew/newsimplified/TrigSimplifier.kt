// app/src/main/java/com/mathsnew/mathsnew/newsimplified/TrigSimplifier.kt
// 三角函数化简引擎

package com.mathsnew.mathsnew.newsimplified

import com.mathsnew.mathsnew.*
import android.util.Log
import kotlin.math.abs

object TrigSimplifier {

    private const val TAG = "TrigSimplifier"
    private const val EPSILON = 1e-10

    fun simplify(node: MathNode): MathNode {
        // 强制日志，确认被调用
        Log.e(TAG, "!!!!! 三角化简被调用 !!!!!")
        Log.d(TAG, "========== 三角化简开始 ==========")
        Log.d(TAG, "输入: $node")

        var result = node
        var previous: MathNode
        var iterations = 0
        val maxIterations = 10

        do {
            previous = result

            result = applyDoubleAngle(result)
            result = applyPythagorean(result)
            result = applyBasicIdentities(result)

            iterations++
            Log.d(TAG, "迭代 $iterations: $result")
        } while (result.toString() != previous.toString() && iterations < maxIterations)

        // ✨ 新增：最终化简数值系数
        val finalResult = simplifyNumericCoefficients(result)

        Log.d(TAG, "最终结果: $finalResult")
        Log.d(TAG, "========== 三角化简完成 ==========")

        return finalResult
    }

    private fun applyDoubleAngle(node: MathNode): MathNode {
        return when (node) {
            is MathNode.Number, is MathNode.Variable -> node

            is MathNode.Function -> {
                MathNode.Function(node.name, applyDoubleAngle(node.argument))
            }

            is MathNode.BinaryOp -> {
                val left = applyDoubleAngle(node.left)
                val right = applyDoubleAngle(node.right)

                val simplified = when (node.operator) {
                    Operator.MULTIPLY -> simplifyDoubleAngleProduct(left, right)
                    Operator.ADD -> simplifyDoubleAngleSum(left, right)
                    Operator.SUBTRACT -> simplifyDoubleAngleDiff(left, right)
                    else -> MathNode.BinaryOp(node.operator, left, right)
                }

                simplified
            }
        }
    }

    private fun simplifyDoubleAngleProduct(left: MathNode, right: MathNode): MathNode {
        val pattern = matchSinCosProduct(left, right)
        if (pattern != null) {
            Log.d(TAG, "匹配到二倍角: ${pattern.coefficient}×sin(${pattern.angle})×cos(${pattern.angle})")

            // 2sin(x)cos(x) = sin(2x)
            // A×sin(x)cos(x) = (A/2)×sin(2x)
            val halfCoeff = pattern.coefficient / 2.0
            val doubleAngle = MathNode.BinaryOp(
                Operator.MULTIPLY,
                MathNode.Number(2.0),
                pattern.angle
            )
            val sin2x = MathNode.Function("sin", doubleAngle)

            return if (abs(halfCoeff - 1.0) < EPSILON) {
                sin2x
            } else {
                MathNode.BinaryOp(
                    Operator.MULTIPLY,
                    MathNode.Number(halfCoeff),
                    sin2x
                )
            }
        }

        return MathNode.BinaryOp(Operator.MULTIPLY, left, right)
    }

    private fun simplifyDoubleAngleSum(left: MathNode, right: MathNode): MathNode {
        return MathNode.BinaryOp(Operator.ADD, left, right)
    }

    private fun simplifyDoubleAngleDiff(left: MathNode, right: MathNode): MathNode {
        // ✨ 新增：匹配 A×cos²(x) - A×sin²(x) → A×cos(2x)
        val pattern = matchCosSquaredMinusSinSquared(left, right)
        if (pattern != null) {
            Log.d(TAG, "匹配到二倍角差: ${pattern.coefficient}×cos²(${pattern.angle}) - ${pattern.coefficient}×sin²(${pattern.angle})")

            val doubleAngle = MathNode.BinaryOp(
                Operator.MULTIPLY,
                MathNode.Number(2.0),
                pattern.angle
            )
            val cos2x = MathNode.Function("cos", doubleAngle)

            return if (abs(pattern.coefficient - 1.0) < EPSILON) {
                cos2x
            } else {
                MathNode.BinaryOp(
                    Operator.MULTIPLY,
                    MathNode.Number(pattern.coefficient),
                    cos2x
                )
            }
        }

        return MathNode.BinaryOp(Operator.SUBTRACT, left, right)
    }

    private fun matchSinCosProduct(left: MathNode, right: MathNode): SinCosPattern? {
        val terms = extractMultiplyFactors(MathNode.BinaryOp(Operator.MULTIPLY, left, right))

        var coefficient = 1.0
        var sinTerm: MathNode.Function? = null
        var cosTerm: MathNode.Function? = null

        for (term in terms) {
            when {
                term is MathNode.Number -> coefficient *= term.value

                term is MathNode.Function && term.name == "sin" -> {
                    if (sinTerm == null) sinTerm = term
                    else return null
                }

                term is MathNode.Function && term.name == "cos" -> {
                    if (cosTerm == null) cosTerm = term
                    else return null
                }

                else -> return null
            }
        }

        if (sinTerm != null && cosTerm != null &&
            sinTerm.argument.toString() == cosTerm.argument.toString()) {
            return SinCosPattern(coefficient, sinTerm.argument)
        }

        return null
    }

    /**
     * ✨ 新增：匹配 cos²(x) - sin²(x) 模式
     *
     * 支持的模式：
     * 1. A×cos²(x) - A×sin²(x) → A×cos(2x)
     * 2. cos²(x) - sin²(x) → cos(2x)
     */
    private fun matchCosSquaredMinusSinSquared(left: MathNode, right: MathNode): CosMinusSinPattern? {
        // 提取左边的 cos² 项
        val cosSquared = extractSquaredTrig(left, "cos")
        if (cosSquared == null) return null

        // 提取右边的 sin² 项
        val sinSquared = extractSquaredTrig(right, "sin")
        if (sinSquared == null) return null

        // 检查角度是否相同
        if (cosSquared.angle.toString() != sinSquared.angle.toString()) {
            return null
        }

        // 检查系数是否相同
        if (abs(cosSquared.coefficient - sinSquared.coefficient) > EPSILON) {
            return null
        }

        return CosMinusSinPattern(cosSquared.coefficient, cosSquared.angle)
    }

    /**
     * 提取平方三角函数项
     *
     * 支持的模式：
     * - A×sin²(x)
     * - sin²(x)
     * - A×sin(x)^2
     */
    private fun extractSquaredTrig(node: MathNode, funcName: String): SquaredTrigPattern? {
        var coefficient = 1.0
        var trigFunc: MathNode.Function? = null
        var power = 1.0

        val factors = extractMultiplyFactors(node)

        for (factor in factors) {
            when {
                factor is MathNode.Number -> coefficient *= factor.value

                factor is MathNode.BinaryOp && factor.operator == Operator.POWER -> {
                    if (factor.left is MathNode.Function &&
                        (factor.left as MathNode.Function).name == funcName &&
                        factor.right is MathNode.Number) {
                        trigFunc = factor.left as MathNode.Function
                        power = (factor.right as MathNode.Number).value
                    } else {
                        return null
                    }
                }

                factor is MathNode.Function && factor.name == funcName -> {
                    trigFunc = factor
                    power = 1.0
                }

                else -> return null
            }
        }

        if (trigFunc != null && abs(power - 2.0) < EPSILON) {
            return SquaredTrigPattern(coefficient, trigFunc.argument)
        }

        return null
    }

    private fun applyPythagorean(node: MathNode): MathNode {
        return when (node) {
            is MathNode.Number, is MathNode.Variable -> node

            is MathNode.Function -> {
                MathNode.Function(node.name, applyPythagorean(node.argument))
            }

            is MathNode.BinaryOp -> {
                val left = applyPythagorean(node.left)
                val right = applyPythagorean(node.right)

                if (node.operator == Operator.ADD) {
                    val simplified = simplifyPythagoreanSum(left, right)
                    if (simplified != null) return simplified
                }

                MathNode.BinaryOp(node.operator, left, right)
            }
        }
    }

    private fun simplifyPythagoreanSum(left: MathNode, right: MathNode): MathNode? {
        val sinSquared = extractSquaredTrig(left, "sin")
        val cosSquared = extractSquaredTrig(right, "cos")

        if (sinSquared != null && cosSquared != null &&
            sinSquared.angle.toString() == cosSquared.angle.toString() &&
            abs(sinSquared.coefficient - cosSquared.coefficient) < EPSILON) {
            Log.d(TAG, "匹配到毕达哥拉斯: sin²(${sinSquared.angle}) + cos²(${sinSquared.angle}) = 1")
            return MathNode.Number(sinSquared.coefficient)
        }

        val cosSquared2 = extractSquaredTrig(left, "cos")
        val sinSquared2 = extractSquaredTrig(right, "sin")

        if (cosSquared2 != null && sinSquared2 != null &&
            cosSquared2.angle.toString() == sinSquared2.angle.toString() &&
            abs(cosSquared2.coefficient - sinSquared2.coefficient) < EPSILON) {
            Log.d(TAG, "匹配到毕达哥拉斯: cos²(${cosSquared2.angle}) + sin²(${cosSquared2.angle}) = 1")
            return MathNode.Number(cosSquared2.coefficient)
        }

        return null
    }

    private fun applyBasicIdentities(node: MathNode): MathNode {
        return when (node) {
            is MathNode.Number, is MathNode.Variable -> node

            is MathNode.Function -> {
                MathNode.Function(node.name, applyBasicIdentities(node.argument))
            }

            is MathNode.BinaryOp -> {
                val left = applyBasicIdentities(node.left)
                val right = applyBasicIdentities(node.right)

                if (node.operator == Operator.DIVIDE) {
                    val simplified = simplifyTrigDivision(left, right)
                    if (simplified != null) return simplified
                }

                MathNode.BinaryOp(node.operator, left, right)
            }
        }
    }

    private fun simplifyTrigDivision(numerator: MathNode, denominator: MathNode): MathNode? {
        if (numerator is MathNode.Function && denominator is MathNode.Function &&
            numerator.argument.toString() == denominator.argument.toString()) {

            val result = when {
                numerator.name == "sin" && denominator.name == "cos" -> {
                    Log.d(TAG, "化简: sin/cos → tan")
                    MathNode.Function("tan", numerator.argument)
                }
                numerator.name == "cos" && denominator.name == "sin" -> {
                    Log.d(TAG, "化简: cos/sin → cot")
                    MathNode.Function("cot", numerator.argument)
                }
                else -> null
            }

            if (result != null) return result
        }

        if (numerator is MathNode.Number && abs(numerator.value - 1.0) < EPSILON) {
            val result = when {
                denominator is MathNode.Function && denominator.name == "cos" -> {
                    Log.d(TAG, "化简: 1/cos → sec")
                    MathNode.Function("sec", denominator.argument)
                }
                denominator is MathNode.Function && denominator.name == "sin" -> {
                    Log.d(TAG, "化简: 1/sin → csc")
                    MathNode.Function("csc", denominator.argument)
                }
                else -> null
            }

            if (result != null) return result
        }

        return null
    }

    /**
     * ✨ 新增：化简数值系数
     *
     * 处理形如 A×B×C 的乘法链，将连续的数值系数相乘
     * 例如：
     * - 2×0.5×sin(2x) → 1.0×sin(2x) → sin(2x)
     * - 3×2×x → 6×x
     * - 0.5×4×cos(x) → 2×cos(x)
     */
    private fun simplifyNumericCoefficients(node: MathNode): MathNode {
        return when (node) {
            is MathNode.Number, is MathNode.Variable -> node

            is MathNode.Function -> {
                val argSimplified = simplifyNumericCoefficients(node.argument)
                MathNode.Function(node.name, argSimplified)
            }

            is MathNode.BinaryOp -> {
                if (node.operator == Operator.MULTIPLY) {
                    // 收集所有乘法因子
                    val factors = extractMultiplyFactors(node)

                    // 分离数值因子和非数值因子
                    val numericFactors = mutableListOf<Double>()
                    val nonNumericFactors = mutableListOf<MathNode>()

                    for (factor in factors) {
                        if (factor is MathNode.Number) {
                            numericFactors.add(factor.value)
                        } else {
                            // 递归化简非数值因子
                            nonNumericFactors.add(simplifyNumericCoefficients(factor))
                        }
                    }

                    // 计算数值因子的乘积
                    val product = numericFactors.fold(1.0) { acc, value -> acc * value }

                    // 重建表达式
                    buildMultiplyChain(product, nonNumericFactors)
                } else {
                    // 其他运算符，递归化简左右子节点
                    val leftSimplified = simplifyNumericCoefficients(node.left)
                    val rightSimplified = simplifyNumericCoefficients(node.right)
                    MathNode.BinaryOp(node.operator, leftSimplified, rightSimplified)
                }
            }
        }
    }

    /**
     * 构建乘法链
     *
     * @param coefficient 数值系数（已相乘）
     * @param factors 非数值因子列表
     * @return 构建好的表达式树
     */
    private fun buildMultiplyChain(coefficient: Double, factors: List<MathNode>): MathNode {
        // 如果没有非数值因子，只返回系数
        if (factors.isEmpty()) {
            return MathNode.Number(coefficient)
        }

        // 如果系数为1，不包含在表达式中
        val allFactors = if (abs(coefficient - 1.0) < EPSILON) {
            factors
        } else {
            listOf(MathNode.Number(coefficient)) + factors
        }

        // 如果只有一个因子，直接返回
        if (allFactors.size == 1) {
            return allFactors[0]
        }

        // 构建左结合的乘法链
        return allFactors.reduce { acc, factor ->
            MathNode.BinaryOp(Operator.MULTIPLY, acc, factor)
        }
    }

    private fun extractMultiplyFactors(node: MathNode): List<MathNode> {
        return when (node) {
            is MathNode.BinaryOp -> {
                if (node.operator == Operator.MULTIPLY) {
                    extractMultiplyFactors(node.left) + extractMultiplyFactors(node.right)
                } else {
                    listOf(node)
                }
            }
            else -> listOf(node)
        }
    }

    private data class SinCosPattern(
        val coefficient: Double,
        val angle: MathNode
    )

    private data class CosMinusSinPattern(
        val coefficient: Double,
        val angle: MathNode
    )

    private data class SquaredTrigPattern(
        val coefficient: Double,
        val angle: MathNode
    )
}