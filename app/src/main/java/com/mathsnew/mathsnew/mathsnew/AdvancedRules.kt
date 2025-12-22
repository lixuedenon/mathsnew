// app/src/main/java/com/mathsnew/mathsnew/AdvancedRules.kt
// 高级微分规则（带调试版本）

package com.mathsnew.mathsnew

import android.util.Log

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
            MathNode.Number(2.0)
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
 *
 * ⚠️ 关键修复：必须返回 DIVIDE 节点！
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

        // ⚠️ 这里必须是 DIVIDE，不是 MULTIPLY！
        val result = MathNode.BinaryOp(
            Operator.DIVIDE,  // 除法
            uPrime,
            u
        )

        Log.d("LnRule", "自然对数规则: $node")
        Log.d("LnRule", "  内层: u = $u")
        Log.d("LnRule", "  导数: u' = $uPrime")
        Log.d("LnRule", "  结果: u'/u = $result")
        Log.d("LnRule", "  运算符: ${(result as MathNode.BinaryOp).operator}")

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
            MathNode.Number(Math.log(10.0))
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
            MathNode.Number(2.0),
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

        return baseHasVar || expHasVar
    }

    override fun apply(node: MathNode, variable: String, calculator: DerivativeCalculator): MathNode {
        val powerNode = node as MathNode.BinaryOp
        val u = powerNode.left
        val v = powerNode.right

        val uPrime = calculator.differentiate(u, variable)
        val vPrime = calculator.differentiate(v, variable)

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

        return MathNode.BinaryOp(Operator.MULTIPLY, node, bracket)
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