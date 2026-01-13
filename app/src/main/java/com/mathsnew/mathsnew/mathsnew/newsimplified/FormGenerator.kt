// app/src/main/java/com/mathsnew/mathsnew/newsimplified/FormGenerator.kt
// 多形式生成器 - Kotlin
// 功能：生成因式分解、展开式、标准型等多种形式
// ✅ 修复：增强公因子提取，支持函数幂次预合并
// ✅ 修复：添加分式分子公因子提取
// ✅ 新增：exp 约分功能（重写版，使用因子展平策略）
// ✅ 修复：先因式分解再约分

package com.mathsnew.mathsnew.newsimplified

import com.mathsnew.mathsnew.*
import android.util.Log
import kotlin.math.abs

/**
 * 多形式生成器
 *
 * 功能：
 * 1. 提取公因子（支持系数、变量、函数）
 * 2. 多项式因式分解
 * 3. 分式约分
 * 4. 生成标准型、展开式等多种形式
 */
class FormGenerator {

    companion object {
        private const val TAG = "FormGenerator"
        private const val EPSILON = 1e-10
    }

    /**
     * 生成多种形式的表达式（用于 ExpressionSimplifierV2）
     *
     * ✅ 修改：添加分式处理，对分子提取公因子
     * ✅ 新增：exp 约分（递归）
     * ✅ 修复：先因式分解再约分
     *
     * @param node 输入节点
     * @return SimplificationFormsV2 对象
     */
    fun generateAllForms(node: MathNode): SimplificationFormsV2 {
        Log.d(TAG, "========== 开始生成形式 ==========")
        Log.d(TAG, "输入: $node")

        val forms = mutableListOf<SimplifiedForm>()

        // 标准型
        forms.add(SimplifiedForm(node, SimplificationType.EXPANDED, "标准型"))

        // ✅ 新增：分式处理
        if (node is MathNode.BinaryOp && node.operator == Operator.DIVIDE) {
            Log.d(TAG, "检测到分式")

            // ✅ 步骤1：先尝试提取分子公因子
            val numeratorFactored = try {
                extractCommonFactor(node.left)
            } catch (e: Exception) {
                Log.e(TAG, "提取分子公因子失败: ${e.message}")
                node.left
            }

            val factoredFraction = if (numeratorFactored.toString() != node.left.toString()) {
                Log.d(TAG, "✅ 分子因式分解成功: $numeratorFactored")
                MathNode.BinaryOp(Operator.DIVIDE, numeratorFactored, node.right)
            } else {
                Log.d(TAG, "ℹ️ 分子无法因式分解，使用原始分式")
                node
            }

            // ✅ 步骤2：尝试 exp 约分（递归，直到无法继续约分）
            var expSimplified: MathNode = simplifyExpInFraction(factoredFraction)
            var lastSimplified: MathNode = factoredFraction
            var iterations = 0
            val maxIterations = 5  // 防止无限循环

            // 递归约分，直到不再变化
            while (expSimplified.toString() != lastSimplified.toString() && iterations < maxIterations) {
                lastSimplified = expSimplified
                expSimplified = simplifyExpInFraction(expSimplified)
                iterations++
                Log.d(TAG, "递归约分第 $iterations 次: $expSimplified")
            }

            if (expSimplified.toString() != node.toString()) {
                Log.d(TAG, "✅ exp约分成功（共 $iterations 次）: $expSimplified")

                // 如果有因式分解，添加因式分解形式
                if (factoredFraction.toString() != node.toString()) {
                    forms.add(SimplifiedForm(factoredFraction, SimplificationType.FACTORED, "分子因式分解"))
                }

                // 添加约分后的形式
                forms.add(SimplifiedForm(expSimplified, SimplificationType.FACTORED, "exp约分"))
            } else if (factoredFraction.toString() != node.toString()) {
                // 只有因式分解，没有约分
                Log.d(TAG, "✅ 仅分子因式分解，无exp约分")
                forms.add(SimplifiedForm(factoredFraction, SimplificationType.FACTORED, "分子因式分解"))
            } else {
                Log.d(TAG, "ℹ️ 无法进行因式分解或约分")
            }
        } else {
            // 非分式，尝试整体因式分解
            try {
                val factored = extractCommonFactor(node)
                if (factored.toString() != node.toString()) {
                    Log.d(TAG, "✅ 因式分解成功: $factored")
                    forms.add(SimplifiedForm(factored, SimplificationType.FACTORED, "因式分解"))
                }
            } catch (e: Exception) {
                Log.e(TAG, "因式分解失败: ${e.message}")
            }
        }

        Log.d(TAG, "生成了 ${forms.size} 种形式")
        return SimplificationFormsV2(forms)
    }

