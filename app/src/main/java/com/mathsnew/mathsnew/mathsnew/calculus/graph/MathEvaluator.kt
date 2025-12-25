// app/src/main/java/com/mathsnew/mathsnew/calculus/graph/MathEvaluator.kt
// AST求值器

package com.mathsnew.mathsnew.calculus.graph

import android.util.Log
import com.mathsnew.mathsnew.CalculationException
import com.mathsnew.mathsnew.MathNode
import com.mathsnew.mathsnew.Operator
import kotlin.math.*

/**
 * AST求值器
 * 将抽象语法树（AST）在给定的x值下计算出具体的数值结果
 *
 * 支持的运算：
 * - 四则运算：+, -, ×, /
 * - 幂运算：^
 * - 三角函数：sin, cos, tan, cot, sec, csc
 * - 指数对数：exp, ln, log
 * - 其他函数：sqrt, abs
 */
class MathEvaluator {

    /**
     * 计算AST在给定x值时的结果
     *
     * @param node AST节点
     * @param xValue x的值
     * @return 计算结果
     * @throws CalculationException 当计算遇到错误时（如除以0、定义域错误等）
     */
    fun evaluate(node: MathNode, xValue: Double): Double {
        return when (node) {
            is MathNode.Number -> evaluateNumber(node)
            is MathNode.Variable -> evaluateVariable(node, xValue)
            is MathNode.BinaryOp -> evaluateBinaryOp(node, xValue)
            is MathNode.Function -> evaluateFunction(node, xValue)
        }
    }

    /**
     * 计算数字节点
     */
    private fun evaluateNumber(node: MathNode.Number): Double {
        return node.value
    }

    /**
     * 计算变量节点
     * 当前只支持变量x，其他变量视为常数
     */
    private fun evaluateVariable(node: MathNode.Variable, xValue: Double): Double {
        return when (node.name) {
            "x" -> xValue
            "π", "pi" -> Math.PI
            "e" -> Math.E
            else -> {
                Log.w("MathEvaluator", "未知变量: ${node.name}，视为常数0")
                0.0
            }
        }
    }

    /**
     * 计算二元运算节点
     */
    private fun evaluateBinaryOp(node: MathNode.BinaryOp, xValue: Double): Double {
        val leftValue = evaluate(node.left, xValue)
        val rightValue = evaluate(node.right, xValue)

        // 检查左右值是否有效
        if (!leftValue.isFinite() || !rightValue.isFinite()) {
            return Double.NaN
        }

        return when (node.operator) {
            Operator.ADD -> leftValue + rightValue
            Operator.SUBTRACT -> leftValue - rightValue
            Operator.MULTIPLY -> leftValue * rightValue
            Operator.DIVIDE -> {
                if (abs(rightValue) < 1e-10) {
                    Log.w("MathEvaluator", "除以0: $leftValue / $rightValue at x=$xValue")
                    Double.NaN
                } else {
                    leftValue / rightValue
                }
            }
            Operator.POWER -> {
                try {
                    val result = leftValue.pow(rightValue)
                    if (!result.isFinite()) {
                        Log.w("MathEvaluator", "幂运算溢出: $leftValue ^ $rightValue at x=$xValue")
                        Double.NaN
                    } else {
                        result
                    }
                } catch (e: Exception) {
                    Log.w("MathEvaluator", "幂运算错误: $leftValue ^ $rightValue", e)
                    Double.NaN
                }
            }
        }
    }

    /**
     * 计算函数节点
     */
    private fun evaluateFunction(node: MathNode.Function, xValue: Double): Double {
        val argValue = evaluate(node.argument, xValue)

        // 检查参数是否有效
        if (!argValue.isFinite()) {
            return Double.NaN
        }

        return when (node.name) {
            // 三角函数
            "sin" -> sin(argValue)
            "cos" -> cos(argValue)
            "tan" -> {
                val result = tan(argValue)
                if (!result.isFinite()) {
                    Log.w("MathEvaluator", "tan值无限: tan($argValue) at x=$xValue")
                    Double.NaN
                } else {
                    result
                }
            }
            "cot" -> {
                val sinValue = sin(argValue)
                if (abs(sinValue) < 1e-10) {
                    Log.w("MathEvaluator", "cot未定义: sin($argValue) ≈ 0 at x=$xValue")
                    Double.NaN
                } else {
                    cos(argValue) / sinValue
                }
            }
            "sec" -> {
                val cosValue = cos(argValue)
                if (abs(cosValue) < 1e-10) {
                    Log.w("MathEvaluator", "sec未定义: cos($argValue) ≈ 0 at x=$xValue")
                    Double.NaN
                } else {
                    1.0 / cosValue
                }
            }
            "csc" -> {
                val sinValue = sin(argValue)
                if (abs(sinValue) < 1e-10) {
                    Log.w("MathEvaluator", "csc未定义: sin($argValue) ≈ 0 at x=$xValue")
                    Double.NaN
                } else {
                    1.0 / sinValue
                }
            }

            // 指数对数函数
            "exp" -> {
                val result = exp(argValue)
                if (!result.isFinite()) {
                    Log.w("MathEvaluator", "exp溢出: exp($argValue) at x=$xValue")
                    Double.NaN
                } else {
                    result
                }
            }
            "ln" -> {
                if (argValue <= 0) {
                    Log.w("MathEvaluator", "ln定义域错误: ln($argValue) at x=$xValue")
                    Double.NaN
                } else {
                    ln(argValue)
                }
            }
            "log" -> {
                if (argValue <= 0) {
                    Log.w("MathEvaluator", "log定义域错误: log($argValue) at x=$xValue")
                    Double.NaN
                } else {
                    log10(argValue)
                }
            }

            // 其他函数
            "sqrt" -> {
                if (argValue < 0) {
                    Log.w("MathEvaluator", "sqrt定义域错误: sqrt($argValue) at x=$xValue")
                    Double.NaN
                } else {
                    sqrt(argValue)
                }
            }
            "abs" -> abs(argValue)

            else -> {
                Log.e("MathEvaluator", "不支持的函数: ${node.name}")
                throw CalculationException("不支持的函数: ${node.name}")
            }
        }
    }

    /**
     * 批量计算AST在多个x值的结果
     *
     * @param node AST节点
     * @param xValues x值列表
     * @return 对应的y值列表（如果计算失败则为NaN）
     */
    fun evaluateBatch(node: MathNode, xValues: List<Double>): List<Double> {
        return xValues.map { xValue ->
            try {
                evaluate(node, xValue)
            } catch (e: Exception) {
                Log.w("MathEvaluator", "批量计算错误 at x=$xValue: ${e.message}")
                Double.NaN
            }
        }
    }
}