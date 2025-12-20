// app/src/main/java/com/mathsnew/mathsnew/ExtendedTrigRules.kt
// 扩展三角函数微分规则：余切、正割、余割

package com.mathsnew.mathsnew

/**
 * 余切函数规则：d/dx[cot(u)] = -csc²(u) × u' = -1/sin²(u) × u'  (链式法则)
 *
 * 测试：
 * - d/dx[cot(x)] = -1/sin²(x)
 * - d/dx[cot(x²)] = -1/sin²(x²) × 2x
 */
class CotRule : DerivativeRule {
    override val name = "余切函数规则"
    override val priority = 70

    override fun matches(node: MathNode, variable: String): Boolean {
        return node is MathNode.Function && node.name == "cot"
    }

    override fun apply(node: MathNode, variable: String, calculator: DerivativeCalculator): MathNode {
        val funcNode = node as MathNode.Function
        val innerNode = funcNode.argument

        // 外层导数：-1 / sin²(u)
        val sinSquared = MathNode.BinaryOp(
            operator = Operator.POWER,
            left = MathNode.Function("sin", innerNode),
            right = MathNode.Number(2.0)
        )

        val outerDerivative = MathNode.BinaryOp(
            operator = Operator.DIVIDE,
            left = MathNode.Number(-1.0),
            right = sinSquared
        )

        // 内层导数：u'
        val innerDerivative = calculator.differentiate(innerNode, variable)

        // 链式法则：-csc²(u) × u'
        return MathNode.BinaryOp(
            operator = Operator.MULTIPLY,
            left = outerDerivative,
            right = innerDerivative
        )
    }
}

/**
 * 正割函数规则：d/dx[sec(u)] = sec(u) × tan(u) × u' = (1/cos(u)) × (sin(u)/cos(u)) × u'  (链式法则)
 *
 * 测试：
 * - d/dx[sec(x)] = sec(x) × tan(x)
 * - d/dx[sec(x²)] = sec(x²) × tan(x²) × 2x
 */
class SecRule : DerivativeRule {
    override val name = "正割函数规则"
    override val priority = 70

    override fun matches(node: MathNode, variable: String): Boolean {
        return node is MathNode.Function && node.name == "sec"
    }

    override fun apply(node: MathNode, variable: String, calculator: DerivativeCalculator): MathNode {
        val funcNode = node as MathNode.Function
        val innerNode = funcNode.argument

        // sec(u) = 1/cos(u)
        val secU = MathNode.BinaryOp(
            operator = Operator.DIVIDE,
            left = MathNode.Number(1.0),
            right = MathNode.Function("cos", innerNode)
        )

        // tan(u) = sin(u)/cos(u)
        val tanU = MathNode.BinaryOp(
            operator = Operator.DIVIDE,
            left = MathNode.Function("sin", innerNode),
            right = MathNode.Function("cos", innerNode)
        )

        // 外层导数：sec(u) × tan(u)
        val outerDerivative = MathNode.BinaryOp(
            operator = Operator.MULTIPLY,
            left = secU,
            right = tanU
        )

        // 内层导数：u'
        val innerDerivative = calculator.differentiate(innerNode, variable)

        // 链式法则：sec(u) × tan(u) × u'
        return MathNode.BinaryOp(
            operator = Operator.MULTIPLY,
            left = outerDerivative,
            right = innerDerivative
        )
    }
}

/**
 * 余割函数规则：d/dx[csc(u)] = -csc(u) × cot(u) × u' = -(1/sin(u)) × (cos(u)/sin(u)) × u'  (链式法则)
 *
 * 测试：
 * - d/dx[csc(x)] = -csc(x) × cot(x)
 * - d/dx[csc(x²)] = -csc(x²) × cot(x²) × 2x
 */
class CscRule : DerivativeRule {
    override val name = "余割函数规则"
    override val priority = 70

    override fun matches(node: MathNode, variable: String): Boolean {
        return node is MathNode.Function && node.name == "csc"
    }

    override fun apply(node: MathNode, variable: String, calculator: DerivativeCalculator): MathNode {
        val funcNode = node as MathNode.Function
        val innerNode = funcNode.argument

        // csc(u) = 1/sin(u)
        val cscU = MathNode.BinaryOp(
            operator = Operator.DIVIDE,
            left = MathNode.Number(1.0),
            right = MathNode.Function("sin", innerNode)
        )

        // cot(u) = cos(u)/sin(u)
        val cotU = MathNode.BinaryOp(
            operator = Operator.DIVIDE,
            left = MathNode.Function("cos", innerNode),
            right = MathNode.Function("sin", innerNode)
        )

        // -csc(u) × cot(u)
        val negativeCsc = MathNode.BinaryOp(
            operator = Operator.MULTIPLY,
            left = MathNode.Number(-1.0),
            right = cscU
        )

        val outerDerivative = MathNode.BinaryOp(
            operator = Operator.MULTIPLY,
            left = negativeCsc,
            right = cotU
        )

        // 内层导数：u'
        val innerDerivative = calculator.differentiate(innerNode, variable)

        // 链式法则：-csc(u) × cot(u) × u'
        return MathNode.BinaryOp(
            operator = Operator.MULTIPLY,
            left = outerDerivative,
            right = innerDerivative
        )
    }
}