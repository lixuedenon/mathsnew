// app/src/main/java/com/mathsnew/mathsnew/MathFormatter.kt
// 数学表达式格式化器（完整版 - 支持 sin²(x) 格式和分数线显示）

package com.mathsnew.mathsnew

import android.graphics.Color
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.SuperscriptSpan
import android.util.Log
import kotlin.math.abs

private enum class FormatterCharType {
    NUMBER, VARIABLE, OPERATOR, FUNCTION, PAREN
}

private enum class FormatterTokenType {
    NUMBER, VARIABLE, FUNCTION, LEFT_PAREN, RIGHT_PAREN, UNKNOWN
}

private data class FormatterCharInfo(
    val char: Char,
    val type: FormatterCharType,
    val isSuperscript: Boolean = false
)

class MathFormatter {

    companion object {
        private val COLOR_FUNCTION = Color.parseColor("#2196F3")
        private val COLOR_VARIABLE = Color.parseColor("#000000")
        private val COLOR_NUMBER = Color.parseColor("#F44336")
        private val COLOR_OPERATOR = Color.parseColor("#2E7D32")
    }

    /**
     * 格式化主入口
     */
    fun format(expression: String): FormattedResult {
        Log.d("MathFormatter", "格式化输入: $expression")

        var text = expression

        // 1. 处理 0-x 转换为 -x
        text = simplifyNegation(text)
        Log.d("MathFormatter", "负数简化后: $text")

        // 2. 格式化数字 (2.0 → 2)
        text = formatNumbers(text)
        Log.d("MathFormatter", "数字格式化后: $text")

        // 3. 重排三角函数幂次 sin(x)^2 → sin²(x)
        text = rearrangeTrigPowers(text)
        Log.d("MathFormatter", "三角函数幂次重排后: $text")

        // 4. 智能移除不必要的括号
        text = removeUnnecessaryParentheses(text)
        Log.d("MathFormatter", "去括号后: $text")

        // 5. 处理负号优化
        text = optimizeNegativeOne(text)
        Log.d("MathFormatter", "负号优化后: $text")

        // 6. 系数和变量合并前置
        text = groupCoefficientsAndVariables(text)
        Log.d("MathFormatter", "系数前置后: $text")

        // 6.5. 强制修正系数位置（确保系数在变量前）
        text = fixCoefficientPosition(text)
        Log.d("MathFormatter", "强制修正后: $text")

        // 7. 处理乘号省略
        text = simplifyMultiplication(text)
        Log.d("MathFormatter", "乘号简化后: $text")

        // 7.5. 合并分数外的系数（强制）
        text = mergeFractionCoefficients(text)
        Log.d("MathFormatter", "分数系数合并后: $text")

        // 8. 检测分数（顶层除法）
        val fractions = detectFractions(text)
        Log.d("MathFormatter", "检测到 ${fractions.size} 个分数")

        // 9. 创建带上标、语法高亮和分数线的SpannableString
        val spannableResult = createFormattedSpannable(text, fractions)

        // ✅ 10. 检测长度，自动缩放字体（>50字符缩小到70%）
        val finalSpannable = applyAutoScaling(spannableResult, text)

        val plainText = text

        return FormattedResult(plainText, finalSpannable)
    }

    /**
     * 检测表达式中的顶层分数
     * 只检测括号深度为0的除法符号
     *
     * @param text 待检测的表达式
     * @return 分数信息列表，按照出现顺序排列
     */
    private fun detectFractions(text: String): List<FractionInfo> {
        val fractions = mutableListOf<FractionInfo>()
        var i = 0
        var depth = 0

        while (i < text.length) {
            when (text[i]) {
                '(' -> depth++
                ')' -> depth--
                '/' -> {
                    if (depth == 0) {
                        // 找到顶层除法，提取分子和分母
                        val numeratorStart = findNumeratorStart(text, i)
                        val denominatorEnd = findDenominatorEnd(text, i)

                        if (numeratorStart >= 0 && denominatorEnd > i) {
                            val numerator = text.substring(numeratorStart, i)
                            val denominator = text.substring(i + 1, denominatorEnd)

                            fractions.add(
                                FractionInfo(
                                    numerator = numerator,
                                    denominator = denominator,
                                    startIndex = numeratorStart,
                                    endIndex = denominatorEnd
                                )
                            )

                            Log.d("MathFormatter", "检测到分数: [$numerator] / [$denominator]")
                        }
                    }
                }
            }
            i++
        }

        return fractions
    }

