// app/src/main/java/com/mathsnew/mathsnew/CalculusFragment.kt
// 修复版本 - 集成 HandwrittenExporter

package com.mathsnew.mathsnew

import android.animation.ValueAnimator
import android.graphics.Color
import android.os.Bundle
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.SuperscriptSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.mathsnew.mathsnew.databinding.FragmentCalculusBinding
import android.util.Log
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.mathsnew.mathsnew.calculus.graph.GraphEngine
import com.mathsnew.mathsnew.newsimplified.CalculusEngineV2
import com.mathsnew.mathsnew.newsimplified.CalculationResult as CalculationResultV2
import com.mathsnew.mathsnew.newsimplified.ExportHelper
import com.mathsnew.mathsnew.utils.CalculationHistoryManager
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

private enum class CharType {
    NUMBER, VARIABLE, OPERATOR, FUNCTION, PAREN, PLACEHOLDER
}

private data class CharInfo(
    val char: Char,
    val type: CharType,
    val isSuperscript: Boolean = false,
    val isPlaceholder: Boolean = false
)

class CalculusFragment : Fragment() {

    companion object {
        private const val USE_V2_ENGINE = true
        private const val TAG = "CalculusFragment"

        private val COLOR_FUNCTION = Color.parseColor("#2196F3")
        private val COLOR_VARIABLE = Color.parseColor("#000000")
        private val COLOR_NUMBER = Color.parseColor("#F44336")
        private val COLOR_OPERATOR = Color.parseColor("#2E7D32")
        private val COLOR_PLACEHOLDER = Color.parseColor("#FF6600")
    }

    private var _binding: FragmentCalculusBinding? = null
    private val binding get() = _binding!!

    private var currentExpression = ""

    private val calculusEngineV2 = CalculusEngineV2()
    private val calculusEngineV1 = CalculusEngine()

    private val graphEngine = GraphEngine()
    private val formatter = MathFormatter()

    private var hasResult = false
    private var blinkAnimator: ValueAnimator? = null

    private var lastResult: CalculationResultV2? = null

    private lateinit var historyManager: CalculationHistoryManager
    private lateinit var exportHelper: ExportHelper

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCalculusBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        historyManager = CalculationHistoryManager(requireContext())
        exportHelper = ExportHelper(requireContext())

