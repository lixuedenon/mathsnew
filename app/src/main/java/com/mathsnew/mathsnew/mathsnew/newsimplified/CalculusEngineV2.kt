// app/src/main/java/com/mathsnew/mathsnew/newsimplified/CalculusEngineV2.kt
// V2版微积分计算引擎 - 支持多形式化简

package com.mathsnew.mathsnew.newsimplified

import com.mathsnew.mathsnew.*
import android.util.Log
import kotlin.math.abs

class CalculusEngineV2 {

    companion object {
        private const val TAG = "CalculusEngineV2"
        private const val EPSILON = 1e-10
    }

    private val parser = ExpressionParser()
    private val derivativeCalculator = DerivativeCalculator()
    private val simplifier = ExpressionSimplifierV2()
    private val selector = FormSelector()

    fun calculateDerivative(expression: String): CalculationResult {
        Log.d(TAG, "========================================")
        Log.d(TAG, "开始计算导数")
        Log.d(TAG, "表达式: $expression")

        return try {
            val ast = parser.parse(expression)
            Log.d(TAG, "AST: $ast")

            // 计算一阶导数
            val rawFirstDerivative = derivativeCalculator.differentiate(ast, "x")
            Log.d(TAG, "原始一阶导AST: $rawFirstDerivative")

            // 🔧 清理求导结果中的冗余
            val cleanedFirstDerivative = cleanAST(rawFirstDerivative)
            Log.d(TAG, "清理后一阶导AST: $cleanedFirstDerivative")

            // 生成多种形式
            val firstDerivForms = simplifier.simplifyToMultipleForms(cleanedFirstDerivative)
            Log.d(TAG, "一阶导生成了 ${firstDerivForms.forms.size} 种形式")

            // 🔧 修复：从 SimplificationForms 中提取 forms 列表
            val bestForSecond = selector.selectBestForDifferentiation(firstDerivForms.forms)
            Log.d(TAG, "选择用于二阶导的形式: $bestForSecond")

            // 计算二阶导数
            val rawSecondDerivative = derivativeCalculator.differentiate(bestForSecond, "x")
            Log.d(TAG, "原始二阶导AST: $rawSecondDerivative")

            // 🔧 清理二阶导数结果
            val cleanedSecondDerivative = cleanAST(rawSecondDerivative)
            Log.d(TAG, "清理后二阶导AST: $cleanedSecondDerivative")

            val secondDerivForms = simplifier.simplifyToMultipleForms(cleanedSecondDerivative)
            Log.d(TAG, "二阶导生成了 ${secondDerivForms.forms.size} 种形式")

            // 获取主要显示形式
            val firstDerivMain = firstDerivForms.forms.firstOrNull()?.expression
            val secondDerivMain = secondDerivForms.forms.firstOrNull()?.expression

            Log.d(TAG, "一阶导主形式: $firstDerivMain")
            Log.d(TAG, "二阶导主形式: $secondDerivMain")
            Log.d(TAG, "========================================")

            CalculationResult.Success(
                forms = firstDerivForms,
                displayText = firstDerivMain?.toString() ?: "",
                secondDerivativeForms = secondDerivForms,
                secondDerivativeDisplayText = secondDerivMain?.toString()
            )

        } catch (e: Exception) {
            Log.e(TAG, "计算失败: ${e.message}", e)
            CalculationResult.Error("计算失败: ${e.message}")
        }
    }

    /**
     * 清理AST中的冗余结构
     *
     * 清理规则：
     * 1. x^1.0 → x
     * 2. 0+x → x, x+0 → x
     * 3. 1×x → x, x×1 → x
     * 4. 0×x → 0, x×0 → 0
     * 5. 0.0+1.0 → 1.0 (常数计算)
     */
    private fun cleanAST(node: MathNode): MathNode {
        return when (node) {
            is MathNode.Number -> node
            is MathNode.Variable -> node

            is MathNode.Function -> {
                MathNode.Function(node.name, cleanAST(node.argument))
            }

            is MathNode.BinaryOp -> {
                val left = cleanAST(node.left)
                val right = cleanAST(node.right)

                when (node.operator) {
                    Operator.ADD -> cleanAddition(left, right)
                    Operator.SUBTRACT -> cleanSubtraction(left, right)
                    Operator.MULTIPLY -> cleanMultiplication(left, right)
                    Operator.DIVIDE -> cleanDivision(left, right)
                    Operator.POWER -> cleanPower(left, right)
                }
            }
        }
    }

