// app/src/main/java/com/mathsnew/mathsnew/ExpressionSimplifier.kt
// 表达式简化器（深度简化）

package com.mathsnew.mathsnew

/**
 * 表达式简化器
 * 将复杂的微分结果简化为最简形式
 *
 * 简化规则分类：
 * 1. 基础代数简化（20个规则）
 * 2. 常数合并
 * 3. 同类项合并
 * 4. 指数合并
 * 5. 分式约分
 *
 * 测试示例：
 * - simplify(1*x) → x
 * - simplify(x^1) → x
 * - simplify(0+x) → x
 * - simplify(2*3*x) → 6*x
 * - simplify(x*x*x) → x^3
 * - simplify(0*sin(x)+3*1) → 3
 * - simplify(2*x/x^2) → 2/x
 */
class ExpressionSimplifier {

    /**
     * 简化表达式（多轮简化直到不能再简化）
     */
    fun simplify(node: MathNode): MathNode {
        var current = node
        var previous: MathNode

        // 最多简化10轮，避免无限循环
        repeat(10) {
            previous = current
            current = simplifyOnce(current)

            // 如果两轮结果相同，说明已经无法继续简化
            if (current.toString() == previous.toString()) {
                return current
            }
        }

        return current
    }

    /**
     * 单轮简化
     */
    private fun simplifyOnce(node: MathNode): MathNode {
        return when (node) {
            is MathNode.Number -> node
            is MathNode.Variable -> node
            is MathNode.Function -> simplifyFunction(node)
            is MathNode.BinaryOp -> simplifyBinaryOp(node)
        }
    }

    /**
     * 简化函数节点
     */
    private fun simplifyFunction(node: MathNode.Function): MathNode {
        val simplifiedArg = simplifyOnce(node.argument)
        return MathNode.Function(node.name, simplifiedArg)
    }

    /**
     * 简化二元运算节点
     */
    private fun simplifyBinaryOp(node: MathNode.BinaryOp): MathNode {
        // 先递归简化左右子树
        val left = simplifyOnce(node.left)
        val right = simplifyOnce(node.right)

        return when (node.operator) {
            Operator.ADD -> simplifyAddition(left, right)
            Operator.SUBTRACT -> simplifySubtraction(left, right)
            Operator.MULTIPLY -> simplifyMultiplication(left, right)
            Operator.DIVIDE -> simplifyDivision(left, right)
            Operator.POWER -> simplifyPower(left, right)
        }
    }

    /**
     * 简化加法
     */
    private fun simplifyAddition(left: MathNode, right: MathNode): MathNode {
        // 规则1: 0 + x = x
        if (left is MathNode.Number && left.value == 0.0) {
            return right
        }

        // 规则2: x + 0 = x
        if (right is MathNode.Number && right.value == 0.0) {
            return left
        }

        // 规则3: 常数相加 (5 + 3 = 8)
        if (left is MathNode.Number && right is MathNode.Number) {
            return MathNode.Number(left.value + right.value)
        }

        // 规则4: 同类项合并 (2*x + 3*x = 5*x)
        val combined = combineLikeTerms(left, right, Operator.ADD)
        if (combined != null) return combined

        return MathNode.BinaryOp(Operator.ADD, left, right)
    }

    /**
     * 简化减法
     */
    private fun simplifySubtraction(left: MathNode, right: MathNode): MathNode {
        // 规则1: x - 0 = x
        if (right is MathNode.Number && right.value == 0.0) {
            return left
        }

        // 规则2: 0 - x = -x
        if (left is MathNode.Number && left.value == 0.0) {
            return MathNode.BinaryOp(
                Operator.MULTIPLY,
                MathNode.Number(-1.0),
                right
            )
        }

        // 规则3: 常数相减 (5 - 3 = 2)
        if (left is MathNode.Number && right is MathNode.Number) {
            return MathNode.Number(left.value - right.value)
        }

        // 规则4: x - x = 0
        if (left.toString() == right.toString()) {
            return MathNode.Number(0.0)
        }

        return MathNode.BinaryOp(Operator.SUBTRACT, left, right)
    }

