// app/src/main/java/com/mathsnew/mathsnew/ExpressionParser.kt
// 表达式解析器（语法分析）

package com.mathsnew.mathsnew

/**
 * 表达式解析器
 * 使用递归下降解析算法，将Token列表转换为抽象语法树（AST）
 *
 * 语法规则（按优先级从低到高）：
 * expression → term (('+' | '-') term)*
 * term       → factor (('×' | '÷') factor)*
 * factor     → base ('^' base)*
 * base       → number | variable | function '(' expression ')' | '(' expression ')'
 *
 * 测试示例：
 * parse("x^2+3") → BinaryOp(ADD, BinaryOp(POWER, Variable(x), Number(2)), Number(3))
 */
class ExpressionParser {

    private lateinit var tokens: List<Token>
    private var position = 0

    /**
     * 解析表达式字符串为AST
     */
    fun parse(expression: String): MathNode {
        val tokenizer = Tokenizer()
        tokens = tokenizer.tokenize(expression)
        position = 0

        if (tokens.isEmpty()) {
            throw ParseException("表达式不能为空")
        }

        val result = parseExpression()

        if (position < tokens.size) {
            throw ParseException("表达式末尾有多余的符号")
        }

        return result
    }

    /**
     * 解析表达式：term (('+' | '-') term)*
     */
    private fun parseExpression(): MathNode {
        var left = parseTerm()

        while (position < tokens.size) {
            val token = tokens[position]

            if (token is Token.Operator && (token.symbol == "+" || token.symbol == "-")) {
                position++
                val right = parseTerm()

                val operator = if (token.symbol == "+") Operator.ADD else Operator.SUBTRACT
                left = MathNode.BinaryOp(operator, left, right)
            } else {
                break
            }
        }

        return left
    }

    /**
     * 解析项：factor (('×' | '÷') factor)*
     */
    private fun parseTerm(): MathNode {
        var left = parseFactor()

        while (position < tokens.size) {
            val token = tokens[position]

            if (token is Token.Operator && (token.symbol == "×" || token.symbol == "÷")) {
                position++
                val right = parseFactor()

                val operator = if (token.symbol == "×") Operator.MULTIPLY else Operator.DIVIDE
                left = MathNode.BinaryOp(operator, left, right)
            } else {
                break
            }
        }

        return left
    }

    /**
     * 解析因子：base ('^' base)*
     */
    private fun parseFactor(): MathNode {
        var left = parseBase()

        while (position < tokens.size) {
            val token = tokens[position]

            if (token is Token.Operator && token.symbol == "^") {
                position++
                val right = parseBase()
                left = MathNode.BinaryOp(Operator.POWER, left, right)
            } else {
                break
            }
        }

        return left
    }

    /**
     * 解析基础元素：number | variable | function '(' expression ')' | '(' expression ')'
     */
    private fun parseBase(): MathNode {
        if (position >= tokens.size) {
            throw ParseException("表达式不完整")
        }

        val token = tokens[position]

        return when (token) {
            // 数字
            is Token.Number -> {
                position++
                MathNode.Number(token.value)
            }

            // 变量
            is Token.Variable -> {
                position++
                MathNode.Variable(token.name)
            }

            // 函数调用
            is Token.Function -> {
                position++

                if (position >= tokens.size || tokens[position] !is Token.LeftParen) {
                    throw ParseException("函数 ${token.name} 后面必须有左括号")
                }
                position++ // 跳过左括号

                val argument = parseExpression()

                if (position >= tokens.size || tokens[position] !is Token.RightParen) {
                    throw ParseException("函数 ${token.name} 缺少右括号")
                }
                position++ // 跳过右括号

                MathNode.Function(token.name, argument)
            }

            // 括号表达式
            is Token.LeftParen -> {
                position++ // 跳过左括号
                val expression = parseExpression()

                if (position >= tokens.size || tokens[position] !is Token.RightParen) {
                    throw ParseException("缺少右括号")
                }
                position++ // 跳过右括号

                expression
            }

            else -> {
                throw ParseException("意外的符号: $token")
            }
        }
    }
}