// app/src/main/java/com/mathsnew/mathsnew/AdvancedRules.kt
// 高级微分规则：商规则、指数规则、对数规则

package com.mathsnew.mathsnew

/**
 * 商规则：d/dx(u/v) = (u'×v - u×v') / v²
 *
 * 测试：
 * - d/dx(x/sin(x)) = (sin(x) - x×cos(x)) / sin²(x)
 * - d/dx(1/x) = -1/x²
 * - d/dx((x²+1)/(x-1)) = ((2x)(x-1) - (x²+1)) / (x-1)²
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

        // 分子：u'×v - u×v'
        val numerator = MathNode.BinaryOp(
            operator = Operator.SUBTRACT,
            left = MathNode.BinaryOp(Operator.MULTIPLY, uPrime, v),
            right = MathNode.BinaryOp(Operator.MULTIPLY, u, vPrime)
        )

        // 分母：v²
        val denominator = MathNode.BinaryOp(
            operator = Operator.POWER,
            left = v,
            right = MathNode.Number(2.0)
        )

        // (u'×v - u×v') / v²
        return MathNode.BinaryOp(
            operator = Operator.DIVIDE,
            left = numerator,
            right = denominator
        )
    }
}

/**
 * 自然指数函数规则：d/dx[exp(u)] = exp(u) × u'  (链式法则)
 * 注：exp(x) = e^x
 *
 * 测试：
 * - d/dx[exp(x)] = exp(x)
 * - d/dx[exp(x²)] = exp(x²) × 2x
 * - d/dx[exp(2x)] = exp(2x) × 2
 */
class ExpRule : DerivativeRule {
    override val name = "指数函数规则"
    override val priority = 65

    override fun matches(node: MathNode, variable: String): Boolean {
        return node is MathNode.Function && node.name == "exp"
    }

    override fun apply(node: MathNode, variable: String, calculator: DerivativeCalculator): MathNode {
        val funcNode = node as MathNode.Function
        val innerNode = funcNode.argument

        // 外层导数：exp(u)
        val outerDerivative = MathNode.Function("exp", innerNode)

        // 内层导数：u'
        val innerDerivative = calculator.differentiate(innerNode, variable)

        // 链式法则：exp(u) × u'
        return MathNode.BinaryOp(
            operator = Operator.MULTIPLY,
            left = outerDerivative,
            right = innerDerivative
        )
    }
}

/**
 * 自然对数函数规则：d/dx[ln(u)] = u' / u  (链式法则)
 *
 * 测试：
 * - d/dx[ln(x)] = 1/x
 * - d/dx[ln(x²)] = 2x/x² = 2/x
 * - d/dx[ln(sin(x))] = cos(x)/sin(x)
 */
class LnRule : DerivativeRule {
    override val name = "自然对数规则"
    override val priority = 65

    override fun matches(node: MathNode, variable: String): Boolean {
        return node is MathNode.Function && node.name == "ln"
    }

    override fun apply(node: MathNode, variable: String, calculator: DerivativeCalculator): MathNode {
        val funcNode = node as MathNode.Function
        val innerNode = funcNode.argument

        // 内层导数：u'
        val innerDerivative = calculator.differentiate(innerNode, variable)

        // 链式法则：u' / u
        return MathNode.BinaryOp(
            operator = Operator.DIVIDE,
            left = innerDerivative,
            right = innerNode
        )
    }
}

/**
 * 常用对数函数规则：d/dx[log(u)] = u' / (u × ln(10))  (链式法则)
 * 注：log(x) 表示以10为底的对数
 *
 * 测试：
 * - d/dx[log(x)] = 1 / (x × ln(10))
 * - d/dx[log(x²)] = 2x / (x² × ln(10)) = 2 / (x × ln(10))
 */
class LogRule : DerivativeRule {
    override val name = "常用对数规则"
    override val priority = 65

    override fun matches(node: MathNode, variable: String): Boolean {
        return node is MathNode.Function && node.name == "log"
    }

