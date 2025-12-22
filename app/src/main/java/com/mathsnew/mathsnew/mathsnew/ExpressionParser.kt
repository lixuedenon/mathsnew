// app/src/main/java/com/mathsnew/mathsnew/ExpressionParser.kt
// 表达式解析器（移除重复定义）

package com.mathsnew.mathsnew

import android.util.Log

/**
 * 表达式解析器
 */
class ExpressionParser {

    private lateinit var tokens: List<Token>
    private var position = 0

    /**
     * 解析表达式字符串
     */
    fun parse(expression: String): MathNode {
        Log.d("ExpressionParser", "========================================")
        Log.d("ExpressionParser", "开始解析: '$expression'")

        val tokenizer = Tokenizer()
        tokens = tokenizer.tokenize(expression)
        position = 0

        Log.d("ExpressionParser", "Token序列 (${tokens.size}个):")
        tokens.forEachIndexed { index, token ->
            Log.d("ExpressionParser", "  [$index] $token")
        }

        if (tokens.isEmpty()) {
            throw ParseException(message = "表达式不能为空")
        }

        val result = parseExpression()

        Log.d("ExpressionParser", "解析完成: position=$position, total=${tokens.size}")

        if (position < tokens.size) {
            Log.e("ExpressionParser", "❌ 还有 ${tokens.size - position} 个未处理的token:")
            for (i in position until tokens.size) {
                Log.e("ExpressionParser", "  [$i] ${tokens[i]}")
            }
            throw ParseException(message = "表达式未包含多余的符号")
        }

        Log.d("ExpressionParser", "✅ 成功, AST: $result")
        Log.d("ExpressionParser", "========================================")

        return result
    }

    /**
     * 解析表达式（加减法）
     */
    private fun parseExpression(): MathNode {
        Log.d("ExpressionParser", "parseExpression() at $position")
        var left = parseTerm()

        while (position < tokens.size) {
            val token = tokens[position]
            val tokenStr = token.toString()

            when {
                tokenStr.contains("+") -> {
                    Log.d("ExpressionParser", "加号 at $position")
                    position++
                    val right = parseTerm()
                    left = MathNode.BinaryOp(Operator.ADD, left, right)
                }
                tokenStr.contains("-") && !tokenStr.contains("SUBTRACT") -> {
                    Log.d("ExpressionParser", "减号 at $position")
                    position++
                    val right = parseTerm()
                    left = MathNode.BinaryOp(Operator.SUBTRACT, left, right)
                }
                else -> break
            }
        }

        return left
    }

    /**
     * 解析项（乘除法）
     */
    private fun parseTerm(): MathNode {
        Log.d("ExpressionParser", "parseTerm() at $position")
        var left = parsePower()

        while (position < tokens.size) {
            val token = tokens[position]
            val tokenStr = token.toString()

            when {
                tokenStr.contains("×") || tokenStr.contains("*") -> {
                    Log.d("ExpressionParser", "乘号 at $position")
                    position++
                    val right = parsePower()
                    left = MathNode.BinaryOp(Operator.MULTIPLY, left, right)
                }
                tokenStr.contains("/") && !tokenStr.contains("DIVIDE") -> {
                    Log.d("ExpressionParser", "除号 at $position")
                    position++
                    val right = parsePower()
                    left = MathNode.BinaryOp(Operator.DIVIDE, left, right)
                }
                else -> break
            }
        }

        return left
    }

    /**
     * 解析幂运算
     */
    private fun parsePower(): MathNode {
        Log.d("ExpressionParser", "parsePower() at $position")
        var left = parseFactor()

        while (position < tokens.size) {
            val token = tokens[position]
            val tokenStr = token.toString()

            if (tokenStr.contains("^")) {
                Log.d("ExpressionParser", "幂 at $position")
                position++
                val right = parseFactor()
                left = MathNode.BinaryOp(Operator.POWER, left, right)
            } else {
                break
            }
        }

        return left
    }

    /**
     * 解析因子（数字、变量、函数、括号）
     */
    private fun parseFactor(): MathNode {
        Log.d("ExpressionParser", "parseFactor() at $position")

        if (position >= tokens.size) {
            throw ParseException(message = "意外的表达式结束")
        }

        val token = tokens[position]
        Log.d("ExpressionParser", "token: $token")

        return when (token) {
            is Token.Number -> {
                Log.d("ExpressionParser", "数字: ${token.value}")
                position++
                MathNode.Number(token.value)
            }
            is Token.Variable -> {
                Log.d("ExpressionParser", "变量: ${token.name}")
                position++
                MathNode.Variable(token.name)
            }
            is Token.Function -> {
                Log.d("ExpressionParser", "函数: ${token.name}")
                position++
                if (position >= tokens.size || tokens[position] !is Token.LeftParen) {
                    throw ParseException(message = "函数 ${token.name} 后面必须有左括号")
                }
                position++

                val argument = parseExpression()

                if (position >= tokens.size || tokens[position] !is Token.RightParen) {
                    throw ParseException(message = "缺少右括号")
                }
                position++

                MathNode.Function(token.name, argument)
            }
            is Token.LeftParen -> {
                Log.d("ExpressionParser", "左括号")
                position++
                val expression = parseExpression()

                if (position >= tokens.size || tokens[position] !is Token.RightParen) {
                    throw ParseException(message = "缺少右括号")
                }
                position++

                expression
            }
            else -> {
                throw ParseException(message = "意外的token: $token")
            }
        }
    }
}