    /**
     * 向前查找分子的起始位置
     * 分子可以是：数字、变量、函数调用、括号表达式
     */
    private fun findNumeratorStart(text: String, divIndex: Int): Int {
        var i = divIndex - 1

        // 跳过空格
        while (i >= 0 && text[i].isWhitespace()) {
            i--
        }

        if (i < 0) return -1

        // 如果是右括号，找到匹配的左括号
        if (text[i] == ')') {
            var depth = 1
            i--
            while (i >= 0 && depth > 0) {
                when (text[i]) {
                    ')' -> depth++
                    '(' -> depth--
                }
                i--
            }

            // 检查括号前是否有函数名
            while (i >= 0 && text[i].isLetter()) {
                i--
            }

            return i + 1
        }

        // 处理普通变量或数字
        val start = i
        while (i >= 0) {
            val c = text[i]
            if (c.isLetterOrDigit() || c == '.' || c == '^') {
                i--
            } else if (c == '-' && i > 0 && text[i - 1] == '^') {
                // 处理负指数 x^-2
                i--
            } else {
                break
            }
        }

        return i + 1
    }

    /**
     * 向后查找分母的结束位置（完全修复版）
     *
     * 支持的分母格式：
     * - 简单变量：x, y
     * - 带幂次的变量：x^2, y^-3
     * - 函数（无括号）：sin, cos
     * - 函数带幂次后跟变量：sin^2x, cos^4x, exp^3x
     * - 括号表达式：(x+1), (x+1)^2
     *
     * 遇到顶层运算符（+、-、×、·）时停止
     */
    private fun findDenominatorEnd(text: String, divIndex: Int): Int {
        var i = divIndex + 1

        // 跳过空格
        while (i < text.length && text[i].isWhitespace()) {
            i++
        }

        if (i >= text.length) return text.length

        // 如果是左括号，找到匹配的右括号
        if (text[i] == '(') {
            var depth = 1
            i++
            while (i < text.length && depth > 0) {
                when (text[i]) {
                    '(' -> depth++
                    ')' -> depth--
                }
                i++
            }

            // 检查括号后是否有幂次
            if (i < text.length && text[i] == '^') {
                i++
                if (i < text.length && text[i] == '-') {
                    i++
                }
                while (i < text.length && (text[i].isDigit() || text[i] == '.')) {
                    i++
                }
            }

            return i
        }

        // 通用处理：连续读取字母、数字、幂次，直到遇到运算符
        // 这个逻辑同时适用于：
        // - 函数：sin, exp, ln
        // - 变量：x, y
        // - 函数+幂次：sin^2, exp^3
        // - 函数+幂次+变量：sin^4x, exp^2x
        // - 变量+幂次：x^2

        while (i < text.length) {
            val c = text[i]

            // 遇到顶层运算符时停止
            if (c in "+-×·/") {
                break
            }

            // 处理括号（函数调用）
            if (c == '(') {
                var depth = 1
                i++
                while (i < text.length && depth > 0) {
                    when (text[i]) {
                        '(' -> depth++
                        ')' -> depth--
                    }
                    i++
                }
                continue
            }

            // 处理字母和数字
            if (c.isLetterOrDigit() || c == '.') {
                i++
                continue
            }

            // 处理幂次
            if (c == '^') {
                i++
                if (i < text.length && text[i] == '-') {
                    i++
                }
                while (i < text.length && (text[i].isDigit() || text[i] == '.')) {
                    i++
                }
                continue
            }

            // 其他字符，停止
            break
        }

        return i
    }

