// app/src/main/java/com/mathsnew/mathsnew/newsimplified/ExpressionCanonicalizer.kt
// 表达式规范化引擎 - 保证完全展开和合并同类项
// 修改版：分子和分母都不完全展开，保留因式结构
// ✅ 新增：分子中的加减法表达式会合并同类项

package com.mathsnew.mathsnew.newsimplified

import com.mathsnew.mathsnew.*
import android.util.Log
import kotlin.math.abs

class ExpressionCanonicalizer {

    companion object {
        private const val TAG = "ExpressionCanonicalizer"
        private const val EPSILON = 1e-10
    }

    /**
     * 规范化表达式的主入口
     *
     * 策略：
     * 1. 如果是分式，分别处理分子和分母
     *    - 分子：只合并同类项，不展开幂运算
     *    - 分母：只合并同类项，不展开幂运算
     * 2. 如果不是分式，完全展开 + 合并同类项
     */
    fun canonicalize(node: MathNode): MathNode {
        Log.d(TAG, "========== 开始规范化 ==========")
        Log.d(TAG, "输入: $node")

        if (node is MathNode.BinaryOp && node.operator == Operator.DIVIDE) {
            Log.d(TAG, "检测到分式，分别处理分子和分母")

            // ⚠️ 关键修改：分子也不完全展开，只合并同类项
            val numerator = canonicalizeNonFraction(node.left)
            Log.d(TAG, "分子规范化完成: $numerator")

            // 分母：只合并同类项，不展开幂运算（保留因式结构）
            val denominator = canonicalizeDenominatorOnly(node.right)
            Log.d(TAG, "分母规范化完成: $denominator")

            val result = MathNode.BinaryOp(Operator.DIVIDE, numerator, denominator)
            Log.d(TAG, "分式规范化完成: $result")
            Log.d(TAG, "========== 规范化完成 ==========")
            return result
        }

        val result = canonicalizeNonFraction(node)
        Log.d(TAG, "========== 规范化完成 ==========")
        return result
    }

    /**
     * 规范化非分式表达式（完全展开 + 合并同类项）
     */
    private fun canonicalizeNonFraction(node: MathNode): MathNode {
        val expanded = fullyExpand(node)
        Log.d(TAG, "展开后: $expanded")

        val terms = extractTerms(expanded)
        Log.d(TAG, "提取了 ${terms.size} 个项")

        val merged = mergeTerms(terms)
        Log.d(TAG, "合并后剩 ${merged.size} 个项")

        val sorted = sortTerms(merged)
        val result = buildExpression(sorted)

        Log.d(TAG, "最终结果: $result")

        return result
    }

    /**
     * 规范化分子/分母（只合并同类项，不展开幂运算）
     *
     * 策略：
     * 1. 如果是加减法，递归处理每一项
     * 2. 如果是乘法，递归处理每个因子
     * 3. 如果是幂运算，保持原样（不展开）
     * 4. 对于简单的加法表达式，合并同类项
     */
    private fun canonicalizeDenominatorOnly(node: MathNode): MathNode {
        Log.d(TAG, "开始化简（不展开幂）: $node")

        return when (node) {
            is MathNode.Number, is MathNode.Variable -> {
                node
            }

            is MathNode.Function -> {
                // 函数参数递归化简
                MathNode.Function(node.name, canonicalizeDenominatorOnly(node.argument))
            }

            is MathNode.BinaryOp -> {
                when (node.operator) {
                    // 加法和减法：尝试合并同类项
                    Operator.ADD, Operator.SUBTRACT -> {
                        val left = canonicalizeDenominatorOnly(node.left)
                        val right = canonicalizeDenominatorOnly(node.right)

                        // 尝试提取并合并同类项
                        val terms = extractTermsWithoutExpanding(
                            MathNode.BinaryOp(node.operator, left, right)
                        )

                        if (terms.size > 1) {
                            val merged = mergeTerms(terms)
                            val sorted = sortTerms(merged)
                            buildExpression(sorted)
                        } else {
                            MathNode.BinaryOp(node.operator, left, right)
                        }
                    }

                    // 乘法：递归化简每个因子
                    Operator.MULTIPLY -> {
                        val left = canonicalizeDenominatorOnly(node.left)
                        val right = canonicalizeDenominatorOnly(node.right)
                        MathNode.BinaryOp(Operator.MULTIPLY, left, right)
                    }

                    // ⚠️ 关键：幂运算保持原样，不展开
                    Operator.POWER -> {
                        val base = canonicalizeDenominatorOnly(node.left)
                        val exponent = canonicalizeDenominatorOnly(node.right)
                        MathNode.BinaryOp(Operator.POWER, base, exponent)
                    }

                    // 除法：递归处理
                    Operator.DIVIDE -> {
                        val left = canonicalizeDenominatorOnly(node.left)
                        val right = canonicalizeDenominatorOnly(node.right)
                        MathNode.BinaryOp(Operator.DIVIDE, left, right)
                    }
                }
            }
        }
    }

