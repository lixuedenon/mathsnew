// app/src/main/java/com/mathsnew/mathsnew/newsimplified/FunctionKey.kt
// 函数因子的结构化键 - 用于支持基于AST的函数等价性判断

package com.mathsnew.mathsnew.newsimplified

import com.mathsnew.mathsnew.*
import android.util.Log
import kotlin.math.abs

/**
 * 函数因子的结构化键
 *
 * 用途：
 * 1. 替代 MathTerm 中的字符串函数键
 * 2. 支持基于 AST 的等价性判断
 * 3. 支持函数因子的提取和合并
 *
 * 示例：
 * - exp(x-9) 和 exp((x-9)) → 等价
 * - sin(2x) 和 sin(x+x) → 需要规范化后才能判断（暂不支持）
 *
 * 设计理念：
 * - 使用 AST 结构而非字符串比较
 * - 实现 equals() 和 hashCode() 以支持作为 Map 的键
 * - 提供双向转换：MathNode.Function ↔ FunctionKey
 */
data class FunctionKey(
    val name: String,           // 函数名：sin, cos, exp, ln 等
    val argument: MathNode      // 参数的 AST 结构
) {
    companion object {
        private const val TAG = "FunctionKey"
        private const val EPSILON = 1e-10

        /**
         * 从 MathNode.Function 创建 FunctionKey
         *
         * @param funcNode 函数节点
         * @return FunctionKey 实例
         */
        fun from(funcNode: MathNode.Function): FunctionKey {
            return FunctionKey(funcNode.name, funcNode.argument)
        }

        /**
         * 从字符串解析 FunctionKey（兼容旧代码）
         *
         * 格式：funcName(arg)
         * 例如：exp(x-9) → FunctionKey("exp", ...)
         *
         * @param str 函数字符串
         * @return FunctionKey 实例，解析失败返回 null
         */
        fun fromString(str: String): FunctionKey? {
            val regex = """(\w+)\((.*)\)""".toRegex()
            val match = regex.matchEntire(str) ?: return null

            val (name, argStr) = match.destructured

            val parser = ExpressionParser()
            val argNode = try {
                parser.parse(argStr)
            } catch (e: Exception) {
                Log.e(TAG, "解析失败: $str", e)
                return null
            }

            return FunctionKey(name, argNode)
        }
    }

    /**
     * 核心方法：基于 AST 的等价性判断
     *
     * 策略：
     * 1. 函数名必须相同
     * 2. 参数的 AST 结构必须等价
     * 3. 使用递归比较 AST 节点
     *
     * @param other 待比较对象
     * @return true 表示等价，false 表示不等价
     */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is FunctionKey) return false

        // 函数名必须相同
        if (name != other.name) {
            return false
        }

        // 参数 AST 等价性判断
        val result = isASTEquivalent(argument, other.argument)

        Log.d(TAG, "等价性判断: $name(${argument}) vs $name(${other.argument}) → $result")

        return result
    }

    /**
     * 生成哈希码
     *
     * 使用规范化字符串生成哈希，确保等价的函数产生相同的哈希值
     *
     * @return 哈希值
     */
    override fun hashCode(): Int {
        val canonicalArg = canonicalizeAST(argument)
        return 31 * name.hashCode() + canonicalArg.hashCode()
    }

    /**
     * 转换回 MathNode.Function
     *
     * @return 函数节点
     */
    fun toFunctionNode(): MathNode.Function {
        return MathNode.Function(name, argument)
    }

    /**
     * 转换为字符串（用于调试和日志）
     *
     * @return 字符串表示
     */
    override fun toString(): String {
        return "$name($argument)"
    }

    /**
     * 获取规范化字符串（用于去重和比较）
     *
     * @return 规范化字符串
     */
    fun toCanonicalString(): String {
        return "$name(${canonicalizeAST(argument)})"
    }

    /**
     * AST 等价性判断（递归）
     *
     * 策略：
     * 1. 类型必须相同
     * 2. 数字：比较值（误差范围内）
     * 3. 变量：比较名称
     * 4. 二元运算：运算符相同 + 左右子树等价
     * 5. 函数：函数名相同 + 参数等价
     *
     * @param node1 第一个节点
     * @param node2 第二个节点
     * @return true 表示等价
     */
    private fun isASTEquivalent(node1: MathNode, node2: MathNode): Boolean {
        return when {
            node1 is MathNode.Number && node2 is MathNode.Number -> {
                abs(node1.value - node2.value) < EPSILON
            }

            node1 is MathNode.Variable && node2 is MathNode.Variable -> {
                node1.name == node2.name
            }

            node1 is MathNode.Function && node2 is MathNode.Function -> {
                node1.name == node2.name &&
                isASTEquivalent(node1.argument, node2.argument)
            }

            node1 is MathNode.BinaryOp && node2 is MathNode.BinaryOp -> {
                node1.operator == node2.operator &&
                isASTEquivalent(node1.left, node2.left) &&
                isASTEquivalent(node1.right, node2.right)
            }

            else -> false
        }
    }

    /**
     * 规范化 AST 为字符串
     *
     * 目的：
     * 1. 统一格式（去括号）
     * 2. 用于 hashCode 和比较
     *
     * 规则：
     * - 数字：2.0 → 2
     * - 运算符：统一顺序
     * - 括号：最小化
     *
     * @param node AST 节点
     * @return 规范化字符串
     */
    private fun canonicalizeAST(node: MathNode): String {
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
                "${node.name}(${canonicalizeAST(node.argument)})"
            }

            is MathNode.BinaryOp -> {
                val left = canonicalizeAST(node.left)
                val right = canonicalizeAST(node.right)
                val op = when (node.operator) {
                    Operator.ADD -> "+"
                    Operator.SUBTRACT -> "-"
                    Operator.MULTIPLY -> "*"
                    Operator.DIVIDE -> "/"
                    Operator.POWER -> "^"
                }
                "($left$op$right)"
            }
        }
    }
}