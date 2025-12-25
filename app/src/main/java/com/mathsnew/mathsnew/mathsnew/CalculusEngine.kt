// app/src/main/java/com/mathsnew/mathsnew/CalculusEngine.kt
// 微积分计算引擎（修复二阶导数问题）

package com.mathsnew.mathsnew

import android.text.SpannableString
import android.util.Log

/**
 * 微积分计算引擎
 */
class CalculusEngine {

    private val parser = ExpressionParser()
    private val derivativeCalculator = DerivativeCalculator()
    private val simplifier = ExpressionSimplifier()
    private val formatter = MathFormatter()

    /**
     * 计算微分
     */
    fun calculateDerivative(expression: String, variable: String = "x"): CalculationResult {
        Log.d("CalculusEngine", "========================================")
        Log.d("CalculusEngine", "开始计算微分")
        Log.d("CalculusEngine", "输入表达式: '$expression'")
        Log.d("CalculusEngine", "求导变量: '$variable'")

        return try {
            // 步骤1: 解析表达式（ExpressionParser内部会调用Tokenizer）
            Log.d("CalculusEngine", "步骤1: 解析表达式...")
            val ast = parser.parse(expression)
            Log.d("CalculusEngine", "AST: $ast")

            // 步骤2: 计算微分
            Log.d("CalculusEngine", "步骤2: 计算微分...")
            val derivativeAst = derivativeCalculator.differentiate(ast, variable)
            Log.d("CalculusEngine", "微分结果AST: $derivativeAst")

            // 步骤3: 简化结果
            Log.d("CalculusEngine", "步骤3: 简化表达式...")
            val simplifiedAst = simplifier.simplify(derivativeAst)
            Log.d("CalculusEngine", "简化结果AST: $simplifiedAst")

            // 步骤4: 转换为字符串（用于后续计算）
            Log.d("CalculusEngine", "步骤4: 转换为字符串...")
            val simplifiedString = simplifiedAst.toString()
            Log.d("CalculusEngine", "原始字符串: $simplifiedString")

            // 步骤5: 格式化为手写数学格式（仅用于显示）
            Log.d("CalculusEngine", "步骤5: 格式化输出...")
            val formattedResult = formatter.format(simplifiedString)
            Log.d("CalculusEngine", "格式化纯文本: ${formattedResult.plainText}")

            Log.d("CalculusEngine", "✅ 计算成功!")
            Log.d("CalculusEngine", "========================================")

            // 🔧 关键修复：result 使用原始字符串（可以被解析器识别）
            //              displayText 使用格式化后的字符串（用于显示）
            CalculationResult.Success(
                result = simplifiedString,  // ← 修改：使用原始字符串，不是 formattedResult.plainText
                displayText = formattedResult.displayText
            )

        } catch (e: ParseException) {
            Log.e("CalculusEngine", "❌ 解析错误: ${e.message}", e)
            Log.d("CalculusEngine", "========================================")
            CalculationResult.Error("表达式格式错误: ${e.message}")
        } catch (e: CalculationException) {
            Log.e("CalculusEngine", "❌ 计算错误: ${e.message}", e)
            Log.d("CalculusEngine", "========================================")
            CalculationResult.Error("计算错误: ${e.message}")
        } catch (e: Exception) {
            Log.e("CalculusEngine", "❌ 未知错误: ${e.message}", e)
            e.printStackTrace()
            Log.d("CalculusEngine", "========================================")
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
    /**
     * 成功结果
     * @param result 原始计算结果字符串（用于后续计算，可被解析器识别）
     * @param displayText 格式化后的显示文本（用于UI显示，包含上标等格式）
     */
    data class Success(
        val result: String,           // ← 用于后续计算的原始字符串
        val displayText: SpannableString  // ← 用于显示的格式化字符串
    ) : CalculationResult()

    /**
     * 错误结果
     */
    data class Error(val message: String) : CalculationResult()
}