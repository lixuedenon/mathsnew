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
                char.isWhitespace() -> {
                    i++
                }

                char.isDigit() || char == '.' -> {
                    val numberStr = StringBuilder()
                    while (i < expression.length &&
                           (expression[i].isDigit() || expression[i] == '.')) {
                        numberStr.append(expression[i])
                        i++
                    }

                    tokens.add(Token.Number(numberStr.toString().toDouble()))

                    if (i < expression.length) {
                        val nextChar = expression[i]
                        if (nextChar.isLetter() || nextChar == '(') {
                            tokens.add(Token.Operator("×"))
                        }
                    }
                    continue
                }

                char.isLetter() -> {
                    val nameStr = StringBuilder()
                    while (i < expression.length && expression[i].isLetter()) {
                        nameStr.append(expression[i])
                        i++
                    }

                    val name = nameStr.toString()

                    if (i < expression.length && expression[i] == '(') {
                        when (name) {
                            "sin", "cos", "tan", "cot", "sec", "csc",
                            "arcsin", "arccos", "arctan", "arccot", "arcsec", "arccsc",
                            "ln", "log", "sqrt", "exp", "abs" -> {
                                tokens.add(Token.Function(name))
                                continue
                            }
                            else -> {
                                tokens.add(Token.Variable(name))
                                tokens.add(Token.Operator("×"))
                                continue
                            }
                        }
                    } else {
                        tokens.add(Token.Variable(name))

                        if (i < expression.length) {
                            val nextChar = expression[i]
                            if (nextChar.isLetter() || nextChar == '(') {
                                tokens.add(Token.Operator("×"))
                            }
                        }
                        continue
                    }
                }

                char in "+-×÷^*/" -> {
                    val operatorSymbol = when (char) {
                        '*' -> "×"
                        '÷' -> "/"
                        else -> char.toString()
                    }
                    tokens.add(Token.Operator(operatorSymbol))
                    i++
                }

                char == '(' -> {
                    tokens.add(Token.LeftParen())
                    i++
                }
                char == ')' -> {
                    tokens.add(Token.RightParen())

                    i++
                    if (i < expression.length) {
                        val nextChar = expression[i]
                        if (nextChar.isDigit() || nextChar.isLetter() || nextChar == '(') {
                            tokens.add(Token.Operator("×"))
                        }
                    }
                    continue
                }

                char == 'π' -> {
                    tokens.add(Token.Number(Math.PI))

                    i++
                    if (i < expression.length) {
                        val nextChar = expression[i]
                        if (nextChar.isLetter() || nextChar == '(') {
                            tokens.add(Token.Operator("×"))
                        }
                    }
                    continue
                }
                char == 'e' && (i + 1 >= expression.length || !expression[i + 1].isLetter()) -> {
                    tokens.add(Token.Number(Math.E))

                    i++
                    if (i < expression.length) {
                        val nextChar = expression[i]
                        if (nextChar.isLetter() || nextChar == '(') {
                            tokens.add(Token.Operator("×"))
                        }
                    }
                    continue
                }

                else -> {
                    throw ParseException("无法识别的字符: $char")
                }
            }
        }

        return tokens
    }
}