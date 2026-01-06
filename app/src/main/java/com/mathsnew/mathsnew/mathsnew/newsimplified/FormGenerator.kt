// app/src/main/java/com/mathsnew/mathsnew/newsimplified/FormGenerator.kt
// 形式生成器 - 从规范形式生成多种等价表达式

package com.mathsnew.mathsnew.newsimplified

import com.mathsnew.mathsnew.*
import android.util.Log
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.round

class FormGenerator {

    companion object {
        private const val TAG = "FormGenerator"
        private const val EPSILON = 1e-10
    }

    private val canonicalizer = ExpressionCanonicalizer()
    private val fractionSimplifier = FractionSimplifier()

    fun generateAllForms(node: MathNode): SimplificationFormsV2 {
        Log.d(TAG, "========== 开始生成形式 ==========")
        Log.d(TAG, "输入: $node")

        val forms = mutableListOf<SimplifiedForm>()

        val canonical = canonicalizer.canonicalize(node)
        forms.add(SimplifiedForm(canonical, SimplificationType.EXPANDED, "完全展开"))
        Log.d(TAG, "形式1 (展开): $canonical")

        val trigSimplified = TrigSimplifier.simplify(canonical)
        if (!isEquivalentString(trigSimplified, canonical)) {
            forms.add(SimplifiedForm(trigSimplified, SimplificationType.STRUCTURAL, "三角化简"))
            Log.d(TAG, "形式2 (三角化简): $trigSimplified")
        }

        if (node is MathNode.BinaryOp && node.operator == Operator.DIVIDE) {
            generateFractionForms(canonical, forms)
        } else {
            val factored = extractCommonFactor(canonical)
            if (!isEquivalentString(factored, canonical)) {
                forms.add(SimplifiedForm(factored, SimplificationType.FACTORED, "提取公因子"))
                Log.d(TAG, "形式 (因式): $factored")
            }

            val trigFactored = TrigSimplifier.simplify(factored)
            if (!isEquivalentString(trigFactored, factored) &&
                !isEquivalentString(trigFactored, canonical) &&
                !isEquivalentString(trigFactored, trigSimplified)) {
                forms.add(SimplifiedForm(trigFactored, SimplificationType.STRUCTURAL, "因式+三角化简"))
                Log.d(TAG, "形式 (因式+三角): $trigFactored")
            }
        }

        Log.d(TAG, "共生成 ${forms.size} 种形式")
        Log.d(TAG, "========== 形式生成完成 ==========")

        return SimplificationFormsV2(forms)
    }

    private fun generateFractionForms(canonical: MathNode, forms: MutableList<SimplifiedForm>) {
        if (canonical !is MathNode.BinaryOp || canonical.operator != Operator.DIVIDE) {
            return
        }

        val numerator = canonical.left
        val denominator = canonical.right

        // ✅ 新增：提取分子的公因子
        val numeratorFactored = extractCommonFactor(numerator)
        if (!isEquivalentString(numeratorFactored, numerator)) {
            // 形式1：因式分解（未约分）
            val formWithFactoredNumerator = MathNode.BinaryOp(
                Operator.DIVIDE,
                numeratorFactored,
                denominator
            )

            forms.add(SimplifiedForm(
                formWithFactoredNumerator,
                SimplificationType.FACTORED,
                "提取分子公因子"
            ))
            Log.d(TAG, "形式 (分子因式分解): $formWithFactoredNumerator")

            // 形式2：因式分解后尝试约分
            val simplified = trySimplifyAfterFactoring(formWithFactoredNumerator)

            if (!isEquivalentString(simplified, formWithFactoredNumerator) &&
                !isEquivalentString(simplified, canonical)) {

                val trigSimplified = TrigSimplifier.simplify(simplified)
                val finalForm = if (!isEquivalentString(trigSimplified, simplified)) {
                    trigSimplified
                } else {
                    simplified
                }

                forms.add(SimplifiedForm(
                    finalForm,
                    SimplificationType.FACTORED,
                    "因式分解并约分"
                ))
                Log.d(TAG, "形式 (分子因式+约分): $finalForm")
            }
        }

        // 原有的分子因式分解逻辑
        val denominatorFactored = tryFactorPolynomial(denominator)
        if (!isEquivalentString(denominatorFactored, denominator)) {
            val formC = MathNode.BinaryOp(Operator.DIVIDE, numerator, denominatorFactored)
            val trigFormC = TrigSimplifier.simplify(formC)

            forms.add(SimplifiedForm(
                if (!isEquivalentString(trigFormC, formC)) trigFormC else formC,
                SimplificationType.FACTORED,
                "分母因式分解"
            ))
            Log.d(TAG, "形式C (分母因式): ${if (!isEquivalentString(trigFormC, formC)) trigFormC else formC}")
        }

        val numeratorFullFactored = extractCommonFactor(numerator)
        val denominatorFullFactored = tryFactorPolynomial(denominator)

        val simplified = trySimplifyFraction(numeratorFullFactored, denominatorFullFactored)
        if (!isEquivalentString(simplified, canonical)) {
            val trigSimplified = TrigSimplifier.simplify(simplified)

            forms.add(SimplifiedForm(
                if (!isEquivalentString(trigSimplified, simplified)) trigSimplified else simplified,
                SimplificationType.FACTORED,
                "完全约分"
            ))
            Log.d(TAG, "形式D (完全约分): ${if (!isEquivalentString(trigSimplified, simplified)) trigSimplified else simplified}")
        }
    }

