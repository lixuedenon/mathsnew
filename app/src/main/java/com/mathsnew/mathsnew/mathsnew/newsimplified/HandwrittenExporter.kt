// app/src/main/java/com/mathsnew/mathsnew/newsimplified/HandwrittenExporter.kt
// 手写格式导出器 - 生成类似手写数学的文本格式

package com.mathsnew.mathsnew.newsimplified

import com.mathsnew.mathsnew.*
import android.util.Log
import kotlin.math.abs
import kotlin.math.max

/**
 * 手写格式导出器
 *
 * 功能：
 * 1. 将数学表达式转换为类似手写的文本格式
 * 2. 支持分数线显示
 * 3. 支持多种形式的对齐显示
 * 4. 使用 Unicode 字符增强可读性
 */
class HandwrittenExporter {

    companion object {
        private const val TAG = "HandwrittenExporter"

        // Unicode 数学符号
        private const val MULTIPLY = "×"
        private const val DOT = "·"
        private const val DIVIDE = "÷"

        // 上标数字
        private val SUPERSCRIPTS = mapOf(
            '0' to '⁰', '1' to '¹', '2' to '²', '3' to '³', '4' to '⁴',
            '5' to '⁵', '6' to '⁶', '7' to '⁷', '8' to '⁸', '9' to '⁹',
            '-' to '⁻', '+' to '⁺', '=' to '⁼', '(' to '⁽', ')' to '⁾'
        )

        // 下标数字（如果需要）
        private val SUBSCRIPTS = mapOf(
            '0' to '₀', '1' to '₁', '2' to '₂', '3' to '₃', '4' to '₄',
            '5' to '₅', '6' to '₆', '7' to '₇', '8' to '₈', '9' to '₉'
        )
    }

    /**
     * 导出完整的计算结果（从 SimplificationFormsV2 直接获取）
     *
     * @param original 原函数表达式
     * @param firstDerivativeForms 一阶导数所有形式
     * @param secondDerivativeForms 二阶导数所有形式
     * @return 手写格式的文本
     */
    fun exportCalculationResult(
        original: String,
        firstDerivativeForms: SimplificationFormsV2,
        secondDerivativeForms: SimplificationFormsV2?
    ): String {
        val sb = StringBuilder()

        // 原函数
        sb.append("f(x) = $original\n\n")

        // 一阶导数 - 从 forms 获取所有形式
        sb.append(formatDerivativeFromForms("f'(x)", firstDerivativeForms))
        sb.append("\n")

        // 二阶导数
        if (secondDerivativeForms != null) {
            sb.append(formatDerivativeFromForms("f''(x)", secondDerivativeForms))
        }

        return sb.toString()
    }

    /**
     * 格式化导数（从 SimplificationFormsV2）
     */
    private fun formatDerivativeFromForms(
        label: String,
        forms: SimplificationFormsV2
    ): String {
        Log.d(TAG, "========== formatDerivativeFromForms START ==========")
        Log.d(TAG, "label: $label")
        Log.d(TAG, "forms count: ${forms.forms.size}")

        val sb = StringBuilder()

        if (forms.forms.isEmpty()) {
            Log.d(TAG, "forms is empty, return")
            return ""
        }

        // 第一行：第一种形式（通常是展开式）
        val firstForm = forms.forms[0]
        val firstExpr = nodeToHandwritten(firstForm.expression)

        Log.d(TAG, "第一种形式:")
        Log.d(TAG, "  表达式: ${firstForm.expression}")
        Log.d(TAG, "  转换后: $firstExpr")
        Log.d(TAG, "  包含分数: ${containsFraction(firstForm.expression)}")

        if (containsFraction(firstForm.expression)) {
            Log.d(TAG, "→ 调用 renderNodeAsFraction")
            sb.append(renderNodeAsFraction(label, firstForm.expression))
        } else {
            Log.d(TAG, "→ 普通显示")
            sb.append("$label = $firstExpr\n")
        }

        // 其他形式
        val indent = " ".repeat(label.length + 3)
        for (i in 1 until forms.forms.size) {
            val form = forms.forms[i]
            val expr = nodeToHandwritten(form.expression)

            Log.d(TAG, "第${i+1}种形式:")
            Log.d(TAG, "  表达式: ${form.expression}")
            Log.d(TAG, "  包含分数: ${containsFraction(form.expression)}")

            if (containsFraction(form.expression)) {
                Log.d(TAG, "→ 调用 renderNodeAsFraction")
                sb.append(renderNodeAsFraction("", form.expression, indent))
            } else {
                Log.d(TAG, "→ 普通显示")
                sb.append("$indent= $expr\n")
            }
        }

        Log.d(TAG, "========== formatDerivativeFromForms END ==========")
        return sb.toString()
    }

