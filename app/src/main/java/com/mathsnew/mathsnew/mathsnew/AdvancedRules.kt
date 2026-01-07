// app/src/main/java/com/mathsnew/mathsnew/AdvancedRules.kt
// 高级微分规则（修复版 - 使用整数常量）

package com.mathsnew.mathsnew

import android.util.Log

/**
 * 复合幂函数规则：d/dx[(f(x))^n] = n × (f(x))^(n-1) × f'(x)
 *
 * 优先级高于 GeneralPowerRule，专门处理底数是复合函数、指数是常数的情况
 *
 * 例如：
 * - (2x)³ → 3(2x)² × 2 = 6(2x)² = 24x²
 * - (x+1)² → 2(x+1) × 1 = 2(x+1)
 * - (sin(x))² → 2sin(x) × cos(x)
 */
class CompositePowerRule : DerivativeRule {
    override val name = "复合幂函数规则"
    override val priority = 98  // 高于 GeneralPowerRule(95)，低于 PowerRule(100)

    override fun matches(node: MathNode, variable: String): Boolean {
        if (node !is MathNode.BinaryOp || node.operator != Operator.POWER) {
            return false
        }

        val baseHasVar = containsVariable(node.left, variable)
        val baseIsNotSimpleVar = node.left !is MathNode.Variable
        val exponentIsConstant = node.right is MathNode.Number

        // 匹配条件：底数包含变量但不是单纯变量，指数是常数
        return baseHasVar && baseIsNotSimpleVar && exponentIsConstant
    }

    override fun apply(node: MathNode, variable: String, calculator: DerivativeCalculator): MathNode {
        val powerNode = node as MathNode.BinaryOp
        val u = powerNode.left  // 底数 f(x)
        val n = (powerNode.right as MathNode.Number).value  // 指数 n

        // 计算 f'(x)
        val uPrime = calculator.differentiate(u, variable)

        // 构建 n × (f(x))^(n-1) × f'(x)
        val newExponent = MathNode.Number(n - 1)  // ✅ Kotlin自动处理 Double - Int
        val power = MathNode.BinaryOp(Operator.POWER, u, newExponent)
        val coefficient = MathNode.Number(n)

        val result = MathNode.BinaryOp(
            Operator.MULTIPLY,
            MathNode.BinaryOp(Operator.MULTIPLY, coefficient, power),
            uPrime
        )

        Log.d("CompositePowerRule", "复合幂规则: $node -> $result")
        return result
    }

    private fun containsVariable(node: MathNode, variable: String): Boolean {
        return when (node) {
            is MathNode.Number -> false
            is MathNode.Variable -> node.name == variable
            is MathNode.BinaryOp -> {
                containsVariable(node.left, variable) || containsVariable(node.right, variable)
            }
            is MathNode.Function -> containsVariable(node.argument, variable)
        }
    }
}

/**
 * 商规则：d/dx(u/v) = (u'v - uv')/v²
 */
class QuotientRule : DerivativeRule {
    override val name = "商规则"
    override val priority = 85

    override fun matches(node: MathNode, variable: String): Boolean {
        return node is MathNode.BinaryOp && node.operator == Operator.DIVIDE
    }

    override fun apply(node: MathNode, variable: String, calculator: DerivativeCalculator): MathNode {
        val divNode = node as MathNode.BinaryOp
        val u = divNode.left
        val v = divNode.right

        val uPrime = calculator.differentiate(u, variable)
        val vPrime = calculator.differentiate(v, variable)

        val numerator = MathNode.BinaryOp(
            Operator.SUBTRACT,
            MathNode.BinaryOp(Operator.MULTIPLY, uPrime, v),
            MathNode.BinaryOp(Operator.MULTIPLY, u, vPrime)
        )

        val denominator = MathNode.BinaryOp(
            Operator.POWER,
            v,
            MathNode.Number(2)  // ✅ 修复：2.0 → 2
        )

        val result = MathNode.BinaryOp(Operator.DIVIDE, numerator, denominator)
        Log.d("QuotientRule", "商规则: $node -> $result")
        return result
    }
}

/**
 * 指数函数规则：d/dx[exp(u)] = exp(u) × u'
 */
class ExpRule : DerivativeRule {
    override val name = "指数函数规则"
    override val priority = 65

    override fun matches(node: MathNode, variable: String): Boolean {
        return node is MathNode.Function && node.name == "exp"
    }

    override fun apply(node: MathNode, variable: String, calculator: DerivativeCalculator): MathNode {
        val funcNode = node as MathNode.Function
        val u = funcNode.argument
        val uPrime = calculator.differentiate(u, variable)

        val result = MathNode.BinaryOp(Operator.MULTIPLY, node, uPrime)
        Log.d("ExpRule", "指数规则: $node -> $result")
        return result
    }
}

/**
 * 自然对数规则：d/dx[ln(u)] = u'/u
 */
class LnRule : DerivativeRule {
    override val name = "自然对数规则"
    override val priority = 65

    override fun matches(node: MathNode, variable: String): Boolean {
        return node is MathNode.Function && node.name == "ln"
    }