    /**
     * 重排三角函数幂次
     *
     * 规则：
     * - sin(x)^2 → sin²(x)
     * - cos(x)^3 → cos³(x)
     * - tan(2x)^4 → tan⁴(2x)
     * - (sin(x))^2 → sin²(x) （去括号）
     *
     * 注意：只处理三角函数，不处理其他函数如 ln, log, sqrt
     */
    private fun rearrangeTrigPowers(text: String): String {
        var result = text

        // 三角函数列表
        val trigFunctions = listOf("sin", "cos", "tan", "cot", "sec", "csc")

        // 模式1: (sin(arg))^n → sin^n(arg)
        for (func in trigFunctions) {
            // 匹配 (sin(任意内容))^数字
            val pattern1 = Regex("""(\($func\(([^)]+)\)\))\^(-?\d+)""")
            result = pattern1.replace(result) { matchResult ->
                val arg = matchResult.groupValues[2]
                val power = matchResult.groupValues[3]
                "$func^$power($arg)"
            }
        }

        // 模式2: sin(arg)^n → sin^n(arg)
        for (func in trigFunctions) {
            // 匹配 sin(任意内容)^数字
            val pattern2 = Regex("""($func\(([^)]+)\))\^(-?\d+)""")
            result = pattern2.replace(result) { matchResult ->
                val arg = matchResult.groupValues[2]
                val power = matchResult.groupValues[3]
                "$func^$power($arg)"
            }
        }

        return result
    }

    /**
     * 简化负数表示：0-x → -x（增强版）
     */
    private fun simplifyNegation(text: String): String {
        var result = text

        result = result.replace(Regex("""\(0-([^)]+)\)"""), "-$1")
        result = result.replace(Regex("""\(0\.0-([^)]+)\)"""), "-$1")
        result = result.replace(Regex("""^0-"""), "-")
        result = result.replace(Regex("""^0\.0-"""), "-")
        result = result.replace(Regex("""([+\-×/·\(^])0-"""), "$1-")
        result = result.replace(Regex("""([+\-×/·\(^])0\.0-"""), "$1-")
        result = result.replace("+-", "-")

        return result
    }

    /**
     * 格式化数字显示
     */
    private fun formatNumbers(text: String): String {
        val regex = Regex("""(-?\d+)\.0+(?=\D|$)""")
        return regex.replace(text) { matchResult ->
            matchResult.groupValues[1]
        }
    }

    /**
     * 智能移除不必要的括号
     */
    private fun removeUnnecessaryParentheses(text: String): String {
        var result = text
        var changed = true
        var iterations = 0
        val maxIterations = 15

        while (changed && iterations < maxIterations) {
            val before = result
            result = removeOneLayerOfParentheses(result)
            changed = (result != before)
            iterations++
        }

        return result
    }

    /**
     * 移除一层不必要的括号
     */
    private fun removeOneLayerOfParentheses(text: String): String {
        val result = StringBuilder()
        var i = 0

        while (i < text.length) {
            if (text[i] == '(' && i < text.length - 1) {
                val closeIndex = findMatchingCloseParen(text, i)

                if (closeIndex != -1) {
                    val content = text.substring(i + 1, closeIndex)

                    if (canRemoveParentheses(text, i, closeIndex, content)) {
                        result.append(content)
                        i = closeIndex + 1
                        continue
                    }
                }
            }

            result.append(text[i])
            i++
        }

        return result.toString()
    }

    /**
     * 找到匹配的右括号位置
     */
    private fun findMatchingCloseParen(text: String, openIndex: Int): Int {
        var depth = 0
        for (i in openIndex until text.length) {
            when (text[i]) {
                '(' -> depth++
                ')' -> {
                    depth--
                    if (depth == 0) return i
                }
            }
        }
        return -1
    }

    /**
     * 判断是否可以移除括号
     */
    private fun canRemoveParentheses(text: String, openIndex: Int, closeIndex: Int, content: String): Boolean {
        if (content.isEmpty()) return false

        if (openIndex > 0) {
            val beforeParen = text.substring(0, openIndex)
            if (beforeParen.matches(Regex(".*[a-z]+$"))) {
                return content.startsWith("(") && content.endsWith(")") &&
                       findMatchingCloseParen(content, 0) == content.length - 1
            }
        }

        if (!hasTopLevelOperator(content)) {
            val before = if (openIndex > 0) text[openIndex - 1] else ' '
            val after = if (closeIndex < text.length - 1) text[closeIndex + 1] else ' '

            if (before == '/' || after == '/') return true
            if (before == '^' || after == '^') return true
            return true
        }

        if (openIndex == 0 && closeIndex == text.length - 1) {
            return true
        }

        return false
    }

