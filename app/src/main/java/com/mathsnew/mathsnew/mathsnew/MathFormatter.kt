// app/src/main/java/com/mathsnew/mathsnew/MathFormatter.kt
// 数学表达式格式化器 - 将计算机格式转换为手写数学格式

package com.mathsnew.mathsnew

import android.text.SpannableString
import android.text.style.RelativeSizeSpan
import android.text.style.SuperscriptSpan

/**
 * 数学表达式格式化器
 *
 * 功能：
 * 1. 将幂次转换为上标显示（x^2 → x²，移除^符号）
 * 2. 省略乘号（2×x → 2x, x×cos(x) → x·cos(x)）
 * 3. 负号优化（-1×sin(x) → -sin(x)）
 *
 * 使用SpannableString实现任意指数的上标显示
 */
class MathFormatter {

    /**
     * 格式化数学表达式为显示格式
     *
     * @param expression 简化后的表达式字符串（如 "2×x×cos(x^2)"）
     * @return FormattedResult 包含纯文本和SpannableString两种格式
     */
    fun format(expression: String): FormattedResult {
        var text = expression

        // 1. 处理负号优化：-1×f(x) → -f(x)
        text = optimizeNegativeOne(text)

        // 2. 处理乘号省略：2×x → 2x, x×cos → x·cos
        text = simplifyMultiplication(text)

        // 3. 创建带上标的SpannableString（会移除^符号）
        val spannableResult = createSpannableWithSuperscript(text)

        // 4. 创建纯文本版本（用于内部计算，不含格式）
        val plainText = text

        return FormattedResult(plainText, spannableResult)
    }

    /**
     * 优化负号：-1×sin(x) → -sin(x)
     *
     * 规则：
     * - 表达式开头的 "-1×" 直接删除
     * - 中间的 "+-1×" 替换为 "-"
     * - 中间的 "--1×" 替换为 "+"
     */
    private fun optimizeNegativeOne(text: String): String {
        var result = text

        // 处理开头的 -1×
        if (result.startsWith("-1×")) {
            result = "-" + result.substring(3)
        }

        // 处理中间的 +-1×
        result = result.replace("+-1×", "-")

        // 处理中间的 --1×
        result = result.replace("--1×", "+")

        return result
    }