    /**
     * 尝试在提取公因子后进行约分
     * 例如：(exp(x)·(cos(x)-sin(x))) / exp(x)² → (cos(x)-sin(x)) / exp(x)
     */
    private fun trySimplifyAfterFactoring(node: MathNode): MathNode {
        if (node !is MathNode.BinaryOp || node.operator != Operator.DIVIDE) {
            return node
        }

        val numerator = node.left
        val denominator = node.right

        // 如果分子是乘法，尝试提取可以约分的因子
        if (numerator is MathNode.BinaryOp && numerator.operator == Operator.MULTIPLY) {
            val numFactors = extractFactors(numerator)
            val denFactors = extractFactors(denominator)

            val remainingNum = numFactors.toMutableList()
            val remainingDen = denFactors.toMutableList()

            // 尝试约分
            for (numFactor in numFactors) {
                for (i in remainingDen.indices) {
                    if (isEquivalentString(numFactor, remainingDen[i])) {
                        remainingNum.remove(numFactor)
                        remainingDen.removeAt(i)
                        Log.d(TAG, "约掉公因子: $numFactor")
                        break
                    }
                }
            }

            // 尝试幂约分
            val numFactorsCopy = remainingNum.toList()
            for (numFactor in numFactorsCopy) {
                for (i in remainingDen.indices) {
                    val matchResult = tryPowerCancellation(numFactor, remainingDen[i])
                    if (matchResult != null) {
                        remainingNum.remove(numFactor)
                        if (matchResult.isOne) {
                            remainingDen.removeAt(i)
                        } else {
                            remainingDen[i] = matchResult.remaining
                        }
                        Log.d(TAG, "幂约分: $numFactor")
                        break
                    }
                }
            }

            if (remainingNum.size < numFactors.size || remainingDen.size < denFactors.size) {
                val newNum = if (remainingNum.isEmpty()) {
                    MathNode.Number(1.0)
                } else {
                    buildProduct(remainingNum)
                }

                val newDen = if (remainingDen.isEmpty()) {
                    MathNode.Number(1.0)
                } else {
                    buildProduct(remainingDen)
                }

                return if (newDen is MathNode.Number && abs(newDen.value - 1.0) < EPSILON) {
                    newNum
                } else {
                    MathNode.BinaryOp(Operator.DIVIDE, newNum, newDen)
                }
            }
        }

        return node
    }

    private fun tryFactorPolynomial(node: MathNode): MathNode {
        Log.d(TAG, "尝试因式分解: $node")

        val perfectPower = tryPerfectPower(node)
        if (perfectPower != null) {
            Log.d(TAG, "识别到完全幂: $perfectPower")
            return perfectPower
        }

        return extractCommonFactor(node)
    }

    private fun tryPerfectPower(node: MathNode): MathNode? {
        val terms = extractTermsFromSum(node)
        if (terms.isEmpty()) return null

        val mathTerms = terms.map { MathTerm.fromNode(it) }

        val sorted = mathTerms.sortedByDescending { it.variables.values.sum() }

        val allVars = sorted.flatMap { it.variables.keys }.toSet()
        if (allVars.size != 1) return null

        val varName = allVars.first()

        val maxDegree = sorted.first().variables[varName]?.toInt() ?: return null
        if (maxDegree < 2) return null

        val coeffs = DoubleArray(maxDegree + 1) { 0.0 }
        for (term in sorted) {
            val degree = term.variables[varName]?.toInt() ?: 0
            coeffs[maxDegree - degree] = term.coefficient
        }

        Log.d(TAG, "多项式系数: ${coeffs.joinToString(", ")}")

        for (n in 2..maxDegree) {
            val result = tryMatchBinomial(varName, coeffs, maxDegree, n)
            if (result != null) {
                Log.d(TAG, "匹配到 (x+a)^$n 形式")
                return result
            }
        }

        return null
    }

