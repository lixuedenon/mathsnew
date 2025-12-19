// app/src/main/java/com/mathsnew/mathsnew/ParseException.kt
// 表达式解析异常

package com.mathsnew.mathsnew

/**
 * 表达式解析异常
 * 当输入的表达式格式错误时抛出
 */
class ParseException(message: String) : Exception(message)