    private fun cleanAddition(left: MathNode, right: MathNode): MathNode {
        // 0 + x = x
        if (left is MathNode.Number && abs(left.value) < EPSILON) {
            return right
        }
        // x + 0 = x
        if (right is MathNode.Number && abs(right.value) < EPSILON) {
            return left
        }
        // 常数相加
        if (left is MathNode.Number && right is MathNode.Number) {
            return MathNode.Number(left.value + right.value)
        }

        return MathNode.BinaryOp(Operator.ADD, left, right)
    }

    private fun cleanSubtraction(left: MathNode, right: MathNode): MathNode {
        // x - 0 = x
        if (right is MathNode.Number && abs(right.value) < EPSILON) {
            return left
        }
        // 0 - x = -x
        if (left is MathNode.Number && abs(left.value) < EPSILON) {
            return MathNode.BinaryOp(Operator.MULTIPLY, MathNode.Number(-1.0), right)
        }
        // 常数相减
        if (left is MathNode.Number && right is MathNode.Number) {
            return MathNode.Number(left.value - right.value)
        }

        return MathNode.BinaryOp(Operator.SUBTRACT, left, right)
    }

    private fun cleanMultiplication(left: MathNode, right: MathNode): MathNode {
        // 0 × x = 0
        if (left is MathNode.Number && abs(left.value) < EPSILON) {
            return MathNode.Number(0.0)
        }
        // x × 0 = 0
        if (right is MathNode.Number && abs(right.value) < EPSILON) {
            return MathNode.Number(0.0)
        }
        // 1 × x = x
        if (left is MathNode.Number && abs(left.value - 1.0) < EPSILON) {
            return right
        }
        // x × 1 = x
        if (right is MathNode.Number && abs(right.value - 1.0) < EPSILON) {
            return left
        }
        // 常数相乘
        if (left is MathNode.Number && right is MathNode.Number) {
            return MathNode.Number(left.value * right.value)
        }

        return MathNode.BinaryOp(Operator.MULTIPLY, left, right)
    }

    private fun cleanDivision(left: MathNode, right: MathNode): MathNode {
        // 0 / x = 0
        if (left is MathNode.Number && abs(left.value) < EPSILON) {
            return MathNode.Number(0.0)
        }
        // x / 1 = x
        if (right is MathNode.Number && abs(right.value - 1.0) < EPSILON) {
            return left
        }
        // 常数相除
        if (left is MathNode.Number && right is MathNode.Number) {
            if (abs(right.value) > EPSILON) {
                return MathNode.Number(left.value / right.value)
            }
        }

        return MathNode.BinaryOp(Operator.DIVIDE, left, right)
    }

    private fun cleanPower(base: MathNode, exponent: MathNode): MathNode {
        // x^0 = 1
        if (exponent is MathNode.Number && abs(exponent.value) < EPSILON) {
            return MathNode.Number(1.0)
        }
        // x^1 = x (关键！)
        if (exponent is MathNode.Number && abs(exponent.value - 1.0) < EPSILON) {
            return base
        }
        // 常数的幂
        if (base is MathNode.Number && exponent is MathNode.Number) {
            return MathNode.Number(Math.pow(base.value, exponent.value))
        }

        return MathNode.BinaryOp(Operator.POWER, base, exponent)
    }
}

sealed class CalculationResult {
    data class Success(
        val forms: SimplificationForms,
        val displayText: String,
        val secondDerivativeForms: SimplificationForms? = null,
        val secondDerivativeDisplayText: String? = null
    ) : CalculationResult()

    data class Error(val message: String) : CalculationResult()
}