    /**
     * 提取项但不展开幂运算
     *
     * 与 extractTerms() 的区别：
     * - extractTerms() 会先完全展开
     * - 这个方法只拍平加法树，不展开幂
     */
    private fun extractTermsWithoutExpanding(node: MathNode): List<MathTerm> {
        val termNodes = flattenSum(node)
        return termNodes.map { MathTerm.fromNode(it) }
    }

    /**
     * 完全展开表达式（用于非分式）
     * 
     * ✅ 修改：对于除法，如果分子是加减法表达式，会合并同类项
     */
    private fun fullyExpand(node: MathNode): MathNode {
        return when (node) {
            is MathNode.Number, is MathNode.Variable -> node

            is MathNode.Function -> {
                MathNode.Function(node.name, fullyExpand(node.argument))
            }

            is MathNode.BinaryOp -> {
                when (node.operator) {
                    Operator.ADD, Operator.SUBTRACT -> {
                        val left = fullyExpand(node.left)
                        val right = fullyExpand(node.right)
                        MathNode.BinaryOp(node.operator, left, right)
                    }

                    Operator.MULTIPLY -> {
                        val left = fullyExpand(node.left)
                        val right = fullyExpand(node.right)
                        expandMultiplication(left, right)
                    }

                    Operator.DIVIDE -> {
                        val left = fullyExpand(node.left)
                        val right = fullyExpand(node.right)
                        
                        // ✅ 关键修改：如果分子是加减法表达式，尝试合并同类项
                        if (left is MathNode.BinaryOp && 
                            (left.operator == Operator.ADD || left.operator == Operator.SUBTRACT)) {
                            
                            Log.d(TAG, "⚠️ 检测到分子是加减法表达式，尝试合并同类项")
                            
                            try {
                                // 提取分子中的所有项
                                val terms = extractTerms(left)
                                Log.d(TAG, "  分子提取了 ${terms.size} 个项")
                                
                                // 合并同类项
                                val merged = mergeTerms(terms)
                                Log.d(TAG, "  分子合并后剩 ${merged.size} 个项")
                                
                                if (merged.size < terms.size) {
                                    // 重建分子
                                    val sorted = sortTerms(merged)
                                    val simplifiedNumerator = buildExpression(sorted)
                                    
                                    Log.d(TAG, "  ✅ 化简后的分子: $simplifiedNumerator")
                                    
                                    return MathNode.BinaryOp(Operator.DIVIDE, simplifiedNumerator, right)
                                } else {
                                    Log.d(TAG, "  ℹ️ 分子没有可合并的同类项")
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "  ❌ 分子化简失败: ${e.message}")
                            }
                        }
                        
                        MathNode.BinaryOp(Operator.DIVIDE, left, right)
                    }

                    Operator.POWER -> {
                        val base = fullyExpand(node.left)
                        val exponent = fullyExpand(node.right)
                        simplifyPower(base, exponent)
                    }
                }
            }
        }
    }

    /**
     * 简化幂运算
     */
    private fun simplifyPower(base: MathNode, exponent: MathNode): MathNode {
        // 处理嵌套幂：(a^b)^c → a^(b×c)
        if (base is MathNode.BinaryOp && base.operator == Operator.POWER
            && base.right is MathNode.Number && exponent is MathNode.Number) {
            val innerExponent = base.right.value
            val outerExponent = exponent.value
            val newExponent = innerExponent * outerExponent

            Log.d(TAG, "简化嵌套幂: (${base.left})^$innerExponent^$outerExponent -> (${base.left})^$newExponent")

            return MathNode.BinaryOp(Operator.POWER, base.left, MathNode.Number(newExponent))
        }

        if (exponent is MathNode.Number) {
            return expandPower(base, exponent)
        }

        return MathNode.BinaryOp(Operator.POWER, base, exponent)
    }

