// app/src/main/java/com/mathsnew/mathsnew/ExpressionSimplifier.kt
// 表达式简化器（修复 -1.0 简化问题）

package com.mathsnew.mathsnew

import android.util.Log

class ExpressionSimplifier {

    fun simplify(node: MathNode): MathNode {
        var current = node
        var previous: MathNode
        var iterations = 0
        val maxIterations = 10

        do {
            previous = current
            current = simplifyOnce(current)
            iterations++
        } while (current != previous && iterations < maxIterations)

        return current
    }

    private fun simplifyOnce(node: MathNode): MathNode {
        return when (node) {
            is MathNode.Number -> node
            is MathNode.Variable -> node
            is MathNode.Function -> {
                MathNode.Function(node.name, simplifyOnce(node.argument))
            }
            is MathNode.BinaryOp -> {
                val left = simplifyOnce(node.left)
                val right = simplifyOnce(node.right)
                simplifyBinaryOp(node.operator, left, right)
            }
        }
    }

    private fun simplifyBinaryOp(op: Operator, left: MathNode, right: MathNode): MathNode {
        return when (op) {
            Operator.ADD -> simplifyAddition(left, right)
            Operator.SUBTRACT -> simplifySubtraction(left, right)
            Operator.MULTIPLY -> simplifyMultiplication(left, right)
            Operator.DIVIDE -> simplifyDivision(left, right)
            Operator.POWER -> simplifyPower(left, right)
        }
    }

    private fun simplifyAddition(left: MathNode, right: MathNode): MathNode {
        if (left is MathNode.Number && left.value == 0.0) return right
        if (right is MathNode.Number && right.value == 0.0) return left

        if (left is MathNode.Number && right is MathNode.Number) {
            return MathNode.Number(left.value + right.value)
        }

        val leftCoeff = extractCoefficient(left)
        val rightCoeff = extractCoefficient(right)
        val leftBase = extractBase(left)
        val rightBase = extractBase(right)

        if (leftBase == rightBase) {
            val newCoeff = leftCoeff + rightCoeff
            return if (newCoeff == 1.0) {
                leftBase
            } else {
                MathNode.BinaryOp(Operator.MULTIPLY, MathNode.Number(newCoeff), leftBase)
            }
        }

        return MathNode.BinaryOp(Operator.ADD, left, right)
    }

    private fun simplifySubtraction(left: MathNode, right: MathNode): MathNode {
        if (right is MathNode.Number && right.value == 0.0) return left
        if (left is MathNode.Number && left.value == 0.0) {
            return MathNode.BinaryOp(
                Operator.MULTIPLY,
                MathNode.Number(-1.0),
                right
            )
        }

        if (left is MathNode.Number && right is MathNode.Number) {
            return MathNode.Number(left.value - right.value)
        }

        if (left == right) {
            return MathNode.Number(0.0)
        }

        return MathNode.BinaryOp(Operator.SUBTRACT, left, right)
    }

    /**
     * 简化乘法
     *
     * ⚠️ 关键修复：-1.0 × x 应该简化为 -x
     */
    private fun simplifyMultiplication(left: MathNode, right: MathNode): MathNode {
        // 0 × x = 0
        if (left is MathNode.Number && left.value == 0.0) return MathNode.Number(0.0)
        if (right is MathNode.Number && right.value == 0.0) return MathNode.Number(0.0)

        // 1 × x = x
        if (left is MathNode.Number && left.value == 1.0) return right
        if (right is MathNode.Number && right.value == 1.0) return left

        // ⚠️ 新增：-1 × x = -x（用减法表示）
        if (left is MathNode.Number && left.value == -1.0) {
            return MathNode.BinaryOp(
                Operator.SUBTRACT,
                MathNode.Number(0.0),
                right
            )
        }
        if (right is MathNode.Number && right.value == -1.0) {
            return MathNode.BinaryOp(
                Operator.SUBTRACT,
                MathNode.Number(0.0),
                left
            )
        }

        // 数字相乘
        if (left is MathNode.Number && right is MathNode.Number) {
            return MathNode.Number(left.value * right.value)
        }

        // x × x = x²
        if (left == right && left is MathNode.Variable) {
            return MathNode.BinaryOp(Operator.POWER, left, MathNode.Number(2.0))
        }

        return MathNode.BinaryOp(Operator.MULTIPLY, left, right)
    }

    private fun simplifyDivision(left: MathNode, right: MathNode): MathNode {
        Log.d("Simplifier", "简化除法: $left / $right")

        if (right is MathNode.Number && right.value == 1.0) {
            Log.d("Simplifier", "  规则: x/1 = x")
            return left
        }

        if (left is MathNode.Number && left.value == 0.0) {
            Log.d("Simplifier", "  规则: 0/x = 0")
            return MathNode.Number(0.0)
        }

        if (left is MathNode.Number && right is MathNode.Number) {
            Log.d("Simplifier", "  规则: 数字相除")
            return MathNode.Number(left.value / right.value)
        }

        if (left == right) {
            Log.d("Simplifier", "  规则: x/x = 1")
            return MathNode.Number(1.0)
        }

        val simplified = simplifyFraction(left, right)
        if (simplified != null) {
            Log.d("Simplifier", "  规则: 约分 -> $simplified")
            return simplified
        }

        Log.d("Simplifier", "  无简化，保持除法")
        val result = MathNode.BinaryOp(Operator.DIVIDE, left, right)
        Log.d("Simplifier", "  结果类型: ${result.operator}")
        return result
    }