    /**
     * 生成多种形式的表达式（旧版接口，保持兼容）
     *
     * @param node 输入节点
     * @return 多种形式的列表
     */
    fun generateForms(node: MathNode): List<Pair<String, MathNode>> {
        Log.d(TAG, "========== 生成多种形式 ==========")
        Log.d(TAG, "输入: $node")

        val forms = mutableListOf<Pair<String, MathNode>>()

        // 标准型（规范化后的形式）
        forms.add(Pair("标准型", node))

        // 尝试因式分解
        try {
            val factored = extractCommonFactor(node)
            if (factored.toString() != node.toString()) {
                Log.d(TAG, "✅ 因式分解成功: $factored")
                forms.add(Pair("因式分解", factored))
            } else {
                Log.d(TAG, "ℹ️ 无法进一步因式分解")
            }
        } catch (e: Exception) {
            Log.e(TAG, "因式分解失败: ${e.message}")
        }

        // 尝试约分（如果是分式）
        if (node is MathNode.BinaryOp && node.operator == Operator.DIVIDE) {
            try {
                val simplified = simplifyFraction(node)
                if (simplified.toString() != node.toString()) {
                    Log.d(TAG, "✅ 约分成功: $simplified")
                    forms.add(Pair("约分", simplified))
                } else {
                    Log.d(TAG, "ℹ️ 无法进一步约分")
                }
            } catch (e: Exception) {
                Log.e(TAG, "约分失败: ${e.message}")
            }
        }

        Log.d(TAG, "生成了 ${forms.size} 种形式")
        return forms
    }

