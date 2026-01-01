// app/src/main/java/com/mathsnew/mathsnew/InverseTrigRules.kt
// 反三角函数微分规则

package com.mathsnew.mathsnew

import android.util.Log
import kotlin.math.abs

/**
 * 反正弦函数规则：d/dx[arcsin(u)] = u' / √(1-u²)
 *
 * 测试：
 * - d/dx[arcsin(x)] = 1 / √(1-x²)
 * - d/dx[arcsin(x²)] = 2x / √(1-(x²)²) = 2x / √(1-x⁴)
 */
class ArcsinRule : DerivativeRule {
    override val name = "反正弦函数规则"
    override val priority = 70

    override fun matches(node: MathNode, variable: String): Boolean {
        return node is MathNode.Function && node.name == "arcsin"
    }

    override fun apply(node: MathNode, variable: String, calculator: DerivativeCalculator): MathNode {
        val funcNode = node as MathNode.Function
        val innerNode = funcNode.argument

        val uPrime = calculator.differentiate(innerNode, variable)

        val uSquared = MathNode.BinaryOp(
            operator = Operator.POWER,
            left = innerNode,
            right = MathNode.Number(2.0)
        )

        val oneMinusUSquared = MathNode.BinaryOp(
            operator = Operator.SUBTRACT,
            left = MathNode.Number(1.0),
            right = uSquared
        )

        val sqrtTerm = MathNode.Function("sqrt", oneMinusUSquared)

        val result = MathNode.BinaryOp(
            operator = Operator.DIVIDE,
            left = uPrime,
            right = sqrtTerm
        )

        Log.d("ArcsinRule", "反正弦规则: $node -> $result")
        return result
    }
}

/**
 * 反余弦函数规则：d/dx[arccos(u)] = -u' / √(1-u²)
 *
 * 测试：
 * - d/dx[arccos(x)] = -1 / √(1-x²)
 * - d/dx[arccos(x²)] = -2x / √(1-x⁴)
 */
class ArccosRule : DerivativeRule {
    override val name = "反余弦函数规则"
    override val priority = 70

    override fun matches(node: MathNode, variable: String): Boolean {
        return node is MathNode.Function && node.name == "arccos"
    }

    override fun apply(node: MathNode, variable: String, calculator: DerivativeCalculator): MathNode {
        val funcNode = node as MathNode.Function
        val innerNode = funcNode.argument

        val uPrime = calculator.differentiate(innerNode, variable)

        val uSquared = MathNode.BinaryOp(
            operator = Operator.POWER,
            left = innerNode,
            right = MathNode.Number(2.0)
        )

        val oneMinusUSquared = MathNode.BinaryOp(
            operator = Operator.SUBTRACT,
            left = MathNode.Number(1.0),
            right = uSquared
        )

        val sqrtTerm = MathNode.Function("sqrt", oneMinusUSquared)

        val denominator = sqrtTerm

        val negativeUPrime = MathNode.BinaryOp(
            operator = Operator.MULTIPLY,
            left = MathNode.Number(-1.0),
            right = uPrime
        )

        val result = MathNode.BinaryOp(
            operator = Operator.DIVIDE,
            left = negativeUPrime,
            right = denominator
        )

        Log.d("ArccosRule", "反余弦规则: $node -> $result")
        return result
    }
}

/**
 * 反正切函数规则：d/dx[arctan(u)] = u' / (1+u²)
 *
 * 测试：
 * - d/dx[arctan(x)] = 1 / (1+x²)
 * - d/dx[arctan(x²)] = 2x / (1+x⁴)
 */
class ArctanRule : DerivativeRule {
    override val name = "反正切函数规则"
    override val priority = 70

    override fun matches(node: MathNode, variable: String): Boolean {
        return node is MathNode.Function && node.name == "arctan"
    }

    override fun apply(node: MathNode, variable: String, calculator: DerivativeCalculator): MathNode {
        val funcNode = node as MathNode.Function
        val innerNode = funcNode.argument

        val uPrime = calculator.differentiate(innerNode, variable)

        val uSquared = MathNode.BinaryOp(
            operator = Operator.POWER,
            left = innerNode,
            right = MathNode.Number(2.0)
        )

        val onePlusUSquared = MathNode.BinaryOp(
            operator = Operator.ADD,
            left = MathNode.Number(1.0),
            right = uSquared
        )

        val result = MathNode.BinaryOp(
            operator = Operator.DIVIDE,
            left = uPrime,
            right = onePlusUSquared
        )

        Log.d("ArctanRule", "反正切规则: $node -> $result")
        return result
    }
}

/**
 * 反余切函数规则：d/dx[arccot(u)] = -u' / (1+u²)
 *
 * 测试：
 * - d/dx[arccot(x)] = -1 / (1+x²)
 * - d/dx[arccot(x²)] = -2x / (1+x⁴)
 */
