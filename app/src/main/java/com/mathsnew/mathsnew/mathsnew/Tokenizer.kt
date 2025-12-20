// app/src/main/java/com/mathsnew/mathsnew/Tokenizer.kt
// 词法分析器

package com.mathsnew.mathsnew

/**
 * 词法分析器
 * 将表达式字符串转换为Token列表
 *
 * 支持隐式乘法：
 * - 3x → 3×x
 * - 2sin(x) → 2×sin(x)
 * - (x+1)(x-1) → (x+1)×(x-1)
 * - x(x+1) → x×(x+1)
 *
 * 测试示例：
 * tokenize("x^2+3") → [Variable(x), Operator(^), Number(2), Operator(+), Number(3)]
 * tokenize("sin(x)") → [Function(sin), LeftParen, Variable(x), RightParen]
 * tokenize("3x") → [Number(3), Operator(×), Variable(x)]
 */
class Tokenizer {

    /**
     * 将表达式字符串转换为Token列表
     */
    fun tokenize(expression: String): List<Token> {
        val tokens = mutableListOf<Token>()
        var i = 0

        while (i < expression.length) {
            val char = expression[i]

            when {
                // 跳过空格
                char.isWhitespace() -> {
                    i++
                }

                // 数字（包括小数）
                char.isDigit() || char == '.' -> {
                    val numberStr = StringBuilder()
                    while (i < expression.length &&
                           (expression[i].isDigit() || expression[i] == '.')) {
                        numberStr.append(expression[i])
                        i++
                    }

                    // 添加数字token
                    tokens.add(Token.Number(numberStr.toString().toDouble()))

                    // 隐式乘法检测：数字后面跟字母或左括号
                    if (i < expression.length) {
                        val nextChar = expression[i]
                        if (nextChar.isLetter() || nextChar == '(') {
                            tokens.add(Token.Operator("×"))
                        }
                    }
                }

                // 字母（变量或函数名）
                char.isLetter() -> {
                    val nameStr = StringBuilder()
                    while (i < expression.length && expression[i].isLetter()) {
                        nameStr.append(expression[i])
                        i++
                    }

                    val name = nameStr.toString()

                    // 判断是否是函数（后面跟着左括号）
                    val hasLeftParenAfter = i < expression.length && expression[i] == '('

                    if (hasLeftParenAfter) {
                        when (name) {
                            "sin", "cos", "tan", "cot", "sec", "csc",
                            "ln", "log", "sqrt", "exp", "abs" -> {
                                // 这是函数，直接添加，不需要隐式乘法
                                tokens.add(Token.Function(name))
                                // 注意：不需要 continue，因为没有后续的隐式乘法检测了
                            }
                            else -> {
                                // 普通变量后跟括号，需要插入乘号：x(x+1) → x×(x+1)
                                tokens.add(Token.Variable(name))
                                tokens.add(Token.Operator("×"))
                            }
                        }
                    } else {
                        // 普通变量（后面不是左括号）
                        tokens.add(Token.Variable(name))

                        // 隐式乘法检测：变量后面跟字母或左括号
                        if (i < expression.length) {
                            val nextChar = expression[i]
                            if (nextChar.isLetter() || nextChar == '(') {
                                tokens.add(Token.Operator("×"))
                            }
                        }
                    }
                }

                // 运算符
                char in "+-×÷^*/" -> {
                    val operatorSymbol = when (char) {
                        '*' -> "×"
                        '÷' -> "/"
                        else -> char.toString()
                    }
                    tokens.add(Token.Operator(operatorSymbol))
                    i++
                }

                // 括号
                char == '(' -> {
                    tokens.add(Token.LeftParen())
                    i++
                }
                char == ')' -> {
                    tokens.add(Token.RightParen())

                    // 隐式乘法检测：右括号后面跟数字、字母或左括号
                    i++
                    if (i < expression.length) {
                        val nextChar = expression[i]
                        if (nextChar.isDigit() || nextChar.isLetter() || nextChar == '(') {
                            tokens.add(Token.Operator("×"))
                        }
                    }
                    continue  // 已经处理了 i++，跳过后面的 i++
                }

                // 特殊符号
                char == 'π' -> {
                    tokens.add(Token.Number(Math.PI))

                    // 隐式乘法检测：π后面跟字母或左括号
                    i++
                    if (i < expression.length) {
                        val nextChar = expression[i]
                        if (nextChar.isLetter() || nextChar == '(') {
                            tokens.add(Token.Operator("×"))
                        }
                    }
                    continue  // 已经处理了 i++
                }
                char == 'e' && (i + 1 >= expression.length || !expression[i + 1].isLetter()) -> {
                    tokens.add(Token.Number(Math.E))

                    // 隐式乘法检测：e后面跟字母或左括号
                    i++
                    if (i < expression.length) {
                        val nextChar = expression[i]
                        if (nextChar.isLetter() || nextChar == '(') {
                            tokens.add(Token.Operator("×"))
                        }
                    }
                    continue  // 已经处理了 i++
                }

                else -> {
                    throw ParseException("无法识别的字符: $char")
                }
            }

            i++
        }

        return tokens
    }
}