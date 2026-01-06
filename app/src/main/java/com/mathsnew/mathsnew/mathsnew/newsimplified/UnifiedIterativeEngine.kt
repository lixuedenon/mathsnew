// app/src/main/java/com/mathsnew/mathsnew/newsimplified/UnifiedIterativeEngine.kt
// 统一迭代引擎（微调：移除 normalizeMultiplicationOrder，简化逻辑）

package com.mathsnew.mathsnew.newsimplified

import com.mathsnew.mathsnew.*
import android.util.Log
import kotlin.math.abs
import kotlin.math.pow

class UnifiedIterativeEngine {

    companion object {
        private const val TAG = "UnifiedEngine"
        private const val MAX_ITERATIONS = 10
        private const val EPSILON = 1e-10
    }

    private val canonicalizer = ExpressionCanonicalizer()
    private val trigSimplifier = TrigSimplifier
    private val formGenerator = FormGenerator()

    fun generateMultipleForms(input: MathNode): SimplificationFormsV2 {
        Log.d(TAG, "========== 开始生成多种形式 ==========")
        Log.d(TAG, "输入: $input")

        val forms = mutableListOf<SimplifiedForm>()
        val seen = mutableSetOf<String>()

        val expanded = applyExpandStrategy(input)
        addIfUnique(expanded, SimplificationType.EXPANDED, "展开形式", forms, seen)

        val trigForm = applyTrigStrategy(input)
        addIfUnique(trigForm, SimplificationType.STRUCTURAL, "三角化简", forms, seen)

        val factored = applyFactorStrategy(input)
        addIfUnique(factored, SimplificationType.FACTORED, "因式分解", forms, seen)

        val fractionForm = applyFractionStrategy(input)
        addIfUnique(fractionForm, SimplificationType.FACTORED, "分数约分", forms, seen)

        val fullForm = applyFullStrategy(input)
        addIfUnique(fullForm, SimplificationType.FACTORED, "完全化简", forms, seen)

        ensureMinimumForms(forms, input, seen)

        Log.d(TAG, "共生成 ${forms.size} 种不同形式")
        Log.d(TAG, "========== 形式生成完成 ==========")

        return SimplificationFormsV2(forms)
    }

    fun iterativeSimplify(input: MathNode): MathNode {
        Log.d(TAG, "========== 开始迭代化简 ==========")
        Log.d(TAG, "输入: $input")

        var current = input
        var totalChanges = 0

        for (iteration in 1..MAX_ITERATIONS) {
            Log.d(TAG, "---------- 第 $iteration 轮 ----------")

            val before = current.toString()
            current = applySingleRound(current)
            val after = current.toString()

            if (before == after) {
                Log.d(TAG, "本轮无变化，化简收敛")
                break
            }

            totalChanges++
            Log.d(TAG, "本轮结果: $current")
        }

        Log.d(TAG, "迭代完成，共 $totalChanges 轮有变化")
        Log.d(TAG, "最终结果: $current")
        Log.d(TAG, "========== 化简完成 ==========")

        return current
    }

    private fun applySingleRound(node: MathNode): MathNode {
        var current = node

        current = tryApply("规范化", current) {
            canonicalizer.canonicalize(it)
        }

        current = tryApply("常数折叠", current) { constantFolding(it) }
        current = tryApply("零元素", current) { removeZeroTerms(it) }
        current = tryApply("单位元素", current) { removeOneFactors(it) }

        current = tryApply("三角化简", current) {
            trigSimplifier.simplify(it)
        }

        current = tryApply("幂化简", current) { simplifyPowers(it) }

        return current
    }

    private fun applyExpandStrategy(input: MathNode): MathNode {
        Log.d(TAG, "应用策略：展开形式")

        var current = input

        for (iteration in 1..MAX_ITERATIONS) {
            val before = current.toString()

            current = canonicalizer.canonicalize(current)
            current = constantFolding(current)
            current = removeZeroTerms(current)
            current = removeOneFactors(current)

            if (before == current.toString()) break
        }

        return current
    }

