// app/src/main/java/com/mathsnew/mathsnew/newsimplified/CalculusEngineV2.kt
// 微积分引擎 V2

package com.mathsnew.mathsnew.newsimplified

import android.util.Log
import com.mathsnew.mathsnew.*

class CalculusEngineV2 {

    private val parser = ExpressionParser()
    private val derivativeCalculator = DerivativeCalculator()
    private val canonicalizer = ExpressionCanonicalizer()
    private val formGenerator = FormGenerator()
    private val formSelector = FormSelector()
    private val formatter = MathFormatter()

    companion object {
        private const val TAG = "CalculusEngineV2"
        private const val EPSILON = 1e-10
    }

    fun calculateDerivative(expression: String): CalculationResult {
        try {
            Log.d(TAG, "========================================")
            Log.d(TAG, "开始计算导数: $expression")

            val startTime = System.currentTimeMillis()

            val ast = parser.parse(expression)
            Log.d(TAG, "解析完成: $ast")

            val rawDerivative = derivativeCalculator.differentiate(ast, "x")
            Log.d(TAG, "求导完成: $rawDerivative")

            val cleanedDerivative = cleanAST(rawDerivative)
            Log.d(TAG, "清理完成: $cleanedDerivative")

            val canonicalDerivative = canonicalizer.canonicalize(cleanedDerivative)
            Log.d(TAG, "规范化完成: $canonicalDerivative")

            val allForms = formGenerator.generateAllForms(canonicalDerivative)
            Log.d(TAG, "生成了 ${allForms.forms.size} 种形式")

            val bestForm = formSelector.selectBestForDifferentiation(allForms.getDisplayForms())
            Log.d(TAG, "选择最佳形式: ${bestForm.expression}")

            val rawSecondDerivative = derivativeCalculator.differentiate(cleanedDerivative, "x")
            Log.d(TAG, "二阶导数: $rawSecondDerivative")

            val cleanedSecondDerivative = cleanAST(rawSecondDerivative)
            val canonicalSecondDerivative = canonicalizer.canonicalize(cleanedSecondDerivative)
            val secondDerivativeForms = formGenerator.generateAllForms(canonicalSecondDerivative)

            val endTime = System.currentTimeMillis()
            Log.d(TAG, "✅ 总耗时: ${endTime - startTime}ms")
            Log.d(TAG, "========================================")

            val firstDerivativeText = formatter.format(bestForm.expression.toString())
            val secondBestForm = formSelector.selectBestForDifferentiation(secondDerivativeForms.getDisplayForms())
            val secondDerivativeText = formatter.format(secondBestForm.expression.toString())

            return CalculationResult.Success(
                displayText = firstDerivativeText.displayText,
                forms = allForms,
                secondDerivativeDisplayText = secondDerivativeText.displayText,
                secondDerivativeForms = secondDerivativeForms
            )

        } catch (e: Exception) {
            Log.e(TAG, "计算失败: ${e.message}", e)
            return CalculationResult.Error("计算错误: ${e.message}")
        }
    }

    private fun cleanAST(node: MathNode): MathNode {
        val step1 = removeZeroAdditions(node)
        val step2 = removeOneMultiplications(step1)
        val step3 = simplifyNegativeOne(step2)
        val step4 = removeDoubleNegation(step3)
        val step5 = simplifyZeroMultiplication(step4)
        val step6 = simplifyIdentityOperations(step5)
        val step7 = flattenNestedOperations(step6)
        val step8 = removeRedundantParentheses(step7)

        return step8
    }

    private fun removeZeroAdditions(node: MathNode): MathNode {
        return when (node) {
            is MathNode.BinaryOp -> {
                val left = removeZeroAdditions(node.left)
                val right = removeZeroAdditions(node.right)

                when (node.operator) {
                    Operator.ADD -> {
                        when {
                            isZero(left) -> right
                            isZero(right) -> left
                            else -> MathNode.BinaryOp(Operator.ADD, left, right)
                        }
                    }
                    Operator.SUBTRACT -> {
                        when {
                            isZero(right) -> left
                            else -> MathNode.BinaryOp(Operator.SUBTRACT, left, right)
                        }
                    }
                    else -> MathNode.BinaryOp(node.operator, left, right)
                }
            }
            is MathNode.Function -> MathNode.Function(node.name, removeZeroAdditions(node.argument))
            else -> node
        }
    }

    private fun removeOneMultiplications(node: MathNode): MathNode {
        return when (node) {
            is MathNode.BinaryOp -> {
                val left = removeOneMultiplications(node.left)
                val right = removeOneMultiplications(node.right)

                when (node.operator) {
                    Operator.MULTIPLY -> {
                        when {
                            isOne(left) -> right
                            isOne(right) -> left
                            else -> MathNode.BinaryOp(Operator.MULTIPLY, left, right)
                        }
                    }
                    Operator.DIVIDE -> {
                        when {
                            isOne(right) -> left
                            else -> MathNode.BinaryOp(Operator.DIVIDE, left, right)
                        }
                    }
                    else -> MathNode.BinaryOp(node.operator, left, right)
                }
            }
            is MathNode.Function -> MathNode.Function(node.name, removeOneMultiplications(node.argument))
            else -> node
        }
    }