    /**
     * 简化乘法（核心：处理大部分复杂情况）
     */
    private fun simplifyMultiplication(left: MathNode, right: MathNode): MathNode {
        // 规则1: 0 * x = 0
        if ((left is MathNode.Number && left.value == 0.0) ||
            (right is MathNode.Number && right.value == 0.0)) {
            return MathNode.Number(0.0)
        }

        // 规则2: 1 * x = x
        if (left is MathNode.Number && left.value == 1.0) {
            return right
        }

        // 规则3: x * 1 = x
        if (right is MathNode.Number && right.value == 1.0) {
            return left
        }

        // 规则4: -1 * x = -x (保持)
        if (left is MathNode.Number && left.value == -1.0) {
            return MathNode.BinaryOp(Operator.MULTIPLY, left, right)
        }

        // 规则5: 常数相乘 (2 * 3 = 6)
        if (left is MathNode.Number && right is MathNode.Number) {
            return MathNode.Number(left.value * right.value)
        }

        // 规则6: 提取常数相乘 (2 * (3 * x) = 6 * x)
        if (left is MathNode.Number && right is MathNode.BinaryOp &&
            right.operator == Operator.MULTIPLY && right.left is MathNode.Number) {
            val newCoeff = left.value * right.left.value
            return simplifyMultiplication(MathNode.Number(newCoeff), right.right)
        }

        // 规则7: 提取常数相乘 ((2 * x) * 3 = 6 * x)
        if (right is MathNode.Number && left is MathNode.BinaryOp &&
            left.operator == Operator.MULTIPLY && left.left is MathNode.Number) {
            val newCoeff = left.left.value * right.value
            return simplifyMultiplication(MathNode.Number(newCoeff), left.right)
        }

        // 规则8: 同底数相乘 (x * x = x^2, x^2 * x = x^3)
        val powerResult = multiplyPowers(left, right)
        if (powerResult != null) return powerResult

        // 规则9: 常数与多项式相乘 (2 * (x + y) = 2*x + 2*y)
        if (left is MathNode.Number && right is MathNode.BinaryOp &&
            (right.operator == Operator.ADD || right.operator == Operator.SUBTRACT)) {
            return MathNode.BinaryOp(
                right.operator,
                simplifyMultiplication(left, right.left),
                simplifyMultiplication(left, right.right)
            )
        }

        return MathNode.BinaryOp(Operator.MULTIPLY, left, right)
    }

    /**
     * 简化除法（增强版）
     */
    private fun simplifyDivision(left: MathNode, right: MathNode): MathNode {
        // 规则1: 0 / x = 0 (x ≠ 0)
        if (left is MathNode.Number && left.value == 0.0) {
            return MathNode.Number(0.0)
        }

        // 规则2: x / 1 = x
        if (right is MathNode.Number && right.value == 1.0) {
            return left
        }

        // 规则3: 常数相除 (6 / 2 = 3)
        if (left is MathNode.Number && right is MathNode.Number && right.value != 0.0) {
            return MathNode.Number(left.value / right.value)
        }

        // 规则4: x / x = 1
        if (left.toString() == right.toString()) {
            return MathNode.Number(1.0)
        }

        // 规则5: 约分相同因子 (2*x / x^2 = 2 / x)
        val simplified = simplifyFraction(left, right)
        if (simplified != null) return simplified

        return MathNode.BinaryOp(Operator.DIVIDE, left, right)
    }

    /**
     * 分式约分
     *
     * 处理情况：
     * - a*x / x^n → a / x^(n-1)
     * - x^m / x^n → x^(m-n) 或 1/x^(n-m)
     * - a*x^m / x^n → a*x^(m-n) 或 a/x^(n-m)
     */
    private fun simplifyFraction(numerator: MathNode, denominator: MathNode): MathNode? {
        // 获取分子分母的基数和指数
        val numBase = getBase(numerator)
        val denBase = getBase(denominator)

        // 基数必须相同才能约分
        if (numBase.toString() != denBase.toString()) {
            return null
        }

        val numCoeff = getCoefficient(numerator)
        val denCoeff = getCoefficient(denominator)
        val numExp = getExponent(numerator)
        val denExp = getExponent(denominator)

        // 计算约分后的系数
        val newCoeff = numCoeff / denCoeff

        // 计算约分后的指数
        val expDiff = numExp - denExp

        return when {
            // 指数差为0：只剩系数
            expDiff == 0.0 -> {
                if (newCoeff == 1.0) {
                    MathNode.Number(1.0)
                } else {
                    MathNode.Number(newCoeff)
                }
            }
            // 指数差为正：结果在分子
            expDiff > 0.0 -> {
                val powerNode = if (expDiff == 1.0) {
                    numBase
                } else {
                    MathNode.BinaryOp(Operator.POWER, numBase, MathNode.Number(expDiff))
                }

                if (newCoeff == 1.0) {
                    powerNode
                } else {
                    MathNode.BinaryOp(Operator.MULTIPLY, MathNode.Number(newCoeff), powerNode)
                }
            }
            // 指数差为负：结果在分母
            else -> {
                val powerNode = if (expDiff == -1.0) {
                    numBase
                } else {
                    MathNode.BinaryOp(Operator.POWER, numBase, MathNode.Number(-expDiff))
                }

                if (newCoeff == 1.0) {
                    MathNode.BinaryOp(Operator.DIVIDE, MathNode.Number(1.0), powerNode)
                } else {
                    MathNode.BinaryOp(Operator.DIVIDE, MathNode.Number(newCoeff), powerNode)
                }
            }
        }
    }