    private fun applyTrigStrategy(input: MathNode): MathNode {
        Log.d(TAG, "应用策略：三角化简")

        var current = input

        for (iteration in 1..MAX_ITERATIONS) {
            val before = current.toString()

            current = canonicalizer.canonicalize(current)
            current = constantFolding(current)
            current = removeZeroTerms(current)
            current = removeOneFactors(current)
            current = trigSimplifier.simplify(current)

            if (before == current.toString()) break
        }

        return current
    }

    private fun applyFactorStrategy(input: MathNode): MathNode {
        Log.d(TAG, "应用策略：因式分解")

        var current = input

        current = canonicalizer.canonicalize(current)
        current = trigSimplifier.simplify(current)

        if (current is MathNode.BinaryOp && current.operator == Operator.DIVIDE) {
            val forms = formGenerator.generateAllForms(current)

            for (form in forms.forms) {
                if (form.type == SimplificationType.FACTORED &&
                    form.description?.contains("因式分解") == true) {
                    Log.d(TAG, "找到因式分解形式: ${form.expression}")
                    return form.expression
                }
            }
        }

        return current
    }

    private fun applyFractionStrategy(input: MathNode): MathNode {
        Log.d(TAG, "应用策略：分数约分")

        var current = input

        current = canonicalizer.canonicalize(current)
        current = trigSimplifier.simplify(current)

        if (current is MathNode.BinaryOp && current.operator == Operator.DIVIDE) {
            val forms = formGenerator.generateAllForms(current)

            for (form in forms.forms) {
                if (form.description?.contains("约分") == true) {
                    Log.d(TAG, "找到约分形式: ${form.expression}")
                    return form.expression
                }
            }

            if (forms.forms.isNotEmpty()) {
                val lastForm = forms.forms.last()
                Log.d(TAG, "使用最终形式: ${lastForm.expression}")
                return lastForm.expression
            }
        }

        return current
    }

    private fun applyFullStrategy(input: MathNode): MathNode {
        Log.d(TAG, "应用策略：完全化简")

        return iterativeSimplify(input)
    }

    private fun tryApply(
        name: String,
        node: MathNode,
        transform: (MathNode) -> MathNode
    ): MathNode {
        val before = node.toString()

        return try {
            val result = transform(node)
            val after = result.toString()

            if (before != after) {
                Log.d(TAG, "  ✓ $name 有变化")
            } else {
                Log.d(TAG, "  - $name 无变化")
            }

            result
        } catch (e: Exception) {
            Log.e(TAG, "  ✗ $name 出错: ${e.message}")
            node
        }
    }

    private fun addIfUnique(
        node: MathNode,
        type: SimplificationType,
        description: String,
        forms: MutableList<SimplifiedForm>,
        seen: MutableSet<String>
    ) {
        val str = node.toString()

        if (str in seen) {
            Log.d(TAG, "- 跳过重复形式: $description = $str")
            return
        }

        seen.add(str)
        forms.add(SimplifiedForm(node, type, description))
        Log.d(TAG, "✓ 添加形式: $description = $str")
    }

    private fun ensureMinimumForms(
        forms: MutableList<SimplifiedForm>,
        input: MathNode,
        seen: MutableSet<String>
    ) {
        if (forms.size >= 3) return

        Log.d(TAG, "形式数量不足 (${forms.size})，尝试添加中间步骤")

        val partial = applyPartialStrategy(input)
        addIfUnique(partial, SimplificationType.STRUCTURAL, "中间步骤", forms, seen)

        if (forms.size < 3) {
            val alternative = applyAlternativeStrategy(input)
            addIfUnique(alternative, SimplificationType.STRUCTURAL, "替代形式", forms, seen)
        }
    }

    private fun applyPartialStrategy(input: MathNode): MathNode {
        var current = input

        current = canonicalizer.canonicalize(current)
        current = constantFolding(current)
        current = trigSimplifier.simplify(current)

        return current
    }

