// app/src/main/java/com/mathsnew/mathsnew/CalculationException.kt
// 计算异常

package com.mathsnew.mathsnew

/**
 * 计算异常
 * 当计算过程中遇到无法处理的情况时抛出
 */
class CalculationException(message: String) : Exception(message)