    /**
     * 展开乘法
     */
    private fun expandMultiplication(left: MathNode, right: MathNode): MathNode {
        val leftIsSum = left is MathNode.BinaryOp && (left.operator == Operator.ADD || left.operator == Operator.SUBTRACT)
        val rightIsSum = right is MathNode.BinaryOp && (right.operator == Operator.ADD || right.operator == Operator.SUBTRACT)

        return when {
            leftIsSum && rightIsSum -> {
                val leftTerms = flattenSum(left)
                val rightTerms = flattenSum(right)

                val products = mutableListOf<MathNode>()
                for (lt in leftTerms) {
                    for (rt in rightTerms) {
                        products.add(multiplySimpleTerms(lt, rt))
                    }
                }

                buildSum(products)
            }

            leftIsSum -> {
                val leftTerms = flattenSum(left)
                val products = leftTerms.map { term ->
                    multiplySimpleTerms(term, right)
                }
                buildSum(products)
            }

            rightIsSum -> {
                val rightTerms = flattenSum(right)
                val products = rightTerms.map { term ->
                    multiplySimpleTerms(left, term)
                }
                buildSum(products)
            }

            else -> {
                multiplySimpleTerms(left, right)
            }
        }
    }

    /**
     * 乘两个简单项
     */
    private fun multiplySimpleTerms(left: MathNode, right: MathNode): MathNode {
        if (left is MathNode.Number && right is MathNode.Number) {
            return MathNode.Number(left.value * right.value)
        }

        if (left is MathNode.Number && abs(left.value) < EPSILON) {
            return MathNode.Number(0.0)
        }
        if (right is MathNode.Number && abs(right.value) < EPSILON) {
            return MathNode.Number(0.0)
        }

        if (left is MathNode.Number && abs(left.value - 1.0) < EPSILON) {
            return right
        }
        if (right is MathNode.Number && abs(right.value - 1.0) < EPSILON) {
            return left
        }

        try {
            val leftTerm = MathTerm.fromNode(left)
            val rightTerm = MathTerm.fromNode(right)

            val newCoefficient = leftTerm.coefficient * rightTerm.coefficient

            val newVariables = mutableMapOf<String, Double>()
            for ((varName, exponent) in leftTerm.variables) {
                newVariables[varName] = exponent
            }
            for ((varName, exponent) in rightTerm.variables) {
                newVariables[varName] = (newVariables[varName] ?: 0.0) + exponent
            }

            val newFunctions = mutableMapOf<String, Double>()
            for ((funcKey, exponent) in leftTerm.functions) {
                newFunctions[funcKey] = exponent
            }
            for ((funcKey, exponent) in rightTerm.functions) {
                newFunctions[funcKey] = (newFunctions[funcKey] ?: 0.0) + exponent
            }

            val newNested = leftTerm.nestedExpressions + rightTerm.nestedExpressions

            val resultTerm = MathTerm(newCoefficient, newVariables, newFunctions, newNested)

            return resultTerm.toNode()

        } catch (e: Exception) {
            return MathNode.BinaryOp(Operator.MULTIPLY, left, right)
        }
    }

    /**
     * 展开幂运算
     */
    private fun expandPower(base: MathNode, exponent: MathNode): MathNode {
        if (exponent !is MathNode.Number) {
            return MathNode.BinaryOp(Operator.POWER, base, exponent)
        }

        val n = exponent.value

        if (base is MathNode.Variable) {
            return MathNode.BinaryOp(Operator.POWER, base, exponent)
        }

        if (base is MathNode.Number) {
            return MathNode.Number(Math.pow(base.value, n))
        }

        // 只展开小的整数幂
        if (n != n.toInt().toDouble() || n < 0 || n > 10) {
            return MathNode.BinaryOp(Operator.POWER, base, exponent)
        }

        val intN = n.toInt()

        return when {
            intN == 0 -> MathNode.Number(1.0)
            intN == 1 -> base
            base is MathNode.BinaryOp && (base.operator == Operator.ADD || base.operator == Operator.SUBTRACT) -> {
                var result = base
                repeat(intN - 1) {
                    result = expandMultiplication(result, base)
                }
                result
            }
            else -> {
                MathNode.BinaryOp(Operator.POWER, base, exponent)
            }
        }
    }

