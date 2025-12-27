// app/src/main/java/com/mathsnew/mathsnew/newsimplified/MathTerm.kt
// 数学项的规范化表示

package com.mathsnew.mathsnew.newsimplified

import com.mathsnew.mathsnew.*
import kotlin.math.abs

data class MathTerm(
    val coefficient: Double,
    val variables: Map<String, Double>,
    val functions: Map<String, Double>,
    val nestedExpressions: List<MathNode.BinaryOp>
) {
    companion object {
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
    }

    fun isZero(): Boolean = abs(coefficient) < EPSILON

    fun isConstant(): Boolean = variables.isEmpty() && functions.isEmpty() && nestedExpressions.isEmpty()

    fun isSimilarTo(other: MathTerm): Boolean {
        if (variables != other.variables) return false

        if (functions.keys != other.functions.keys) return false

        if (nestedExpressions.size != other.nestedExpressions.size) return false
        if (nestedExpressions.zip(other.nestedExpressions).any { (e1, e2) -> e1.toString() != e2.toString() }) {
            return false
        }

        return true
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

        val parts = mutableListOf<MathNode>()

        if (abs(coefficient - 1.0) > EPSILON && abs(coefficient + 1.0) > EPSILON) {
            parts.add(MathNode.Number(abs(coefficient)))
        }

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

        if (abs(coefficient - 1.0) > EPSILON && abs(coefficient + 1.0) > EPSILON) {
            result = MathNode.BinaryOp(Operator.MULTIPLY, MathNode.Number(abs(coefficient)), result)
        }

        if (coefficient < 0) {
            result = MathNode.BinaryOp(
                Operator.MULTIPLY,
                MathNode.Number(-1.0),
                result
            )
        }

        return result
    }

    fun getBaseKey(): String {
        val varPart = variables.toSortedMap().entries.joinToString("*") { (v, e) ->
            if (abs(e - 1.0) < EPSILON) v else "$v^$e"
        }

        val funcPart = functions.entries.sortedBy { it.key }.joinToString("*") { (f, e) ->
            if (abs(e - 1.0) < EPSILON) f else "$f^$e"
        }

        val nestedPart = nestedExpressions.joinToString("*") { it.toString() }

        return listOf(varPart, funcPart, nestedPart)
            .filter { it.isNotEmpty() }
            .joinToString("*")
            .ifEmpty { "1" }
    }

    override fun toString(): String {
        if (isZero()) return "0"

        val coeffStr = when {
            abs(coefficient - 1.0) < EPSILON -> ""
            abs(coefficient + 1.0) < EPSILON -> "-"
            else -> coefficient.toString()
        }

        val baseStr = getBaseKey()

        return if (baseStr == "1") {
            coeffStr.ifEmpty { "1" }
        } else if (coeffStr.isEmpty()) {
            baseStr
        } else {
            "$coeffStr*$baseStr"
        }
    }
}