    /**
     * 检查表达式是否包含顶层运算符
     */
    private fun hasTopLevelOperator(expr: String): Boolean {
        var depth = 0
        for (char in expr) {
            when (char) {
                '(' -> depth++
                ')' -> depth--
                '+', '-', '×', '/', '·' -> {
                    if (depth == 0) return true
                }
            }
        }
        return false
    }

    /**
     * 优化负一系数显示
     */
    private fun optimizeNegativeOne(text: String): String {
        var result = text
        if (result.startsWith("-1×")) {
            result = "-" + result.substring(3)
        }
        result = result.replace("+-1×", "-")
        result = result.replace("--1×", "+")
        return result
    }

    /**
     * 系数和变量合并前置
     */
    private fun groupCoefficientsAndVariables(text: String): String {
        val terms = splitByAddSubtract(text)
        val rearrangedTerms = terms.map { term ->
            rearrangeTerm(term.value, term.operator)
        }
        return rearrangedTerms.joinToString("")
    }

    /**
     * 强制修正系数位置
     *
     * 将所有"变量·系数"强制改为"系数·变量"
     * 例如：x·6 → 6·x, x^2·4 → 4·x^2
     */
    private fun fixCoefficientPosition(text: String): String {
        var result = text

        // 模式1: 变量(可能有幂次)·系数  →  系数·变量(可能有幂次)
        // 例如：x·6 → 6·x, x^2·4 → 4·x^2
        val pattern1 = Regex("""([a-z])(?:\^(-?\d+))?[×·](-?\d+(?:\.\d+)?)""")
        result = pattern1.replace(result) { matchResult ->
            val variable = matchResult.groupValues[1]
            val power = matchResult.groupValues[2]
            val coeff = matchResult.groupValues[3]

            if (power.isNotEmpty()) {
                "$coeff×$variable^$power"
            } else {
                "$coeff×$variable"
            }
        }

        // 模式2: 变量(可能有幂次) 系数（没有乘号）
        // 例如：x 6 → 6×x（虽然这种情况应该很少）
        val pattern2 = Regex("""([a-z])(?:\^(-?\d+))?\s+(-?\d+(?:\.\d+)?)""")
        result = pattern2.replace(result) { matchResult ->
            val variable = matchResult.groupValues[1]
            val power = matchResult.groupValues[2]
            val coeff = matchResult.groupValues[3]

            if (power.isNotEmpty()) {
                "$coeff×$variable^$power"
            } else {
                "$coeff×$variable"
            }
        }

        return result
    }

    /**
     * 合并分数外的系数到分子
     *
     * 将 "2·(分子)/(分母)" 或 "2(分子)/(分母)" 转换为 "(2·分子)/(分母)"
     *
     * 例如：
     * - 2×(-2x⁵-7x⁴+1)/(x+1)⁸ → (-4x⁵-14x⁴+2)/(x+1)⁸
     * - 2(-2x⁵-7x⁴+1)/(x+1)⁸ → (-4x⁵-14x⁴+2)/(x+1)⁸
     */
    private fun mergeFractionCoefficients(text: String): String {
        var result = text

        // 模式1：系数[×·](...)/(任意)
        val pattern1 = Regex("""(\d+(?:\.\d+)?)[×·]\(([^)]+)\)/""")
        result = pattern1.replace(result) { match ->
            val coeffStr = match.groupValues[1]
            val numerator = match.groupValues[2]

            val coeff = coeffStr.toDoubleOrNull() ?: return@replace match.value
            val expanded = multiplyPolynomial(coeff, numerator)

            "($expanded)/"
        }

        // ✅ 模式2：系数(...)/(任意)  （没有乘号，紧挨着）
        // 例如：2(-2x^5-7x^4+1)/(x+1)^8
        val pattern2 = Regex("""(\d+(?:\.\d+)?)\(([^)]+)\)/""")
        result = pattern2.replace(result) { match ->
            val coeffStr = match.groupValues[1]
            val numerator = match.groupValues[2]

            val coeff = coeffStr.toDoubleOrNull() ?: return@replace match.value
            val expanded = multiplyPolynomial(coeff, numerator)

            "($expanded)/"
        }

        return result
    }