    /**
     * 提取公因子
     *
     * 策略：
     * 1. 提取所有项为 MathTerm
     * 2. 计算最大公约数（GCD）
     * 3. 验证每个项都有足够的指数
     * 4. 构建因式分解表达式
     *
     * ✅ 增强：在提取项之前，先合并同底函数的幂次
     */
    private fun extractCommonFactor(node: MathNode): MathNode {
        Log.d(TAG, "========== extractCommonFactor START ==========")

        try {
            Log.d(TAG, "节点类型: ${node.javaClass.simpleName}")
        } catch (e: Exception) {
            Log.e(TAG, "获取节点类型失败: ${e.message}")
        }

        if (node is MathNode.BinaryOp) {
            Log.d(TAG, "确认是 BinaryOp")

            try {
                val op = node.operator
                Log.d(TAG, "运算符获取成功")

                val isAdd = (op == Operator.ADD)
                val isSubtract = (op == Operator.SUBTRACT)
                Log.d(TAG, "是加法: $isAdd, 是减法: $isSubtract")

                if (!isAdd && !isSubtract) {
                    Log.d(TAG, "不是加减法，直接返回")
                    return node
                }
            } catch (e: Exception) {
                Log.e(TAG, "处理运算符时出错: ${e.message}")
                return node
            }
        } else {
            Log.d(TAG, "不是 BinaryOp，直接返回")
            return node
        }

        Log.d(TAG, "是加减法节点，继续处理")

        // ✅ 修改：先规范化幂次，再提取项
        val normalizedTermNodes = extractTermsFromSum(node)
            .map { normalizePowersInTerm(it) }

        Log.d(TAG, "规范化后的项节点数: ${normalizedTermNodes.size}")

        val normalizedTerms = normalizedTermNodes.map { MathTerm.fromNode(it) }

        Log.d(TAG, "提取到 ${normalizedTerms.size} 个项（已规范化幂次）")

        for ((index, term) in normalizedTerms.withIndex()) {
            Log.d(TAG, "项 $index: coeff=${term.coefficient}")
            Log.d(TAG, "项 $index: vars=${term.variables.keys}")
            Log.d(TAG, "项 $index: funcs=${term.functions.keys.map { it.toCanonicalString() }}")
        }

        if (normalizedTerms.size < 2) {
            Log.d(TAG, "项数少于2，跳过")
            return node
        }

        Log.d(TAG, "准备调用 findGCD")

        val gcd = findGCD(normalizedTerms)

        Log.d(TAG, "GCD结果: coeff=${gcd.coefficient}, vars=${gcd.variables}, funcs=${gcd.functions.keys.map { it.toCanonicalString() }}")

        if (abs(gcd.coefficient - 1.0) < EPSILON &&
            gcd.variables.isEmpty() &&
            gcd.functions.isEmpty()) {
            Log.d(TAG, "GCD为1，无法提取公因子")
            return node
        }

        for ((varName, gcdExp) in gcd.variables) {
            for (term in normalizedTerms) {
                val termExp = term.variables[varName] ?: 0.0
                if (termExp < gcdExp - EPSILON) {
                    Log.d(TAG, "跳过提取变量 $varName，因为不是所有项都有足够的指数")
                    val coeffOnlyGCD = MathTerm(gcd.coefficient, emptyMap(), emptyMap(), emptyList())
                    return buildFactoredExpression(normalizedTerms, coeffOnlyGCD)
                }
            }
        }

        for ((funcKey, gcdExp) in gcd.functions) {
            Log.d(TAG, "检查函数公因子: ${funcKey.toCanonicalString()}, 需要指数: $gcdExp")
            for (term in normalizedTerms) {
                val termExp = term.functions[funcKey] ?: 0.0
                Log.d(TAG, "  项的指数: $termExp")
                if (termExp < gcdExp - EPSILON) {
                    Log.d(TAG, "跳过提取函数 ${funcKey.toCanonicalString()}，因为不是所有项都有足够的指数")
                    val coeffOnlyGCD = MathTerm(gcd.coefficient, emptyMap(), emptyMap(), emptyList())
                    return buildFactoredExpression(normalizedTerms, coeffOnlyGCD)
                }
            }
        }

        Log.d(TAG, "✅ 所有检查通过，开始提取公因子")
        val result = buildFactoredExpression(normalizedTerms, gcd)
        Log.d(TAG, "提取后的结果: $result")
        return result
    }

    /**
     * 计算最大公约数（GCD）
     *
     * 包括：
     * 1. 系数的最大公约数
     * 2. 变量的最小指数
     * 3. 函数的最小指数
     */
    private fun findGCD(terms: List<MathTerm>): MathTerm {
        Log.d(TAG, "========== findGCD START ==========")
        Log.d(TAG, "输入 ${terms.size} 个项")

        if (terms.isEmpty()) {
            return MathTerm(1.0, emptyMap(), emptyMap(), emptyList())
        }

        // 计算系数的GCD
        var coeffGCD = abs(terms[0].coefficient)
        for (i in 1 until terms.size) {
            coeffGCD = gcd(coeffGCD, abs(terms[i].coefficient))
        }

        Log.d(TAG, "系数GCD: $coeffGCD")

        // 计算变量的最小指数（修复：必须所有项都有）
        val allVars = terms.flatMap { it.variables.keys }.toSet()
        val varGCD = mutableMapOf<String, Double>()

        for (varName in allVars) {
            // ✅ 关键修复：检查是否所有项都包含这个变量
            val allHaveVar = terms.all { it.variables.containsKey(varName) }
            Log.d(TAG, "变量 $varName: 所有项都有=$allHaveVar")

            if (allHaveVar) {
                val minExp = terms.mapNotNull { it.variables[varName] }.minOrNull()
                if (minExp != null && minExp > EPSILON) {
                    varGCD[varName] = minExp
                    Log.d(TAG, "变量 $varName 可以提取，指数=$minExp")
                }
            } else {
                Log.d(TAG, "变量 $varName 不能提取，因为不是所有项都有")
            }
        }

        // ✅ 计算函数的最小指数（修复：必须所有项都有）
        val allFuncs = terms.flatMap { it.functions.keys }.toSet()
        val funcGCD = mutableMapOf<FunctionKey, Double>()

        for (funcKey in allFuncs) {
            // ✅ 关键修复：检查是否所有项都包含这个函数
            val allHaveFunc = terms.all { it.functions.containsKey(funcKey) }
            Log.d(TAG, "函数 ${funcKey.toCanonicalString()}: 所有项都有=$allHaveFunc")

            if (allHaveFunc) {
                val minExp = terms.mapNotNull { it.functions[funcKey] }.minOrNull()
                if (minExp != null && minExp > EPSILON) {
                    funcGCD[funcKey] = minExp
                    Log.e(TAG, "!!!!! 函数 ${funcKey.toCanonicalString()} 可以提取，指数=$minExp !!!!!")
                }
            } else {
                Log.d(TAG, "函数 ${funcKey.toCanonicalString()} 不能提取，因为不是所有项都有")
            }
        }

        val result = MathTerm(coeffGCD, varGCD, funcGCD, emptyList())
        Log.d(TAG, "========== findGCD END ==========")
        return result
    }

