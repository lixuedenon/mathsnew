// app/src/main/java/com/mathsnew/mathsnew/CalculusEngine.kt
// 微积分计算引擎（业务协调层）

package com.mathsnew.mathsnew

/**
 * 微积分计算引擎
 * 业务协调层，统一处理微分和积分计算
 *
 * 职责：
 * 1. 统一的对外接口
 * 2. 异常处理和错误消息
 * 3. 协调各个计算器
 * 4. 调用简化器优化结果
 */
class CalculusEngine {

    private val derivativeCalculator = DerivativeCalculator()
    private val simplifier = ExpressionSimplifier()

    /**
     * 计算微分
     *
     * @param expression 表达式字符串
     * @param variable 求导变量（默认为"x"）
     * @return 计算结果（包含成功状态、结果或错误信息）
     */
    fun calculateDerivative(expression: String, variable: String = "x"): CalculationResult {
        return try {
            // 1. 解析表达式
            val parser = ExpressionParser()
            val ast = parser.parse(expression)

            // 2. 计算微分
            val derivativeAst = derivativeCalculator.differentiate(ast, variable)

            // 3. 简化结果
            val simplifiedAst = simplifier.simplify(derivativeAst)

            // 4. 转换为字符串
            val result = simplifiedAst.toString()

            CalculationResult.Success(result)
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
     */
    fun calculateIntegral(expression: String, variable: String = "x"): CalculationResult {
        return CalculationResult.Error("积分功能正在开发中")
    }
}

/**
 * 计算结果封装类
 */
sealed class CalculationResult {
    data class Success(val result: String) : CalculationResult()
    data class Error(val message: String) : CalculationResult()
}
