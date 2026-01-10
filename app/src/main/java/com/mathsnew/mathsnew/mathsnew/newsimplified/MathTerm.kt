package com.mathsnew.mathsnew.newsimplified

import android.util.Log
import com.mathsnew.mathsnew.*
import kotlin.math.abs

/**
 * 数学项的数据结构
 *
 * 表示形式：coefficient × variables × functions × nestedExpressions
 *
 * 例如：
 * - 3x²y  → MathTerm(3.0, {"x": 2.0, "y": 1.0}, {}, [])
 * - 2sin(x)exp(y) → MathTerm(2.0, {}, {FunctionKey("sin", x): 1.0, FunctionKey("exp", y): 1.0}, [])
 * - 5x·(a+b) → MathTerm(5.0, {"x": 1.0}, {}, [a+b])
 *
 * ⚠️ Phase 1 修改：functions 字段从 Map<String, Double> 改为 Map<FunctionKey, Double>
 */
data class MathTerm(
    val coefficient: Double,
    val variables: Map<String, Double>,
    val functions: Map<FunctionKey, Double>,  // ✅ 修改：使用 FunctionKey
    val nestedExpressions: List<MathNode.BinaryOp>
) {
    companion object {
        private const val TAG = "MathTerm"
        private const val EPSILON = 1e-10

        /**
         * 从 MathNode 创建 MathTerm
         */
        fun fromNode(node: MathNode): MathTerm {
            Log.d(TAG, "fromNode: 输入节点 = $node")

            return when (node) {
                is MathNode.Number -> {
                    Log.d(TAG, "  识别为数字: ${node.value}")
                    MathTerm(node.value, emptyMap(), emptyMap(), emptyList())
                }

                is MathNode.Variable -> {
                    Log.d(TAG, "  识别为变量: ${node.name}")
                    MathTerm(1.0, mapOf(node.name to 1.0), emptyMap(), emptyList())
                }

                // ✅ 修改：Function 节点使用 FunctionKey
                is MathNode.Function -> {
                    val funcKey = FunctionKey.from(node)
                    Log.d(TAG, "  识别为函数: ${funcKey.toCanonicalString()}")
                    MathTerm(
                        coefficient = 1.0,
                        variables = emptyMap(),
                        functions = mapOf(funcKey to 1.0),
                        nestedExpressions = emptyList()
                    )
                }

                is MathNode.BinaryOp -> {
                    when (node.operator) {
                        Operator.MULTIPLY -> {
                            Log.d(TAG, "  识别为乘法: ${node.left} × ${node.right}")
                            val left = fromNode(node.left)
                            val right = fromNode(node.right)
                            left * right
                        }

                        Operator.POWER -> {
                            val base = node.left
                            val exponent = node.right

                            if (exponent !is MathNode.Number) {
                                Log.d(TAG, "  幂次不是数字，作为嵌套表达式")
                                return MathTerm(1.0, emptyMap(), emptyMap(), listOf(node))
                            }

                            when (base) {
                                is MathNode.Variable -> {
                                    Log.d(TAG, "  识别为变量幂: ${base.name}^${exponent.value}")
                                    MathTerm(
                                        coefficient = 1.0,
                                        variables = mapOf(base.name to exponent.value),
                                        functions = emptyMap(),
                                        nestedExpressions = emptyList()
                                    )
                                }

                                // ✅ 修改：Function 的幂次使用 FunctionKey
                                is MathNode.Function -> {
                                    val funcKey = FunctionKey.from(base)
                                    Log.d(TAG, "  识别为函数幂: ${funcKey.toCanonicalString()}^${exponent.value}")
                                    MathTerm(
                                        coefficient = 1.0,
                                        variables = emptyMap(),
                                        functions = mapOf(funcKey to exponent.value),
                                        nestedExpressions = emptyList()
                                    )
                                }

                                is MathNode.Number -> {
                                    Log.d(TAG, "  识别为数字幂: ${base.value}^${exponent.value}")
                                    val result = Math.pow(base.value, exponent.value)
                                    MathTerm(result, emptyMap(), emptyMap(), emptyList())
                                }

                                else -> {
                                    Log.d(TAG, "  复杂幂次，作为嵌套表达式")
                                    MathTerm(1.0, emptyMap(), emptyMap(), listOf(node))
                                }
                            }
                        }

                        else -> {
                            Log.d(TAG, "  其他运算符，作为嵌套表达式")
                            MathTerm(1.0, emptyMap(), emptyMap(), listOf(node))
                        }
                    }
                }
            }
        }
    }

    /**
     * 乘法运算
     */
    operator fun times(other: MathTerm): MathTerm {
        Log.d(TAG, "times: this=$this, other=$other")

        val newCoefficient = this.coefficient * other.coefficient
        Log.d(TAG, "  系数: ${this.coefficient} × ${other.coefficient} = $newCoefficient")

        // 合并变量
        val newVariables = mutableMapOf<String, Double>()
        for ((variable, exponent) in this.variables) {
            newVariables[variable] = exponent
        }
        for ((variable, exponent) in other.variables) {
            newVariables[variable] = (newVariables[variable] ?: 0.0) + exponent
        }
        Log.d(TAG, "  合并变量: $newVariables")

        // ✅ 修改：合并函数时使用 FunctionKey
        val newFunctions = mutableMapOf<FunctionKey, Double>()
        for ((funcKey, exponent) in this.functions) {
            newFunctions[funcKey] = exponent
        }
        for ((funcKey, exponent) in other.functions) {
            val currentExp = newFunctions[funcKey] ?: 0.0
            val newExp = currentExp + exponent
            newFunctions[funcKey] = newExp
            Log.d(TAG, "  合并函数 ${funcKey.toCanonicalString()}: $currentExp + $exponent = $newExp")
        }

        // 合并嵌套表达式
        val newNested = this.nestedExpressions + other.nestedExpressions
        Log.d(TAG, "  嵌套表达式数量: ${this.nestedExpressions.size} + ${other.nestedExpressions.size} = ${newNested.size}")

        val result = MathTerm(newCoefficient, newVariables, newFunctions, newNested)
        Log.d(TAG, "  结果: $result")
        return result
    }

    /**
     * 加法运算（同类项）
     */
    operator fun plus(other: MathTerm): MathTerm {
        // 验证是否为同类项
        if (this.getBaseKey() != other.getBaseKey()) {
            throw IllegalArgumentException("不能对非同类项进行加法: ${this.getBaseKey()} vs ${other.getBaseKey()}")
        }

        val newCoefficient = this.coefficient + other.coefficient
        Log.d(TAG, "✅ 合并: ${this.coefficient} + ${other.coefficient} = $newCoefficient")

        return MathTerm(
            coefficient = newCoefficient,
            variables = this.variables,
            functions = this.functions,
            nestedExpressions = this.nestedExpressions
        )
    }

    /**
     * 转换回 MathNode
     */
    fun toNode(): MathNode {
        val parts = mutableListOf<MathNode>()

        // 1. 系数（如果不是1）
        if (abs(coefficient - 1.0) > EPSILON) {
            val coeffNode = if (abs(coefficient - coefficient.toLong().toDouble()) < EPSILON) {
                MathNode.Number(coefficient.toLong().toInt())
            } else {
                MathNode.Number(coefficient)
            }
            parts.add(coeffNode)
        } else if (coefficient < 0) {
            val coeffNode = if (abs(coefficient - coefficient.toLong().toDouble()) < EPSILON) {
                MathNode.Number(coefficient.toLong().toInt())
            } else {
                MathNode.Number(coefficient)
            }
            parts.add(coeffNode)
        }

        // 2. 变量
        for ((variable, exponent) in variables.toList().sortedBy { it.first }) {
            if (abs(exponent) < EPSILON) continue

            val varNode = MathNode.Variable(variable)
            val withExponent = if (abs(exponent - 1.0) < EPSILON) {
                varNode
            } else {
                val expNumber = if (abs(exponent - exponent.toLong().toDouble()) < EPSILON) {
                    MathNode.Number(exponent.toLong().toInt())
                } else {
                    MathNode.Number(exponent)
                }
                MathNode.BinaryOp(Operator.POWER, varNode, expNumber)
            }
            parts.add(withExponent)
        }

        // ✅ 修改：使用 FunctionKey 重建函数节点
        for ((funcKey, exponent) in functions.toList().sortedBy { it.first.toCanonicalString() }) {
            if (abs(exponent) < EPSILON) continue

            val funcNode = funcKey.toFunctionNode()

            val withExponent = if (abs(exponent - 1.0) < EPSILON) {
                funcNode
            } else {
                val expNumber = if (abs(exponent - exponent.toLong().toDouble()) < EPSILON) {
                    MathNode.Number(exponent.toLong().toInt())
                } else {
                    MathNode.Number(exponent)
                }
                MathNode.BinaryOp(Operator.POWER, funcNode, expNumber)
            }
            parts.add(withExponent)
        }

        // 4. 嵌套表达式
        parts.addAll(nestedExpressions)

        // 5. 组合所有部分
        return when {
            parts.isEmpty() -> MathNode.Number(coefficient)
            parts.size == 1 && abs(coefficient - 1.0) < EPSILON -> parts[0]
            else -> {
                var result = parts[0]
                for (i in 1 until parts.size) {
                    result = MathNode.BinaryOp(Operator.MULTIPLY, result, parts[i])
                }
                result
            }
        }
    }

    /**
     * 获取项的 base key（用于判断是否为同类项）
     */
    fun getBaseKey(): String {
        val key = TermKey.generate(this)
        Log.d(TAG, "getBaseKey: coeff=$coefficient → key='$key'")
        return key
    }

    /**
     * 判断两个项是否为同类项
     */
    fun isSimilarTo(other: MathTerm): Boolean {
        return this.getBaseKey() == other.getBaseKey()
    }

    /**
     * 合并同类项（ExpressionCanonicalizer 需要此方法）
     */
    fun mergeWith(other: MathTerm): MathTerm? {
        if (!isSimilarTo(other)) return null

        val newCoeff = this.coefficient + other.coefficient
        Log.d(TAG, "✅ 合并: ${this.coefficient} + ${other.coefficient} = $newCoeff")

        return MathTerm(
            coefficient = newCoeff,
            variables = this.variables,
            functions = this.functions,
            nestedExpressions = this.nestedExpressions
        )
    }

    /**
     * 判断项是否为零
     */
    fun isZero(): Boolean {
        return abs(coefficient) < EPSILON
    }

    /**
     * 判断项是否为常数项
     */
    fun isConstant(): Boolean {
        return variables.isEmpty() && functions.isEmpty() && nestedExpressions.isEmpty()
    }

    /**
     * 获取项的 GCD（最大公约数）
     */
    fun gcd(other: MathTerm): MathTerm {
        // 系数的 GCD
        val gcdCoeff = gcd(abs(this.coefficient), abs(other.coefficient))

        // 变量的最小指数
        val gcdVars = mutableMapOf<String, Double>()
        for ((variable, exp1) in this.variables) {
            val exp2 = other.variables[variable]
            if (exp2 != null) {
                gcdVars[variable] = minOf(exp1, exp2)
            }
        }

        // ✅ 修改：函数的最小指数使用 FunctionKey
        val gcdFuncs = mutableMapOf<FunctionKey, Double>()
        for ((funcKey, exp1) in this.functions) {
            val exp2 = other.functions[funcKey]
            if (exp2 != null) {
                gcdFuncs[funcKey] = minOf(exp1, exp2)
            }
        }

        return MathTerm(gcdCoeff, gcdVars, gcdFuncs, emptyList())
    }

    /**
     * 除以另一个项（用于提取公因子后）
     */
    operator fun div(divisor: MathTerm): MathTerm {
        val newCoeff = this.coefficient / divisor.coefficient

        val newVars = this.variables.toMutableMap()
        for ((variable, exponent) in divisor.variables) {
            val currentExp = newVars[variable] ?: 0.0
            val newExp = currentExp - exponent
            if (abs(newExp) < EPSILON) {
                newVars.remove(variable)
            } else {
                newVars[variable] = newExp
            }
        }

        // ✅ 修改：函数的除法使用 FunctionKey
        val newFuncs = this.functions.toMutableMap()
        for ((funcKey, exponent) in divisor.functions) {
            val currentExp = newFuncs[funcKey] ?: 0.0
            val newExp = currentExp - exponent
            if (abs(newExp) < EPSILON) {
                newFuncs.remove(funcKey)
            } else {
                newFuncs[funcKey] = newExp
            }
        }

        return MathTerm(newCoeff, newVars, newFuncs, this.nestedExpressions)
    }

    /**
     * 计算两个 Double 的 GCD
     */
    private fun gcd(a: Double, b: Double): Double {
        if (abs(a) < EPSILON) return abs(b)
        if (abs(b) < EPSILON) return abs(a)

        // 对于浮点数，简化为 1.0
        return 1.0
    }

    override fun toString(): String {
        val parts = mutableListOf<String>()

        if (abs(coefficient - 1.0) > EPSILON || (variables.isEmpty() && functions.isEmpty() && nestedExpressions.isEmpty())) {
            parts.add(coefficient.toString())
        }

        for ((variable, exponent) in variables.toList().sortedBy { it.first }) {
            if (abs(exponent - 1.0) < EPSILON) {
                parts.add(variable)
            } else {
                parts.add("$variable^$exponent")
            }
        }

        // ✅ 修改：toString 使用 FunctionKey
        for ((funcKey, exponent) in functions.toList().sortedBy { it.first.toCanonicalString() }) {
            if (abs(exponent - 1.0) < EPSILON) {
                parts.add(funcKey.toString())
            } else {
                parts.add("${funcKey}^$exponent")
            }
        }

        for (nested in nestedExpressions) {
            parts.add("($nested)")
        }

        return if (parts.isEmpty()) "1" else parts.joinToString("·")
    }
}