    private fun applyAlternativeStrategy(input: MathNode): MathNode {
        var current = input

        current = canonicalizer.canonicalize(current)
        current = removeZeroTerms(current)
        current = removeOneFactors(current)

        return current
    }

    private fun constantFolding(node: MathNode): MathNode {
        return when (node) {
            is MathNode.BinaryOp -> {
                val left = constantFolding(node.left)
                val right = constantFolding(node.right)

                if (left is MathNode.Number && right is MathNode.Number) {
                    val result = when (node.operator) {
                        Operator.ADD -> left.value + right.value
                        Operator.SUBTRACT -> left.value - right.value
                        Operator.MULTIPLY -> left.value * right.value
                        Operator.DIVIDE -> {
                            if (abs(right.value) > EPSILON) {
                                left.value / right.value
                            } else {
                                return MathNode.BinaryOp(node.operator, left, right)
                            }
                        }
                        Operator.POWER -> left.value.pow(right.value)
                    }
                    MathNode.Number(result)
                } else {
                    MathNode.BinaryOp(node.operator, left, right)
                }
            }
            is MathNode.Function -> {
                MathNode.Function(node.name, constantFolding(node.argument))
            }
            else -> node
        }
    }

    private fun removeZeroTerms(node: MathNode): MathNode {
        return when (node) {
            is MathNode.BinaryOp -> {
                val left = removeZeroTerms(node.left)
                val right = removeZeroTerms(node.right)

                when (node.operator) {
                    Operator.ADD -> {
                        when {
                            left is MathNode.Number && abs(left.value) < EPSILON -> right
                            right is MathNode.Number && abs(right.value) < EPSILON -> left
                            else -> MathNode.BinaryOp(Operator.ADD, left, right)
                        }
                    }
                    Operator.MULTIPLY -> {
                        when {
                            left is MathNode.Number && abs(left.value) < EPSILON -> MathNode.Number(0.0)
                            right is MathNode.Number && abs(right.value) < EPSILON -> MathNode.Number(0.0)
                            else -> MathNode.BinaryOp(Operator.MULTIPLY, left, right)
                        }
                    }
                    else -> MathNode.BinaryOp(node.operator, left, right)
                }
            }
            is MathNode.Function -> MathNode.Function(node.name, removeZeroTerms(node.argument))
            else -> node
        }
    }

    private fun removeOneFactors(node: MathNode): MathNode {
        return when (node) {
            is MathNode.BinaryOp -> {
                val left = removeOneFactors(node.left)
                val right = removeOneFactors(node.right)

                when (node.operator) {
                    Operator.MULTIPLY -> {
                        when {
                            left is MathNode.Number && abs(left.value - 1.0) < EPSILON -> right
                            right is MathNode.Number && abs(right.value - 1.0) < EPSILON -> left
                            else -> MathNode.BinaryOp(Operator.MULTIPLY, left, right)
                        }
                    }
                    else -> MathNode.BinaryOp(node.operator, left, right)
                }
            }
            is MathNode.Function -> MathNode.Function(node.name, removeOneFactors(node.argument))
            else -> node
        }
    }

    private fun simplifyPowers(node: MathNode): MathNode {
        return when (node) {
            is MathNode.BinaryOp -> {
                val left = simplifyPowers(node.left)
                val right = simplifyPowers(node.right)

                if (node.operator == Operator.POWER && right is MathNode.Number) {
                    when {
                        abs(right.value) < EPSILON -> MathNode.Number(1.0)
                        abs(right.value - 1.0) < EPSILON -> left
                        else -> MathNode.BinaryOp(Operator.POWER, left, right)
                    }
                } else {
                    MathNode.BinaryOp(node.operator, left, right)
                }
            }
            is MathNode.Function -> MathNode.Function(node.name, simplifyPowers(node.argument))
            else -> node
        }
    }
}
