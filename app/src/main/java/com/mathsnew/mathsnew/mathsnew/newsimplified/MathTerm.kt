// app/src/main/java/com/mathsnew/mathsnew/newsimplified/MathTerm.kt
// 数学项的规范化表示

package com.mathsnew.mathsnew.newsimplified

import com.mathsnew.mathsnew.*
import kotlin.math.abs
import android.util.Log

data class MathTerm(
    val coefficient: Double,
    val variables: Map<String, Double>,
    val functions: List<MathNode.Function>,
    val nestedExpressions: List<MathNode.BinaryOp>
) {
    companion object {
        private const val TAG = "MathTerm"
        private const val EPSILON = 1e-10

        fun fromNode(node: MathNode): MathTerm {
            val result = when (node) {
                is MathNode.Number -> MathTerm(
                    coefficient = node.value,
                    variables = emptyMap(),
                    functions = emptyList(),
                    nestedExpressions = emptyList()
                )

                is MathNode.Variable -> MathTerm(
                    coefficient = 1.0,
                    variables = mapOf(node.name to 1.0),
                    functions = emptyList(),
                    nestedExpressions = emptyList()
                )

                is MathNode.Function -> MathTerm(
                    coefficient = 1.0,
                    variables = emptyMap(),
                    functions = listOf(node),
                    nestedExpressions = emptyList()
                )

                is MathNode.BinaryOp -> {
                    when (node.operator) {
                        Operator.MULTIPLY -> extractFromMultiply(node)
                        Operator.POWER -> extractFromPower(node)
                        else -> MathTerm(
                            coefficient = 1.0,
                            variables = emptyMap(),
                            functions = emptyList(),
                            nestedExpressions = listOf(node)
                        )
                    }
                }
            }

            Log.d(TAG, "fromNode($node) -> coeff=${result.coefficient}, vars=${result.variables}")
            return result
        }

        private fun extractFromMultiply(node: MathNode.BinaryOp): MathTerm {
            val factors = collectMultiplyFactors(node)
            Log.d(TAG, "extractFromMultiply: 收集到 ${factors.size} 个因子: $factors")

            var coefficient = 1.0
            val variables = mutableMapOf<String, Double>()
            val functions = mutableListOf<MathNode.Function>()
            val nested = mutableListOf<MathNode.BinaryOp>()

            for (factor in factors) {
                val term = fromNode(factor)
                coefficient *= term.coefficient

                for ((varName, exponent) in term.variables) {
                    val current = variables[varName] ?: 0.0
                    variables[varName] = current + exponent
                    Log.d(TAG, "  变量 $varName: $current + $exponent = ${variables[varName]}")
                }

                functions.addAll(term.functions)
                nested.addAll(term.nestedExpressions)
            }

            Log.d(TAG, "extractFromMultiply 结果: coeff=$coefficient, vars=$variables")
            return MathTerm(coefficient, variables, functions, nested)
        }

        private fun extractFromPower(node: MathNode.BinaryOp): MathTerm {
            val base = node.left
            val exponent = node.right

            if (exponent !is MathNode.Number) {
                return MathTerm(1.0, emptyMap(), emptyList(), listOf(node))
            }

            return when (base) {
                is MathNode.Variable -> MathTerm(
                    coefficient = 1.0,
                    variables = mapOf(base.name to exponent.value),
                    functions = emptyList(),
                    nestedExpressions = emptyList()
                )

                is MathNode.Number -> MathTerm(
                    coefficient = Math.pow(base.value, exponent.value),
                    variables = emptyMap(),
                    functions = emptyList(),
                    nestedExpressions = emptyList()
                )

                else -> MathTerm(
                    coefficient = 1.0,
                    variables = emptyMap(),
                    functions = emptyList(),
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
    }

    fun isZero(): Boolean = abs(coefficient) < EPSILON

    fun isConstant(): Boolean = variables.isEmpty() && functions.isEmpty() && nestedExpressions.isEmpty()

    fun isSimilarTo(other: MathTerm): Boolean {
        if (variables != other.variables) return false

        if (functions.size != other.functions.size) return false
        if (functions.zip(other.functions).any { (f1, f2) -> !areNodesEqual(f1, f2) }) {
            return false
        }

        if (nestedExpressions.size != other.nestedExpressions.size) return false
        if (nestedExpressions.zip(other.nestedExpressions).any { (e1, e2) -> !areNodesEqual(e1, e2) }) {
            return false
        }

        return true
    }

    private fun areNodesEqual(a: MathNode, b: MathNode): Boolean {
        return when {
            a is MathNode.Number && b is MathNode.Number -> abs(a.value - b.value) < EPSILON
            a is MathNode.Variable && b is MathNode.Variable -> a.name == b.name
            a is MathNode.Function && b is MathNode.Function -> {
                a.name == b.name && areNodesEqual(a.argument, b.argument)
            }
            a is MathNode.BinaryOp && b is MathNode.BinaryOp -> {
                a.operator == b.operator &&
                areNodesEqual(a.left, b.left) &&
                areNodesEqual(a.right, b.right)
            }
            else -> false
        }
    }

    fun mergeWith(other: MathTerm): MathTerm? {
        if (!isSimilarTo(other)) return null

        return MathTerm(
            coefficient = this.coefficient + other.coefficient,
            variables = this.variables,
            functions = this.functions,
            nestedExpressions = this.nestedExpressions
        )
    }

    fun toNode(): MathNode {
        if (isZero()) return MathNode.Number(0.0)

        if (isConstant()) {
            return MathNode.Number(coefficient)
        }

        val parts = mutableListOf<MathNode>()

        if (coefficient < 0 && abs(coefficient + 1.0) > EPSILON) {
            parts.add(MathNode.Number(coefficient))
        } else if (coefficient < 0 && abs(coefficient + 1.0) < EPSILON) {
            parts.add(MathNode.Number(-1.0))
        } else if (coefficient > 0 && abs(coefficient - 1.0) > EPSILON) {
            parts.add(MathNode.Number(coefficient))
        }

        for ((varName, exponent) in variables.toSortedMap()) {
            if (abs(exponent) < EPSILON) continue

            val varNode = MathNode.Variable(varName)
            if (abs(exponent - 1.0) < EPSILON) {
                parts.add(varNode)
            } else {
                parts.add(MathNode.BinaryOp(Operator.POWER, varNode, MathNode.Number(exponent)))
            }
        }

        parts.addAll(functions)
        parts.addAll(nestedExpressions)

        if (parts.isEmpty()) {
            return MathNode.Number(coefficient)
        }

        var result: MathNode = parts[0]
        for (i in 1 until parts.size) {
            result = MathNode.BinaryOp(Operator.MULTIPLY, result, parts[i])
        }

        Log.d(TAG, "toNode() 结果: $result")
        return result
    }

    fun getBaseKey(): String {
        val varPart = variables.toSortedMap().entries.joinToString("*") { (v, e) ->
            if (abs(e - 1.0) < EPSILON) v else "$v^${formatExponent(e)}"
        }

        val funcPart = functions.joinToString("*") { it.name }

        val nestedPart = nestedExpressions.joinToString("*") { "nested" }

        return listOf(varPart, funcPart, nestedPart)
            .filter { it.isNotEmpty() }
            .joinToString("*")
            .ifEmpty { "1" }
    }

    private fun formatExponent(e: Double): String {
        return if (e == e.toInt().toDouble()) {
            e.toInt().toString()
        } else {
            e.toString()
        }
    }

    override fun toString(): String {
        if (isZero()) return "0"

        val absCoeff = abs(coefficient)
        val sign = if (coefficient < 0) "-" else ""

        val coeffStr = when {
            abs(absCoeff - 1.0) < EPSILON -> sign
            else -> "$sign$absCoeff"
        }

        val baseStr = getBaseKey()

        return when {
            baseStr == "1" && coeffStr.isEmpty() -> "1"
            baseStr == "1" -> if (coeffStr == "-") "-1" else coeffStr
            coeffStr.isEmpty() -> baseStr
            coeffStr == "-" -> "-$baseStr"
            else -> "$coeffStr*$baseStr"
        }
    }
}