    private fun tryMatchBinomial(varName: String, coeffs: DoubleArray, degree: Int, n: Int): MathNode? {
        if (n != degree) return null

        if (abs(coeffs[0] - 1.0) > EPSILON) return null

        if (coeffs.size < 2) return null
        val a = coeffs[1] / n

        for (k in 0..n) {
            val expectedCoeff = binomialCoefficient(n, k) * a.pow(n - k)
            val actualCoeff = coeffs.getOrElse(k) { 0.0 }

            if (abs(expectedCoeff - actualCoeff) > EPSILON * 10) {
                Log.d(TAG, "系数不匹配: k=$k, 期望=$expectedCoeff, 实际=$actualCoeff")
                return null
            }
        }

        Log.d(TAG, "成功匹配: (x+$a)^$n")

        val innerSum = if (abs(a) < EPSILON) {
            MathNode.Variable(varName)
        } else {
            MathNode.BinaryOp(
                if (a > 0) Operator.ADD else Operator.SUBTRACT,
                MathNode.Variable(varName),
                MathNode.Number(abs(a))
            )
        }

        return MathNode.BinaryOp(
            Operator.POWER,
            innerSum,
            MathNode.Number(n.toDouble())
        )
    }

    private fun binomialCoefficient(n: Int, k: Int): Double {
        if (k > n || k < 0) return 0.0
        if (k == 0 || k == n) return 1.0

        var result = 1.0
        for (i in 0 until minOf(k, n - k)) {
            result *= (n - i).toDouble()
            result /= (i + 1).toDouble()
        }

        return result
    }

    private fun trySimplifyFraction(numerator: MathNode, denominator: MathNode): MathNode {
        Log.d(TAG, "尝试约分: $numerator / $denominator")

        val numFactors = extractFactors(numerator)
        val denFactors = extractFactors(denominator)

        Log.d(TAG, "分子因子: ${numFactors.size} 个")
        Log.d(TAG, "分母因子: ${denFactors.size} 个")

        val commonFactors = mutableListOf<MathNode>()
        val remainingNum = mutableListOf<MathNode>()
        val remainingDen = denFactors.toMutableList()

        for (numFactor in numFactors) {
            var found = false

            for (i in remainingDen.indices) {
                if (isEquivalentString(numFactor, remainingDen[i])) {
                    commonFactors.add(numFactor)
                    remainingDen.removeAt(i)
                    found = true
                    Log.d(TAG, "找到公因子: $numFactor")
                    break
                }
            }

            if (!found) {
                for (i in remainingDen.indices) {
                    val matchResult = tryPowerCancellation(numFactor, remainingDen[i])
                    if (matchResult != null) {
                        commonFactors.add(numFactor)
                        if (matchResult.isOne) {
                            remainingDen.removeAt(i)
                        } else {
                            remainingDen[i] = matchResult.remaining
                        }
                        found = true
                        Log.d(TAG, "幂约分成功: $numFactor")
                        break
                    }
                }
            }

            if (!found) {
                remainingNum.add(numFactor)
            }
        }

        if (commonFactors.isNotEmpty()) {
            Log.d(TAG, "约去了 ${commonFactors.size} 个公因子")

            val newNum = if (remainingNum.isEmpty()) {
                MathNode.Number(1.0)
            } else {
                buildProduct(remainingNum)
            }

            val newDen = if (remainingDen.isEmpty()) {
                MathNode.Number(1.0)
            } else {
                buildProduct(remainingDen)
            }

            return if (newDen is MathNode.Number && abs(newDen.value - 1.0) < EPSILON) {
                newNum
            } else {
                MathNode.BinaryOp(Operator.DIVIDE, newNum, newDen)
            }
        }

        return MathNode.BinaryOp(Operator.DIVIDE, numerator, denominator)
    }

    private data class PowerCancellationResult(
        val isOne: Boolean,
        val remaining: MathNode
    )