    /**
     * 检查节点是否包含分数
     */
    private fun containsFraction(node: MathNode): Boolean {
        return when (node) {
            is MathNode.BinaryOp -> {
                if (node.operator == Operator.DIVIDE) {
                    true
                } else {
                    containsFraction(node.left) || containsFraction(node.right)
                }
            }
            is MathNode.Function -> containsFraction(node.argument)
            else -> false
        }
    }

    /**
     * 将 MathNode 渲染为带分数线的多行文本
     */
    private fun renderNodeAsFraction(
        label: String,
        node: MathNode,
        indent: String = ""
    ): String {
        Log.d(TAG, "---------- renderNodeAsFraction START ----------")
        Log.d(TAG, "label: '$label'")
        Log.d(TAG, "node: $node")

        // 寻找最外层的分数结构
        val fractionInfo = findOutermostFraction(node)

        Log.d(TAG, "fractionInfo: $fractionInfo")

        return if (fractionInfo != null) {
            val (prefix, numerator, denominator) = fractionInfo
            Log.d(TAG, "找到分数结构:")
            Log.d(TAG, "  prefix: $prefix")
            Log.d(TAG, "  numerator: $numerator")
            Log.d(TAG, "  denominator: $denominator")
            Log.d(TAG, "→ 调用 renderMultilineFractionFromNodes")
            renderMultilineFractionFromNodes(label, prefix, numerator, denominator, indent)
        } else {
            Log.d(TAG, "未找到分数结构，普通显示")
            // 没有分数，普通显示
            val expr = nodeToHandwritten(node)
            if (label.isNotEmpty()) {
                "$label = $expr\n"
            } else {
                "$indent= $expr\n"
            }
        }
    }

    /**
     * 找到最外层的分数（处理 prefix + 分数 的情况）
     */
    private fun findOutermostFraction(node: MathNode): Triple<MathNode?, MathNode, MathNode>? {
        return when (node) {
            // 情况1: 纯分数 a/b
            is MathNode.BinaryOp -> {
                if (node.operator == Operator.DIVIDE) {
                    Triple(null, node.left, node.right)
                } else if (node.operator == Operator.ADD || node.operator == Operator.SUBTRACT) {
                    // 情况2: prefix + 分数 或 prefix - 分数
                    if (node.right is MathNode.BinaryOp &&
                        (node.right as MathNode.BinaryOp).operator == Operator.DIVIDE) {
                        Triple(node.left, (node.right as MathNode.BinaryOp).left, (node.right as MathNode.BinaryOp).right)
                    } else {
                        null
                    }
                } else {
                    null
                }
            }
            else -> null
        }
    }

