// app/src/main/java/com/mathsnew/mathsnew/CalculusFragment.kt
// 微积分计算器页面 - 支持SpannableString上标显示和语法高亮 + 绘图功能 + 二阶导数显示

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
import com.mathsnew.mathsnew.calculus.graph.GraphEngine

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

    private var _binding: FragmentCalculusBinding? = null
    private val binding get() = _binding!!

    private var currentExpression = ""
    private val calculusEngine = CalculusEngine()
    private val graphEngine = GraphEngine()

    private var hasResult = false
    private var blinkAnimator: ValueAnimator? = null

    companion object {
        private val COLOR_FUNCTION = Color.parseColor("#2196F3")
        private val COLOR_VARIABLE = Color.parseColor("#000000")
        private val COLOR_NUMBER = Color.parseColor("#F44336")
        private val COLOR_OPERATOR = Color.parseColor("#2E7D32")
        private val COLOR_PLACEHOLDER = Color.parseColor("#FF6600")
    }

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

        binding.graphView.visibility = View.GONE

        setupBackButton()
        setupKeyboardListeners()
        setupFunctionButtons()
    }

    private fun setupBackButton() {
        binding.btnBack.setOnClickListener {
            findNavController().navigateUp()
        }
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

        binding.btnLn.setOnClickListener { appendToExpression("ln(") }
        binding.btnLog.setOnClickListener { appendToExpression("log(") }

        binding.btnE.setOnClickListener { appendToExpression("exp(") }

        binding.btnSqrt.setOnClickListener { appendToExpression("sqrt(") }
        binding.btnAbs.setOnClickListener { appendToExpression("abs(") }

        binding.btnDerivative.setOnClickListener { calculateDerivative() }
    }

    private fun handlePowerInput() {
        if (hasResult) {
            currentExpression = ""
            hasResult = false
            enableDerivativeButton()
        }

        currentExpression += "^n"
        updateDisplayWithBlink()
    }

    private fun appendToExpression(value: String) {
        if (hasResult) {
            currentExpression = ""
            hasResult = false
            enableDerivativeButton()
        }

        stopBlinkAnimation()

        if (currentExpression.endsWith("^n") && value[0].isDigit()) {
            currentExpression = currentExpression.dropLast(1) + value
        } else {
            currentExpression += value
        }

        updateDisplay()
    }

    private fun clearExpression() {
        currentExpression = ""
        hasResult = false
        stopBlinkAnimation()
        updateDisplay()
        enableDerivativeButton()
        binding.graphView.visibility = View.GONE
        binding.graphView.clearGraph()
    }

    private fun backspace() {
        if (currentExpression.isNotEmpty()) {
            if (hasResult) {
                currentExpression = ""
                hasResult = false
                enableDerivativeButton()
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
        Log.d("DERIV_DEBUG", "=================================================")
        Log.d("DERIV_DEBUG", "========== calculateDerivative START ===========")
        Log.d("DERIV_DEBUG", "=================================================")
        Log.d("DERIV_DEBUG", "currentExpression = '$currentExpression'")

        if (currentExpression.isEmpty()) {
            Log.d("DERIV_DEBUG", "❌ 表达式为空")
            Toast.makeText(requireContext(), "请先输入表达式", Toast.LENGTH_SHORT).show()
            return
        }

        if (currentExpression.contains("^n")) {
            Log.d("DERIV_DEBUG", "❌ 包含占位符 ^n")
            Toast.makeText(requireContext(), "请完成指数输入", Toast.LENGTH_SHORT).show()
            return
        }

        if (hasResult) {
            Log.d("DERIV_DEBUG", "❌ 已有结果，忽略重复计算")
            return
        }

        stopBlinkAnimation()

        try {
            Log.d("DERIV_DEBUG", "")
            Log.d("DERIV_DEBUG", "========== 步骤1: 计算一阶导数 ==========")

            when (val firstDerivResult = calculusEngine.calculateDerivative(currentExpression)) {
                is CalculationResult.Success -> {
                    Log.d("DERIV_DEBUG", "✅✅✅ 一阶导数计算成功！")
                    Log.d("DERIV_DEBUG", "firstDerivResult.result = '${firstDerivResult.result}'")
                    Log.d("DERIV_DEBUG", "firstDerivResult.displayText = '${firstDerivResult.displayText}'")

                    val firstDerivExpression = firstDerivResult.result

                    Log.d("DERIV_DEBUG", "")
                    Log.d("DERIV_DEBUG", "========== 步骤2: 计算二阶导数 ==========")
                    Log.d("DERIV_DEBUG", "输入表达式: '$firstDerivExpression'")

                    when (val secondDerivResult = calculusEngine.calculateDerivative(firstDerivExpression)) {
                        is CalculationResult.Success -> {
                            Log.d("DERIV_DEBUG", "")
                            Log.d("DERIV_DEBUG", "🎉🎉🎉🎉🎉🎉🎉🎉🎉🎉🎉🎉🎉🎉🎉")
                            Log.d("DERIV_DEBUG", "✅✅✅ 二阶导数计算成功！！！")
                            Log.d("DERIV_DEBUG", "🎉🎉🎉🎉🎉🎉🎉🎉🎉🎉🎉🎉🎉🎉🎉")
                            Log.d("DERIV_DEBUG", "secondDerivResult.result = '${secondDerivResult.result}'")
                            Log.d("DERIV_DEBUG", "secondDerivResult.displayText = '${secondDerivResult.displayText}'")

                            Log.d("DERIV_DEBUG", "")
                            Log.d("DERIV_DEBUG", "========== 步骤3: 调用 appendResultsToDisplay ==========")
                            Log.d("DERIV_DEBUG", "参数1 firstDerivText = '${firstDerivResult.displayText}'")
                            Log.d("DERIV_DEBUG", "参数2 secondDerivText = '${secondDerivResult.displayText}'")

                            appendResultsToDisplay(
                                firstDerivResult.displayText,
                                secondDerivResult.displayText
                            )

                            Log.d("DERIV_DEBUG", "✅ appendResultsToDisplay 调用完成")

                            hasResult = true
                            disableDerivativeButton()

                            Log.d("DERIV_DEBUG", "")
                            Log.d("DERIV_DEBUG", "========== 步骤4: 生成图像 ==========")
                            try {
                                val graphData = graphEngine.generateGraphData(currentExpression)
                                binding.graphView.visibility = View.VISIBLE
                                binding.graphView.setGraphData(graphData)
                                Log.d("DERIV_DEBUG", "✅ 图像绘制完成")
                            } catch (e: Exception) {
                                Log.e("DERIV_DEBUG", "❌ 绘图失败: ${e.message}", e)
                                Toast.makeText(requireContext(), "绘图失败: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                        is CalculationResult.Error -> {
                            Log.d("DERIV_DEBUG", "")
                            Log.d("DERIV_DEBUG", "❌❌❌❌❌❌❌❌❌❌❌❌❌❌❌")
                            Log.d("DERIV_DEBUG", "❌❌❌ 二阶导数计算失败！！！")
                            Log.d("DERIV_DEBUG", "❌❌❌❌❌❌❌❌❌❌❌❌❌❌❌")
                            Log.d("DERIV_DEBUG", "错误信息: ${secondDerivResult.message}")

                            Log.d("DERIV_DEBUG", "")
                            Log.d("DERIV_DEBUG", "========== 调用 appendResultsToDisplay（无二阶导数） ==========")
                            Log.d("DERIV_DEBUG", "参数1 firstDerivText = '${firstDerivResult.displayText}'")
                            Log.d("DERIV_DEBUG", "参数2 secondDerivText = null")

                            appendResultsToDisplay(firstDerivResult.displayText, null)

                            Log.d("DERIV_DEBUG", "✅ appendResultsToDisplay 调用完成（无二阶导数）")

                            hasResult = true
                            disableDerivativeButton()

                            try {
                                val graphData = graphEngine.generateGraphData(currentExpression)
                                binding.graphView.visibility = View.VISIBLE
                                binding.graphView.setGraphData(graphData)
                            } catch (e: Exception) {
                                Log.e("DERIV_DEBUG", "❌ 绘图失败: ${e.message}", e)
                            }
                        }
                    }
                }
                is CalculationResult.Error -> {
                    Log.d("DERIV_DEBUG", "❌ 一阶导数计算失败")
                    Log.d("DERIV_DEBUG", "错误信息: ${firstDerivResult.message}")
                    Toast.makeText(requireContext(), firstDerivResult.message, Toast.LENGTH_LONG).show()
                }
            }
        } catch (e: Exception) {
            Log.e("DERIV_DEBUG", "💥💥💥 发生异常", e)
            Log.e("DERIV_DEBUG", "异常信息: ${e.message}")
            Log.e("DERIV_DEBUG", "堆栈跟踪:", e)
            Toast.makeText(requireContext(), "发生异常: ${e.message}", Toast.LENGTH_LONG).show()
        }

        Log.d("DERIV_DEBUG", "")
        Log.d("DERIV_DEBUG", "=================================================")
        Log.d("DERIV_DEBUG", "========== calculateDerivative END =============")
        Log.d("DERIV_DEBUG", "=================================================")
    }

    private fun appendResultsToDisplay(
        firstDerivText: SpannableString,
        secondDerivText: SpannableString?
    ) {
        Log.d("DERIV_DEBUG", "")
        Log.d("DERIV_DEBUG", ">>> 进入 appendResultsToDisplay <<<")
        Log.d("DERIV_DEBUG", "参数 firstDerivText = '$firstDerivText'")
        Log.d("DERIV_DEBUG", "参数 secondDerivText = ${if (secondDerivText != null) "'$secondDerivText'" else "null"}")

        val currentText = binding.tvDisplay.text
        Log.d("DERIV_DEBUG", "当前显示文本 = '$currentText'")

        val newText = SpannableStringBuilder()

        newText.append(currentText)
        Log.d("DERIV_DEBUG", "步骤1: append currentText")

        newText.append("\nf'(x) = ")
        Log.d("DERIV_DEBUG", "步骤2: append '\\nf'(x) = '")

        newText.append(firstDerivText)
        Log.d("DERIV_DEBUG", "步骤3: append firstDerivText")

        if (secondDerivText != null) {
            Log.d("DERIV_DEBUG", "步骤4: secondDerivText 不为 null，继续添加")
            newText.append("\nf''(x) = ")
            Log.d("DERIV_DEBUG", "步骤4.1: append '\\nf''(x) = '")
            newText.append(secondDerivText)
            Log.d("DERIV_DEBUG", "步骤4.2: append secondDerivText")
        } else {
            Log.d("DERIV_DEBUG", "步骤4: secondDerivText 为 null，跳过")
        }

        Log.d("DERIV_DEBUG", "最终文本内容 = '$newText'")

        binding.tvDisplay.text = newText
        Log.d("DERIV_DEBUG", "✅ 设置 tvDisplay.text 完成")

        Log.d("DERIV_DEBUG", "<<< 退出 appendResultsToDisplay >>>")
        Log.d("DERIV_DEBUG", "")
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