    private fun simplifyFraction(numerator: MathNode, denominator: MathNode): MathNode? {
        val numBase = getBase(numerator)
        val numCoeff = getCoefficient(numerator)
        val numExp = getExponent(numerator)

        val denBase = getBase(denominator)
        val denCoeff = getCoefficient(denominator)
        val denExp = getExponent(denominator)

        if (numBase != denBase) {
            return null
        }

        val resultCoeff = numCoeff / denCoeff
        val resultExp = numExp - denExp

        return when {
            resultCoeff == 1.0 && resultExp == 0.0 -> {
                MathNode.Number(1.0)
            }
            resultExp == 0.0 -> {
                MathNode.Number(resultCoeff)
            }
            resultCoeff == 1.0 && resultExp == 1.0 -> {
                numBase
            }
            resultCoeff == 1.0 && resultExp == -1.0 -> {
                MathNode.BinaryOp(Operator.DIVIDE, MathNode.Number(1.0), numBase)
            }
            resultExp == -1.0 -> {
                MathNode.BinaryOp(
                    Operator.DIVIDE,
                    MathNode.Number(resultCoeff),
                    numBase
                )
            }
            resultCoeff == 1.0 && resultExp > 0.0 -> {
                MathNode.BinaryOp(Operator.POWER, numBase, MathNode.Number(resultExp))
            }
            resultCoeff == 1.0 && resultExp < 0.0 -> {
                MathNode.BinaryOp(
                    Operator.DIVIDE,
                    MathNode.Number(1.0),
                    MathNode.BinaryOp(Operator.POWER, numBase, MathNode.Number(-resultExp))
                )
            }
            resultExp > 0.0 -> {
                MathNode.BinaryOp(
                    Operator.MULTIPLY,
                    MathNode.Number(resultCoeff),
                    MathNode.BinaryOp(Operator.POWER, numBase, MathNode.Number(resultExp))
                )
            }
            resultExp < 0.0 -> {
                MathNode.BinaryOp(
                    Operator.DIVIDE,
                    MathNode.Number(resultCoeff),
                    MathNode.BinaryOp(Operator.POWER, numBase, MathNode.Number(-resultExp))
                )
            }
            else -> null
        }
    }

    private fun getBase(node: MathNode): MathNode {
        return when (node) {
            is MathNode.Variable -> node
            is MathNode.BinaryOp -> {
                when (node.operator) {
                    Operator.MULTIPLY -> {
                        if (node.left is MathNode.Number) getBase(node.right)
                        else getBase(node.left)
                    }
                    Operator.POWER -> getBase(node.left)
                    else -> node
                }
            }
            else -> node
        }
    }

    private fun getExponent(node: MathNode): Double {
        return when (node) {
            is MathNode.Variable -> 1.0
            is MathNode.BinaryOp -> {
                when (node.operator) {
                    Operator.MULTIPLY -> {
                        if (node.left is MathNode.Number) getExponent(node.right)
                        else getExponent(node.left)
                    }
                    Operator.POWER -> {
                        if (node.right is MathNode.Number) node.right.value
                        else 1.0
                    }
                    else -> 1.0
                }
            }
            else -> 1.0
        }
    }

    private fun getCoefficient(node: MathNode): Double {
        return when (node) {
            is MathNode.Number -> node.value
            is MathNode.Variable -> 1.0
            is MathNode.BinaryOp -> {
                when (node.operator) {
                    Operator.MULTIPLY -> {
                        if (node.left is MathNode.Number) node.left.value
                        else 1.0
                    }
                    else -> 1.0
                }
            }
            else -> 1.0
        }
    }

    private fun simplifyPower(left: MathNode, right: MathNode): MathNode {
        if (right is MathNode.Number && right.value == 0.0) {
            return MathNode.Number(1.0)
        }

        if (right is MathNode.Number && right.value == 1.0) {
            return left
        }

        if (left is MathNode.Number && left.value == 1.0) {
            return MathNode.Number(1.0)
        }

        if (left is MathNode.Number && right is MathNode.Number) {
            return MathNode.Number(Math.pow(left.value, right.value))
        }

        return MathNode.BinaryOp(Operator.POWER, left, right)
    }

    private fun extractCoefficient(node: MathNode): Double {
        return when (node) {
            is MathNode.Number -> node.value
            is MathNode.BinaryOp -> {
                if (node.operator == Operator.MULTIPLY && node.left is MathNode.Number) {
                    node.left.value
                } else {
                    1.0
                }
            }
            else -> 1.0
        }
    }

    private fun extractBase(node: MathNode): MathNode {
        return when (node) {
            is MathNode.BinaryOp -> {
                if (node.operator == Operator.MULTIPLY && node.left is MathNode.Number) {
                    node.right
                } else {
                    node
                }
            }
            else -> node
        }
    }
}