    /**
     * 将多项式的每一项乘以系数
     *
     * 例如：2 × (-2x⁵-7x⁴+1) → -4x⁵-14x⁴+2
     */
    private fun multiplyPolynomial(coeff: Double, polynomial: String): String {
        if (abs(coeff - 1.0) < 0.0001) return polynomial

        val result = StringBuilder()
        var i = 0
        var currentTerm = StringBuilder()

        while (i < polynomial.length) {
            val char = polynomial[i]

            when {
                char in "+-" && i > 0 && currentTerm.isNotEmpty() -> {
                    // 处理完成一项
                    result.append(multiplyTerm(coeff, currentTerm.toString()))
                    currentTerm = StringBuilder()
                    currentTerm.append(char)
                }
                else -> {
                    currentTerm.append(char)
                }
            }
            i++
        }

        // 处理最后一项
        if (currentTerm.isNotEmpty()) {
            result.append(multiplyTerm(coeff, currentTerm.toString()))
        }

        return result.toString()
    }

    /**
     * 将单项乘以系数
     *
     * 例如：2 × (-3x²) → -6x²
     */
    private fun multiplyTerm(coeff: Double, term: String): String {
        val trimmed = term.trim()
        if (trimmed.isEmpty()) return ""

        // 提取项的系数和变量部分
        val pattern = Regex("""([+-]?\d*\.?\d*)(.*)""")
        val match = pattern.find(trimmed) ?: return trimmed

        val termCoeffStr = match.groupValues[1]
        val rest = match.groupValues[2]

        // 计算项的系数
        val termCoeff = when {
            termCoeffStr.isEmpty() -> 1.0
            termCoeffStr == "+" -> 1.0
            termCoeffStr == "-" -> -1.0
            else -> termCoeffStr.toDoubleOrNull() ?: 1.0
        }

        // 计算新系数
        val newCoeff = coeff * termCoeff

        // 格式化系数
        val coeffStr = when {
            abs(newCoeff - newCoeff.toInt()) < 0.0001 -> {
                val intCoeff = newCoeff.toInt()
                when {
                    intCoeff == 1 && rest.isNotEmpty() -> ""
                    intCoeff == -1 && rest.isNotEmpty() -> "-"
                    else -> intCoeff.toString()
                }
            }
            else -> newCoeff.toString()
        }

        return coeffStr + rest
    }

    /**
     * 项数据类
     */
    private data class Term(val value: String, val operator: String)

    /**
     * 按加减法分割表达式为多个项
     */
    private fun splitByAddSubtract(expression: String): List<Term> {
        val terms = mutableListOf<Term>()
        var currentTerm = StringBuilder()
        var currentOperator = ""

        var i = 0
        var depth = 0

        while (i < expression.length) {
            when (expression[i]) {
                '(' -> {
                    depth++
                    currentTerm.append(expression[i])
                }
                ')' -> {
                    depth--
                    currentTerm.append(expression[i])
                }
                '+', '-' -> {
                    if (depth == 0) {
                        val isNegativeSign = i == 0 || expression[i - 1] in "+-×/(^"

                        if (isNegativeSign) {
                            currentTerm.append(expression[i])
                        } else {
                            if (currentTerm.isNotEmpty()) {
                                terms.add(Term(currentTerm.toString(), currentOperator))
                                currentTerm = StringBuilder()
                            }
                            currentOperator = expression[i].toString()
                        }
                    } else {
                        currentTerm.append(expression[i])
                    }
                }
                else -> {
                    currentTerm.append(expression[i])
                }
            }
            i++
        }

        if (currentTerm.isNotEmpty()) {
            terms.add(Term(currentTerm.toString(), currentOperator))
        }

        return terms
    }

    /**
     * 智能分割乘法因子
     */
    private fun splitFactors(term: String): List<String> {
        val factors = mutableListOf<String>()
        var currentFactor = StringBuilder()
        var depth = 0

        for (char in term) {
            when (char) {
                '(' -> {
                    depth++
                    currentFactor.append(char)
                }
                ')' -> {
                    depth--
                    currentFactor.append(char)
                }
                '×', '·' -> {
                    if (depth == 0) {
                        if (currentFactor.isNotEmpty()) {
                            factors.add(currentFactor.toString())
                            currentFactor = StringBuilder()
                        }
                    } else {
                        currentFactor.append(char)
                    }
                }
                else -> {
                    currentFactor.append(char)
                }
            }
        }

        if (currentFactor.isNotEmpty()) {
            factors.add(currentFactor.toString())
        }

        return factors
    }