        setupDisplayEditText()
        setupBackButton()
        setupClearButton()
        setupExportButton()
        setupHistoryButton()
        setupKeyboardListeners()
        setupFunctionButtons()
    }

    /**
     * ✅ 配置输入和显示控件
     * etInput: 输入阶段（有光标）
     * tvDisplay: 结果阶段（无光标，多行）
     */
    private fun setupDisplayEditText() {
        // ✅ 配置 EditText（输入阶段）
        binding.etInput.apply {
            showSoftInputOnFocus = false  // 禁用系统键盘
            setHorizontallyScrolling(true)
            isHorizontalScrollBarEnabled = true
        }

        // ✅ 配置 TextView（结果阶段）
        binding.tvDisplay.apply {
            setHorizontallyScrolling(true)
            isHorizontalScrollBarEnabled = true
            movementMethod = android.text.method.ScrollingMovementMethod.getInstance()

            // 🔥 关键修复：强制不自动换行
            // 即使有自定义 Span，也不要在行内换行
            setSingleLine(false)  // 允许多个逻辑行（通过 \n 分隔）
            // 但每个逻辑行内部不自动换行（由 setHorizontallyScrolling 控制）
        }
    }

    private fun setupBackButton() {
        binding.btnBack.setOnClickListener {
            findNavController().navigateUp()
        }
    }

    private fun setupClearButton() {
        binding.btnClearToolbar?.setOnClickListener {
            clearAll()
        }
    }

    private fun setupExportButton() {
        binding.btnExport?.setOnClickListener {
            exportResults()
        }
    }

    private fun setupHistoryButton() {
        binding.btnHistory?.setOnClickListener {
            showHistoryDialog()
        }
    }

    private fun showHistoryDialog() {
        val history = historyManager.getHistory()

        if (history.isEmpty()) {
            Toast.makeText(requireContext(), getString(R.string.history_empty), Toast.LENGTH_SHORT).show()
            return
        }

        val dialogView = layoutInflater.inflate(R.layout.dialog_history, null)
        val recyclerView = dialogView.findViewById<RecyclerView>(R.id.recycler_history)

        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        val adapter = HistoryAdapter(history) { expression ->
            loadExpressionFromHistory(expression)

            var parent = dialogView.parent
            while (parent != null) {
                if (parent is AlertDialog) {
                    parent.dismiss()
                    break
                }
                parent = (parent as? View)?.parent
            }
        }
        recyclerView.adapter = adapter

        AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun loadExpressionFromHistory(expression: String) {
        Log.d(TAG, "从历史加载: '$expression'")

        hasResult = false
        lastResult = null
        stopBlinkAnimation()

        binding.graphView.clearGraph()
        binding.graphView.visibility = View.GONE

        currentExpression = expression
        updateDisplay()

        enableDerivativeButton()

        // ✅ 切换显示：显示输入控件，隐藏结果控件
        binding.etInput.visibility = View.VISIBLE
        binding.tvDisplay.visibility = View.GONE

        Toast.makeText(requireContext(), "已加载历史表达式", Toast.LENGTH_SHORT).show()
    }

    private fun clearAll() {
        Log.d(TAG, "🔄 clearAll() 开始")

        currentExpression = ""
        hasResult = false
        lastResult = null

        stopBlinkAnimation()

        updateDisplay()
        enableDerivativeButton()


        binding.graphView.clearGraph()
        binding.graphView.visibility = View.GONE

        // ✅ 清空两个控件
        binding.etInput.setText("")
        binding.tvDisplay.setText("")

        // ✅ 切换显示：显示输入控件，隐藏结果控件
        binding.etInput.visibility = View.VISIBLE
        binding.tvDisplay.visibility = View.GONE

        Log.d(TAG, "✅ clearAll() 完成: hasResult=$hasResult, expr='$currentExpression'")
    }

    private fun exportResults() {
        // 检查是否有结果
        if (!hasResult || lastResult == null) {
            Toast.makeText(requireContext(), "没有可导出的内容", Toast.LENGTH_SHORT).show()
            return
        }

        // 检查是否是成功的结果
        val result = lastResult
        if (result !is CalculationResultV2.Success) {
            Toast.makeText(requireContext(), "没有可导出的内容", Toast.LENGTH_SHORT).show()
            return
        }

        // ✅ 使用新的 ExportHelper 导出手写格式
        exportHelper.exportAndCopy(result, currentExpression)
    }

    private fun setupKeyboardListeners() {
        binding.btn0.setOnClickListener { appendToExpression("0") }
        binding.btn1.setOnClickListener { appendToExpression("1") }
        binding.btn2.setOnClickListener { appendToExpression("2") }
        binding.btn3.setOnClickListener { appendToExpression("3") }
        binding.btn4.setOnClickListener { appendToExpression("4") }
        binding.btn5.setOnClickListener { appendToExpression("5") }
        binding.btn6.setOnClickListener { appendToExpression("6") }
        binding.btn7.setOnClickListener { appendToExpression("7") }
        binding.btn8.setOnClickListener { appendToExpression("8") }
        binding.btn9.setOnClickListener { appendToExpression("9") }

        binding.btnAdd.setOnClickListener { appendToExpression("+") }
        binding.btnSubtract.setOnClickListener { appendToExpression("-") }
        binding.btnMultiply.setOnClickListener { appendToExpression("×") }
        binding.btnDivide.setOnClickListener { appendToExpression("/") }
        binding.btnPower.setOnClickListener { handlePowerInput() }

        binding.btnLeftParen.setOnClickListener { appendToExpression("(") }
        binding.btnRightParen.setOnClickListener { appendToExpression(")") }

        binding.btnX.setOnClickListener { appendToExpression("x") }
        binding.btnPi.setOnClickListener { appendToExpression("π") }
        binding.btnDot.setOnClickListener { appendToExpression(".") }

        binding.btnClear.setOnClickListener { clearExpression() }
        binding.btnBackspace.setOnClickListener { backspace() }
    }

    private fun setupFunctionButtons() {
        binding.btnSin.setOnClickListener { appendToExpression("sin(") }
        binding.btnCos.setOnClickListener { appendToExpression("cos(") }
        binding.btnTan.setOnClickListener { appendToExpression("tan(") }

        binding.btnArcsin.setOnClickListener { appendToExpression("arcsin(") }
        binding.btnArccos.setOnClickListener { appendToExpression("arccos(") }
        binding.btnArctan.setOnClickListener { appendToExpression("arctan(") }

        binding.btnLn.setOnClickListener { appendToExpression("ln(") }
        binding.btnLog.setOnClickListener { appendToExpression("log(") }

        binding.btnE.setOnClickListener { appendToExpression("exp(") }

        binding.btnSqrt.setOnClickListener { appendToExpression("sqrt(") }
        binding.btnAbs.setOnClickListener { appendToExpression("abs(") }

        binding.btnDerivative.setOnClickListener { calculateDerivative() }
    }

    /**
     * ✅ 处理幂次输入 x^n
     * 简单版本：直接追加到末尾
     */
    private fun handlePowerInput() {
        Log.d(TAG, "^️ handlePowerInput(): hasResult=$hasResult")

        if (hasResult) {
            Log.d(TAG, "  → 调用 clearResults()")
            clearResults()
        }

        currentExpression += "^n"
        updateDisplayWithBlink()

        Log.d(TAG, "  → 完成: expr='$currentExpression'")
    }

    /**
     * ✅ 追加表达式（原始简单版本）
     * 逻辑：
     * 1. 所有输入都追加到末尾
     * 2. 如果表达式以 ^n 结尾且输入是数字，替换 n
     * 3. 否则直接追加
     */
    private fun appendToExpression(value: String) {
        Log.d(TAG, "📝 appendToExpression('$value'): hasResult=$hasResult, expr='$currentExpression'")

        if (hasResult) {
            Log.d(TAG, "  → 检测到 hasResult=true，调用 clearResults()")
            clearResults()
        }

        stopBlinkAnimation()

        // ✅ 检查是否以 ^n 结尾，且输入的是数字
        if (currentExpression.endsWith("^n") && value.length == 1 && value[0].isDigit()) {
            // 替换 n 为输入的数字
            currentExpression = currentExpression.dropLast(1) + value
            Log.d(TAG, "  → 替换 ^n 为 ^$value: expr='$currentExpression'")
        } else {
            // 直接追加到末尾
            currentExpression += value
            Log.d(TAG, "  → 追加到末尾: expr='$currentExpression'")
        }

        updateDisplay()

        Log.d(TAG, "  → 完成: expr='$currentExpression'")
    }

    private fun clearExpression() {
        Log.d(TAG, "🗑️ clearExpression() 开始")

        currentExpression = ""
        hasResult = false
        lastResult = null

        stopBlinkAnimation()
        updateDisplay()
        enableDerivativeButton()


        binding.graphView.clearGraph()
        binding.graphView.visibility = View.GONE

        // ✅ 清空两个控件
        binding.etInput.setText("")
        binding.tvDisplay.setText("")

        // ✅ 切换显示：显示输入控件，隐藏结果控件
        binding.etInput.visibility = View.VISIBLE
        binding.tvDisplay.visibility = View.GONE

        Log.d(TAG, "✅ clearExpression() 完成")
    }

    /**
     * ✅ 清除结果和图形（开始新输入时调用）
     */
    private fun clearResults() {
        Log.d(TAG, "🔄 clearResults() 开始")

        currentExpression = ""
        hasResult = false
        lastResult = null

        stopBlinkAnimation()

        updateDisplay()
        enableDerivativeButton()


        binding.graphView.clearGraph()
        binding.graphView.visibility = View.GONE

        // ✅ 清空两个控件
        binding.etInput.setText("")
        binding.tvDisplay.setText("")

        // ✅ 切换显示：显示输入控件，隐藏结果控件
        binding.etInput.visibility = View.VISIBLE
        binding.tvDisplay.visibility = View.GONE

        Log.d(TAG, "✅ clearResults() 完成: hasResult=$hasResult, expr='$currentExpression'")
    }

    /**
     * ✅ 退格删除（原始简单版本）
     */
    private fun backspace() {
        Log.d(TAG, "⌫ backspace(): hasResult=$hasResult, expr='$currentExpression'")

        if (currentExpression.isEmpty()) {
            return
        }

        if (hasResult) {
            Log.d(TAG, "  → hasResult=true，调用 clearResults()")
            clearResults()
            return
        }

        // ✅ 如果以 ^n 结尾，删除两个字符
        if (currentExpression.endsWith("^n")) {
            currentExpression = currentExpression.dropLast(2)
            stopBlinkAnimation()
        } else {
            currentExpression = currentExpression.dropLast(1)
        }

        updateDisplay()

        Log.d(TAG, "  → 完成: expr='$currentExpression'")
    }

    private fun updateDisplay() {
        val formattedText = formatExpressionWithHighlight(currentExpression, false)
        binding.etInput.setText(formattedText)  // ← 改成 etInput
    }

    private fun updateDisplayWithBlink() {
        startBlinkAnimation()
    }

    private fun startBlinkAnimation() {
        stopBlinkAnimation()

        blinkAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 500
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE

            addUpdateListener { animator ->
                val alpha = animator.animatedValue as Float
                val formattedText = formatExpressionWithHighlight(
                    currentExpression,
                    true,
                    alpha
                )
                binding.etInput.setText(formattedText)  // ← 改成 etInput
            }

            start()
        }
    }

    private fun stopBlinkAnimation() {
        blinkAnimator?.cancel()
        blinkAnimator = null
    }

    private fun formatExpressionWithHighlight(
        expression: String,
        shouldBlink: Boolean = false,
        blinkAlpha: Float = 1.0f
    ): SpannableString {
        val charInfoList = mutableListOf<CharInfo>()
        var i = 0

        while (i < expression.length) {
            val char = expression[i]

            when {
                char == '^' -> {
                    i++
                    val isPlaceholder = i < expression.length && expression[i] == 'n'

                    if (i < expression.length && expression[i] == '-') {
                        charInfoList.add(CharInfo(expression[i], CharType.OPERATOR, true, false))
                        i++
                    }

                    while (i < expression.length && (expression[i].isDigit() || expression[i] == '.' || expression[i] == 'n')) {
                        val type = if (expression[i] == 'n') CharType.PLACEHOLDER else CharType.NUMBER
                        charInfoList.add(CharInfo(expression[i], type, true, isPlaceholder))
                        i++
                    }
                }

                char.isDigit() || char == '.' -> {
                    charInfoList.add(CharInfo(char, CharType.NUMBER))
                    i++
                }

                char.isLetter() -> {
                    val nameBuilder = StringBuilder()
                    while (i < expression.length && expression[i].isLetter()) {
                        nameBuilder.append(expression[i])
                        i++
                    }
                    val name = nameBuilder.toString()

                    val isFunctionName = name in listOf("sin", "cos", "tan", "cot", "sec", "csc",
                                                        "arcsin", "arccos", "arctan", "arccot", "arcsec", "arccsc",
                                                        "ln", "log", "sqrt", "exp", "abs")
                    val type = if (isFunctionName) CharType.FUNCTION else CharType.VARIABLE

                    for (c in name) {
                        charInfoList.add(CharInfo(c, type))
                    }
                }

                char in "+-×/÷·" -> {
                    charInfoList.add(CharInfo(char, CharType.OPERATOR))
                    i++
                }

                char in "()" -> {
                    charInfoList.add(CharInfo(char, CharType.PAREN))
                    i++
                }

                char == 'π' || char == 'e' -> {
                    charInfoList.add(CharInfo(char, CharType.NUMBER))
                    i++
                }

                else -> {
                    i++
                }
            }
        }

        val displayText = charInfoList.map { it.char }.joinToString("")
        val spannableString = SpannableString(displayText)

        var currentPos = 0
        for (info in charInfoList) {
            val start = currentPos
            val end = currentPos + 1

            if (info.isSuperscript) {
                spannableString.setSpan(
                    SuperscriptSpan(),
                    start,
                    end,
                    SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                spannableString.setSpan(
                    RelativeSizeSpan(0.7f),
                    start,
                    end,
                    SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }

            val color = when {
                info.isPlaceholder && shouldBlink -> {
                    Color.argb(
                        (255 * blinkAlpha).toInt(),
                        255, 100, 0
                    )
                }
                info.type == CharType.FUNCTION -> COLOR_FUNCTION
                info.type == CharType.VARIABLE -> COLOR_VARIABLE
                info.type == CharType.NUMBER -> COLOR_NUMBER
                info.type == CharType.OPERATOR -> COLOR_OPERATOR
                info.type == CharType.PLACEHOLDER -> COLOR_PLACEHOLDER
                else -> COLOR_VARIABLE
            }

            spannableString.setSpan(
                ForegroundColorSpan(color),
                start,
                end,
                SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE
                )

            currentPos++
        }

        return spannableString
    }

    private fun calculateDerivative() {
        Log.d(TAG, "========================================")
        Log.d(TAG, "===== calculateDerivative 被调用 =====")
        Log.d(TAG, "currentExpression = '$currentExpression'")

        if (currentExpression.isEmpty()) {
            Toast.makeText(requireContext(), "请先输入表达式", Toast.LENGTH_SHORT).show()
            return
        }

        if (currentExpression.contains("^n")) {
            Toast.makeText(requireContext(), "请完成指数输入", Toast.LENGTH_SHORT).show()
            return
        }

        if (hasResult) {
            return
        }

        stopBlinkAnimation()

        historyManager.saveExpression(currentExpression)

        if (USE_V2_ENGINE) {
            calculateWithV2Engine()
        } else {
            calculateWithV1Engine()
        }
    }

    private fun calculateWithV2Engine() {
        try {
            val startTime = System.currentTimeMillis()
            Log.d(TAG, "使用 V2 引擎计算")

            when (val result = calculusEngineV2.calculateDerivative(currentExpression)) {
                is CalculationResultV2.Success -> {
                    val calcTime = System.currentTimeMillis()
                    Log.d(TAG, "✅ 计算成功! 耗时: ${calcTime - startTime}ms")

                    Log.d(TAG, "⏱️ 开始显示结果...")
                    val displayStartTime = System.currentTimeMillis()

                    lastResult = result

                    appendMultiFormResultToDisplay(result)

                    val displayEndTime = System.currentTimeMillis()
                    Log.d(TAG, "⏱️ 显示结果完成! 耗时: ${displayEndTime - displayStartTime}ms")

                    hasResult = true
                    disableDerivativeButton()


                    Log.d(TAG, "🚀 开始异步生成图形...")
                    lifecycleScope.launch {
                        val graphStartTime = System.currentTimeMillis()

                        try {
                            val firstDerivAST = result.forms.getDisplayForms().firstOrNull()?.expression
                            val secondDerivAST = result.secondDerivativeForms?.getDisplayForms()?.firstOrNull()?.expression

                            val graphData = withContext(Dispatchers.Default) {
                                graphEngine.generateGraphData(
                                    originalExpression = currentExpression,
                                    firstDerivativeAST = firstDerivAST,
                                    secondDerivativeAST = secondDerivAST
                                )
                            }

                            binding.graphView.setGraphData(graphData)
                            binding.graphView.visibility = View.VISIBLE

                            val graphEndTime = System.currentTimeMillis()
                            Log.d(TAG, "✅ 图形生成完成! 耗时: ${graphEndTime - graphStartTime}ms")
                        } catch (e: Exception) {
                            Log.e(TAG, "❌ 绘图失败: ${e.message}", e)
                        }
                    }

                    val immediateTime = System.currentTimeMillis()
                    Log.d(TAG, "⏱️ 用户可见耗时: ${immediateTime - startTime}ms")
                }
                is CalculationResultV2.Error -> {
                    Toast.makeText(requireContext(), result.message, Toast.LENGTH_LONG).show()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "V2引擎异常: ${e.message}", e)
            Toast.makeText(requireContext(), "计算错误: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun calculateWithV1Engine() {
        try {
            Log.d(TAG, "使用 V1 引擎计算")
            when (val result = calculusEngineV1.calculateDerivative(currentExpression)) {
                is CalculationResult.Success -> {
                    appendResultToDisplay(result)
                    hasResult = true
                    disableDerivativeButton()


                    lifecycleScope.launch {
                        try {
                            val graphData = withContext(Dispatchers.Default) {
                                graphEngine.generateGraphData(currentExpression)
                            }
                            binding.graphView.setGraphData(graphData)
                            binding.graphView.visibility = View.VISIBLE
                        } catch (e: Exception) {
                            Log.e(TAG, "绘图失败: ${e.message}", e)
                        }
                    }
                }
                is CalculationResult.Error -> {
                    Toast.makeText(requireContext(), result.message, Toast.LENGTH_LONG).show()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "V1引擎异常: ${e.message}", e)
            Toast.makeText(requireContext(), "计算错误: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun appendResultToDisplay(result: CalculationResult.Success) {
        val builder = SpannableStringBuilder()

        // ✅ 从 etInput 读取当前输入的表达式
        builder.append(binding.etInput.text)

        // ✅ 添加 f(x) = 行
        builder.append("\n\nf(x) = ")
        builder.append(currentExpression)
        builder.append("\n\n")

        builder.append("f'(x) = ")
        builder.append(result.displayText)

        if (result.secondDerivativeDisplayText != null) {
            builder.append("\n\nf''(x) = ")
            builder.append(result.secondDerivativeDisplayText)
        }

        // ✅ 显示到 tvDisplay
        binding.tvDisplay.setText(builder)

        // ✅ 切换显示：隐藏输入控件，显示结果控件
        binding.etInput.visibility = View.GONE
        binding.tvDisplay.visibility = View.VISIBLE
    }

    private fun appendMultiFormResultToDisplay(result: CalculationResultV2.Success) {
        val builder = SpannableStringBuilder()

        // ✅ 从 etInput 读取当前输入的表达式
        builder.append(binding.etInput.text)

        builder.append("\n\nf(x) = ")
        builder.append(currentExpression)

        val displayForms = result.forms.getDisplayForms()

        builder.append("\n\n")
        builder.append("f'(x) = ")

        for ((index, form) in displayForms.withIndex()) {
            if (index > 0) {
                builder.append("\n\n      = ")
            }

            val formatted = formatter.format(form.expression.toString())

            // 🔍 DEBUG: 查看格式化后的文本
            Log.d(TAG, "🔍 f'(x) form[$index]:")
            Log.d(TAG, "  原始表达式: ${form.expression}")
            Log.d(TAG, "  格式化plainText: ${formatted.plainText}")
            Log.d(TAG, "  格式化displayText: ${formatted.displayText}")
            Log.d(TAG, "  displayText长度: ${formatted.displayText.length}")

            builder.append(formatted.displayText)
        }

        if (result.secondDerivativeForms != null) {
            val secondDisplayForms = result.secondDerivativeForms.getDisplayForms()

            builder.append("\n\n")

            for ((index, form) in secondDisplayForms.withIndex()) {
                val formatted = formatter.format(form.expression.toString())

                if (index == 0) {
                    builder.append("f''(x) = ")

                    // 🔍 DEBUG: 查看二阶导数
                    Log.d(TAG, "🔍 f''(x) form[$index]:")
                    Log.d(TAG, "  原始表达式: ${form.expression}")
                    Log.d(TAG, "  格式化plainText: ${formatted.plainText}")
                    Log.d(TAG, "  格式化displayText: ${formatted.displayText}")
                } else {
                    builder.append("\n\n       = ")
                }

                builder.append(formatted.displayText)
            }
        } else if (result.secondDerivativeDisplayText != null) {
            builder.append("\n\n")
            builder.append("f''(x) = ")
            builder.append(result.secondDerivativeDisplayText)
        }

        // 🔍 DEBUG: 查看最终构建的完整文本
        Log.d(TAG, "🔍 最终构建的文本:")
        Log.d(TAG, "  总长度: ${builder.length}")
        Log.d(TAG, "  文本内容:\n$builder")
        Log.d(TAG, "  plainText:\n${builder.toString()}")

        // 🔍 DEBUG: 检查是否有换行符
        val text = builder.toString()
        val lines = text.split("\n")
        Log.d(TAG, "🔍 文本行数: ${lines.size}")
        lines.forEachIndexed { index, line ->
            Log.d(TAG, "  第${index+1}行 (${line.length}字符): $line")
        }

        // ✅ 显示到 tvDisplay
        binding.tvDisplay.setText(builder)

        // 🔥 关键修复：强制设置 TextView 的布局行为
        binding.tvDisplay.post {
            binding.tvDisplay.apply {
                // 确保横向滚动生效
                setHorizontallyScrolling(true)
                // 强制重新布局
                requestLayout()
            }
        }

        // ✅ 切换显示：隐藏输入控件，显示结果控件
        binding.etInput.visibility = View.GONE
        binding.tvDisplay.visibility = View.VISIBLE
    }

    private fun disableDerivativeButton() {
        binding.btnDerivative.isEnabled = false
        binding.btnDerivative.alpha = 0.5f
    }

    private fun enableDerivativeButton() {
        binding.btnDerivative.isEnabled = true
        binding.btnDerivative.alpha = 1.0f
    }

    override fun onDestroyView() {
        super.onDestroyView()
        stopBlinkAnimation()
        _binding = null
    }
}

class HistoryAdapter(
    private val history: List<String>,
    private val onItemClick: (String) -> Unit
) : RecyclerView.Adapter<HistoryAdapter.HistoryViewHolder>() {

    class HistoryViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvExpression: android.widget.TextView = view.findViewById(R.id.tv_history_expression)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_history, parent, false)
        return HistoryViewHolder(view)
    }

    override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
        val expression = history[position]
        holder.tvExpression.text = expression
        holder.itemView.setOnClickListener {
            onItemClick(expression)
        }
    }

    override fun getItemCount() = history.size
}