    private fun tryPowerCancellation(num: MathNode, den: MathNode): PowerCancellationResult? {
        if (num is MathNode.BinaryOp && num.operator == Operator.POWER &&
            den is MathNode.BinaryOp && den.operator == Operator.POWER &&
            num.right is MathNode.Number && den.right is MathNode.Number) {

            if (isEquivalentString(num.left, den.left)) {
                val numExp = num.right.value
                val denExp = den.right.value
                val remaining = denExp - numExp

                if (remaining > EPSILON) {
                    return PowerCancellationResult(
                        false,
                        MathNode.BinaryOp(Operator.POWER, den.left, MathNode.Number(remaining))
                    )
                } else if (abs(remaining) < EPSILON) {
                    return PowerCancellationResult(true, MathNode.Number(1.0))
                }
            }
        }

        if (den is MathNode.BinaryOp && den.operator == Operator.POWER &&
            den.right is MathNode.Number) {

            if (isEquivalentString(num, den.left)) {
                val denExp = den.right.value
                val remaining = denExp - 1.0

                if (remaining > 1.0 + EPSILON) {
                    return PowerCancellationResult(
                        false,
                        MathNode.BinaryOp(Operator.POWER, den.left, MathNode.Number(remaining))
                    )
                } else if (abs(remaining - 1.0) < EPSILON) {
                    return PowerCancellationResult(false, den.left)
                } else if (abs(remaining) < EPSILON) {
                    return PowerCancellationResult(true, MathNode.Number(1.0))
                }
            }
        }

        if (num is MathNode.BinaryOp && num.operator == Operator.POWER &&
            num.right is MathNode.Number) {

            if (isEquivalentString(num.left, den)) {
                return null
            }
        }

        return null
    }

    private fun extractFactors(node: MathNode): List<MathNode> {
        return when (node) {
            is MathNode.Number -> {
                if (abs(node.value - 1.0) < EPSILON) {
                    emptyList()
                } else {
                    listOf(node)
                }
            }
            is MathNode.BinaryOp -> {
                when (node.operator) {
                    Operator.MULTIPLY -> extractFactors(node.left) + extractFactors(node.right)
                    Operator.POWER -> listOf(node)
                    else -> listOf(node)
                }
            }
            else -> listOf(node)
        }
    }

    private fun buildProduct(factors: List<MathNode>): MathNode {
        if (factors.isEmpty()) return MathNode.Number(1.0)
        if (factors.size == 1) return factors[0]

        var result = factors[0]
        for (i in 1 until factors.size) {
            result = MathNode.BinaryOp(Operator.MULTIPLY, result, factors[i])
        }
        return result
    }

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

        val terms = extractTermsFromSum(node).map { MathTerm.fromNode(it) }
        Log.d(TAG, "提取到 ${terms.size} 个项")

        for ((index, term) in terms.withIndex()) {
            Log.d(TAG, "项 $index: coeff=${term.coefficient}")
            Log.d(TAG, "项 $index: vars=${term.variables.keys}")
            Log.d(TAG, "项 $index: funcs=${term.functions.keys}")
        }

        if (terms.size < 2) {
            Log.d(TAG, "项数少于2，跳过")
            return node
        }

        Log.d(TAG, "准备调用 findGCD")

        val gcd = findGCD(terms)

        Log.d(TAG, "GCD结果: coeff=${gcd.coefficient}, vars=${gcd.variables}, funcs=${gcd.functions}")

        if (abs(gcd.coefficient - 1.0) < EPSILON &&
            gcd.variables.isEmpty() &&
            gcd.functions.isEmpty()) {
            Log.d(TAG, "GCD为1，无法提取公因子")
            return node
        }

        // ✅ 检查变量公因子
        for ((varName, gcdExp) in gcd.variables) {
            for (term in terms) {
                val termExp = term.variables[varName] ?: 0.0
                if (termExp < gcdExp - EPSILON) {
                    Log.d(TAG, "跳过提取变量 $varName，因为不是所有项都有足够的指数")
                    val coeffOnlyGCD = MathTerm(gcd.coefficient, emptyMap(), emptyMap(), emptyList())
                    return buildFactoredExpression(terms, coeffOnlyGCD)
                }
            }
        }

        // ✅ 检查函数公因子（新增）
        for ((funcName, gcdExp) in gcd.functions) {
            Log.d(TAG, "检查函数公因子: $funcName, 需要指数: $gcdExp")
            for (term in terms) {
                val termExp = term.functions[funcName] ?: 0.0
                Log.d(TAG, "  项的指数: $termExp")
                if (termExp < gcdExp - EPSILON) {
                    Log.d(TAG, "跳过提取函数 $funcName，因为不是所有项都有足够的指数")
                    val coeffOnlyGCD = MathTerm(gcd.coefficient, emptyMap(), emptyMap(), emptyList())
                    return buildFactoredExpression(terms, coeffOnlyGCD)
                }
            }
        }

