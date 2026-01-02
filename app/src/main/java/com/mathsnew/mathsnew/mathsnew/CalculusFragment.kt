// app/src/main/java/com/mathsnew/mathsnew/CalculusFragment.kt

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

    // 保存最后的计算结果用于导出
    private var lastResult: CalculationResultV2? = null

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

        // ✅ 强制允许横向滚动
        binding.tvDisplay.apply {
            setHorizontallyScrolling(true)
            isHorizontalScrollBarEnabled = true
            movementMethod = android.text.method.ScrollingMovementMethod.getInstance()

            // ✅ 强制左对齐
            textAlignment = View.TEXT_ALIGNMENT_VIEW_START
            gravity = android.view.Gravity.START
        }

        setupBackButton()
        setupClearButton()
        setupExportButton()
        setupKeyboardListeners()
        setupFunctionButtons()
    }

    private fun setupBackButton() {
        binding.btnBack.setOnClickListener {
            findNavController().navigateUp()
        }
    }

    /**
     * ✅ 设置标题栏清除按钮
     */
    private fun setupClearButton() {
        binding.btnClearToolbar?.setOnClickListener {
            clearAll()
        }
    }

    /**
     * ✅ 设置导出按钮
     */
    private fun setupExportButton() {
        binding.btnExport?.setOnClickListener {
            exportResults()
        }
    }

    /**
     * ✅ 清除所有内容（标题栏清除按钮）
     */
    private fun clearAll() {
        Log.d(TAG, "🔄 clearAll() 开始")

        currentExpression = ""
        hasResult = false
        lastResult = null

        stopBlinkAnimation()  // ✅ 停止闪烁动画

        updateDisplay()
        enableDerivativeButton()

        binding.graphView.clearGraph()
        binding.graphView.visibility = View.GONE
        binding.tvDisplay.text = ""

        // ✅ 重新获取焦点（延迟执行确保UI更新完成）
        binding.tvDisplay.post {
            binding.tvDisplay.requestFocus()
        }

        Log.d(TAG, "✅ clearAll() 完成: hasResult=$hasResult, expr='$currentExpression'")
    }

    /**
     * ✅ 导出结果到剪贴板（纯文本格式）
     */
    private fun exportResults() {
        if (currentExpression.isEmpty() && !hasResult) {
            Toast.makeText(requireContext(), "没有可导出的内容", Toast.LENGTH_SHORT).show()
            return
        }

        val output = buildExportText()

        if (output.isEmpty()) {
            Toast.makeText(requireContext(), "没有可导出的内容", Toast.LENGTH_SHORT).show()
            return
        }

        val clipboard = requireContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val clip = android.content.ClipData.newPlainText("导数计算结果", output)
        clipboard.setPrimaryClip(clip)

        Toast.makeText(requireContext(), "✅ 已复制到剪贴板", Toast.LENGTH_SHORT).show()
        Log.d(TAG, "✅ 已导出结果:\n$output")
    }

    /**
     * ✅ 构建导出用的纯文本
     */
    private fun buildExportText(): String {
        val builder = StringBuilder()

        if (currentExpression.isNotEmpty()) {
            builder.append("f(x) = $currentExpression\n")
        }

        if (hasResult && lastResult != null) {
            when (val result = lastResult) {
                is CalculationResultV2.Success -> {
                    builder.append("\n")

                    // 一阶导数
                    val firstForms = result.forms.getDisplayForms()
                    builder.append("f'(x) = ")
                    for ((index, form) in firstForms.withIndex()) {
                        if (index > 0) {
                            builder.append("\n      = ")
                        }
                        builder.append(form.expression.toString())
                    }

                    // 二阶导数
                    if (result.secondDerivativeForms != null) {
                        builder.append("\n\n")
                        val secondForms = result.secondDerivativeForms.getDisplayForms()
                        builder.append("f''(x) = ")
                        for ((index, form) in secondForms.withIndex()) {
                            if (index > 0) {
                                builder.append("\n       = ")
                            }
                            builder.append(form.expression.toString())
                        }
                    }
                }
                else -> {
                    // 错误情况，不导出
                }
            }
        }

        return builder.toString()
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

    private fun appendToExpression(value: String) {
        Log.d(TAG, "📝 appendToExpression('$value'): hasResult=$hasResult, expr='$currentExpression'")

        if (hasResult) {
            Log.d(TAG, "  → 检测到 hasResult=true，调用 clearResults()")
            clearResults()
        }

        stopBlinkAnimation()

        if (currentExpression.endsWith("^n") && value[0].isDigit()) {
            currentExpression = currentExpression.dropLast(1) + value
        } else {
            currentExpression += value
        }

        updateDisplay()

        Log.d(TAG, "  → 完成: expr='$currentExpression'")
    }

    private fun clearExpression() {
        Log.d(TAG, "🗑️ clearExpression() 开始")

        currentExpression = ""
        hasResult = false
        lastResult = null  // ✅ 清除保存的结果

        stopBlinkAnimation()
        updateDisplay()
        enableDerivativeButton()

        binding.graphView.clearGraph()
        binding.graphView.visibility = View.GONE

        // ✅ 清除显示区域
        binding.tvDisplay.text = ""

        Log.d(TAG, "✅ clearExpression() 完成")
    }

    /**
     * ✅ 清除结果和图形（开始新输入时调用）
     */
    private fun clearResults() {
        Log.d(TAG, "🔄 clearResults() 开始")

        currentExpression = ""
        hasResult = false
        lastResult = null  // ✅ 清除保存的结果

        stopBlinkAnimation()  // ✅ 停止闪烁动画

        updateDisplay()
        enableDerivativeButton()

        binding.graphView.clearGraph()
        binding.graphView.visibility = View.GONE

        // ✅ 清除显示区域
        binding.tvDisplay.text = ""

        Log.d(TAG, "✅ clearResults() 完成: hasResult=$hasResult, expr='$currentExpression'")
    }

    private fun backspace() {
        Log.d(TAG, "⌫ backspace(): hasResult=$hasResult, expr='$currentExpression'")

        if (currentExpression.isNotEmpty()) {
            if (hasResult) {
                Log.d(TAG, "  → hasResult=true，调用 clearResults()")
                clearResults()
            } else {
                if (currentExpression.endsWith("^n")) {
                    currentExpression = currentExpression.dropLast(2)
                    stopBlinkAnimation()
                } else {
                    currentExpression = currentExpression.dropLast(1)
                }
            }
            updateDisplay()
        }

        Log.d(TAG, "  → 完成: expr='$currentExpression'")
    }

    private fun updateDisplay() {
        val formattedText = formatExpressionWithHighlight(currentExpression, false)
        binding.tvDisplay.text = formattedText
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
                binding.tvDisplay.text = formattedText
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

                    // ✅ 保存结果用于导出
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
        builder.append(binding.tvDisplay.text)
        builder.append("\n\n")

        builder.append("f'(x) = ")
        builder.append(result.displayText)

        if (result.secondDerivativeDisplayText != null) {
            builder.append("\n\nf''(x) = ")
            builder.append(result.secondDerivativeDisplayText)
        }

        binding.tvDisplay.text = builder
    }

    private fun appendMultiFormResultToDisplay(result: CalculationResultV2.Success) {
        val builder = SpannableStringBuilder()
        builder.append(binding.tvDisplay.text)

        // ✅ 1. 显示原始表达式
        builder.append("\n\nf(x) = ")
        builder.append(currentExpression)

        val displayForms = result.forms.getDisplayForms()

        // ✅ 2. 一阶导数
        builder.append("\n\n")
        builder.append("f'(x) = ")

        for ((index, form) in displayForms.withIndex()) {
            if (index > 0) {
                builder.append("\n\n      = ")
            }

            val formatted = formatter.format(form.expression.toString())
            builder.append(formatted.displayText)
        }

        if (result.secondDerivativeForms != null) {
            val secondDisplayForms = result.secondDerivativeForms.getDisplayForms()

            // ✅ 3. 二阶导数
            builder.append("\n\n")

            for ((index, form) in secondDisplayForms.withIndex()) {
                val formatted = formatter.format(form.expression.toString())
                val resultText = formatted.plainText

                val needsNewLine = resultText.length > 50

                if (index == 0) {
                    if (needsNewLine) {
                        builder.append("f''(x) =\n")
                        builder.append("        ")
                    } else {
                        builder.append("f''(x) = ")
                    }
                } else {
                    if (needsNewLine) {
                        builder.append("\n\n       =\n")
                        builder.append("        ")
                    } else {
                        builder.append("\n\n       = ")
                    }
                }

                builder.append(formatted.displayText)
            }
        } else if (result.secondDerivativeDisplayText != null) {
            val resultText = result.secondDerivativeDisplayText.toString()
            val needsNewLine = resultText.length > 50

            builder.append("\n\n")
            if (needsNewLine) {
                builder.append("f''(x) =\n")
                builder.append("        ")
            } else {
                builder.append("f''(x) = ")
            }
            builder.append(result.secondDerivativeDisplayText)
        }

        binding.tvDisplay.text = builder
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