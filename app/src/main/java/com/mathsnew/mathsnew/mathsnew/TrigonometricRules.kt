// app/src/main/java/com/mathsnew/mathsnew/TrigonometricRules.kt
// 三角函数微分规则

package com.mathsnew.mathsnew

/**
 * 正弦函数规则：d/dx[sin(u)] = cos(u) * u'  (链式法则)
 *
 * 测试：
 * - d/dx[sin(x)] = cos(x)
 * - d/dx[sin(x^2)] = cos(x^2) * 2*x
 */
class SinRule : DerivativeRule {
    override val name = "正弦函数规则"
    override val priority = 70

    override fun matches(node: MathNode, variable: String): Boolean {
        return node is MathNode.Function && node.name == "sin"
    }

    override fun apply(node: MathNode, variable: String, calculator: DerivativeCalculator): MathNode {
        val funcNode = node as MathNode.Function
        val innerNode = funcNode.argument

        // 外层导数：cos(u)
        val outerDerivative = MathNode.Function("cos", innerNode)

        // 内层导数：u'
        val innerDerivative = calculator.differentiate(innerNode, variable)

        // 链式法则：cos(u) * u'
        return MathNode.BinaryOp(
            operator = Operator.MULTIPLY,
            left = outerDerivative,
            right = innerDerivative
        )
    }
}

/**
 * 余弦函数规则：d/dx[cos(u)] = -sin(u) * u'  (链式法则)
 *
 * 测试：
 * - d/dx[cos(x)] = -sin(x)
 * - d/dx[cos(x^2)] = -sin(x^2) * 2*x
 */
class CosRule : DerivativeRule {
    override val name = "余弦函数规则"
    override val priority = 70

    override fun matches(node: MathNode, variable: String): Boolean {
        return node is MathNode.Function && node.name == "cos"
    }

    override fun apply(node: MathNode, variable: String, calculator: DerivativeCalculator): MathNode {
        val funcNode = node as MathNode.Function
        val innerNode = funcNode.argument

        // 外层导数：-sin(u)
        val outerDerivative = MathNode.BinaryOp(
            operator = Operator.MULTIPLY,
            left = MathNode.Number(-1.0),
            right = MathNode.Function("sin", innerNode)
        )

        // 内层导数：u'
        val innerDerivative = calculator.differentiate(innerNode, variable)

        // 链式法则：-sin(u) * u'
        return MathNode.BinaryOp(
            operator = Operator.MULTIPLY,
            left = outerDerivative,
            right = innerDerivative
        )
    }
}

/**
 * 正切函数规则：d/dx[tan(u)] = sec^2(u) * u' = (1/cos^2(u)) * u'
 *
 * 测试：
 * - d/dx[tan(x)] = 1/cos^2(x)
 * - d/dx[tan(x^2)] = (1/cos^2(x^2)) * 2*x
 */
class TanRule : DerivativeRule {
    override val name = "正切函数规则"
    override val priority = 70

    override fun matches(node: MathNode, variable: String): Boolean {
        return node is MathNode.Function && node.name == "tan"
    }

    override fun apply(node: MathNode, variable: String, calculator: DerivativeCalculator): MathNode {
        val funcNode = node as MathNode.Function
        val innerNode = funcNode.argument

        // 外层导数：1 / cos^2(u) = sec^2(u)
        val cosSquared = MathNode.BinaryOp(
            operator = Operator.POWER,
            left = MathNode.Function("cos", innerNode),
            right = MathNode.Number(2.0)
        )

        val outerDerivative = MathNode.BinaryOp(
            operator = Operator.DIVIDE,
            left = MathNode.Number(1.0),
            right = cosSquared
        )

        // 内层导数：u'
        val innerDerivative = calculator.differentiate(innerNode, variable)

        // 链式法则：sec^2(u) * u'
        return MathNode.BinaryOp(
            operator = Operator.MULTIPLY,
            left = outerDerivative,
            right = innerDerivative
        )
    }
}