    override fun apply(node: MathNode, variable: String, calculator: DerivativeCalculator): MathNode {
        val funcNode = node as MathNode.Function
        val innerNode = funcNode.argument

        // 内层导数：u'
        val innerDerivative = calculator.differentiate(innerNode, variable)

        // ln(10)
        val ln10 = MathNode.Function("ln", MathNode.Number(10.0))

        // 分母：u × ln(10)
        val denominator = MathNode.BinaryOp(
            operator = Operator.MULTIPLY,
            left = innerNode,
            right = ln10
        )

        // 链式法则：u' / (u × ln(10))
        return MathNode.BinaryOp(
            operator = Operator.DIVIDE,
            left = innerDerivative,
            right = denominator
        )
    }
}

/**
 * 平方根函数规则：d/dx[sqrt(u)] = u' / (2×sqrt(u))  (链式法则)
 * 注：sqrt(x) = x^(1/2)
 *
 * 测试：
 * - d/dx[sqrt(x)] = 1 / (2×sqrt(x))
 * - d/dx[sqrt(x²+1)] = 2x / (2×sqrt(x²+1)) = x / sqrt(x²+1)
 */
class SqrtRule : DerivativeRule {
    override val name = "平方根规则"
    override val priority = 65

    override fun matches(node: MathNode, variable: String): Boolean {
        return node is MathNode.Function && node.name == "sqrt"
    }

    override fun apply(node: MathNode, variable: String, calculator: DerivativeCalculator): MathNode {
        val funcNode = node as MathNode.Function
        val innerNode = funcNode.argument

        // 内层导数：u'
        val innerDerivative = calculator.differentiate(innerNode, variable)

        // 分母：2 × sqrt(u)
        val denominator = MathNode.BinaryOp(
            operator = Operator.MULTIPLY,
            left = MathNode.Number(2.0),
            right = MathNode.Function("sqrt", innerNode)
        )

        // 链式法则：u' / (2×sqrt(u))
        return MathNode.BinaryOp(
            operator = Operator.DIVIDE,
            left = innerDerivative,
            right = denominator
        )
    }
}

/**
 * 一般幂函数规则：d/dx[u^v] = u^v × (v' × ln(u) + v × u'/u)
 * 适用于底数和指数都含变量的情况
 *
 * 特殊情况：
 * - 如果 v 是常数，则退化为幂规则
 * - 如果 u 是常数，则退化为指数规则
 *
 * 测试：
 * - d/dx[x^x] = x^x × (ln(x) + 1)
 * - d/dx[x^(2x)] = x^(2x) × (2×ln(x) + 2)
 */
class GeneralPowerRule : DerivativeRule {
    override val name = "一般幂函数规则"
    override val priority = 95

    override fun matches(node: MathNode, variable: String): Boolean {
        if (node !is MathNode.BinaryOp || node.operator != Operator.POWER) {
            return false
        }

        // 检查指数是否包含变量
        val exponentContainsVariable = containsVariable(node.right, variable)

        // 只有当指数包含变量时，才使用此规则
        // 如果指数是常数，应该使用PowerRule
        return exponentContainsVariable
    }

    override fun apply(node: MathNode, variable: String, calculator: DerivativeCalculator): MathNode {
        val powerNode = node as MathNode.BinaryOp
        val u = powerNode.left
        val v = powerNode.right

        val uPrime = calculator.differentiate(u, variable)
        val vPrime = calculator.differentiate(v, variable)

        // 第一项：v' × ln(u)
        val term1 = MathNode.BinaryOp(
            operator = Operator.MULTIPLY,
            left = vPrime,
            right = MathNode.Function("ln", u)
        )

        // 第二项：v × u'/u
        val term2 = MathNode.BinaryOp(
            operator = Operator.MULTIPLY,
            left = v,
            right = MathNode.BinaryOp(
                operator = Operator.DIVIDE,
                left = uPrime,
                right = u
            )
        )

        // 括号内：v' × ln(u) + v × u'/u
        val bracket = MathNode.BinaryOp(
            operator = Operator.ADD,
            left = term1,
            right = term2
        )

        // 结果：u^v × (...)
        return MathNode.BinaryOp(
            operator = Operator.MULTIPLY,
            left = powerNode,
            right = bracket
        )
    }

    /**
     * 检查节点是否包含指定变量
     */
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