    /**
     * 拍平加法树
     */
    private fun flattenSum(node: MathNode): List<MathNode> {
        return when (node) {
            is MathNode.BinaryOp -> {
                when (node.operator) {
                    Operator.ADD -> flattenSum(node.left) + flattenSum(node.right)

                    Operator.SUBTRACT -> {
                        flattenSum(node.left) + flattenSum(node.right).map { term ->
                            negateTerm(term)
                        }
                    }

                    else -> listOf(node)
                }
            }
            else -> listOf(node)
        }
    }

    /**
     * 取负
     */
    private fun negateTerm(term: MathNode): MathNode {
        return when (term) {
            is MathNode.Number -> MathNode.Number(-term.value)
            is MathNode.BinaryOp -> {
                if (term.operator == Operator.MULTIPLY && term.left is MathNode.Number) {
                    MathNode.BinaryOp(
                        Operator.MULTIPLY,
                        MathNode.Number(-term.left.value),
                        term.right
                    )
                } else {
                    MathNode.BinaryOp(Operator.MULTIPLY, MathNode.Number(-1.0), term)
                }
            }
            else -> MathNode.BinaryOp(Operator.MULTIPLY, MathNode.Number(-1.0), term)
        }
    }

    /**
     * 构建加法表达式
     */
    private fun buildSum(terms: List<MathNode>): MathNode {
        if (terms.isEmpty()) return MathNode.Number(0.0)
        if (terms.size == 1) return terms[0]

        var result = terms[0]
        for (i in 1 until terms.size) {
            result = MathNode.BinaryOp(Operator.ADD, result, terms[i])
        }
        return result
    }

    /**
     * 提取项
     */
    private fun extractTerms(node: MathNode): List<MathTerm> {
        val termNodes = flattenSum(node)
        return termNodes.map { MathTerm.fromNode(it) }
    }

    /**
     * 合并同类项
     */
    private fun mergeTerms(terms: List<MathTerm>): List<MathTerm> {
        if (terms.isEmpty()) return emptyList()

        Log.e(TAG, "!!!!! mergeTerms 被调用，有 ${terms.size} 个项 !!!!!")
        
        terms.forEachIndexed { index, term ->
            Log.e(TAG, "项: coeff=${term.coefficient}, key='${term.getBaseKey()}'")
        }

        val groups = mutableMapOf<String, MutableList<MathTerm>>()

        for (term in terms) {
            val key = term.getBaseKey()
            if (!groups.containsKey(key)) {
                groups[key] = mutableListOf()
            }
            groups[key]!!.add(term)
        }

        Log.e(TAG, "分组后有 ${groups.size} 组")
        for ((key, group) in groups) {
            Log.e(TAG, "  组 '$key': ${group.size} 个项")
        }

        val merged = mutableListOf<MathTerm>()

        for ((key, group) in groups) {
            var combined = group[0]
            for (i in 1 until group.size) {
                val next = group[i]
                val mergeResult = combined.mergeWith(next)
                if (mergeResult != null) {
                    combined = mergeResult
                    Log.e(TAG, "✅ 成功合并: $key")
                } else {
                    Log.d(TAG, "无法合并（不是同类项）: ${combined.getBaseKey()} 和 ${next.getBaseKey()}")
                }
            }

            if (!combined.isZero()) {
                merged.add(combined)
            }
        }

        Log.e(TAG, "合并后剩 ${merged.size} 个项")

        return merged
    }

    /**
     * 排序项
     */
    private fun sortTerms(terms: List<MathTerm>): List<MathTerm> {
        return terms.sortedWith(compareBy(
            { it.isConstant() },
            { -getTotalDegree(it) },
            { it.getBaseKey() }
        ))
    }

    /**
     * 获取总次数
     */
    private fun getTotalDegree(term: MathTerm): Double {
        return term.variables.values.sum()
    }

    /**
     * 构建表达式
     */
    private fun buildExpression(terms: List<MathTerm>): MathNode {
        if (terms.isEmpty()) return MathNode.Number(0.0)

        val nodes = terms.map { it.toNode() }

        if (nodes.size == 1) return nodes[0]

        var result = nodes[0]
        for (i in 1 until nodes.size) {
            result = MathNode.BinaryOp(Operator.ADD, result, nodes[i])
        }

        return result
    }
}