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

    fun generateAllForms(node: MathNode): SimplificationFormsV2 {  // ← 改这里
        Log.d(TAG, "========== 开始生成形式 ==========")
        Log.d(TAG, "输入: $node")

        val forms = mutableListOf<SimplifiedForm>()

        // 规范化，得到真正的展开形式
        val canonical = canonicalizer.canonicalize(node)
        forms.add(SimplifiedForm(canonical, SimplificationType.EXPANDED, "完全展开"))
        Log.d(TAG, "形式1 (展开): $canonical")

        // 如果是分式，生成 ABCD 四种形式
        if (node is MathNode.BinaryOp && node.operator == Operator.DIVIDE) {
            generateFractionForms(canonical, forms)
        } else {
            // 非分式，尝试提取公因子
            val factored = extractCommonFactor(canonical)
            if (!isEquivalentString(factored, canonical)) {
                forms.add(SimplifiedForm(factored, SimplificationType.FACTORED, "提取公因子"))
                Log.d(TAG, "形式2 (因式): $factored")
            }
        }

        Log.d(TAG, "共生成 ${forms.size} 种形式")
        Log.d(TAG, "========== 形式生成完成 ==========")

        return SimplificationFormsV2(forms)  // ← 改这里
    }

    /**
     * 生成分式的 ABCD 四种形式
     */
    private fun generateFractionForms(canonical: MathNode, forms: MutableList<SimplifiedForm>) {
        if (canonical !is MathNode.BinaryOp || canonical.operator != Operator.DIVIDE) {
            return
        }

        val numerator = canonical.left
        val denominator = canonical.right

        // 形式 B: 分子提取公因子
        val numeratorFactored = extractCommonFactor(numerator)
        if (!isEquivalentString(numeratorFactored, numerator)) {
            val formB = MathNode.BinaryOp(Operator.DIVIDE, numeratorFactored, denominator)
            forms.add(SimplifiedForm(formB, SimplificationType.FACTORED, "分子因式分解"))
            Log.d(TAG, "形式B (分子因式): $formB")
        }

        // 形式 C: 分母因式分解
        val denominatorFactored = tryFactorPolynomial(denominator)
        if (!isEquivalentString(denominatorFactored, denominator)) {
            val formC = MathNode.BinaryOp(Operator.DIVIDE, numerator, denominatorFactored)
            forms.add(SimplifiedForm(formC, SimplificationType.FACTORED, "分母因式分解"))
            Log.d(TAG, "形式C (分母因式): $formC")
        }

        // 形式 D: 完全约分
        val numeratorFullFactored = extractCommonFactor(numerator)
        val denominatorFullFactored = tryFactorPolynomial(denominator)

        val simplified = trySimplifyFraction(numeratorFullFactored, denominatorFullFactored)
        if (!isEquivalentString(simplified, canonical)) {
            forms.add(SimplifiedForm(simplified, SimplificationType.FACTORED, "约分"))
            Log.d(TAG, "形式D (约分): $simplified")
        }
    }

    /**
     * 尝试因式分解多项式
     */
    private fun tryFactorPolynomial(node: MathNode): MathNode {
        Log.d(TAG, "尝试因式分解: $node")

        // 尝试识别任意次完全幂
        val perfectPower = tryPerfectPower(node)
        if (perfectPower != null) {
            Log.d(TAG, "识别到完全幂: $perfectPower")
            return perfectPower
        }

        // 尝试提取公因子
        return extractCommonFactor(node)
    }

    /**
     * 识别任意次完全幂
     */
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

    /**
     * 尝试匹配 (x+a)^n 的二项式展开
     */
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

    /**
     * 计算二项式系数 C(n, k)
     */
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

    /**
     * 尝试约分分式
     */
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

    /**
     * 尝试幂运算约分
     */
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

    /**
     * 提取表达式中的因子
     */
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

    /**
     * 🔧 修复：提取公因子（避免产生负指数）
     */
    private fun extractCommonFactor(node: MathNode): MathNode {
        if (node !is MathNode.BinaryOp ||
            (node.operator != Operator.ADD && node.operator != Operator.SUBTRACT)) {
            return node
        }

        val terms = extractTermsFromSum(node).map { MathTerm.fromNode(it) }

        if (terms.size < 2) return node

        val gcd = findGCD(terms)

        // 🔧 关键修复：检查是否真的应该提取这个公因子
        // 如果提取后会产生负指数或者分数指数，就不提取
        if (abs(gcd.coefficient - 1.0) < EPSILON &&
            gcd.variables.isEmpty() &&
            gcd.functions.isEmpty()) {
            return node
        }

        // 🔧 新增：检查是否所有项都包含这些变量
        for ((varName, gcdExp) in gcd.variables) {
            for (term in terms) {
                val termExp = term.variables[varName] ?: 0.0
                if (termExp < gcdExp - EPSILON) {
                    // 有项不包含这个变量或指数不够，不应该提取
                    Log.d(TAG, "跳过提取变量 $varName，因为不是所有项都有")
                    // 只提取系数
                    val coeffOnlyGCD = MathTerm(gcd.coefficient, emptyMap(), emptyMap(), emptyList())
                    return buildFactoredExpression(terms, coeffOnlyGCD)
                }
            }
        }

        return buildFactoredExpression(terms, gcd)
    }

    /**
     * 🔧 新增：构建因式分解表达式
     */
    private fun buildFactoredExpression(terms: List<MathTerm>, gcd: MathTerm): MathNode {
        val remaining = terms.map { divideTerm(it, gcd) }
        val sumNode = buildSum(remaining.map { it.toNode() })
        return MathNode.BinaryOp(Operator.MULTIPLY, gcd.toNode(), sumNode)
    }

    /**
     * 🔧 修复：找最大公因子（更严格的逻辑）
     */
    private fun findGCD(terms: List<MathTerm>): MathTerm {
        if (terms.isEmpty()) return MathTerm(1.0, emptyMap(), emptyMap(), emptyList())

        // 系数的GCD
        val coeffGCD = terms.map { abs(it.coefficient) }
            .reduce { a, b -> gcd(a, b) }

        // 🔧 修复：变量的GCD - 只提取所有项都有的变量
        val allVars = terms.flatMap { it.variables.keys }.toSet()
        val varGCD = mutableMapOf<String, Double>()

        for (v in allVars) {
            // 检查是否所有项都包含这个变量
            val allHaveVar = terms.all { it.variables.containsKey(v) }

            if (allHaveVar) {
                // 所有项都有，取最小指数
                val minExponent = terms.mapNotNull { it.variables[v] }.minOrNull() ?: 0.0
                if (minExponent > EPSILON) {
                    varGCD[v] = minExponent
                    Log.d(TAG, "变量 $v 可以提取，指数=$minExponent")
                }
            } else {
                Log.d(TAG, "变量 $v 不能提取，因为不是所有项都有")
            }
        }

        return MathTerm(coeffGCD, varGCD, emptyMap(), emptyList())
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