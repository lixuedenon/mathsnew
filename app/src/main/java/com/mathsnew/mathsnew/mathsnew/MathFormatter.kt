// app/src/main/java/com/mathsnew/mathsnew/MathFormatter.kt
// 数学表达式格式化器（完整版 - 修复 0-x 负号显示）

package com.mathsnew.mathsnew

import android.graphics.Color
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.SuperscriptSpan
import android.util.Log

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

        // 1. 处理 0-x 转换为 -x（增强版）
        text = simplifyNegation(text)
        Log.d("MathFormatter", "负数简化后: $text")

        // 2. 格式化数字 (2.0 → 2)
        text = formatNumbers(text)
        Log.d("MathFormatter", "数字格式化后: $text")

        // 3. 智能移除不必要的括号
        text = removeUnnecessaryParentheses(text)
        Log.d("MathFormatter", "去括号后: $text")

        // 4. 处理负号优化
        text = optimizeNegativeOne(text)
        Log.d("MathFormatter", "负号优化后: $text")

        // 5. 系数和变量合并前置
        text = groupCoefficientsAndVariables(text)
        Log.d("MathFormatter", "系数前置后: $text")

        // 6. 处理乘号省略
        text = simplifyMultiplication(text)
        Log.d("MathFormatter", "乘号简化后: $text")

        // 7. 创建带上标和语法高亮的SpannableString
        val spannableResult = createFormattedSpannable(text)

        val plainText = text

        return FormattedResult(plainText, spannableResult)
    }

    /**
     * 简化负数表示：0-x → -x（增强版）
     *
     * 处理所有形式：
     * - (0-x) → -x
     * - 0-sin(x) → -sin(x)
     * - 2x+0-sin(x) → 2x-sin(x)
     * - x²×cos(x)+0-sin(x) → x²·cos(x)-sin(x)
     */
    private fun simplifyNegation(text: String): String {
        var result = text

        // 1. 先处理带括号的形式：(0-x) → -x
        result = result.replace(Regex("""\(0-([^)]+)\)"""), "-$1")
        result = result.replace(Regex("""\(0\.0-([^)]+)\)"""), "-$1")

        // 2. 处理表达式开头的：0-x → -x
        result = result.replace(Regex("""^0-"""), "-")
        result = result.replace(Regex("""^0\.0-"""), "-")

        // 3. 处理运算符后的：+0-x → -x，×0-sin(x) → ×-sin(x)
        // 匹配加减乘除、左括号、幂运算符后的 0-
        result = result.replace(Regex("""([+\-×/·\(^])0-"""), "$1-")
        result = result.replace(Regex("""([+\-×/·\(^])0\.0-"""), "$1-")

        // 4. 清理双运算符：+- → -
        result = result.replace("+-", "-")

        return result
    }

    /**
     * 格式化数字显示
     *
     * 规则：如果小数部分为0，则只显示整数部分
     *
     * 例如：
     * - 2.0 → 2
     * - 1.0 → 1
     * - -3.0 → -3
     * - 2.5 → 2.5 （保留有效小数）
     */
    private fun formatNumbers(text: String): String {
        // 匹配数字（包括负数和小数），如果小数部分全是0则去掉
        val regex = Regex("""(-?\d+)\.0+(?=\D|$)""")
        return regex.replace(text) { matchResult ->
            matchResult.groupValues[1] // 只保留整数部分
        }
    }

    /**
     * 智能移除不必要的括号
     *
     * 规则：
     * 1. 移除单项的括号：(2x) → 2x, (x²) → x²
     * 2. 移除函数参数的多余外层括号：cos((x²)) → cos(x²)
     * 3. 移除嵌套括号：((a+b)) → (a+b)
     * 4. 移除分子分母的多余括号：((a))/b → a/b
     * 5. 保留必要的括号：(a+b)×c, sin(x+1)
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
                // 找到匹配的右括号
                val closeIndex = findMatchingCloseParen(text, i)

                if (closeIndex != -1) {
                    val content = text.substring(i + 1, closeIndex)

                    // 判断是否可以移除这对括号
                    if (canRemoveParentheses(text, i, closeIndex, content)) {
                        // 移除括号
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
     *
     * 可以移除的情况：
     * 1. 括号内是单个原子项（数字、变量、函数调用）
     * 2. 括号前是函数名，且内容是简单表达式（去除多余外层）
     * 3. 括号是最外层且不影响运算优先级
     */
    private fun canRemoveParentheses(text: String, openIndex: Int, closeIndex: Int, content: String): Boolean {
        // 情况1：空括号（不应该出现，但防御性处理）
        if (content.isEmpty()) return false

        // 情况2：括号前是函数名，保留括号但可能去除内部多余括号
        if (openIndex > 0) {
            val beforeParen = text.substring(0, openIndex)
            if (beforeParen.matches(Regex(".*[a-z]+$"))) {
                // 这是函数参数，检查内容是否有多余的最外层括号
                return content.startsWith("(") && content.endsWith(")") &&
                       findMatchingCloseParen(content, 0) == content.length - 1
            }
        }

        // 情况3：括号内是单项（没有顶层运算符）
        if (!hasTopLevelOperator(content)) {
            // 检查移除后是否安全
            val before = if (openIndex > 0) text[openIndex - 1] else ' '
            val after = if (closeIndex < text.length - 1) text[closeIndex + 1] else ' '

            // 除法分子分母的括号可以移除
            if (before == '/' || after == '/') return true

            // 幂运算的括号可以移除（如果内容是单项）
            if (before == '^' || after == '^') return true

            // 其他单项括号可以移除
            return true
        }

        // 情况4：括号是整个表达式的最外层
        if (openIndex == 0 && closeIndex == text.length - 1) {
            return true
        }

        return false
    }

    /**
     * 检查表达式是否包含顶层运算符
     *
     * 顶层运算符：不在括号内的 +, -, ×, /
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
     *
     * 例如：
     * - -1× → -
     * - +-1× → -
     * - --1× → +
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
     *
     * 将系数移到变量和函数之前，符合数学习惯
     * 例如：cos(x)×2×x → 2×x×cos(x)
     */
    private fun groupCoefficientsAndVariables(text: String): String {
        val terms = splitByAddSubtract(text)
        val rearrangedTerms = terms.map { term ->
            rearrangeTerm(term.value, term.operator)
        }
        return rearrangedTerms.joinToString("")
    }

    /**
     * 项数据类：包含项的值和前面的运算符
     */
    private data class Term(val value: String, val operator: String)

    /**
     * 按加减法分割表达式为多个项
     *
     * 注意：
     * - 处理括号嵌套
     * - 区分负号和减号
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
     *
     * 处理括号嵌套，正确分割：
     * - "2×x×cos(x^2)" → ["2", "x", "cos(x^2)"]
     * - "(a+b)×(c+d)" → ["(a+b)", "(c+d)"]
     *
     * 不是简单的 split("×")，而是考虑括号配对
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
                        // 在顶层遇到乘号，分割因子
                        if (currentFactor.isNotEmpty()) {
                            factors.add(currentFactor.toString())
                            currentFactor = StringBuilder()
                        }
                    } else {
                        // 在括号内，乘号是因子的一部分
                        currentFactor.append(char)
                    }
                }
                else -> {
                    currentFactor.append(char)
                }
            }
        }

        // 添加最后一个因子
        if (currentFactor.isNotEmpty()) {
            factors.add(currentFactor.toString())
        }

        return factors
    }

    /**
     * 判断因子类型
     */
    private enum class FactorType {
        COEFFICIENT,  // 纯数字
        VARIABLE,     // 单个变量（可能带指数）
        FUNCTION,     // 函数或复合表达式
        PARENTHESIZED // 括号表达式
    }

    /**
     * 获取因子类型（改进版：先尝试去括号）
     */
    private fun getFactorType(factor: String): FactorType {
        var trimmed = factor.trim()

        // 尝试去掉最外层单项括号
        while (trimmed.startsWith("(") && trimmed.endsWith(")")) {
            val inner = trimmed.substring(1, trimmed.length - 1)
            if (!hasTopLevelOperator(inner)) {
                trimmed = inner
            } else {
                break
            }
        }

        return when {
            // 纯数字（包括负数和小数）
            trimmed.matches(Regex("-?\\d+(\\.\\d+)?")) -> FactorType.COEFFICIENT

            // 单个变量（可能带指数），如 x, x^2, y^3
            trimmed.matches(Regex("[a-z](\\^-?\\d+)?")) -> FactorType.VARIABLE

            // 数字×变量（如 2x, 3y^2）
            trimmed.matches(Regex("-?\\d+(\\.\\d+)?×?[a-z](\\^-?\\d+)?")) -> FactorType.COEFFICIENT

            // 括号表达式
            trimmed.startsWith("(") && trimmed.endsWith(")") -> FactorType.PARENTHESIZED

            // 其他情况（函数、复合表达式）
            else -> FactorType.FUNCTION
        }
    }

    /**
     * 重排单个项，将系数前置
     *
     * 规则：
     * - 系数（数字）放最前
     * - 变量放中间
     * - 函数和括号表达式放最后
     * - 除法不参与重排
     *
     * 例如：
     * - cos(x)×2×x → 2×x×cos(x)
     * - x×(x+1)×2 → 2×x×(x+1)
     */
    private fun rearrangeTerm(term: String, operator: String): String {
        if (term.contains("/")) {
            Log.d("MathFormatter", "项包含除法，不重排: $term")
            return operator + term
        }

        // 智能分割因子（考虑括号嵌套）
        val factors = splitFactors(term)

        val coefficients = mutableListOf<String>()
        val variables = mutableListOf<String>()
        val others = mutableListOf<String>()

        for (factor in factors) {
            if (factor.isEmpty()) continue

            when (getFactorType(factor)) {
                FactorType.COEFFICIENT -> coefficients.add(factor)
                FactorType.VARIABLE -> variables.add(factor)
                FactorType.FUNCTION, FactorType.PARENTHESIZED -> others.add(factor)
            }
        }

        val result = StringBuilder()
        result.append(operator)

        // 按顺序组合：系数 + 变量 + 其他
        val allFactors = coefficients + variables + others

        for (i in allFactors.indices) {
            result.append(allFactors[i])
            if (i < allFactors.size - 1) {
                result.append("×")
            }
        }

        return result.toString()
    }

    /**
     * 简化乘号显示
     *
     * 规则：
     * - 数字×变量 → 数字和变量（省略乘号）
     * - 数字×括号 → 数字和括号（省略乘号）
     * - 变量×变量 → 变量和变量（省略乘号）
     * - 其他情况 → 保留为点乘 ·
     *
     * 例如：
     * - 2×x → 2x
     * - x×y → xy
     * - sin(x)×cos(x) → sin(x)·cos(x)
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
     * 创建带上标和语法高亮的SpannableString
     *
     * 处理：
     * - ^后面的内容显示为上标
     * - 不同类型的字符使用不同颜色
     * - 函数：蓝色
     * - 变量：黑色
     * - 数字：红色
     * - 运算符：绿色
     */
    private fun createFormattedSpannable(text: String): SpannableString {
        Log.d("MathFormatter", "创建SpannableString: $text")

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
        Log.d("MathFormatter", "最终显示文本: $displayText")

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
}

/**
 * 格式化结果数据类
 *
 * @param plainText 纯文本结果（用于日志和调试）
 * @param displayText 带格式的SpannableString（用于UI显示）
 */
data class FormattedResult(
    val plainText: String,
    val displayText: SpannableString
)