        Log.d(TAG, "✅ 所有检查通过，开始提取公因子")
        val result = buildFactoredExpression(terms, gcd)
        Log.d(TAG, "提取后的结果: $result")
        return result
    }

    private fun buildFactoredExpression(terms: List<MathTerm>, gcd: MathTerm): MathNode {
        val remaining = terms.map { divideTerm(it, gcd) }
        val sumNode = buildSum(remaining.map { it.toNode() })
        return MathNode.BinaryOp(Operator.MULTIPLY, gcd.toNode(), sumNode)
    }

    private fun findGCD(terms: List<MathTerm>): MathTerm {
        Log.d(TAG, "========== findGCD ==========")
        if (terms.isEmpty()) return MathTerm(1.0, emptyMap(), emptyMap(), emptyList())

        val coeffGCD = terms.map { abs(it.coefficient) }
            .reduce { a, b -> gcd(a, b) }
        Log.d(TAG, "系数GCD: $coeffGCD")

        val allVars = terms.flatMap { it.variables.keys }.toSet()
        Log.d(TAG, "所有变量: $allVars")

        val varGCD = mutableMapOf<String, Double>()

        for (v in allVars) {
            val allHaveVar = terms.all { it.variables.containsKey(v) }
            Log.d(TAG, "变量 $v: 所有项都有=$allHaveVar")

            if (allHaveVar) {
                val minExponent = terms.mapNotNull { it.variables[v] }.minOrNull() ?: 0.0
                if (minExponent > EPSILON) {
                    varGCD[v] = minExponent
                    Log.d(TAG, "变量 $v 可以提取，指数=$minExponent")
                }
            } else {
                Log.d(TAG, "变量 $v 不能提取，因为不是所有项都有")
            }
        }

        // ✅ 新增：处理函数公因子
        val allFuncs = terms.flatMap { it.functions.keys }.toSet()
        Log.d(TAG, "所有函数: $allFuncs")

        val funcGCD = mutableMapOf<String, Double>()

        for (f in allFuncs) {
            val allHaveFunc = terms.all { it.functions.containsKey(f) }
            Log.d(TAG, "函数 $f: 所有项都有=$allHaveFunc")

            if (allHaveFunc) {
                val minExponent = terms.mapNotNull { it.functions[f] }.minOrNull() ?: 0.0
                if (minExponent > EPSILON) {
                    funcGCD[f] = minExponent
                    Log.d(TAG, "函数 $f 可以提取，指数=$minExponent")
                }
            } else {
                Log.d(TAG, "函数 $f 不能提取，因为不是所有项都有")
            }
        }

        val result = MathTerm(coeffGCD, varGCD, funcGCD, emptyList())
        Log.d(TAG, "最终GCD: coeff=$coeffGCD, vars=$varGCD, funcs=$funcGCD")
        return result
    }

    private fun gcd(a: Double, b: Double): Double {
        val aInt = round(a).toLong()
        val bInt = round(b).toLong()

        if (aInt == 0L) return bInt.toDouble()
        if (bInt == 0L) return aInt.toDouble()

        var x = abs(aInt)
        var y = abs(bInt)

        while (y != 0L) {
            val temp = y
            y = x % y
            x = temp
        }

        return x.toDouble()
    }

    private fun divideTerm(term: MathTerm, divisor: MathTerm): MathTerm {
        val newCoeff = term.coefficient / divisor.coefficient

        val newVars = term.variables.toMutableMap()
        for ((v, exp) in divisor.variables) {
            newVars[v] = (newVars[v] ?: 0.0) - exp
            if (abs(newVars[v]!!) < EPSILON) {
                newVars.remove(v)
            }
        }

        return MathTerm(newCoeff, newVars, term.functions, term.nestedExpressions)
    }

    private fun extractTermsFromSum(node: MathNode): List<MathNode> {
        return when (node) {
            is MathNode.BinaryOp -> {
                if (node.operator == Operator.ADD) {
                    extractTermsFromSum(node.left) + extractTermsFromSum(node.right)
                } else if (node.operator == Operator.SUBTRACT) {
                    extractTermsFromSum(node.left) + listOf(
                        MathNode.BinaryOp(Operator.MULTIPLY, MathNode.Number(-1.0), node.right)
                    )
                } else {
                    listOf(node)
                }
            }
            else -> listOf(node)
        }
    }

    private fun buildSum(terms: List<MathNode>): MathNode {
        if (terms.isEmpty()) return MathNode.Number(0.0)
        if (terms.size == 1) return terms[0]

        var result = terms[0]
        for (i in 1 until terms.size) {
            result = MathNode.BinaryOp(Operator.ADD, result, terms[i])
        }
        return result
    }

    private fun isEquivalentString(a: MathNode, b: MathNode): Boolean {
        return a.toString() == b.toString()
    }
}