    override fun apply(node: MathNode, variable: String, calculator: DerivativeCalculator): MathNode {
        val funcNode = node as MathNode.Function
        val u = funcNode.argument
        val uPrime = calculator.differentiate(u, variable)

        val result = MathNode.BinaryOp(
            Operator.DIVIDE,
            uPrime,
            u
        )

        Log.d("LnRule", "自然对数规则: $node")
        Log.d("LnRule", "  内层: u = $u")
        Log.d("LnRule", "  导数: u' = $uPrime")
        Log.d("LnRule", "  结果: u'/u = $result")

        return result
    }
}

/**
 * 常用对数规则：d/dx[log(u)] = u'/(u×ln(10))
 */
class LogRule : DerivativeRule {
    override val name = "常用对数规则"
    override val priority = 65

    override fun matches(node: MathNode, variable: String): Boolean {
        return node is MathNode.Function && node.name == "log"
    }

    override fun apply(node: MathNode, variable: String, calculator: DerivativeCalculator): MathNode {
        val funcNode = node as MathNode.Function
        val u = funcNode.argument
        val uPrime = calculator.differentiate(u, variable)

        val denominator = MathNode.BinaryOp(
            Operator.MULTIPLY,
            u,
            MathNode.Number(Math.log(10.0))  // ✅ 这里必须保持浮点数，因为ln(10)是无理数
        )

        return MathNode.BinaryOp(Operator.DIVIDE, uPrime, denominator)
    }
}

/**
 * 平方根规则：d/dx[sqrt(u)] = u'/(2×sqrt(u))
 */
class SqrtRule : DerivativeRule {
    override val name = "平方根规则"
    override val priority = 65

    override fun matches(node: MathNode, variable: String): Boolean {
        return node is MathNode.Function && node.name == "sqrt"
    }

    override fun apply(node: MathNode, variable: String, calculator: DerivativeCalculator): MathNode {
        val funcNode = node as MathNode.Function
        val u = funcNode.argument
        val uPrime = calculator.differentiate(u, variable)

        val denominator = MathNode.BinaryOp(
            Operator.MULTIPLY,
            MathNode.Number(2),  // ✅ 修复：2.0 → 2
            node
        )

        return MathNode.BinaryOp(Operator.DIVIDE, uPrime, denominator)
    }
}

/**
 * 绝对值规则：d/dx[abs(u)] = (u/abs(u)) × u'
 */
class AbsRule : DerivativeRule {
    override val name = "绝对值规则"
    override val priority = 65

    override fun matches(node: MathNode, variable: String): Boolean {
        return node is MathNode.Function && node.name == "abs"
    }

    override fun apply(node: MathNode, variable: String, calculator: DerivativeCalculator): MathNode {
        val funcNode = node as MathNode.Function
        val u = funcNode.argument
        val uPrime = calculator.differentiate(u, variable)

        val signFunction = MathNode.BinaryOp(Operator.DIVIDE, u, node)

        return MathNode.BinaryOp(Operator.MULTIPLY, signFunction, uPrime)
    }
}

/**
 * 一般幂函数规则：d/dx[u^v] = u^v × (v'×ln(u) + v×u'/u)
 *
 * 处理底数和指数都包含变量的情况，如：
 * - x^x
 * - x^(2x)
 * - (sin(x))^(cos(x))
 */
class GeneralPowerRule : DerivativeRule {
    override val name = "一般幂函数规则"
    override val priority = 95

    override fun matches(node: MathNode, variable: String): Boolean {
        if (node !is MathNode.BinaryOp || node.operator != Operator.POWER) {
            return false
        }

        val baseHasVar = containsVariable(node.left, variable)
        val expHasVar = containsVariable(node.right, variable)

        // 只有当指数包含变量时才使用这个规则
        // （底数有变量但指数是常数的情况由 CompositePowerRule 处理）
        return expHasVar || (baseHasVar && expHasVar)
    }

    override fun apply(node: MathNode, variable: String, calculator: DerivativeCalculator): MathNode {
        val powerNode = node as MathNode.BinaryOp
        val u = powerNode.left
        val v = powerNode.right

        val uPrime = calculator.differentiate(u, variable)
        val vPrime = calculator.differentiate(v, variable)

        // d/dx[u^v] = u^v × (v'×ln(u) + v×u'/u)
        val term1 = MathNode.BinaryOp(
            Operator.MULTIPLY,
            vPrime,
            MathNode.Function("ln", u)
        )

        val term2 = MathNode.BinaryOp(
            Operator.MULTIPLY,
            v,
            MathNode.BinaryOp(Operator.DIVIDE, uPrime, u)
        )

        val bracket = MathNode.BinaryOp(Operator.ADD, term1, term2)

        val result = MathNode.BinaryOp(Operator.MULTIPLY, node, bracket)
        Log.d("GeneralPowerRule", "一般幂规则: $node -> $result")
        return result
    }

    private fun containsVariable(node: MathNode, variable: String): Boolean {
        return when (node) {
            is MathNode.Number -> false
            is MathNode.Variable -> node.name == variable
            is MathNode.BinaryOp -> {
                containsVariable(node.left, variable) || containsVariable(node.right, variable)
            }
            is MathNode.Function -> containsVariable(node.argument, variable)
        }
    }
}