    /**
     * 简化乘号
     *
     * 规则：
     * - 数字×变量：2×x → 2x
     * - 数字×函数：2×sin(x) → 2·sin(x)
     * - 变量×变量：x×y → x·y
     * - 变量×函数：x×cos(x) → x·cos(x)
     * - 函数×函数：sin(x)×cos(x) → sin(x)·cos(x)
     */
    private fun simplifyMultiplication(text: String): String {
        val result = StringBuilder()
        var i = 0

        while (i < text.length) {
            val char = text[i]

            // 查找乘号
            if (char == '×') {
                // 获取乘号前后的字符
                val before = if (i > 0) getTypeBeforeMultiply(text, i - 1) else TokenType.UNKNOWN
                val after = if (i < text.length - 1) getTypeAfterMultiply(text, i + 1) else TokenType.UNKNOWN

                // 根据前后类型决定乘号的处理
                when {
                    // 数字×变量 或 数字×左括号（如 2×(x+1)）：省略乘号
                    (before == TokenType.NUMBER && after == TokenType.VARIABLE) ||
                    (before == TokenType.NUMBER && after == TokenType.LEFT_PAREN) -> {
                        // 直接跳过乘号，不输出任何字符
                    }

                    // 其他情况：使用点号
                    else -> {
                        result.append('·')
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
     * 判断乘号前面的类型
     *
     * @param text 完整文本
     * @param index 乘号前一个字符的索引
     * @return TokenType
     */
    private fun getTypeBeforeMultiply(text: String, index: Int): TokenType {
        var i = index

        // 向前找到第一个非空白字符
        while (i >= 0 && text[i].isWhitespace()) {
            i--
        }

        if (i < 0) return TokenType.UNKNOWN

        val char = text[i]

        return when {
            // 右括号
            char == ')' -> TokenType.RIGHT_PAREN

            // 数字
            char.isDigit() -> TokenType.NUMBER

            // 字母（变量）
            char.isLetter() -> {
                // 需要向前检查是否是函数结尾
                // 如果前面有左括号，说明是函数；否则是变量
                var j = i
                while (j >= 0 && text[j].isLetter()) {
                    j--
                }
                // 检查前面是否是右括号（函数调用的结束）
                if (i > 0 && hasFunctionPattern(text, i)) {
                    TokenType.FUNCTION
                } else {
                    TokenType.VARIABLE
                }
            }

            else -> TokenType.UNKNOWN
        }
    }

    /**
     * 判断乘号后面的类型
     *
     * @param text 完整文本
     * @param index 乘号后一个字符的索引
     * @return TokenType
     */
    private fun getTypeAfterMultiply(text: String, index: Int): TokenType {
        var i = index

        // 跳过空白字符
        while (i < text.length && text[i].isWhitespace()) {
            i++
        }

        if (i >= text.length) return TokenType.UNKNOWN

        val char = text[i]

        return when {
            // 左括号
            char == '(' -> TokenType.LEFT_PAREN

            // 数字
            char.isDigit() -> TokenType.NUMBER

            // 字母（可能是变量或函数）
            char.isLetter() -> {
                // 向后查找，如果后面跟着左括号，说明是函数
                var j = i
                while (j < text.length && text[j].isLetter()) {
                    j++
                }
                if (j < text.length && text[j] == '(') {
                    TokenType.FUNCTION
                } else {
                    TokenType.VARIABLE
                }
            }

            else -> TokenType.UNKNOWN
        }
    }

    /**
     * 检查是否是函数模式（函数名后跟括号）
     */
    private fun hasFunctionPattern(text: String, index: Int): Boolean {
        // 向前找到函数名的开始
        var start = index
        while (start >= 0 && text[start].isLetter()) {
            start--
        }
        start++

        // 向后找到函数名的结束
        var end = index
        while (end < text.length && text[end].isLetter()) {
            end++
        }

        // 检查函数名后面是否有左括号
        return end < text.length && text[end] == '('
    }

    /**
     * 创建带上标的SpannableString
     *
     * 将 x^2 转换为 x² 的显示效果（移除^符号）
     * 使用SuperscriptSpan和RelativeSizeSpan实现上标
     */
    private fun createSpannableWithSuperscript(text: String): SpannableString {
        // 第一步：移除所有^符号，并记录指数位置
        val cleanedText = StringBuilder()
        val superscriptRanges = mutableListOf<Pair<Int, Int>>()

        var i = 0
        while (i < text.length) {
            if (text[i] == '^') {
                // 找到^符号，跳过它
                i++

                // 记录指数的开始位置（在cleanedText中的位置）
                val exponentStart = cleanedText.length

                // 跳过可能的负号
                if (i < text.length && text[i] == '-') {
                    cleanedText.append(text[i])
                    i++
                }

                // 读取指数（数字和小数点）
                while (i < text.length && (text[i].isDigit() || text[i] == '.')) {
                    cleanedText.append(text[i])
                    i++
                }

                // 记录指数的结束位置
                val exponentEnd = cleanedText.length

                // 保存这个范围
                if (exponentEnd > exponentStart) {
                    superscriptRanges.add(Pair(exponentStart, exponentEnd))
                }
            } else {
                // 普通字符，直接添加
                cleanedText.append(text[i])
                i++
            }
        }

        // 第二步：创建SpannableString并应用上标效果
        val spannableString = SpannableString(cleanedText.toString())

        for ((start, end) in superscriptRanges) {
            // SuperscriptSpan: 将文字提升到上标位置
            spannableString.setSpan(
                SuperscriptSpan(),
                start,
                end,
                SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE
            )

            // RelativeSizeSpan: 将上标文字缩小到70%
            spannableString.setSpan(
                RelativeSizeSpan(0.7f),
                start,
                end,
                SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }

        return spannableString
    }

    /**
     * Token类型枚举
     */
    private enum class TokenType {
        NUMBER,          // 数字
        VARIABLE,        // 变量（x, y等）
        FUNCTION,        // 函数（sin, cos等）
        LEFT_PAREN,      // 左括号
        RIGHT_PAREN,     // 右括号
        UNKNOWN          // 未知类型
    }
}

/**
 * 格式化结果
 *
 * @param plainText 纯文本版本（用于内部计算）
 * @param displayText 显示文本版本（SpannableString，带上标格式，无^符号）
 */
data class FormattedResult(
    val plainText: String,
    val displayText: SpannableString
)