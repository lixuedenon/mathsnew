package com.mathsnew.mathsnew.newsimplified

import com.mathsnew.mathsnew.*
import android.util.Log
import kotlin.math.abs

/**
 * 数学项的唯一标识生成器
 *
 * 用途：
 * 1. 为 MathTerm 生成唯一标识字符串
 * 2. 用于判断同类项
 * 3. 支持 FunctionKey 的规范化
 *
 * 修改记录：
 * - 2026-01-07: 支持 FunctionKey 而非字符串函数键
 */
object TermKey {

    private const val TAG = "TermKey"
    private const val EPSILON = 1e-10

    /**
     * 为 MathTerm 生成唯一标识
     *
     * @param term 数学项
     * @return 唯一标识字符串
     */
    fun generate(term: MathTerm): String {
        val parts = mutableListOf<String>()

        // 添加变量部分
        if (term.variables.isNotEmpty()) {
            val varPart = term.variables.toSortedMap().entries.joinToString("*") { (varName, exponent) ->
                formatVariable(varName, exponent)
            }
            parts.add(varPart)
        }

        // 添加函数部分
        if (term.functions.isNotEmpty()) {
            val funcPart = term.functions.entries.sortedBy { it.key.toCanonicalString() }.joinToString("*") { (funcKey, exponent) ->
                formatFunction(funcKey, exponent)
            }
            parts.add(funcPart)
        }

        // 添加嵌套表达式部分
        if (term.nestedExpressions.isNotEmpty()) {
            val nestedPart = term.nestedExpressions
                .map { normalizedNodeToString(it) }
                .sorted()
                .joinToString("*")
            parts.add(nestedPart)
        }

        return if (parts.isEmpty()) {
            "1"
        } else {
            parts.joinToString("*")
        }
    }

    /**
     * 格式化变量
     *
     * @param varName 变量名
     * @param exponent 指数
     * @return 格式化字符串
     */
    private fun formatVariable(varName: String, exponent: Double): String {
        return if (abs(exponent - 1.0) < EPSILON) {
            varName
        } else {
            "$varName^${formatExponent(exponent)}"
        }
    }

    /**
     * 格式化函数
     *
     * @param funcKey 函数键
     * @param exponent 指数
     * @return 格式化字符串
     */
    private fun formatFunction(funcKey: FunctionKey, exponent: Double): String {
        val canonicalStr = funcKey.toCanonicalString()
        return if (abs(exponent - 1.0) < EPSILON) {
            canonicalStr
        } else {
            "$canonicalStr^${formatExponent(exponent)}"
        }
    }

    /**
     * 格式化指数
     *
     * @param exponent 指数
     * @return 格式化字符串
     */
    private fun formatExponent(exponent: Double): String {
        return if (abs(exponent - exponent.toLong().toDouble()) < EPSILON) {
            exponent.toLong().toString()
        } else {
            exponent.toString()
        }
    }

    /**
     * 规范化节点为字符串（处理加法中的同类项）
     *
     * @param node AST 节点
     * @return 规范化字符串
     */
    private fun normalizedNodeToString(node: MathNode): String {
        return when (node) {
            is MathNode.BinaryOp -> {
                when (node.operator) {
                    Operator.ADD, Operator.SUBTRACT -> {
                        normalizeAddition(node)
                    }
                    else -> simpleNodeToString(node)
                }
            }
            else -> simpleNodeToString(node)
        }
    }

    /**
     * 规范化加法表达式（合并同类项）
     *
     * @param node 加法节点
     * @return 规范化字符串
     */
    private fun normalizeAddition(node: MathNode): String {
        Log.d(TAG, "规范化加法: $node")

        val termNodes = flattenAddition(node)
        Log.d(TAG, "  拍平后: ${termNodes.size} 个节点")

        val simplifiedTerms = termNodes.map { SimpleTerm.from(it) }

        val groups = mutableMapOf<String, MutableList<SimpleTerm>>()
        for (term in simplifiedTerms) {
            val key = term.baseKey
            if (!groups.containsKey(key)) {
                groups[key] = mutableListOf()
            }
            groups[key]!!.add(term)
        }

        Log.d(TAG, "  分组: ${groups.size} 组")

        val merged = mutableListOf<SimpleTerm>()
        for ((key, group) in groups) {
            val totalCoeff = group.sumOf { it.coefficient }
            if (abs(totalCoeff) > EPSILON) {
                merged.add(SimpleTerm(totalCoeff, key))
                if (group.size > 1) {
                    Log.d(TAG, "  ✅ 合并: $key (${group.size}项 → 系数=$totalCoeff)")
                }
            }
        }

        val sorted = merged.sortedBy { it.baseKey }
        val result = sorted.joinToString("+") { it.toString() }

        Log.d(TAG, "  结果: $result")
        return result
    }

