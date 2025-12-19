// app/src/main/java/com/mathsnew/mathsnew/Token.kt
// 词法单元定义

package com.mathsnew.mathsnew

/**
 * 词法单元（Token）
 * 表达式解析的第一步：将字符串分解为词法单元
 *
 * 例如：
 * "x^2+3" → [Variable(x), Operator(^), Number(2), Operator(+), Number(3)]
 */
sealed class Token {
    data class Number(val value: Double) : Token()
    data class Variable(val name: String) : Token()
    data class Operator(val symbol: String) : Token()
    data class Function(val name: String) : Token()
    data class LeftParen(val dummy: Unit = Unit) : Token()
    data class RightParen(val dummy: Unit = Unit) : Token()
}