class ArccotRule : DerivativeRule {
    override val name = "反余切函数规则"
    override val priority = 70

    override fun matches(node: MathNode, variable: String): Boolean {
        return node is MathNode.Function && node.name == "arccot"
    }

    override fun apply(node: MathNode, variable: String, calculator: DerivativeCalculator): MathNode {
        val funcNode = node as MathNode.Function
        val innerNode = funcNode.argument

        val uPrime = calculator.differentiate(innerNode, variable)

        val uSquared = MathNode.BinaryOp(
            operator = Operator.POWER,
            left = innerNode,
            right = MathNode.Number(2.0)
        )

        val onePlusUSquared = MathNode.BinaryOp(
            operator = Operator.ADD,
            left = MathNode.Number(1.0),
            right = uSquared
        )

        val negativeUPrime = MathNode.BinaryOp(
            operator = Operator.MULTIPLY,
            left = MathNode.Number(-1.0),
            right = uPrime
        )

        val result = MathNode.BinaryOp(
            operator = Operator.DIVIDE,
            left = negativeUPrime,
            right = onePlusUSquared
        )

        Log.d("ArccotRule", "反余切规则: $node -> $result")
        return result
    }
}

/**
 * 反正割函数规则：d/dx[arcsec(u)] = u' / (|u|·√(u²-1))
 *
 * 简化实现：不考虑绝对值，直接使用 u' / (u·√(u²-1))
 *
 * 测试：
 * - d/dx[arcsec(x)] = 1 / (x·√(x²-1))
 * - d/dx[arcsec(x²)] = 2x / (x²·√(x⁴-1))
 */
class ArcsecRule : DerivativeRule {
    override val name = "反正割函数规则"
    override val priority = 70

    override fun matches(node: MathNode, variable: String): Boolean {
        return node is MathNode.Function && node.name == "arcsec"
    }

    override fun apply(node: MathNode, variable: String, calculator: DerivativeCalculator): MathNode {
        val funcNode = node as MathNode.Function
        val innerNode = funcNode.argument

        val uPrime = calculator.differentiate(innerNode, variable)

        val uSquared = MathNode.BinaryOp(
            operator = Operator.POWER,
            left = innerNode,
            right = MathNode.Number(2.0)
        )

        val uSquaredMinusOne = MathNode.BinaryOp(
            operator = Operator.SUBTRACT,
            left = uSquared,
            right = MathNode.Number(1.0)
        )

        val sqrtTerm = MathNode.Function("sqrt", uSquaredMinusOne)

        val denominator = MathNode.BinaryOp(
            operator = Operator.MULTIPLY,
            left = innerNode,
            right = sqrtTerm
        )

        val result = MathNode.BinaryOp(
            operator = Operator.DIVIDE,
            left = uPrime,
            right = denominator
        )

        Log.d("ArcsecRule", "反正割规则: $node -> $result")
        return result
    }
}

/**
 * 反余割函数规则：d/dx[arccsc(u)] = -u' / (|u|·√(u²-1))
 *
 * 简化实现：不考虑绝对值，直接使用 -u' / (u·√(u²-1))
 *
 * 测试：
 * - d/dx[arccsc(x)] = -1 / (x·√(x²-1))
 * - d/dx[arccsc(x²)] = -2x / (x²·√(x⁴-1))
 */
class ArccscRule : DerivativeRule {
    override val name = "反余割函数规则"
    override val priority = 70

    override fun matches(node: MathNode, variable: String): Boolean {
        return node is MathNode.Function && node.name == "arccsc"
    }

    override fun apply(node: MathNode, variable: String, calculator: DerivativeCalculator): MathNode {
        val funcNode = node as MathNode.Function
        val innerNode = funcNode.argument

        val uPrime = calculator.differentiate(innerNode, variable)

        val uSquared = MathNode.BinaryOp(
            operator = Operator.POWER,
            left = innerNode,
            right = MathNode.Number(2.0)
        )

        val uSquaredMinusOne = MathNode.BinaryOp(
            operator = Operator.SUBTRACT,
            left = uSquared,
            right = MathNode.Number(1.0)
        )

        val sqrtTerm = MathNode.Function("sqrt", uSquaredMinusOne)

        val denominator = MathNode.BinaryOp(
            operator = Operator.MULTIPLY,
            left = innerNode,
            right = sqrtTerm
        )

        val negativeUPrime = MathNode.BinaryOp(
            operator = Operator.MULTIPLY,
            left = MathNode.Number(-1.0),
            right = uPrime
        )

        val result = MathNode.BinaryOp(
            operator = Operator.DIVIDE,
            left = negativeUPrime,
            right = denominator
        )

        Log.d("ArccscRule", "反余割规则: $node -> $result")
        return result
    }
}