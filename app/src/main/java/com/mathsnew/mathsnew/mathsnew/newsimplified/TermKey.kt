// app/src/main/java/com/mathsnew/mathsnew/newsimplified/TermKey.kt
// 数学项的唯一标识生成器 - 修复嵌套加法规范化

package com.mathsnew.mathsnew.newsimplified

import com.mathsnew.mathsnew.*
import android.util.Log
import kotlin.math.abs

object TermKey {

    private const val TAG = "TermKey"
    private const val EPSILON = 1e-10

    fun generate(term: MathTerm): String {
        val parts = mutableListOf<String>()

        if (term.variables.isNotEmpty()) {
            val varPart = term.variables.toSortedMap().entries.joinToString("*") { (varName, exponent) ->
                formatVariable(varName, exponent)
            }
            parts.add(varPart)
        }

        if (term.functions.isNotEmpty()) {
            val funcPart = term.functions.entries.sortedBy { it.key }.joinToString("*") { (funcKey, exponent) ->
                formatFunction(funcKey, exponent)
            }
            parts.add(funcPart)
        }

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

    private fun formatVariable(varName: String, exponent: Double): String {
        return if (abs(exponent - 1.0) < EPSILON) {
            varName
        } else {
            "$varName^${formatExponent(exponent)}"
        }
    }

    private fun formatFunction(funcKey: String, exponent: Double): String {
        return if (abs(exponent - 1.0) < EPSILON) {
            funcKey
        } else {
            "$funcKey^${formatExponent(exponent)}"
        }
    }

    private fun formatExponent(exponent: Double): String {
        return if (abs(exponent - exponent.toLong().toDouble()) < EPSILON) {
            exponent.toLong().toString()
        } else {
            exponent.toString()
        }
    }

    /**
     * 规范化节点为字符串（新增：处理加法中的同类项）
     */
    private fun normalizedNodeToString(node: MathNode): String {
        return when (node) {
            is MathNode.BinaryOp -> {
                when (node.operator) {
                    Operator.ADD, Operator.SUBTRACT -> {
                        // ✅ 关键修复：对加法表达式，先提取项，再合并同类项
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
     */
    private fun normalizeAddition(node: MathNode): String {
        Log.d(TAG, "规范化加法: $node")

        // 1. 拍平加法树
        val termNodes = flattenAddition(node)
        Log.d(TAG, "  拍平后: ${termNodes.size} 个节点")

        // 2. 转换为简化的 Term 表示（不使用 MathTerm，避免循环依赖）
        val simplifiedTerms = termNodes.map { SimpleTerm.from(it) }

        // 3. 按 baseKey 分组
        val groups = mutableMapOf<String, MutableList<SimpleTerm>>()
        for (term in simplifiedTerms) {
            val key = term.baseKey
            if (!groups.containsKey(key)) {
                groups[key] = mutableListOf()
            }
            groups[key]!!.add(term)
        }

        Log.d(TAG, "  分组: ${groups.size} 组")

        // 4. 合并各组
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

        // 5. 排序并生成字符串
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

    private fun negate(node: MathNode): MathNode {
        return when (node) {
            is MathNode.Number -> MathNode.Number(-node.value)
            else -> MathNode.BinaryOp(Operator.MULTIPLY, MathNode.Number(-1.0), node)
        }
    }
}