    /**
     * 从 MathNode 渲染多行分数
     * 支持长表达式智能换行
     */
    private fun renderMultilineFractionFromNodes(
        label: String,
        prefix: MathNode?,
        numerator: MathNode,
        denominator: MathNode,
        indent: String
    ): String {
        Log.d(TAG, "********** renderMultilineFractionFromNodes START **********")

        val sb = StringBuilder()

        val numStr = nodeToHandwritten(numerator)
        val denStr = nodeToHandwritten(denominator)
        val prefixStr = if (prefix != null) nodeToHandwritten(prefix) + " + " else ""

        Log.d(TAG, "分子字符串长度: ${numStr.length}")
        Log.d(TAG, "分子内容: $numStr")
        Log.d(TAG, "分母内容: $denStr")

        // ✅ 智能判断：如果分子过长，尝试拆分
        val MAX_LINE_WIDTH = 60  // 单行最大字符数

        if (numStr.length > MAX_LINE_WIDTH) {
            Log.d(TAG, "分子过长 (${numStr.length} > $MAX_LINE_WIDTH)，尝试拆分")
            // 尝试拆分分子
            return renderLongFractionWithSplit(label, prefix, numerator, denominator, indent)
        }

        Log.d(TAG, "分子长度正常，使用普通渲染")

        // 普通分数渲染（分子不太长）
        val fractionWidth = max(numStr.length, denStr.length)
        val fractionLine = "─".repeat(fractionWidth)

        val labelWidth = if (label.isNotEmpty()) label.length + 3 else indent.length + 2

        // 第1行：标签 + 前置项 + 分子
        val line1 = StringBuilder()
        if (label.isNotEmpty()) {
            line1.append("$label = ")
        } else {
            line1.append("$indent= ")
        }
        line1.append(prefixStr)

        val numPadding = (fractionWidth - numStr.length) / 2
        line1.append(" ".repeat(numPadding))
        line1.append(numStr)
        sb.append(line1.toString()).append("\n")

        // 第2行：分数线
        val line2 = StringBuilder()
        line2.append(" ".repeat(labelWidth))
        line2.append(" ".repeat(prefixStr.length))
        line2.append(fractionLine)
        sb.append(line2.toString()).append("\n")

        // 第3行：分母
        val line3 = StringBuilder()
        line3.append(" ".repeat(labelWidth))
        line3.append(" ".repeat(prefixStr.length))
        val denPadding = (fractionWidth - denStr.length) / 2
        line3.append(" ".repeat(denPadding))
        line3.append(denStr)
        sb.append(line3.toString()).append("\n")

        Log.d(TAG, "********** renderMultilineFractionFromNodes END **********")
        return sb.toString()
    }

    /**
     * 渲染长分数：分子拆分成多项
     */
    private fun renderLongFractionWithSplit(
        label: String,
        prefix: MathNode?,
        numerator: MathNode,
        denominator: MathNode,
        indent: String
    ): String {
        val sb = StringBuilder()

        // 尝试将分子拆分成加法项
        val terms = splitIntoTerms(numerator)

        if (terms.size <= 1) {
            // 无法拆分，回退到简化显示
            return renderSimplifiedFraction(label, prefix, numerator, denominator, indent)
        }

        // 渲染第一项
        val prefixStr = if (prefix != null) nodeToHandwritten(prefix) + " + " else ""
        val labelWidth = if (label.isNotEmpty()) label.length + 3 else indent.length + 2

        // 收集所有行的文本（用于计算宽度）
        val lines = mutableListOf<String>()

        val firstTerm = nodeToHandwritten(terms[0])
        lines.add(firstTerm)

        // 第1行：标签 + 前置项 + 第一项
        if (label.isNotEmpty()) {
            sb.append("$label = ")
        } else {
            sb.append("$indent= ")
        }
        sb.append(prefixStr)
        sb.append(firstTerm)

        // 后续项，每项一行
        for (i in 1 until terms.size) {
            val term = nodeToHandwritten(terms[i])
            val line = "+ $term"
            lines.add(line)

            sb.append("\n")
            sb.append(" ".repeat(labelWidth + prefixStr.length))
            sb.append(line)
        }

        // 计算分数线宽度（取所有行的最大宽度）
        val denStr = nodeToHandwritten(denominator)
        val maxTermWidth = lines.maxOfOrNull { it.length } ?: 0

        val fractionWidth = maxOf(maxTermWidth, denStr.length)

        Log.d(TAG, "分数线宽度计算:")
        Log.d(TAG, "  最长分子行宽度: $maxTermWidth")
        Log.d(TAG, "  分母宽度: ${denStr.length}")
        Log.d(TAG, "  分数线宽度: $fractionWidth")
        Log.d(TAG, "  分母填充: ${(fractionWidth - denStr.length) / 2}")

        // 分数线
        sb.append("\n")
        sb.append(" ".repeat(labelWidth + prefixStr.length))
        sb.append("─".repeat(fractionWidth))

        // 分母（居中）
        sb.append("\n")
        sb.append(" ".repeat(labelWidth + prefixStr.length))
        val denPadding = (fractionWidth - denStr.length) / 2
        sb.append(" ".repeat(denPadding))
        sb.append(denStr)
        sb.append("\n")

        return sb.toString()
    }

