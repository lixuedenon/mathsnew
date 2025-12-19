// app/src/main/java/com/mathsnew/mathsnew/Tokenizer.kt
// 词法分析器

package com.mathsnew.mathsnew

/**
 * 词法分析器
 * 将表达式字符串转换为Token列表
 *
 * 测试示例：
 * tokenize("x^2+3") → [Variable(x), Operator(^), Number(2), Operator(+), Number(3)]
 * tokenize("sin(x)") → [Function(sin), LeftParen, Variable(x), RightParen]
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
                    tokens.add(Token.Number(numberStr.toString().toDouble()))
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
                    if (i < expression.length && expression[i] == '(') {
                        when (name) {
                            "sin", "cos", "tan", "ln", "log", "sqrt", "exp" -> {
                                tokens.add(Token.Function(name))
                            }
                            else -> {
                                // 普通变量
                                tokens.add(Token.Variable(name))
                            }
                        }
                    } else {
                        // 普通变量
                        tokens.add(Token.Variable(name))
                    }
                }

                // 运算符
                char in "+-×÷^*/" -> {
                    val operatorSymbol = when (char) {
                        '*' -> "×"
                        '/' -> "÷"
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
                    i++
                }

                // 特殊符号
                char == 'π' -> {
                    tokens.add(Token.Number(Math.PI))
                    i++
                }
                char == 'e' && (i + 1 >= expression.length || !expression[i + 1].isLetter()) -> {
                    tokens.add(Token.Number(Math.E))
                    i++
                }

                else -> {
                    throw ParseException("无法识别的字符: $char")
                }
            }
        }

        return tokens
    }
}