    /**
     * 简化幂运算
     */
    private fun simplifyPower(base: MathNode, exponent: MathNode): MathNode {
        // 规则1: x^0 = 1
        if (exponent is MathNode.Number && exponent.value == 0.0) {
            return MathNode.Number(1.0)
        }

        // 规则2: x^1 = x
        if (exponent is MathNode.Number && exponent.value == 1.0) {
            return base
        }

        // 规则3: 0^n = 0 (n > 0)
        if (base is MathNode.Number && base.value == 0.0 &&
            exponent is MathNode.Number && exponent.value > 0.0) {
            return MathNode.Number(0.0)
        }

        // 规则4: 1^n = 1
        if (base is MathNode.Number && base.value == 1.0) {
            return MathNode.Number(1.0)
        }

        // 规则5: 常数的幂 (2^3 = 8)
        if (base is MathNode.Number && exponent is MathNode.Number) {
            return MathNode.Number(Math.pow(base.value, exponent.value))
        }

        // 规则6: (x^m)^n = x^(m*n)
        if (base is MathNode.BinaryOp && base.operator == Operator.POWER &&
            base.right is MathNode.Number && exponent is MathNode.Number) {
            val newExponent = base.right.value * exponent.value
            return simplifyPower(base.left, MathNode.Number(newExponent))
        }

        return MathNode.BinaryOp(Operator.POWER, base, exponent)
    }

    /**
     * 同底数幂相乘：x * x = x^2, x^2 * x = x^3, x^2 * x^3 = x^5
     */
    private fun multiplyPowers(left: MathNode, right: MathNode): MathNode? {
        val leftBase = getBase(left)
        val rightBase = getBase(right)

        // 底数必须相同
        if (leftBase.toString() != rightBase.toString()) {
            return null
        }

        val leftExp = getExponent(left)
        val rightExp = getExponent(right)

        // 指数相加
        val newExponent = leftExp + rightExp

        return if (newExponent == 1.0) {
            leftBase  // x^1 = x
        } else {
            MathNode.BinaryOp(Operator.POWER, leftBase, MathNode.Number(newExponent))
        }
    }

    /**
     * 获取表达式的底数
     * x → x, x^2 → x, 3*x → x, 3*x^2 → x
     */
    private fun getBase(node: MathNode): MathNode {
        return when (node) {
            is MathNode.Variable -> node
            is MathNode.BinaryOp -> {
                when (node.operator) {
                    Operator.POWER -> node.left
                    Operator.MULTIPLY -> {
                        if (node.left is MathNode.Number) getBase(node.right)
                        else getBase(node.left)
                    }
                    else -> node
                }
            }
            else -> node
        }
    }

    /**
     * 获取表达式的指数
     * x → 1, x^2 → 2, 3*x → 1, 3*x^2 → 2
     */
    private fun getExponent(node: MathNode): Double {
        return when (node) {
            is MathNode.Variable -> 1.0
            is MathNode.BinaryOp -> {
                when (node.operator) {
                    Operator.POWER -> {
                        if (node.right is MathNode.Number) node.right.value else 1.0
                    }
                    Operator.MULTIPLY -> {
                        if (node.left is MathNode.Number) getExponent(node.right)
                        else getExponent(node.left)
                    }
                    else -> 1.0
                }
            }
            else -> 1.0
        }
    }

    /**
     * 合并同类项
     * 2*x + 3*x = 5*x
     * x + 2*x = 3*x
     */
    private fun combineLikeTerms(left: MathNode, right: MathNode, operator: Operator): MathNode? {
        val leftCoeff = getCoefficient(left)
        val rightCoeff = getCoefficient(right)
        val leftTerm = getTerm(left)
        val rightTerm = getTerm(right)

        // 必须是同类项
        if (leftTerm.toString() != rightTerm.toString()) {
            return null
        }

        // 计算新系数
        val newCoeff = when (operator) {
            Operator.ADD -> leftCoeff + rightCoeff
            Operator.SUBTRACT -> leftCoeff - rightCoeff
            else -> return null
        }

        // 生成结果
        return when {
            newCoeff == 0.0 -> MathNode.Number(0.0)
            newCoeff == 1.0 -> leftTerm
            newCoeff == -1.0 -> MathNode.BinaryOp(
                Operator.MULTIPLY,
                MathNode.Number(-1.0),
                leftTerm
            )
            else -> MathNode.BinaryOp(
                Operator.MULTIPLY,
                MathNode.Number(newCoeff),
                leftTerm
            )
        }
    }

    /**
     * 获取系数
     * 3*x → 3, x → 1, -2*x → -2
     */
    private fun getCoefficient(node: MathNode): Double {
        return when (node) {
            is MathNode.Number -> node.value
            is MathNode.Variable -> 1.0
            is MathNode.Function -> 1.0
            is MathNode.BinaryOp -> {
                when (node.operator) {
                    Operator.MULTIPLY -> {
                        if (node.left is MathNode.Number) node.left.value
                        else 1.0
                    }
                    else -> 1.0
                }
            }
        }
    }

    /**
     * 获取项（去掉系数）
     * 3*x → x, x → x, 2*sin(x) → sin(x)
     */
    private fun getTerm(node: MathNode): MathNode {
        return when (node) {
            is MathNode.Number -> MathNode.Number(1.0)
            is MathNode.Variable -> node
            is MathNode.Function -> node
            is MathNode.BinaryOp -> {
                when (node.operator) {
                    Operator.MULTIPLY -> {
                        if (node.left is MathNode.Number) node.right
                        else node
                    }
                    else -> node
                }
            }
        }
    }
}