    /**
     * 计算两个数的最大公约数
     */
    private fun gcd(a: Double, b: Double): Double {
        if (abs(b) < EPSILON) return abs(a)
        return gcd(b, a % b)
    }

    /**
     * 构建因式分解表达式
     *
     * GCD × (剩余项之和)
     */
    private fun buildFactoredExpression(terms: List<MathTerm>, gcd: MathTerm): MathNode {
        Log.d(TAG, "构建因式分解表达式")

        // 每个项除以GCD
        val remainingTerms = terms.map { divideTerm(it, gcd) }

        // 构建剩余项的和
        val sumNode = buildSum(remainingTerms.map { it.toNode() })

        // 如果GCD是1，直接返回和
        if (abs(gcd.coefficient - 1.0) < EPSILON &&
            gcd.variables.isEmpty() &&
            gcd.functions.isEmpty()) {
            return sumNode
        }

        // 否则，返回 GCD × (剩余项之和)
        val gcdNode = gcd.toNode()
        return MathNode.BinaryOp(Operator.MULTIPLY, gcdNode, sumNode)
    }

    /**
     * 除法：term ÷ gcd
     *
     * ✅ 支持函数因子的除法
     */
    private fun divideTerm(term: MathTerm, gcd: MathTerm): MathTerm {
        val newCoeff = term.coefficient / gcd.coefficient

        val newVars = mutableMapOf<String, Double>()
        for ((varName, exp) in term.variables) {
            val gcdExp = gcd.variables[varName] ?: 0.0
            val remainingExp = exp - gcdExp
            if (remainingExp > EPSILON) {
                newVars[varName] = remainingExp
            }
        }

        // ✅ 处理函数
        val newFuncs = mutableMapOf<FunctionKey, Double>()
        for ((funcKey, exp) in term.functions) {
            val gcdExp = gcd.functions[funcKey] ?: 0.0
            val remainingExp = exp - gcdExp
            if (remainingExp > EPSILON) {
                newFuncs[funcKey] = remainingExp
            }
        }

        return MathTerm(newCoeff, newVars, newFuncs, term.nestedExpressions)
    }

    /**
     * 构建加法表达式
     */
    private fun buildSum(terms: List<MathNode>): MathNode {
        if (terms.isEmpty()) return MathNode.Number(0)
        if (terms.size == 1) return terms[0]

        var result = terms[0]
        for (i in 1 until terms.size) {
            result = MathNode.BinaryOp(Operator.ADD, result, terms[i])
        }
        return result
    }

