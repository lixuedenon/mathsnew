// app/src/main/java/com/mathsnew/mathsnew/CalculusEngine.kt
// 微积分计算引擎

package com.mathsnew.mathsnew

import android.text.SpannableString
import android.util.Log

sealed class CalculationResult {
    data class Success(
        val result: String,
        val displayText: SpannableString,
        val secondDerivativeResult: String? = null,
        val secondDerivativeDisplayText: SpannableString? = null,
        val forms: SimplificationForms
    ) : CalculationResult()

    data class Error(val message: String) : CalculationResult()
}

class CalculusEngine {

    private val parser = ExpressionParser()
    private val derivativeCalculator = DerivativeCalculator()
    private val simplifier = ExpressionSimplifier()
    private val formatter = MathFormatter()

    fun calculateDerivative(expression: String, variable: String = "x"): CalculationResult {
        return try {
            Log.d("CalculusEngine", "========================================")
            Log.d("CalculusEngine", "开始计算导数")
            Log.d("CalculusEngine", "表达式: $expression")

            val ast = parser.parse(expression)
            Log.d("CalculusEngine", "AST: $ast")

            val derivativeAst = derivativeCalculator.differentiate(ast, variable)
            Log.d("CalculusEngine", "原始导数AST: $derivativeAst")

            val forms = simplifier.simplifyToMultipleForms(derivativeAst)
            Log.d("CalculusEngine", "生成了 ${forms.forms.size} 种形式")

            val displayForms = forms.getDisplayForms()
            Log.d("CalculusEngine", "去重后有 ${displayForms.size} 种不同形式")

            val mainForm = displayForms.first()
            val mainResult = mainForm.expression.toString()
            val mainFormatted = formatter.format(mainResult)

            Log.d("CalculusEngine", "主形式: $mainResult")

            val secondDerivativeAst = derivativeCalculator.differentiate(derivativeAst, variable)
            Log.d("CalculusEngine", "二阶导数AST: $secondDerivativeAst")

            val secondForms = simplifier.simplifyToMultipleForms(secondDerivativeAst)
            val secondDisplayForms = secondForms.getDisplayForms()
            val secondMainForm = secondDisplayForms.first()
            val secondResult = secondMainForm.expression.toString()
            val secondFormatted = formatter.format(secondResult)

            Log.d("CalculusEngine", "二阶导数主形式: $secondResult")
            Log.d("CalculusEngine", "========================================")

            CalculationResult.Success(
                result = mainResult,
                displayText = mainFormatted.displayText,
                secondDerivativeResult = secondResult,
                secondDerivativeDisplayText = secondFormatted.displayText,
                forms = forms
            )
        } catch (e: ParseException) {
            Log.e("CalculusEngine", "解析错误: ${e.message}", e)
            CalculationResult.Error("解析错误: ${e.message}")
        } catch (e: CalculationException) {
            Log.e("CalculusEngine", "计算错误: ${e.message}", e)
            CalculationResult.Error("计算错误: ${e.message}")
        } catch (e: Exception) {
            Log.e("CalculusEngine", "未知错误: ${e.message}", e)
            CalculationResult.Error("发生错误: ${e.message}")
        }
    }
}