    /**
     * 判断因子类型
     */
    private enum class FactorType {
        COEFFICIENT,
        VARIABLE,
        FUNCTION,
        PARENTHESIZED
    }

    /**
     * 获取因子类型
     */
    private fun getFactorType(factor: String): FactorType {
        var trimmed = factor.trim()

        while (trimmed.startsWith("(") && trimmed.endsWith(")")) {
            val inner = trimmed.substring(1, trimmed.length - 1)
            if (!hasTopLevelOperator(inner)) {
                trimmed = inner
            } else {
                break
            }
        }

        return when {
            trimmed.matches(Regex("""-?\\d+(\\.\\d+)?""")) -> FactorType.COEFFICIENT
            trimmed.matches(Regex("""[a-z](\\^-?\\d+)?""")) -> FactorType.VARIABLE
            trimmed.matches(Regex("""-?\\d+(\\.\\d+)?×?[a-z](\\^-?\\d+)?""")) -> FactorType.COEFFICIENT
            trimmed.startsWith("(") && trimmed.endsWith(")") -> FactorType.PARENTHESIZED
            else -> FactorType.FUNCTION
        }
    }

    /**
     * 重排单个项（修复版：正确处理系数在后面的情况）
     */
    private fun rearrangeTerm(term: String, operator: String): String {
        if (term.contains("/")) {
            Log.d("MathFormatter", "项包含除法，不重排: $term")
            return operator + term
        }

        val factors = splitFactors(term)

        val coefficients = mutableListOf<String>()
        val variables = mutableListOf<String>()
        val others = mutableListOf<String>()

        for (factor in factors) {
            if (factor.isEmpty()) continue

            // 检查是否是"变量·系数"的形式（如 x·2）
            val varCoeffPattern = Regex("""([a-z](?:\^-?\d+)?)[×·](-?\d+(?:\.\d+)?)""")
            val varCoeffMatch = varCoeffPattern.find(factor)
            if (varCoeffMatch != null) {
                variables.add(varCoeffMatch.groupValues[1])  // 变量
                coefficients.add(varCoeffMatch.groupValues[2])  // 系数
                Log.d("MathFormatter", "分离变量·系数: ${varCoeffMatch.groupValues[1]} 和 ${varCoeffMatch.groupValues[2]}")
                continue
            }

            // 检查是否是"系数·变量"的形式（如 2·x, 2x）
            val coeffVarPattern = Regex("""(-?\d+(?:\.\d+)?)[×·]?([a-z](?:\^-?\d+)?)""")
            val coeffVarMatch = coeffVarPattern.find(factor)
            if (coeffVarMatch != null && coeffVarMatch.value == factor) {
                coefficients.add(coeffVarMatch.groupValues[1])  // 系数
                variables.add(coeffVarMatch.groupValues[2])  // 变量
                Log.d("MathFormatter", "分离系数·变量: ${coeffVarMatch.groupValues[1]} 和 ${coeffVarMatch.groupValues[2]}")
                continue
            }

            // 使用原有逻辑判断类型
            when (getFactorType(factor)) {
                FactorType.COEFFICIENT -> coefficients.add(factor)
                FactorType.VARIABLE -> variables.add(factor)
                FactorType.FUNCTION, FactorType.PARENTHESIZED -> others.add(factor)
            }
        }

        val result = StringBuilder()
        result.append(operator)

        // 拼接顺序：系数 + 变量 + 其他
        val allFactors = coefficients + variables + others

        for (i in allFactors.indices) {
            result.append(allFactors[i])
            if (i < allFactors.size - 1) {
                result.append("×")
            }
        }

        Log.d("MathFormatter", "重排结果: ${result.toString()}")
        return result.toString()
    }

