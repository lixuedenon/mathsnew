// app/src/main/java/com/mathsnew/mathsnew/DerivativeCalculator.kt
// 微分计算器（规则引擎）

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
 *
 * 测试建议：
 * 1. 在CalculusFragment输入 "x^2"，点击微分，应显示 "2×x"
 * 2. 输入 "x^2+3*x"，点击微分，应显示 "2×x+3"
 * 3. 输入 "sin(x)"，点击微分，应显示 "cos(x)"
 * 4. 输入 "sin(x^2)"，点击微分，应显示 "cos(x^2)×2×x"
 */
class DerivativeCalculator {

    /**
     * 所有微分规则（按优先级排序）
     */
    private val rules: List<DerivativeRule> = listOf(
        // 基础规则（优先级 200-150）
        ConstantRule(),       // 优先级 200
        VariableRule(),       // 优先级 150

        // 幂运算规则（优先级 100-95）
        PowerRule(),          // 优先级 100
        GeneralPowerRule(),   // 优先级 95（处理 x^x 等情况）

        // 四则运算规则（优先级 90-80）
        AdditionRule(),       // 优先级 90
        SubtractionRule(),    // 优先级 90
        QuotientRule(),       // 优先级 85（商规则）
        ProductRule(),        // 优先级 80

        // 三角函数规则（优先级 70）
        SinRule(),            // 优先级 70
        CosRule(),            // 优先级 70
        TanRule(),            // 优先级 70
        CotRule(),            // 优先级 70
        SecRule(),            // 优先级 70
        CscRule(),            // 优先级 70

        // 指数对数规则（优先级 65）
        ExpRule(),            // 优先级 65
        LnRule(),             // 优先级 65
        LogRule(),            // 优先级 65
        SqrtRule(),           // 优先级 65
        AbsRule()             // 优先级 65（绝对值规则）
    ).sortedByDescending { it.priority }

    /**
     * 对AST节点进行微分
     *
     * @param node AST节点
     * @param variable 求导变量（默认为"x"）
     * @return 微分后的AST节点
     */
    fun differentiate(node: MathNode, variable: String = "x"): MathNode {
        // 遍历所有规则
        for (rule in rules) {
            if (rule.matches(node, variable)) {
                // 找到匹配的规则，应用并返回
                return rule.apply(node, variable, this)
            }
        }

        // 没有匹配的规则
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
        // 1. 解析表达式为AST
        val parser = ExpressionParser()
        val ast = parser.parse(expression)

        // 2. 对AST进行微分
        val resultAst = differentiate(ast, variable)

        // 3. 将AST转换回字符串
        return resultAst.toString()
    }
}
