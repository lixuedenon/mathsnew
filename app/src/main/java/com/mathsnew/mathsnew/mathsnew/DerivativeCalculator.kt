// app/src/main/java/com/mathsnew/mathsnew/DerivativeCalculator.kt
// 微分计算器（规则引擎）- 完整版

package com.mathsnew.mathsnew

/**
 * 微分计算器
 * 使用规则引擎模式，按优先级匹配并应用微分规则
 *
 * 工作流程：
 * 1. 接收AST节点
 * 2. 遍历所有规则（按优先级从高到低）
 * 3. 找到第一个匹配的规则
 * 4. 应用该规则进行转换
 * 5. 返回转换后的AST
 */
class DerivativeCalculator {

    /**
     * 所有微分规则（按优先级排序）
     *
     * 规则优先级说明：
     * 200 - 常数规则
     * 150 - 变量规则
     * 100 - 简单幂规则 (x^n)
     * 98  - 复合幂规则 ((f(x))^n)
     * 95  - 一般幂规则 (u^v)
     * 90  - 加减法规则
     * 85  - 商规则
     * 80  - 乘法规则
     * 70  - 三角函数规则（包括反三角函数）
     * 65  - 指数对数规则
     */
    private val rules: List<DerivativeRule> = listOf(
        ConstantRule(),
        VariableRule(),
        PowerRule(),
        CompositePowerRule(),
        GeneralPowerRule(),
        AdditionRule(),
        SubtractionRule(),
        QuotientRule(),
        ProductRule(),
        SinRule(),
        CosRule(),
        TanRule(),
        CotRule(),
        SecRule(),
        CscRule(),
        ArcsinRule(),
        ArccosRule(),
        ArctanRule(),
        ArccotRule(),
        ArcsecRule(),
        ArccscRule(),
        ExpRule(),
        LnRule(),
        LogRule(),
        SqrtRule(),
        AbsRule()
    ).sortedByDescending { it.priority }

    /**
     * 对AST节点进行微分
     *
     * @param node AST节点
     * @param variable 求导变量（默认为"x"）
     * @return 微分后的AST节点
     */
    fun differentiate(node: MathNode, variable: String = "x"): MathNode {
        for (rule in rules) {
            if (rule.matches(node, variable)) {
                return rule.apply(node, variable, this)
            }
        }

        throw CalculationException("无法对表达式求导: $node")
    }

    /**
     * 对表达式字符串进行微分
     *
     * @param expression 表达式字符串
     * @param variable 求导变量（默认为"x"）
     * @return 微分结果字符串
     */
    fun differentiateExpression(expression: String, variable: String = "x"): String {
        val parser = ExpressionParser()
        val ast = parser.parse(expression)
        val resultAst = differentiate(ast, variable)
        return resultAst.toString()
    }
}