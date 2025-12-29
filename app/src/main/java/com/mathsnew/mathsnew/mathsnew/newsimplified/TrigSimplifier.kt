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

        Log.d(TAG, "最终结果: $result")
        Log.d(TAG, "========== 三角化简完成 ==========")

        return result
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
                MathNode.BinaryOp(Operator.MULTIPLY, MathNode.Number(halfCoeff), sin2x)
            }
        }

        return MathNode.BinaryOp(Operator.MULTIPLY, left, right)
    }

    private fun simplifyDoubleAngleSum(left: MathNode, right: MathNode): MathNode {
        return MathNode.BinaryOp(Operator.ADD, left, right)
    }

    private fun simplifyDoubleAngleDiff(left: MathNode, right: MathNode): MathNode {
        val pattern = matchCosSquaredMinusSinSquared(left, right)
        if (pattern != null) {
            Log.d(TAG, "匹配到二倍角: cos²(${pattern.angle}) - sin²(${pattern.angle})")

            val doubleAngle = MathNode.BinaryOp(
                Operator.MULTIPLY,
                MathNode.Number(2.0),
                pattern.angle
            )
            val cos2x = MathNode.Function("cos", doubleAngle)

            return if (abs(pattern.coefficient - 1.0) < EPSILON) {
                cos2x
            } else {
                MathNode.BinaryOp(Operator.MULTIPLY, MathNode.Number(pattern.coefficient), cos2x)
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

    private fun matchCosSquaredMinusSinSquared(left: MathNode, right: MathNode): CosMinusSinPattern? {
        val cosSquared = extractSquaredTrig(left, "cos")
        val sinSquared = extractSquaredTrig(right, "sin")

        if (cosSquared != null && sinSquared != null &&
            cosSquared.angle.toString() == sinSquared.angle.toString() &&
            abs(cosSquared.coefficient - sinSquared.coefficient) < EPSILON) {
            return CosMinusSinPattern(cosSquared.coefficient, cosSquared.angle)
        }

        return null
    }

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