    /**
     * 简化乘号显示
     */
    private fun simplifyMultiplication(text: String): String {
        val result = StringBuilder()
        var i = 0

        while (i < text.length) {
            val char = text[i]

            if (char == '×') {
                val before = if (i > 0) getTypeBeforeMultiply(text, i - 1) else FormatterTokenType.UNKNOWN
                val after = if (i < text.length - 1) getTypeAfterMultiply(text, i + 1) else FormatterTokenType.UNKNOWN

                when {
                    (before == FormatterTokenType.NUMBER && after == FormatterTokenType.VARIABLE) ||
                    (before == FormatterTokenType.NUMBER && after == FormatterTokenType.LEFT_PAREN) ||
                    (before == FormatterTokenType.VARIABLE && after == FormatterTokenType.VARIABLE) -> {
                        Log.d("MathFormatter", "省略乘号: 位置 $i")
                    }
                    else -> {
                        result.append('·')
                        Log.d("MathFormatter", "保留乘号为点: 位置 $i")
                    }
                }
            } else {
                result.append(char)
            }
            i++
        }

        return result.toString()
    }

    /**
     * 创建带上标、语法高亮和分数线的SpannableString
     *
     * @param text 格式化后的文本
     * @param fractions 检测到的分数列表
     * @return 完整格式化的SpannableString
     */
    private fun createFormattedSpannable(text: String, fractions: List<FractionInfo>): SpannableString {
        Log.d("MathFormatter", "创建SpannableString: $text")

        // 如果没有分数，使用原有逻辑
        if (fractions.isEmpty()) {
            return createSimpleSpannable(text)
        }

        // 有分数时，需要分段处理
        val builder = SpannableStringBuilder()
        var lastEnd = 0

        for (fraction in fractions) {
            // 添加分数前的部分
            if (fraction.startIndex > lastEnd) {
                val beforeText = text.substring(lastEnd, fraction.startIndex)
                builder.append(createSimpleSpannable(beforeText))
            }

            // 创建分子和分母的SpannableString（带上标和语法高亮）
            val numeratorSpan = createSimpleSpannable(fraction.numerator)
            val denominatorSpan = createSimpleSpannable(fraction.denominator)

            // 应用FractionSpan
            val fractionPlaceholder = "█" // 使用占位符
            val start = builder.length
            builder.append(fractionPlaceholder)
            val end = builder.length

            val fractionSpan = FractionSpan(
                numerator = numeratorSpan,
                denominator = denominatorSpan,
                textSize = 48f,
                lineThickness = 3f,
                padding = 12f
            )

            builder.setSpan(
                fractionSpan,
                start,
                end,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )

            Log.d("MathFormatter", "应用分数线: [$numeratorSpan] / [$denominatorSpan]")

            lastEnd = fraction.endIndex
        }

        // 添加最后一部分
        if (lastEnd < text.length) {
            val afterText = text.substring(lastEnd)
            builder.append(createSimpleSpannable(afterText))
        }

        return SpannableString(builder)
    }