    private fun simplifyNegativeOne(node: MathNode): MathNode {
        return when (node) {
            is MathNode.BinaryOp -> {
                val left = simplifyNegativeOne(node.left)
                val right = simplifyNegativeOne(node.right)

                if (node.operator == Operator.MULTIPLY) {
                    when {
                        isNegativeOne(left) && isNegativeOne(right) -> MathNode.Number(1.0)
                        isNegativeOne(left) -> MathNode.BinaryOp(
                            Operator.MULTIPLY,
                            MathNode.Number(-1.0),
                            right
                        )
                        isNegativeOne(right) -> MathNode.BinaryOp(
                            Operator.MULTIPLY,
                            MathNode.Number(-1.0),
                            left
                        )
                        else -> MathNode.BinaryOp(Operator.MULTIPLY, left, right)
                    }
                } else {
                    MathNode.BinaryOp(node.operator, left, right)
                }
            }
            is MathNode.Function -> MathNode.Function(node.name, simplifyNegativeOne(node.argument))
            else -> node
        }
    }

    private fun removeDoubleNegation(node: MathNode): MathNode {
        return when (node) {
            is MathNode.BinaryOp -> {
                val left = removeDoubleNegation(node.left)
                val right = removeDoubleNegation(node.right)

                if (node.operator == Operator.MULTIPLY &&
                    left is MathNode.Number && left.value == -1.0 &&
                    right is MathNode.BinaryOp && right.operator == Operator.MULTIPLY &&
                    right.left is MathNode.Number && right.left.value == -1.0) {
                    right.right
                } else {
                    MathNode.BinaryOp(node.operator, left, right)
                }
            }
            is MathNode.Function -> MathNode.Function(node.name, removeDoubleNegation(node.argument))
            else -> node
        }
    }

    private fun simplifyZeroMultiplication(node: MathNode): MathNode {
        return when (node) {
            is MathNode.BinaryOp -> {
                val left = simplifyZeroMultiplication(node.left)
                val right = simplifyZeroMultiplication(node.right)

                if (node.operator == Operator.MULTIPLY) {
                    when {
                        isZero(left) || isZero(right) -> MathNode.Number(0.0)
                        else -> MathNode.BinaryOp(Operator.MULTIPLY, left, right)
                    }
                } else {
                    MathNode.BinaryOp(node.operator, left, right)
                }
            }
            is MathNode.Function -> MathNode.Function(node.name, simplifyZeroMultiplication(node.argument))
            else -> node
        }
    }

    private fun simplifyIdentityOperations(node: MathNode): MathNode {
        return when (node) {
            is MathNode.BinaryOp -> {
                val left = simplifyIdentityOperations(node.left)
                val right = simplifyIdentityOperations(node.right)

                when (node.operator) {
                    Operator.POWER -> {
                        when {
                            isZero(right) -> MathNode.Number(1.0)
                            isOne(right) -> left
                            else -> MathNode.BinaryOp(Operator.POWER, left, right)
                        }
                    }
                    else -> MathNode.BinaryOp(node.operator, left, right)
                }
            }
            is MathNode.Function -> MathNode.Function(node.name, simplifyIdentityOperations(node.argument))
            else -> node
        }
    }

    private fun flattenNestedOperations(node: MathNode): MathNode {
        return when (node) {
            is MathNode.BinaryOp -> {
                val left = flattenNestedOperations(node.left)
                val right = flattenNestedOperations(node.right)
                MathNode.BinaryOp(node.operator, left, right)
            }
            is MathNode.Function -> MathNode.Function(node.name, flattenNestedOperations(node.argument))
            else -> node
        }
    }

    private fun removeRedundantParentheses(node: MathNode): MathNode {
        return when (node) {
            is MathNode.BinaryOp -> {
                val left = removeRedundantParentheses(node.left)
                val right = removeRedundantParentheses(node.right)
                MathNode.BinaryOp(node.operator, left, right)
            }
            is MathNode.Function -> MathNode.Function(node.name, removeRedundantParentheses(node.argument))
            else -> node
        }
    }

    private fun isZero(node: MathNode): Boolean {
        return node is MathNode.Number && kotlin.math.abs(node.value) < EPSILON
    }

    private fun isOne(node: MathNode): Boolean {
        return node is MathNode.Number && kotlin.math.abs(node.value - 1.0) < EPSILON
    }

    private fun isNegativeOne(node: MathNode): Boolean {
        return node is MathNode.Number && kotlin.math.abs(node.value + 1.0) < EPSILON
    }
}

sealed class CalculationResult {
    data class Success(
        val displayText: CharSequence,
        val forms: SimplificationFormsV2,
        val secondDerivativeDisplayText: CharSequence?,
        val secondDerivativeForms: SimplificationFormsV2?
    ) : CalculationResult()

    data class Error(val message: String) : CalculationResult()
}
