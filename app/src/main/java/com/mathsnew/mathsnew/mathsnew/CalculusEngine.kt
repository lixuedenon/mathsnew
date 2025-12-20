// app/src/main/java/com/mathsnew/mathsnew/CalculusEngine.kt
// 微积分计算引擎 - 协调层，集成格式化器

package com.mathsnew.mathsnew

import android.text.SpannableString

/**
 * 微积分计算引擎
 *
 * 职责：
 * 1. 协调各个组件完成微积分计算
 * 2. 统一的异常处理
 * 3. 封装计算结果
 *
 * 计算流程：
 * 输入字符串 → 解析 → 微分 → 简化 → 格式化 → 返回结果
 */
class CalculusEngine {

    private val derivativeCalculator = DerivativeCalculator()
    private val simplifier = ExpressionSimplifier()
    private val formatter = MathFormatter()

    /**
     * 计算微分
     *
     * @param expression 表达式字符串（如 "sin(x^2)"）
     * @param variable 求导变量（默认为"x"）
     * @return CalculationResult 计算结果（Success或Error）
     */
    fun calculateDerivative(expression: String, variable: String = "x"): CalculationResult {
        return try {
            // 步骤1: 解析表达式
            val parser = ExpressionParser()
            val ast = parser.parse(expression)

            // 步骤2: 计算微分
            val derivativeAst = derivativeCalculator.differentiate(ast, variable)

            // 步骤3: 简化结果
            val simplifiedAst = simplifier.simplify(derivativeAst)

            // 步骤4: 转换为字符串
            val simplifiedString = simplifiedAst.toString()

            // 步骤5: 格式化为手写数学格式
            val formattedResult = formatter.format(simplifiedString)

            // 返回成功结果（包含格式化后的SpannableString）
            CalculationResult.Success(
                result = formattedResult.plainText,
                displayText = formattedResult.displayText
            )

        } catch (e: ParseException) {
            CalculationResult.Error("表达式格式错误: ${e.message}")
        } catch (e: CalculationException) {
            CalculationResult.Error("计算错误: ${e.message}")
        } catch (e: Exception) {
            CalculationResult.Error("未知错误: ${e.message}")
        }
    }

    /**
     * 计算积分（暂未实现）
     *
     * @param expression 表达式字符串
     * @param variable 积分变量（默认为"x"）
     * @return CalculationResult 计算结果
     */
    fun calculateIntegral(expression: String, variable: String = "x"): CalculationResult {
        return CalculationResult.Error("积分功能正在开发中")
    }
}

/**
 * 计算结果封装类
 */
sealed class CalculationResult {
    /**
     * 成功结果
     *
     * @param result 纯文本结果（用于内部处理）
     * @param displayText 显示文本（SpannableString，带格式）
     */
    data class Success(
        val result: String,
        val displayText: SpannableString
    ) : CalculationResult()

    /**
     * 错误结果
     *
     * @param message 错误信息
     */
    data class Error(val message: String) : CalculationResult()
}