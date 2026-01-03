// app/src/main/java/com/mathsnew/mathsnew/newsimplified/MathTerm.kt
// 数学项的规范化表示（完全增强版 - 深度规范化）

package com.mathsnew.mathsnew.newsimplified

import com.mathsnew.mathsnew.*
import android.util.Log
import kotlin.math.abs

data class MathTerm(
    val coefficient: Double,
    val variables: Map<String, Double>,
    val functions: Map<String, Double>,
    val nestedExpressions: List<MathNode.BinaryOp>
) {
    companion object {
        private const val TAG = "MathTerm"
        private const val EPSILON = 1e-10

        fun fromNode(node: MathNode): MathTerm {
            return when (node) {
                is MathNode.Number -> MathTerm(
                    coefficient = node.value,
                    variables = emptyMap(),
                    functions = emptyMap(),
                    nestedExpressions = emptyList()
                )

                is MathNode.Variable -> MathTerm(
                    coefficient = 1.0,
                    variables = mapOf(node.name to 1.0),
                    functions = emptyMap(),
                    nestedExpressions = emptyList()
                )

                is MathNode.Function -> MathTerm(
                    coefficient = 1.0,
                    variables = emptyMap(),
                    functions = mapOf(node.toString() to 1.0),
                    nestedExpressions = emptyList()
                )

                is MathNode.BinaryOp -> {
                    when (node.operator) {
                        Operator.MULTIPLY -> extractFromMultiply(node)
                        Operator.POWER -> extractFromPower(node)
                        else -> MathTerm(
                            coefficient = 1.0,
                            variables = emptyMap(),
                            functions = emptyMap(),
                            nestedExpressions = listOf(node)
                        )
                    }
                }
            }
        }

        private fun extractFromMultiply(node: MathNode.BinaryOp): MathTerm {
            val factors = collectMultiplyFactors(node)

            var coefficient = 1.0
            val variables = mutableMapOf<String, Double>()
            val functions = mutableMapOf<String, Double>()
            val nested = mutableListOf<MathNode.BinaryOp>()

            for (factor in factors) {
                val term = fromNode(factor)
                coefficient *= term.coefficient

                for ((varName, exponent) in term.variables) {
                    variables[varName] = (variables[varName] ?: 0.0) + exponent
                }

                for ((funcKey, exponent) in term.functions) {
                    functions[funcKey] = (functions[funcKey] ?: 0.0) + exponent
                }

                nested.addAll(term.nestedExpressions)
            }

            return MathTerm(coefficient, variables, functions, nested)
        }

        private fun extractFromPower(node: MathNode.BinaryOp): MathTerm {
            val base = node.left
            val exponent = node.right

            if (exponent !is MathNode.Number) {
                return MathTerm(1.0, emptyMap(), emptyMap(), listOf(node))
            }

            return when (base) {
                is MathNode.Variable -> MathTerm(
                    coefficient = 1.0,
                    variables = mapOf(base.name to exponent.value),
                    functions = emptyMap(),
                    nestedExpressions = emptyList()
                )

                is MathNode.Number -> MathTerm(
                    coefficient = Math.pow(base.value, exponent.value),
                    variables = emptyMap(),
                    functions = emptyMap(),
                    nestedExpressions = emptyList()
                )

                is MathNode.Function -> MathTerm(
                    coefficient = 1.0,
                    variables = emptyMap(),
                    functions = mapOf(base.toString() to exponent.value),
                    nestedExpressions = emptyList()
                )

                else -> MathTerm(
                    coefficient = 1.0,
                    variables = emptyMap(),
                    functions = emptyMap(),
                    nestedExpressions = listOf(node)
                )
            }
        }

        private fun collectMultiplyFactors(node: MathNode): List<MathNode> {
            return when (node) {
                is MathNode.BinaryOp -> {
                    if (node.operator == Operator.MULTIPLY) {
                        collectMultiplyFactors(node.left) + collectMultiplyFactors(node.right)
                    } else {
                        listOf(node)
                    }
                }
                else -> listOf(node)
            }
        }

        private fun parseFunctionKey(key: String): MathNode.Function? {
            val regex = """(\w+)\((.*)\)""".toRegex()
            val match = regex.matchEntire(key) ?: return null

            val (name, argStr) = match.destructured

            val parser = ExpressionParser()
            val argNode = try {
                parser.parse(argStr)
            } catch (e: Exception) {
                return null
            }

            return MathNode.Function(name, argNode)
        }

        /**
         * 🆕 规范化 MathNode 为字符串（深度增强版）
         *
         * 目标：确保数学上等价的表达式生成相同的字符串
         *
         * 策略：
         * 1. 对加法：拍平 → 提取 MathTerm → 合并同类项 → 排序
         * 2. 对乘法：收集因子 → 排序
         * 3. 递归处理所有嵌套表达式
         */
        private fun canonicalizeNode(node: MathNode): String {
            return when (node) {
                is MathNode.Number -> {
                    // 整数不显示小数点
                    if (abs(node.value - node.value.toLong().toDouble()) < EPSILON) {
                        node.value.toLong().toString()
                    } else {
                        node.value.toString()
                    }
                }

                is MathNode.Variable -> node.name

                is MathNode.Function -> {
                    val argStr = canonicalizeNode(node.argument)
                    "${node.name}($argStr)"
                }

                is MathNode.BinaryOp -> {
                    when (node.operator) {
                        // 🆕 加法和减法：深度合并同类项
                        Operator.ADD, Operator.SUBTRACT -> {
                            canonicalizeAddition(node)
                        }

                        // 乘法：收集因子后排序
                        Operator.MULTIPLY -> {
                            val factors = flattenMultiplication(node)
                            val sortedFactors = factors.sortedWith(compareBy(
                                { getPriority(it) },
                                { canonicalizeNode(it) }
                            ))
                            sortedFactors.joinToString("*") { canonicalizeNode(it) }
                        }

                        // 除法：规范化分子和分母
                        Operator.DIVIDE -> {
                            val left = canonicalizeNode(node.left)
                            val right = canonicalizeNode(node.right)
                            "$left/$right"
                        }

                        // 幂：规范化底数和指数
                        Operator.POWER -> {
                            val base = canonicalizeNode(node.left)
                            val exp = canonicalizeNode(node.right)
                            "$base^$exp"
                        }
                    }
                }
            }
        }

        /**
         * 🆕 深度规范化加法表达式
         *
         * 步骤：
         * 1. 拍平加法树
         * 2. 提取为 MathTerm
         * 3. 合并同类项
         * 4. 排序
         * 5. 重建字符串
         */
        private fun canonicalizeAddition(node: MathNode): String {
            // 1. 拍平加法树
            val termNodes = flattenAddition(node)

            // 2. 提取为 MathTerm
            val terms = termNodes.map { fromNode(it) }

            // 3. 合并同类项
            val merged = mergeSimpleTerms(terms)

            // 4. 排序（按次数降序，常数项最后）
            val sorted = merged.sortedWith(compareBy(
                { it.isConstant() },
                { -getTotalDegree(it) },
                { getTermKey(it) }
            ))

            // 5. 重建字符串
            if (sorted.isEmpty()) return "0"

            val result = StringBuilder()
            for ((index, term) in sorted.withIndex()) {
                val termStr = termToString(term)

                if (index == 0) {
                    result.append(termStr)
                } else {
                    // 如果项以负号开头，直接拼接（已经包含减号）
                    if (termStr.startsWith("-")) {
                        result.append(termStr)
                    } else {
                        result.append("+").append(termStr)
                    }
                }
            }

            return result.toString()
        }

        /**
         * 🆕 合并简单的同类项
         */
        private fun mergeSimpleTerms(terms: List<MathTerm>): List<MathTerm> {
            if (terms.isEmpty()) return emptyList()

            // 按 baseKey 分组
            val groups = mutableMapOf<String, MutableList<MathTerm>>()
            for (term in terms) {
                val key = getTermKey(term)
                if (!groups.containsKey(key)) {
                    groups[key] = mutableListOf()
                }
                groups[key]!!.add(term)
            }

            // 合并每组
            val merged = mutableListOf<MathTerm>()
            for ((_, group) in groups) {
                var combined = group[0]
                for (i in 1 until group.size) {
                    combined = MathTerm(
                        coefficient = combined.coefficient + group[i].coefficient,
                        variables = combined.variables,
                        functions = combined.functions,
                        nestedExpressions = combined.nestedExpressions
                    )
                }

                // 过滤掉系数为0的项
                if (abs(combined.coefficient) > EPSILON) {
                    merged.add(combined)
                }
            }

            return merged
        }

        /**
         * 🆕 获取 MathTerm 的简化 key（用于分组）
         */
        private fun getTermKey(term: MathTerm): String {
            val varPart = term.variables.toSortedMap().entries.joinToString("*") { (v, e) ->
                if (abs(e - 1.0) < EPSILON) v else "$v^${formatExponent(e)}"
            }

            val funcPart = term.functions.entries.sortedBy { it.key }.joinToString("*") { (f, e) ->
                if (abs(e - 1.0) < EPSILON) f else "$f^${formatExponent(e)}"
            }

            val nestedPart = term.nestedExpressions
                .map { canonicalizeNode(it) }
                .sorted()
                .joinToString("*")

            return listOf(varPart, funcPart, nestedPart)
                .filter { it.isNotEmpty() }
                .joinToString("*")
                .ifEmpty { "1" }
        }

        /**
         * 🆕 将 MathTerm 转为字符串
         */
        private fun termToString(term: MathTerm): String {
            if (abs(term.coefficient) < EPSILON) return "0"

            val key = getTermKey(term)

            return when {
                key == "1" -> {
                    // 纯常数项
                    if (abs(term.coefficient - term.coefficient.toLong().toDouble()) < EPSILON) {
                        term.coefficient.toLong().toString()
                    } else {
                        term.coefficient.toString()
                    }
                }
                abs(term.coefficient - 1.0) < EPSILON -> {
                    // 系数为1
                    key
                }
                abs(term.coefficient + 1.0) < EPSILON -> {
                    // 系数为-1
                    "-$key"
                }
                else -> {
                    // 一般情况
                    val coeffStr = if (abs(term.coefficient - term.coefficient.toLong().toDouble()) < EPSILON) {
                        term.coefficient.toLong().toString()
                    } else {
                        term.coefficient.toString()
                    }
                    "$coeffStr*$key"
                }
            }
        }

        /**
         * 🆕 获取总次数
         */
        private fun getTotalDegree(term: MathTerm): Double {
            return term.variables.values.sum()
        }

        /**
         * 拍平加法树
         */
        private fun flattenAddition(node: MathNode): List<MathNode> {
            return when (node) {
                is MathNode.BinaryOp -> {
                    when (node.operator) {
                        Operator.ADD -> flattenAddition(node.left) + flattenAddition(node.right)
                        Operator.SUBTRACT -> {
                            flattenAddition(node.left) + flattenAddition(node.right).map { negate(it) }
                        }
                        else -> listOf(node)
                    }
                }
                else -> listOf(node)
            }
        }

        /**
         * 拍平乘法树
         */
        private fun flattenMultiplication(node: MathNode): List<MathNode> {
            return when (node) {
                is MathNode.BinaryOp -> {
                    if (node.operator == Operator.MULTIPLY) {
                        flattenMultiplication(node.left) + flattenMultiplication(node.right)
                    } else {
                        listOf(node)
                    }
                }
                else -> listOf(node)
            }
        }

        /**
         * 取负
         */
        private fun negate(node: MathNode): MathNode {
            return when (node) {
                is MathNode.Number -> MathNode.Number(-node.value)
                else -> MathNode.BinaryOp(Operator.MULTIPLY, MathNode.Number(-1.0), node)
            }
        }

        /**
         * 获取优先级（用于排序）
         */
        private fun getPriority(node: MathNode): Int {
            return when (node) {
                is MathNode.Number -> 0
                is MathNode.Variable -> 1
                is MathNode.Function -> 2
                is MathNode.BinaryOp -> 3
            }
        }

        /**
         * 格式化指数（去掉不必要的小数点）
         */
        private fun formatExponent(exp: Double): String {
            return if (abs(exp - exp.toLong().toDouble()) < EPSILON) {
                exp.toLong().toString()
            } else {
                exp.toString()
            }
        }
    }

    fun isZero(): Boolean = abs(coefficient) < EPSILON

    fun isConstant(): Boolean = variables.isEmpty() && functions.isEmpty() && nestedExpressions.isEmpty()

    /**
     * 判断是否为同类项（增强版）
     */
    fun isSimilarTo(other: MathTerm): Boolean {
        Log.d(TAG, "========== isSimilarTo ==========")
        Log.d(TAG, "this: coeff=$coefficient, vars=$variables, funcs=${functions.keys}")
        Log.d(TAG, "other: coeff=${other.coefficient}, vars=${other.variables}, funcs=${other.functions.keys}")

        // 1. 变量部分必须完全相同
        if (variables != other.variables) {
            Log.d(TAG, "❌ 变量不同: $variables vs ${other.variables}")
            return false
        }

        // 2. 函数键必须完全相同
        if (functions.keys != other.functions.keys) {
            Log.d(TAG, "❌ 函数键不同: ${functions.keys} vs ${other.functions.keys}")
            return false
        }

        // 3. 嵌套表达式数量必须相同
        if (nestedExpressions.size != other.nestedExpressions.size) {
            Log.d(TAG, "❌ 嵌套表达式数量不同: ${nestedExpressions.size} vs ${other.nestedExpressions.size}")
            return false
        }

        // 4. 使用规范化字符串比较嵌套表达式
        if (nestedExpressions.isNotEmpty()) {
            val thisNested = nestedExpressions.map { canonicalizeNode(it) }.sorted()
            val otherNested = other.nestedExpressions.map { canonicalizeNode(it) }.sorted()

            if (thisNested != otherNested) {
                Log.d(TAG, "❌ 嵌套表达式不同:")
                Log.d(TAG, "  this:  $thisNested")
                Log.d(TAG, "  other: $otherNested")
                return false
            }
        }

        Log.d(TAG, "✅ 是同类项")
        return true
    }

    fun mergeWith(other: MathTerm): MathTerm? {
        if (!isSimilarTo(other)) return null

        val merged = MathTerm(
            coefficient = this.coefficient + other.coefficient,
            variables = this.variables,
            functions = this.functions,
            nestedExpressions = this.nestedExpressions
        )

        Log.d(TAG, "✅ 合并: $coefficient + ${other.coefficient} = ${merged.coefficient}")

        return merged
    }

    fun toNode(): MathNode {
        if (isZero()) return MathNode.Number(0.0)

        val parts = mutableListOf<MathNode>()

        for ((varName, exponent) in variables.toSortedMap()) {
            if (abs(exponent) < EPSILON) continue

            val varNode = MathNode.Variable(varName)
            val withExponent = if (abs(exponent - 1.0) < EPSILON) {
                varNode
            } else {
                MathNode.BinaryOp(Operator.POWER, varNode, MathNode.Number(exponent))
            }
            parts.add(withExponent)
        }

        for ((funcKey, exponent) in functions.toList().sortedBy { it.first }) {
            if (abs(exponent) < EPSILON) continue

            val funcNode = parseFunctionKey(funcKey) ?: continue

            val withExponent = if (abs(exponent - 1.0) < EPSILON) {
                funcNode
            } else {
                MathNode.BinaryOp(Operator.POWER, funcNode, MathNode.Number(exponent))
            }
            parts.add(withExponent)
        }

        parts.addAll(nestedExpressions)

        if (parts.isEmpty()) {
            return MathNode.Number(coefficient)
        }

        var result = parts[0]
        for (i in 1 until parts.size) {
            result = MathNode.BinaryOp(Operator.MULTIPLY, result, parts[i])
        }

        return when {
            abs(coefficient - 1.0) < EPSILON -> {
                result
            }
            abs(coefficient + 1.0) < EPSILON -> {
                MathNode.BinaryOp(Operator.MULTIPLY, MathNode.Number(-1.0), result)
            }
            else -> {
                MathNode.BinaryOp(Operator.MULTIPLY, MathNode.Number(coefficient), result)
            }
        }
    }

    /**
     * 生成 baseKey（使用规范化版本）
     */
    fun getBaseKey(): String {
        val key = getTermKey(this)
        Log.d(TAG, "getBaseKey: coeff=$coefficient → key='$key'")
        return key
    }

    override fun toString(): String {
        if (isZero()) return "0"
        return termToString(this)
    }
}