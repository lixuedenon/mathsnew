// app/src/main/java/com/mathsnew/mathsnew/BasicRules.kt
// 基础微分规则

package com.mathsnew.mathsnew

/**
 * 常数规则：d/dx(c) = 0
 *
 * 测试：d/dx(5) = 0
 */
class ConstantRule : DerivativeRule {
    override val name = "常数规则"
    override val priority = 200

    override fun matches(node: MathNode, variable: String): Boolean {
        return node is MathNode.Number
    }

    override fun apply(node: MathNode, variable: String, calculator: DerivativeCalculator): MathNode {
        return MathNode.Number(0.0)
    }
}

/**
 * 变量规则：
 * - d/dx(x) = 1
 * - d/dx(y) = 0 (y是其他变量)
 *
 * 测试：
 * - d/dx(x) = 1
 * - d/dx(y) = 0
 */
class VariableRule : DerivativeRule {
    override val name = "变量规则"
    override val priority = 150

    override fun matches(node: MathNode, variable: String): Boolean {
        return node is MathNode.Variable
    }

    override fun apply(node: MathNode, variable: String, calculator: DerivativeCalculator): MathNode {
        val varNode = node as MathNode.Variable
        return if (varNode.name == variable) {
            MathNode.Number(1.0)
        } else {
            MathNode.Number(0.0)
        }
    }
}

/**
 * 幂规则：d/dx(x^n) = n * x^(n-1)
 *
 * 测试：
 * - d/dx(x^2) = 2*x
 * - d/dx(x^3) = 3*x^2
 */
class PowerRule : DerivativeRule {
    override val name = "幂规则"
    override val priority = 100

    override fun matches(node: MathNode, variable: String): Boolean {
        return node is MathNode.BinaryOp
            && node.operator == Operator.POWER
            && node.left is MathNode.Variable
            && (node.left as MathNode.Variable).name == variable
            && node.right is MathNode.Number
    }

    override fun apply(node: MathNode, variable: String, calculator: DerivativeCalculator): MathNode {
        val powerNode = node as MathNode.BinaryOp
        val exponent = (powerNode.right as MathNode.Number).value

        // 特殊情况：x^1 → 1
        if (exponent == 1.0) {
            return MathNode.Number(1.0)
        }

        // 特殊情况：x^0 → 0
        if (exponent == 0.0) {
            return MathNode.Number(0.0)
        }

        // 一般情况：x^n → n * x^(n-1)
        return MathNode.BinaryOp(
            operator = Operator.MULTIPLY,
            left = MathNode.Number(exponent),
            right = MathNode.BinaryOp(
                operator = Operator.POWER,
                left = powerNode.left,
                right = MathNode.Number(exponent - 1.0)
            )
        )
    }
}

/**
 * 加法规则：d/dx(u + v) = du/dx + dv/dx
 *
 * 测试：d/dx(x^2 + x) = 2*x + 1
 */
class AdditionRule : DerivativeRule {
    override val name = "加法规则"
    override val priority = 90

    override fun matches(node: MathNode, variable: String): Boolean {
        return node is MathNode.BinaryOp && node.operator == Operator.ADD
    }

    override fun apply(node: MathNode, variable: String, calculator: DerivativeCalculator): MathNode {
        val addNode = node as MathNode.BinaryOp
        return MathNode.BinaryOp(
            operator = Operator.ADD,
            left = calculator.differentiate(addNode.left, variable),
            right = calculator.differentiate(addNode.right, variable)
        )
    }
}

/**
 * 减法规则：d/dx(u - v) = du/dx - dv/dx
 *
 * 测试：d/dx(x^2 - x) = 2*x - 1
 */
class SubtractionRule : DerivativeRule {
    override val name = "减法规则"
    override val priority = 90

    override fun matches(node: MathNode, variable: String): Boolean {
        return node is MathNode.BinaryOp && node.operator == Operator.SUBTRACT
    }

    override fun apply(node: MathNode, variable: String, calculator: DerivativeCalculator): MathNode {
        val subNode = node as MathNode.BinaryOp
        return MathNode.BinaryOp(
            operator = Operator.SUBTRACT,
            left = calculator.differentiate(subNode.left, variable),
            right = calculator.differentiate(subNode.right, variable)
        )
    }
}

/**
 * 乘积规则：d/dx(u * v) = u' * v + u * v'
 *
 * 测试：d/dx(3*x) = 3*1 + 0*x = 3
 */
class ProductRule : DerivativeRule {
    override val name = "乘积规则"
    override val priority = 80

    override fun matches(node: MathNode, variable: String): Boolean {
        return node is MathNode.BinaryOp && node.operator == Operator.MULTIPLY
    }

    override fun apply(node: MathNode, variable: String, calculator: DerivativeCalculator): MathNode {
        val mulNode = node as MathNode.BinaryOp
        val u = mulNode.left
        val v = mulNode.right
        val uPrime = calculator.differentiate(u, variable)
        val vPrime = calculator.differentiate(v, variable)

        // u' * v + u * v'
        return MathNode.BinaryOp(
            operator = Operator.ADD,
            left = MathNode.BinaryOp(Operator.MULTIPLY, uPrime, v),
            right = MathNode.BinaryOp(Operator.MULTIPLY, u, vPrime)
        )
    }
}