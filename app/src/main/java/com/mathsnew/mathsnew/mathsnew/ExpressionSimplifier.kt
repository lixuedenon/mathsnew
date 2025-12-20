// app/src/main/java/com/mathsnew/mathsnew/ExpressionSimplifier.kt
// 表达式简化器 - 深度优化版本，支持系数提前和排序

package com.mathsnew.mathsnew

/**
 * 表达式简化器（深度优化版）
 *
 * 功能：
 * 1. 基础代数简化（0+x=x, 1×x=x等）
 * 2. 常数合并（2×3=6）
 * 3. 同类项合并（2x+3x=5x）
 * 4. 系数提前（cos(x²)×2×x → 2×x×cos(x²)）
 * 5. 常数连乘合并（2×3×x → 6×x）
 * 6. 乘法项排序（常数→变量→函数）
 *
 * 采用多轮迭代简化策略，最多10轮
 */
class ExpressionSimplifier {

    /**
     * 简化表达式（多轮迭代直到不能再简化）
     *
     * @param node 要简化的AST节点
     * @return 简化后的AST节点
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
     *
     * @param node 要简化的AST节点
     * @return 简化后的AST节点
     */
    private fun simplifyOnce(node: MathNode): MathNode {
        return when (node) {
            is MathNode.Number -> node
            is MathNode.Variable -> node

            is MathNode.BinaryOp -> {
                // 递归简化左右子节点
                val left = simplifyOnce(node.left)
                val right = simplifyOnce(node.right)

                // 根据运算符类型进行简化
                when (node.operator) {
                    Operator.ADD -> simplifyAddition(left, right)
                    Operator.SUBTRACT -> simplifySubtraction(left, right)
                    Operator.MULTIPLY -> simplifyMultiplication(left, right)
                    Operator.DIVIDE -> simplifyDivision(left, right)
                    Operator.POWER -> simplifyPower(left, right)
                }
            }

            is MathNode.Function -> {
                // 递归简化函数参数
                val simplifiedArg = simplifyOnce(node.argument)
                MathNode.Function(node.name, simplifiedArg)
            }
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

        // 规则3: 常数相加
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

        // 规则3: x - x = 0
        if (left.toString() == right.toString()) {
            return MathNode.Number(0.0)
        }

        // 规则4: 常数相减
        if (left is MathNode.Number && right is MathNode.Number) {
            return MathNode.Number(left.value - right.value)
        }

        // 规则5: 同类项合并
        val combined = combineLikeTerms(left, right, Operator.SUBTRACT)
        if (combined != null) return combined

        return MathNode.BinaryOp(Operator.SUBTRACT, left, right)
    }

    /**
     * 简化乘法（深度优化版）
     *
     * 新增功能：
     * 1. 系数提前
     * 2. 常数连乘合并
     * 3. 乘法项排序（常数→变量→函数）
     */
    private fun simplifyMultiplication(left: MathNode, right: MathNode): MathNode {
        // 规则1: 0 × x = 0
        if ((left is MathNode.Number && left.value == 0.0) ||
            (right is MathNode.Number && right.value == 0.0)) {
            return MathNode.Number(0.0)
        }

        // 规则2: 1 × x = x
        if (left is MathNode.Number && left.value == 1.0) {
            return right
        }

        // 规则3: x × 1 = x
        if (right is MathNode.Number && right.value == 1.0) {
            return left
        }

        // 规则4: 常数相乘
        if (left is MathNode.Number && right is MathNode.Number) {
            return MathNode.Number(left.value * right.value)
        }

        // 规则5: 提取并合并常数（深度优化）
        // 2 × (3 × x) → 6 × x
        // cos(x) × 2 × x → 2 × x × cos(x)
        val optimized = extractAndCombineConstants(left, right)
        if (optimized != null) return optimized

        // 规则6: 同底数幂相乘 (x × x = x^2, x^2 × x^3 = x^5)
        val powerResult = multiplyPowers(left, right)
        if (powerResult != null) return powerResult

        // 规则7: 乘法项排序（常数→变量→函数）
        val sorted = sortMultiplicationTerms(left, right)
        if (sorted != null) return sorted

        return MathNode.BinaryOp(Operator.MULTIPLY, left, right)
    }

    /**
     * 提取并合并常数
     *
     * 处理以下情况：
     * 1. 2 × (3 × x) → 6 × x
     * 2. (2 × x) × 3 → 6 × x
     * 3. cos(x) × 2 → 2 × cos(x)（系数提前）
     */
    private fun extractAndCombineConstants(left: MathNode, right: MathNode): MathNode? {
        val leftCoeff = extractConstant(left)
        val rightCoeff = extractConstant(right)

        // 如果至少有一个常数
        if (leftCoeff != null || rightCoeff != null) {
            val leftTerm = removeConstant(left)
            val rightTerm = removeConstant(right)

            val coeff1 = leftCoeff ?: 1.0
            val coeff2 = rightCoeff ?: 1.0
            val combinedCoeff = coeff1 * coeff2

            // 构建结果
            val term = when {
                leftTerm != null && rightTerm != null ->
                    MathNode.BinaryOp(Operator.MULTIPLY, leftTerm, rightTerm)
                leftTerm != null -> leftTerm
                rightTerm != null -> rightTerm
                else -> null
            }

            return when {
                combinedCoeff == 1.0 && term != null -> term
                combinedCoeff == 0.0 -> MathNode.Number(0.0)
                term != null -> MathNode.BinaryOp(
                    Operator.MULTIPLY,
                    MathNode.Number(combinedCoeff),
                    term
                )
                else -> MathNode.Number(combinedCoeff)
            }
        }

        return null
    }

    /**
     * 从乘法表达式中提取常数
     *
     * @return 常数值，如果没有常数则返回null
     */
    private fun extractConstant(node: MathNode): Double? {
        return when (node) {
            is MathNode.Number -> node.value
            is MathNode.BinaryOp -> {
                if (node.operator == Operator.MULTIPLY) {
                    if (node.left is MathNode.Number) {
                        node.left.value
                    } else if (node.right is MathNode.Number) {
                        node.right.value
                    } else {
                        null
                    }
                } else {
                    null
                }
            }
            else -> null
        }
    }

    /**
     * 从乘法表达式中移除常数，返回剩余部分
     *
     * @return 移除常数后的表达式，如果表达式本身就是常数则返回null
     */
    private fun removeConstant(node: MathNode): MathNode? {
        return when (node) {
            is MathNode.Number -> null
            is MathNode.BinaryOp -> {
                if (node.operator == Operator.MULTIPLY) {
                    when {
                        node.left is MathNode.Number -> node.right
                        node.right is MathNode.Number -> node.left
                        else -> node
                    }
                } else {
                    node
                }
            }
            else -> node
        }
    }

    /**
     * 乘法项排序（常数→变量→函数）
     *
     * 例如：cos(x²) × x × 2 → 2 × x × cos(x²)
     */
    private fun sortMultiplicationTerms(left: MathNode, right: MathNode): MathNode? {
        val leftPriority = getTermPriority(left)
        val rightPriority = getTermPriority(right)

        // 如果需要交换顺序
        if (leftPriority > rightPriority) {
            return MathNode.BinaryOp(Operator.MULTIPLY, right, left)
        }

        return null
    }

    /**
     * 获取项的优先级（用于排序）
     *
     * @return 优先级数字（越小越靠前）
     */
    private fun getTermPriority(node: MathNode): Int {
        return when (node) {
            is MathNode.Number -> 1                    // 常数优先级最高（排最前）
            is MathNode.Variable -> 2                  // 变量次之
            is MathNode.Function -> 3                  // 函数最后
            is MathNode.BinaryOp -> {
                when (node.operator) {
                    Operator.MULTIPLY -> {
                        // 如果是乘法，取左右子节点优先级的最小值
                        minOf(getTermPriority(node.left), getTermPriority(node.right))
                    }
                    Operator.POWER -> 2                // 幂运算视为变量优先级
                    else -> 4                          // 其他运算优先级最低
                }
            }
        }
    }

    /**
     * 简化除法
     */
    private fun simplifyDivision(left: MathNode, right: MathNode): MathNode {
        // 规则1: 0 / x = 0
        if (left is MathNode.Number && left.value == 0.0) {
            return MathNode.Number(0.0)
        }

        // 规则2: x / 1 = x
        if (right is MathNode.Number && right.value == 1.0) {
            return left
        }

        // 规则3: x / x = 1
        if (left.toString() == right.toString()) {
            return MathNode.Number(1.0)
        }

        // 规则4: 常数相除
        if (left is MathNode.Number && right is MathNode.Number) {
            return MathNode.Number(left.value / right.value)
        }

        return MathNode.BinaryOp(Operator.DIVIDE, left, right)
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

        // 规则3: 0^x = 0 (x ≠ 0)
        if (base is MathNode.Number && base.value == 0.0) {
            return MathNode.Number(0.0)
        }

        // 规则4: 1^x = 1
        if (base is MathNode.Number && base.value == 1.0) {
            return MathNode.Number(1.0)
        }

        // 规则5: 常数的幂
        if (base is MathNode.Number && exponent is MathNode.Number) {
            return MathNode.Number(Math.pow(base.value, exponent.value))
        }

        return MathNode.BinaryOp(Operator.POWER, base, exponent)
    }

    /**
     * 同底数幂相乘：x * x = x^2, x^2 * x^3 = x^5
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
     *
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
     *
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
     *
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
     *
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
     *
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