    /**
     * 简化的 Term 表示（避免与 MathTerm 循环依赖）
     */
    private data class SimpleTerm(
        val coefficient: Double,
        val baseKey: String
    ) {
        companion object {
            fun from(node: MathNode): SimpleTerm {
                return when (node) {
                    is MathNode.Number -> SimpleTerm(node.value, "1")

                    is MathNode.Variable -> SimpleTerm(1.0, node.name)

                    is MathNode.BinaryOp -> {
                        when (node.operator) {
                            Operator.MULTIPLY -> {
                                val factors = flattenMultiplication(node)
                                var coeff = 1.0
                                val nonNumeric = mutableListOf<String>()

                                for (factor in factors) {
                                    if (factor is MathNode.Number) {
                                        coeff *= factor.value
                                    } else {
                                        nonNumeric.add(simpleNodeToString(factor))
                                    }
                                }

                                val key = if (nonNumeric.isEmpty()) "1"
                                          else nonNumeric.sorted().joinToString("*")
                                SimpleTerm(coeff, key)
                            }

                            else -> SimpleTerm(1.0, simpleNodeToString(node))
                        }
                    }

                    else -> SimpleTerm(1.0, simpleNodeToString(node))
                }
            }
        }

        override fun toString(): String {
            return when {
                baseKey == "1" -> {
                    if (abs(coefficient - coefficient.toLong().toDouble()) < EPSILON) {
                        coefficient.toLong().toString()
                    } else {
                        coefficient.toString()
                    }
                }
                abs(coefficient - 1.0) < EPSILON -> baseKey
                abs(coefficient + 1.0) < EPSILON -> "-$baseKey"
                else -> {
                    val coeffStr = if (abs(coefficient - coefficient.toLong().toDouble()) < EPSILON) {
                        coefficient.toLong().toString()
                    } else {
                        coefficient.toString()
                    }
                    "$coeffStr*$baseKey"
                }
            }
        }
    }

    /**
     * 简单节点转字符串
     *
     * @param node AST 节点
     * @return 字符串
     */
    private fun simpleNodeToString(node: MathNode): String {
        return when (node) {
            is MathNode.Number -> {
                if (abs(node.value - node.value.toLong().toDouble()) < EPSILON) {
                    node.value.toLong().toString()
                } else {
                    node.value.toString()
                }
            }

            is MathNode.Variable -> node.name

            is MathNode.Function -> {
                val argStr = simpleNodeToString(node.argument)
                "${node.name}($argStr)"
            }

            is MathNode.BinaryOp -> {
                val left = simpleNodeToString(node.left)
                val right = simpleNodeToString(node.right)

                when (node.operator) {
                    Operator.ADD -> "$left+$right"
                    Operator.SUBTRACT -> "$left-$right"
                    Operator.MULTIPLY -> {
                        val parts = flattenMultiplication(node).map { simpleNodeToString(it) }.sorted()
                        parts.joinToString("*")
                    }
                    Operator.DIVIDE -> "$left/$right"
                    Operator.POWER -> "$left^$right"
                }
            }
        }
    }

    /**
     * 拍平加法树
     *
     * @param node AST 节点
     * @return 项列表
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
     *
     * @param node AST 节点
     * @return 因子列表
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
     *
     * @param node AST 节点
     * @return 取负后的节点
     */
    private fun negate(node: MathNode): MathNode {
        return when (node) {
            is MathNode.Number -> MathNode.Number(-node.value)
            else -> MathNode.BinaryOp(Operator.MULTIPLY, MathNode.Number(-1.0), node)
        }
    }
}