    /**
     * 从加减法表达式中提取所有项
     */
    private fun extractTermsFromSum(node: MathNode): List<MathNode> {
        Log.d(TAG, "提取项: $node")

        return when (node) {
            is MathNode.BinaryOp -> {
                when (node.operator) {
                    Operator.ADD -> {
                        extractTermsFromSum(node.left) + extractTermsFromSum(node.right)
                    }
                    Operator.SUBTRACT -> {
                        extractTermsFromSum(node.left) + extractTermsFromSum(node.right).map { negateTerm(it) }
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
                    MathNode.BinaryOp(Operator.MULTIPLY, MathNode.Number(-1), term)
                }
            }
            else -> MathNode.BinaryOp(Operator.MULTIPLY, MathNode.Number(-1), term)
        }
    }

    /**
     * 约分
     */
    private fun simplifyFraction(node: MathNode): MathNode {
        if (node !is MathNode.BinaryOp || node.operator != Operator.DIVIDE) {
            return node
        }

        Log.d(TAG, "尝试约分: $node")

        // 分子和分母分别提取公因子
        val numerator = extractCommonFactor(node.left)
        val denominator = extractCommonFactor(node.right)

        // TODO: 实现约分逻辑
        // 1. 检查分子和分母是否有公因子
        // 2. 如果有，约去公因子
        // 3. 返回约分后的表达式

        return MathNode.BinaryOp(Operator.DIVIDE, numerator, denominator)
    }

    /**
     * ✅ 新增：合并项中同底函数的幂次
     *
     * 例如：
     * exp(x-9)² · exp(x-9) · A → exp(x-9)³ · A
     * sin(x) · sin(x)² · B → sin(x)³ · B
     *
     * ⚠️ 注意：不处理除法节点，因为除法中的函数指数应该已经是负数
     *
     * @param node 待规范化的节点
     * @return 规范化后的节点
     */
    private fun normalizePowersInTerm(node: MathNode): MathNode {
        // ⚠️ 不处理除法节点，避免重复处理
        if (node is MathNode.BinaryOp && node.operator == Operator.DIVIDE) {
            return node
        }

        val factors = extractFactors(node)

        if (factors.size <= 1) {
            return node
        }

        // 按函数底数分组
        val functionGroups = mutableMapOf<FunctionKey, MutableList<Pair<MathNode, Double>>>()
        val otherFactors = mutableListOf<MathNode>()

        for (factor in factors) {
            when {
                // 情况1：f(x)^n
                factor is MathNode.BinaryOp &&
                factor.operator == Operator.POWER &&
                factor.left is MathNode.Function &&
                factor.right is MathNode.Number -> {
                    val funcKey = FunctionKey.from(factor.left as MathNode.Function)
                    val exponent = (factor.right as MathNode.Number).value

                    Log.d(TAG, "  发现幂函数: ${funcKey.toCanonicalString()}^$exponent")

                    if (!functionGroups.containsKey(funcKey)) {
                        functionGroups[funcKey] = mutableListOf()
                    }
                    functionGroups[funcKey]!!.add(Pair(factor.left, exponent))
                }

                // 情况2：f(x)
                factor is MathNode.Function -> {
                    val funcKey = FunctionKey.from(factor)

                    Log.d(TAG, "  发现函数: ${funcKey.toCanonicalString()}^1")

                    if (!functionGroups.containsKey(funcKey)) {
                        functionGroups[funcKey] = mutableListOf()
                    }
                    functionGroups[funcKey]!!.add(Pair(factor, 1.0))
                }

                // 其他因子
                else -> {
                    otherFactors.add(factor)
                }
            }
        }

        // 合并同底函数的幂次
        val mergedFunctions = mutableListOf<MathNode>()
        for ((funcKey, instances) in functionGroups) {
            val totalExponent = instances.sumOf { it.second }

            if (instances.size > 1) {
                Log.e(TAG, "!!!!! 合并幂次: ${funcKey.toCanonicalString()} 共 ${instances.size} 个因子，总指数=$totalExponent !!!!!")

                // 调试：列出每个实例的指数
                instances.forEachIndexed { idx, (_, exp) ->
                    Log.e(TAG, "      实例$idx: 指数=$exp")
                }
            }

            // ⚠️ 检查总指数，如果是负数说明有问题
            if (totalExponent < -EPSILON) {
                Log.e(TAG, "❌ 警告：总指数为负 ($totalExponent)，这不应该发生在乘法项中！")
            }

            val baseFunc = instances[0].first
            val mergedNode = if (abs(totalExponent - 1.0) < EPSILON) {
                baseFunc
            } else if (abs(totalExponent) < EPSILON) {
                // 指数为0，返回1
                MathNode.Number(1)
            } else {
                MathNode.BinaryOp(
                    Operator.POWER,
                    baseFunc,
                    MathNode.Number(totalExponent)
                )
            }

            mergedFunctions.add(mergedNode)
        }

        val allFactors = otherFactors + mergedFunctions

        if (allFactors.size == factors.size) {
            return node
        }

        Log.d(TAG, "幂次规范化: ${factors.size}个因子 → ${allFactors.size}个因子")
        return buildProduct(allFactors)
    }

    /**
     * 提取乘法中的所有因子
     */
    private fun extractFactors(node: MathNode): List<MathNode> {
        return when (node) {
            is MathNode.BinaryOp -> {
                if (node.operator == Operator.MULTIPLY) {
                    extractFactors(node.left) + extractFactors(node.right)
                } else {
                    listOf(node)
                }
            }
            else -> listOf(node)
        }
    }

    /**
     * 构建乘法表达式
     */
    private fun buildProduct(factors: List<MathNode>): MathNode {
        if (factors.isEmpty()) return MathNode.Number(1)
        if (factors.size == 1) return factors[0]

        var result = factors[0]
        for (i in 1 until factors.size) {
            result = MathNode.BinaryOp(Operator.MULTIPLY, result, factors[i])
        }
        return result
    }

    /**
     * ✅ 重写：简化分式中的 exp（使用因子展平策略）
     *
     * 策略：
     * 1. 将分子和分母展平为因子列表
     * 2. 识别所有 exp 因子及其指数
     * 3. 约分公共的 exp
     * 4. 重新构建分式
     *
     * 示例：
     * exp(x)·(cos(x)-sin(x)) / exp(x)²
     * → 因子：[exp(x), (cos(x)-sin(x))] / [exp(x)²]
     * → 约分：exp(x)¹ / exp(x)² = 1 / exp(x)
     * → 结果：(cos(x)-sin(x)) / exp(x)
     */
    private fun simplifyExpInFraction(node: MathNode): MathNode {
        if (node !is MathNode.BinaryOp || node.operator != Operator.DIVIDE) {
            return node
        }

        Log.d(TAG, "========== simplifyExpInFraction (重写版) ==========")
        Log.d(TAG, "输入分式: $node")

        // 1. 展平分子和分母为因子列表
        val numeratorFactors = flattenToFactors(node.left)
        val denominatorFactors = flattenToFactors(node.right)

        Log.d(TAG, "分子因子数: ${numeratorFactors.size}")
        Log.d(TAG, "分母因子数: ${denominatorFactors.size}")

        // ✅ 添加调试日志
        Log.d(TAG, "分子因子详情:")
        numeratorFactors.forEachIndexed { i, f ->
            Log.d(TAG, "  因子$i: $f (类型=${f.javaClass.simpleName})")
        }

        Log.d(TAG, "分母因子详情:")
        denominatorFactors.forEachIndexed { i, f ->
            Log.d(TAG, "  因子$i: $f (类型=${f.javaClass.simpleName})")
        }

        // 2. 统计 exp 因子
        val numeratorExpMap = mutableMapOf<FunctionKey, Double>()
        val numeratorNonExpFactors = mutableListOf<MathNode>()

        for (factor in numeratorFactors) {
            val (expKey, exp) = getExpInfo(factor)
            if (expKey != null) {
                numeratorExpMap[expKey] = (numeratorExpMap[expKey] ?: 0.0) + exp
                Log.d(TAG, "分子exp: ${expKey.toCanonicalString()}, 指数=$exp")
            } else {
                numeratorNonExpFactors.add(factor)
            }
        }

        val denominatorExpMap = mutableMapOf<FunctionKey, Double>()
        val denominatorNonExpFactors = mutableListOf<MathNode>()

        for (factor in denominatorFactors) {
            val (expKey, exp) = getExpInfo(factor)
            if (expKey != null) {
                denominatorExpMap[expKey] = (denominatorExpMap[expKey] ?: 0.0) + exp
                Log.d(TAG, "分母exp: ${expKey.toCanonicalString()}, 指数=$exp")
            } else {
                denominatorNonExpFactors.add(factor)
            }
        }

        // 3. 找到公共的 exp 参数
        val commonExpArgs = numeratorExpMap.keys.intersect(denominatorExpMap.keys)

        if (commonExpArgs.isEmpty()) {
            Log.d(TAG, "没有公共exp，无需约分")
            return node
        }

        Log.d(TAG, "找到 ${commonExpArgs.size} 个可约分的exp")

        // 4. 计算约分后的指数
        val finalNumeratorExpMap = numeratorExpMap.toMutableMap()
        val finalDenominatorExpMap = denominatorExpMap.toMutableMap()

        for (expKey in commonExpArgs) {
            val numExp = numeratorExpMap[expKey]!!
            val denExp = denominatorExpMap[expKey]!!

            Log.d(TAG, "约分 exp(${expKey.argument}): 分子=$numExp, 分母=$denExp")

            val diff = numExp - denExp

            if (abs(diff) < EPSILON) {
                // 完全约掉
                finalNumeratorExpMap.remove(expKey)
                finalDenominatorExpMap.remove(expKey)
                Log.d(TAG, "  → 完全约掉")
            } else if (diff > 0) {
                // 分子剩余
                finalNumeratorExpMap[expKey] = diff
                finalDenominatorExpMap.remove(expKey)
                Log.d(TAG, "  → 分子剩余: $diff")
            } else {
                // 分母剩余
                finalNumeratorExpMap.remove(expKey)
                finalDenominatorExpMap[expKey] = -diff
                Log.d(TAG, "  → 分母剩余: ${-diff}")
            }
        }

        // 5. 重新构建分子和分母
        val newNumeratorFactors = numeratorNonExpFactors + buildExpFactors(finalNumeratorExpMap)
        val newDenominatorFactors = denominatorNonExpFactors + buildExpFactors(finalDenominatorExpMap)

        val newNumerator = if (newNumeratorFactors.isEmpty()) {
            MathNode.Number(1)
        } else {
            buildProduct(newNumeratorFactors)
        }

        val newDenominator = if (newDenominatorFactors.isEmpty()) {
            MathNode.Number(1)
        } else {
            buildProduct(newDenominatorFactors)
        }

        Log.d(TAG, "约分后分子: $newNumerator")
        Log.d(TAG, "约分后分母: $newDenominator")

        return MathNode.BinaryOp(Operator.DIVIDE, newNumerator, newDenominator)
    }

    /**
     * 将表达式展平为因子列表（只处理乘法）
     */
    private fun flattenToFactors(node: MathNode): List<MathNode> {
        return when (node) {
            is MathNode.BinaryOp -> {
                if (node.operator == Operator.MULTIPLY) {
                    flattenToFactors(node.left) + flattenToFactors(node.right)
                } else {
                    listOf(node)
                }
            }
            else -> listOf(node)
        }
    }

    /**
     * 获取因子的 exp 信息
     *
     * @return Pair(FunctionKey?, 指数)
     *   - 如果是 exp 因子，返回 (key, 指数)
     *   - 否则返回 (null, 0.0)
     */
    private fun getExpInfo(factor: MathNode): Pair<FunctionKey?, Double> {
        return when (factor) {
            // exp(x)
            is MathNode.Function -> {
                if (factor.name == "exp") {
                    Pair(FunctionKey.from(factor), 1.0)
                } else {
                    Pair(null, 0.0)
                }
            }

            // exp(x)^n
            is MathNode.BinaryOp -> {
                if (factor.operator == Operator.POWER &&
                    factor.left is MathNode.Function &&
                    (factor.left as MathNode.Function).name == "exp" &&
                    factor.right is MathNode.Number) {

                    val key = FunctionKey.from(factor.left as MathNode.Function)
                    val exp = (factor.right as MathNode.Number).value
                    Pair(key, exp)
                } else {
                    Pair(null, 0.0)
                }
            }

            else -> Pair(null, 0.0)
        }
    }

    /**
     * 从 exp Map 构建因子列表
     */
    private fun buildExpFactors(expMap: Map<FunctionKey, Double>): List<MathNode> {
        return expMap.map { (key, exp) ->
            val expFunc = key.toFunctionNode()
            if (abs(exp - 1.0) < EPSILON) {
                expFunc
            } else {
                MathNode.BinaryOp(Operator.POWER, expFunc, MathNode.Number(exp))
            }
        }
    }
}