    /**
     * 创建简单的SpannableString（上标 + 语法高亮）
     * 用于非分数部分或分数的分子分母
     */
    private fun createSimpleSpannable(text: String): SpannableString {
        val charInfoList = mutableListOf<FormatterCharInfo>()
        var i = 0

        while (i < text.length) {
            val char = text[i]

            when {
                char == '^' -> {
                    i++
                    if (i < text.length && text[i] == '-') {
                        charInfoList.add(FormatterCharInfo(text[i], FormatterCharType.OPERATOR, true))
                        i++
                    }
                    while (i < text.length && (text[i].isDigit() || text[i] == '.')) {
                        charInfoList.add(FormatterCharInfo(text[i], FormatterCharType.NUMBER, true))
                        i++
                    }
                }

                char.isDigit() || char == '.' -> {
                    charInfoList.add(FormatterCharInfo(char, FormatterCharType.NUMBER))
                    i++
                }

                char.isLetter() -> {
                    val nameBuilder = StringBuilder()
                    while (i < text.length && text[i].isLetter()) {
                        nameBuilder.append(text[i])
                        i++
                    }
                    val name = nameBuilder.toString()

                    val isFunctionName = name in listOf("sin", "cos", "tan", "cot", "sec", "csc",
                                                        "arcsin", "arccos", "arctan", "arccot", "arcsec", "arccsc",
                                                        "ln", "log", "sqrt", "exp", "abs")
                    val type = if (isFunctionName) FormatterCharType.FUNCTION else FormatterCharType.VARIABLE

                    for (c in name) {
                        charInfoList.add(FormatterCharInfo(c, type))
                    }
                }

                char in "+-×/÷·" -> {
                    charInfoList.add(FormatterCharInfo(char, FormatterCharType.OPERATOR))
                    i++
                }

                char in "()" -> {
                    charInfoList.add(FormatterCharInfo(char, FormatterCharType.PAREN))
                    i++
                }

                char == 'π' || char == 'e' -> {
                    charInfoList.add(FormatterCharInfo(char, FormatterCharType.NUMBER))
                    i++
                }

                else -> {
                    i++
                }
            }
        }

        val displayText = charInfoList.map { it.char }.joinToString("")
        val spannableString = SpannableString(displayText)

        var currentPos = 0
        for (info in charInfoList) {
            val start = currentPos
            val end = currentPos + 1

            if (info.isSuperscript) {
                spannableString.setSpan(
                    SuperscriptSpan(),
                    start,
                    end,
                    SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                spannableString.setSpan(
                    RelativeSizeSpan(0.7f),
                    start,
                    end,
                    SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }

            val color = when (info.type) {
                FormatterCharType.FUNCTION -> COLOR_FUNCTION
                FormatterCharType.VARIABLE -> COLOR_VARIABLE
                FormatterCharType.NUMBER -> COLOR_NUMBER
                FormatterCharType.OPERATOR -> COLOR_OPERATOR
                FormatterCharType.PAREN -> COLOR_VARIABLE
            }

            spannableString.setSpan(
                ForegroundColorSpan(color),
                start,
                end,
                SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE
            )

            currentPos++
        }

        return spannableString
    }

    /**
     * 获取乘号前面的token类型
     */
    private fun getTypeBeforeMultiply(text: String, index: Int): FormatterTokenType {
        var i = index
        while (i >= 0 && text[i].isWhitespace()) {
            i--
        }
        if (i < 0) return FormatterTokenType.UNKNOWN
        val char = text[i]
        return when {
            char == ')' -> FormatterTokenType.RIGHT_PAREN
            char.isDigit() -> FormatterTokenType.NUMBER
            char.isLetter() -> {
                if (hasFunctionPattern(text, i)) FormatterTokenType.FUNCTION
                else FormatterTokenType.VARIABLE
            }
            else -> FormatterTokenType.UNKNOWN
        }
    }

    /**
     * 获取乘号后面的token类型
     */
    private fun getTypeAfterMultiply(text: String, index: Int): FormatterTokenType {
        var i = index
        while (i < text.length && text[i].isWhitespace()) {
            i++
        }
        if (i >= text.length) return FormatterTokenType.UNKNOWN
        val char = text[i]
        return when {
            char == '(' -> FormatterTokenType.LEFT_PAREN
            char.isDigit() -> FormatterTokenType.NUMBER
            char.isLetter() -> {
                var j = i
                while (j < text.length && text[j].isLetter()) {
                    j++
                }
                if (j < text.length && text[j] == '(') FormatterTokenType.FUNCTION
                else FormatterTokenType.VARIABLE
            }
            else -> FormatterTokenType.UNKNOWN
        }
    }

    /**
     * 判断某个位置的字母是否是函数名的一部分
     */
    private fun hasFunctionPattern(text: String, index: Int): Boolean {
        var start = index
        while (start >= 0 && text[start].isLetter()) {
            start--
        }
        start++
        var end = index
        while (end < text.length && text[end].isLetter()) {
            end++
        }
        return end < text.length && text[end] == '('
    }

    /**
     * 根据表达式长度自动缩放字体
     *
     * 策略：
     * - 长度 > 50：缩小到 0.7（最小值，不再继续缩小）
     * - 长度 ≤ 50：正常大小 1.0
     */
    private fun applyAutoScaling(
        spannable: SpannableString,
        originalText: String
    ): SpannableString {
        val length = originalText.length

        // 只有一个阈值：>50 就缩小到70%
        val scale = if (length > 50) 0.7f else 1.0f

        // 如果需要缩放
        if (scale < 1.0f) {
            Log.d("MathFormatter", "表达式长度 $length，缩放到 70%")

            val builder = SpannableStringBuilder(spannable)

            builder.setSpan(
                RelativeSizeSpan(scale),
                0,
                builder.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )

            return SpannableString(builder)
        }

        return spannable
    }
}

/**
 * 格式化结果数据类
 */
data class FormattedResult(
    val plainText: String,
    val displayText: SpannableString
)