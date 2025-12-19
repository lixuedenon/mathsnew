// app/src/main/java/com/mathsnew/mathsnew/DerivativeRule.kt
// 微分规则接口定义

package com.mathsnew.mathsnew

/**
 * 微分规则接口
 * 所有具体的微分规则都实现此接口
 *
 * 规则引擎的工作原理：
 * 1. 遍历所有规则（按优先级排序）
 * 2. 对每个规则调用 matches() 判断是否适用
 * 3. 如果适用，调用 apply() 进行转换
 * 4. 返回转换后的结果
 */
interface DerivativeRule {
    /**
     * 规则名称（用于调试）
     */
    val name: String

    /**
     * 优先级（数字越大越先匹配）
     * 建议范围：
     * - 200: 常数规则
     * - 150: 变量规则
     * - 100: 基础运算规则（幂、加减乘除）
     * - 50-90: 函数规则（三角、对数、指数）
     * - 10: 链式法则（兜底规则）
     */
    val priority: Int

    /**
     * 判断此规则是否适用于给定的节点
     *
     * @param node 要判断的AST节点
     * @param variable 求导变量（默认为"x"）
     * @return true表示此规则适用，false表示不适用
     */
    fun matches(node: MathNode, variable: String): Boolean

    /**
     * 应用此规则进行微分转换
     *
     * @param node 要转换的AST节点
     * @param variable 求导变量
     * @param calculator 计算器实例（用于递归调用）
     * @return 转换后的AST节点
     */
    fun apply(node: MathNode, variable: String, calculator: DerivativeCalculator): MathNode
}