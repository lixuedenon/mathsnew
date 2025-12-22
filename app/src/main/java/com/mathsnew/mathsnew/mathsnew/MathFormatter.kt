// app/src/main/java/com/mathsnew/mathsnew/MathFormatter.kt
// 数学表达式格式化器（修复负数显示）

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

    fun format(expression: String): FormattedResult {
        Log.d("MathFormatter", "格式化输入: $expression")

        var text = expression

        // 1. 处理 0-x 转换为 -x
        text = simplifyNegation(text)
        Log.d("MathFormatter", "负数简化后: $text")

        // 2. 处理负号优化
        text = optimizeNegativeOne(text)
        Log.d("MathFormatter", "负号优化后: $text")

        // 3. 系数和变量合并前置
        text = groupCoefficientsAndVariables(text)
        Log.d("MathFormatter", "系数前置后: $text")

        // 4. 处理乘号省略
        text = simplifyMultiplication(text)
        Log.d("MathFormatter", "乘号简化后: $text")

        // 5. 创建带上标和语法高亮的SpannableString
        val spannableResult = createFormattedSpannable(text)

        val plainText = text

        return FormattedResult(plainText, spannableResult)
    }

    /**
     * 简化负数表示：(0-x) → -x
     */
    private fun simplifyNegation(text: String): String {
        var result = text

        // (0-x) → -x
        result = result.replace(Regex("""\(0-([^)]+)\)"""), "-$1")

        // (0.0-x) → -x
        result = result.replace(Regex("""\(0\.0-([^)]+)\)"""), "-$1")

        return result
    }

    private fun optimizeNegativeOne(text: String): String {
        var result = text
        if (result.startsWith("-1×")) {
            result = "-" + result.substring(3)
        }
        result = result.replace("+-1×", "-")
        result = result.replace("--1×", "+")
        return result
    }

    private fun groupCoefficientsAndVariables(text: String): String {
        val terms = splitByAddSubtract(text)
        val rearrangedTerms = terms.map { term ->
            rearrangeTerm(term.value, term.operator)
        }
        return rearrangedTerms.joinToString("")
    }

    private data class Term(val value: String, val operator: String)

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

    private fun rearrangeTerm(term: String, operator: String): String {
        if (term.contains("/")) {
            Log.d("MathFormatter", "项包含除法，不重排: $term")
            return operator + term
        }

        val factors = term.split("×")

        val coefficients = mutableListOf<String>()
        val variables = mutableListOf<String>()
        val functions = mutableListOf<String>()

        for (factor in factors) {
            if (factor.isEmpty()) continue

            when {
                factor.matches(Regex("-?\\d+(\\.\\d+)?")) -> {
                    coefficients.add(factor)
                }
                factor.matches(Regex("[a-z](\\^\\d+)?")) -> {
                    variables.add(factor)
                }
                else -> {
                    functions.add(factor)
                }
            }
        }

        val result = StringBuilder()
        result.append(operator)

        val allFactors = coefficients + variables + functions

        for (i in allFactors.indices) {
            result.append(allFactors[i])
            if (i < allFactors.size - 1) {
                result.append("×")
            }
        }

        return result.toString()
    }

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

data class FormattedResult(
    val plainText: String,
    val displayText: SpannableString
)