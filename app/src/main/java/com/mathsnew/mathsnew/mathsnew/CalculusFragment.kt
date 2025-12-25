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
        Log.d("CalculusFragment", "========================================")
        Log.d("CalculusFragment", "===== calculateDerivative 被调用 =====")
        Log.d("CalculusFragment", "currentExpression = '$currentExpression'")

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

        try {
            Log.d("CalculusFragment", "开始计算一阶导数...")
            when (val firstDerivResult = calculusEngine.calculateDerivative(currentExpression)) {
                is CalculationResult.Success -> {
                    Log.d("CalculusFragment", "✅ 一阶导数计算成功")

                    val firstDerivExpression = firstDerivResult.result

                    Log.d("CalculusFragment", "开始计算二阶导数...")
                    when (val secondDerivResult = calculusEngine.calculateDerivative(firstDerivExpression)) {
                        is CalculationResult.Success -> {
                            Log.d("CalculusFragment", "✅ 二阶导数计算成功")

                            appendResultsToDisplay(
                                firstDerivResult.displayText,
                                secondDerivResult.displayText
                            )

                            hasResult = true
                            disableDerivativeButton()

                            Log.d("CalculusFragment", "开始生成图像数据...")
                            try {
                                val graphData = graphEngine.generateGraphData(currentExpression)
                                binding.graphView.visibility = View.VISIBLE
                                binding.graphView.setGraphData(graphData)
                                Log.d("CalculusFragment", "✅ 图像绘制完成")
                            } catch (e: Exception) {
                                Log.e("CalculusFragment", "❌ 绘图失败: ${e.message}", e)
                                Toast.makeText(requireContext(), "绘图失败: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                        is CalculationResult.Error -> {
                            Log.d("CalculusFragment", "⚠️ 二阶导数计算失败: ${secondDerivResult.message}")

                            appendResultsToDisplay(firstDerivResult.displayText, null)
                            hasResult = true
                            disableDerivativeButton()

                            try {
                                val graphData = graphEngine.generateGraphData(currentExpression)
                                binding.graphView.visibility = View.VISIBLE
                                binding.graphView.setGraphData(graphData)
                            } catch (e: Exception) {
                                Log.e("CalculusFragment", "❌ 绘图失败: ${e.message}", e)
                            }
                        }
                    }
                }
                is CalculationResult.Error -> {
                    Log.d("CalculusFragment", "❌ 一阶导数计算失败: ${firstDerivResult.message}")
                    Toast.makeText(requireContext(), firstDerivResult.message, Toast.LENGTH_LONG).show()
                }
            }
        } catch (e: Exception) {
            Log.e("CalculusFragment", "💥 发生异常: ${e.message}", e)
            Toast.makeText(requireContext(), "发生异常: ${e.message}", Toast.LENGTH_LONG).show()
        }

        Log.d("CalculusFragment", "===== calculateDerivative 结束 =====")
        Log.d("CalculusFragment", "========================================")
    }

    private fun appendResultsToDisplay(
        firstDerivText: SpannableString,
        secondDerivText: SpannableString?
    ) {
        val currentText = binding.tvDisplay.text
        val newText = SpannableStringBuilder()

        newText.append(currentText)
        newText.append("\nf'(x) = ")
        newText.append(firstDerivText)

        if (secondDerivText != null) {
            newText.append("\nf''(x) = ")
            newText.append(secondDerivText)
        }

        binding.tvDisplay.text = newText
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