    /**
     * 简化显示：对于过长的表达式，使用符号表示
     */
    private fun renderSimplifiedFraction(
        label: String,
        prefix: MathNode?,
        numerator: MathNode,
        denominator: MathNode,
        indent: String
    ): String {
        val sb = StringBuilder()

        val prefixStr = if (prefix != null) nodeToHandwritten(prefix) + " + " else ""

        if (label.isNotEmpty()) {
            sb.append("$label = ")
        } else {
            sb.append("$indent= ")
        }
        sb.append(prefixStr)
        sb.append("(复杂表达式)")
        sb.append("\n")
        sb.append(" ".repeat(if (label.isNotEmpty()) label.length + 3 else indent.length + 2))
        sb.append(" ".repeat(prefixStr.length))
        sb.append("─────────────")
        sb.append("\n")
        sb.append(" ".repeat(if (label.isNotEmpty()) label.length + 3 else indent.length + 2))
        sb.append(" ".repeat(prefixStr.length))
        sb.append(nodeToHandwritten(denominator))
        sb.append("\n")

        return sb.toString()
    }

    /**
     * 将表达式拆分成加法项
     */
    private fun splitIntoTerms(node: MathNode): List<MathNode> {
        val terms = mutableListOf<MathNode>()

        fun collectTerms(n: MathNode) {
            when (n) {
                is MathNode.BinaryOp -> {
                    if (n.operator == Operator.ADD) {
                        collectTerms(n.left)
                        collectTerms(n.right)
                    } else {
                        terms.add(n)
                    }
                }
                else -> terms.add(n)
            }
        }

        collectTerms(node)
        return terms
    }

    /**
     * 将 MathNode 转换为手写字符串
     */
    private fun nodeToHandwritten(node: MathNode): String {
        return when (node) {
            is MathNode.Number -> {
                val value = node.value
                if (abs(value - value.toLong().toDouble()) < 1e-10) {
                    value.toLong().toString()
                } else {
                    value.toString()
                }
            }

            is MathNode.Variable -> node.name

            is MathNode.Function -> {
                val argStr = nodeToHandwritten(node.argument)
                "${node.name}($argStr)"
            }

            is MathNode.BinaryOp -> {
                val left = nodeToHandwritten(node.left)
                val right = nodeToHandwritten(node.right)

                when (node.operator) {
                    Operator.ADD -> "$left+$right"
                    Operator.SUBTRACT -> "$left-$right"
                    Operator.MULTIPLY -> "$left$DOT$right"
                    Operator.DIVIDE -> "($left)/($right)"
                    Operator.POWER -> {
                        // 尝试转换为上标
                        if (node.right is MathNode.Number) {
                            val exp = (node.right as MathNode.Number).value
                            if (exp == exp.toLong().toDouble() && exp >= 0 && exp <= 9) {
                                val superExp = SUPERSCRIPTS[exp.toLong().toString()[0]]
                                if (superExp != null) {
                                    return "$left$superExp"
                                }
                            }
                        }
                        "$left^$right"
                    }
                }
            }
        }
    }



}