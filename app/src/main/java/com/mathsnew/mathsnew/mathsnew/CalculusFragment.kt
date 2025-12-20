// app/src/main/java/com/mathsnew/mathsnew/CalculusFragment.kt
// 微积分计算器页面 - 支持SpannableString上标显示和语法高亮

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

/**
 * 字符类型枚举
 */
private enum class CharType {
    NUMBER, VARIABLE, OPERATOR, FUNCTION, PAREN, PLACEHOLDER
}

/**
 * 字符信息数据类
 */
private data class CharInfo(
    val char: Char,
    val type: CharType,
    val isSuperscript: Boolean = false,
    val isPlaceholder: Boolean = false
)

/**
 * 微积分计算器Fragment
 *
 * 功能：
 * 1. 数学键盘输入
 * 2. 表达式显示（带上标格式和语法高亮）
 * 3. 微分计算
 * 4. 结果显示（支持上标格式）
 */
class CalculusFragment : Fragment() {

    private var _binding: FragmentCalculusBinding? = null
    private val binding get() = _binding!!

    private var currentExpression = ""
    private val calculusEngine = CalculusEngine()

    private var hasResult = false
    private var blinkAnimator: ValueAnimator? = null

    // 语法高亮颜色定义
    companion object {
        private val COLOR_FUNCTION = Color.parseColor("#2196F3")  // 蓝色
        private val COLOR_VARIABLE = Color.parseColor("#000000")  // 黑色
        private val COLOR_NUMBER = Color.parseColor("#F44336")    // 红色
        private val COLOR_OPERATOR = Color.parseColor("#2E7D32")  // 深绿色
        private val COLOR_PLACEHOLDER = Color.parseColor("#FF6600") // 橙色（占位符）
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

        setupBackButton()
        setupKeyboardListeners()
        setupFunctionButtons()
    }

    /**
     * 设置返回按钮
     */
    private fun setupBackButton() {
        binding.btnBack.setOnClickListener {
            findNavController().navigateUp()
        }
    }

    /**
     * 设置键盘输入监听器
     */
    private fun setupKeyboardListeners() {
        // 数字键
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

        // 运算符
        binding.btnAdd.setOnClickListener { appendToExpression("+") }
        binding.btnSubtract.setOnClickListener { appendToExpression("-") }
        binding.btnMultiply.setOnClickListener { appendToExpression("×") }
        binding.btnDivide.setOnClickListener { appendToExpression("/") }
        binding.btnPower.setOnClickListener { handlePowerInput() }

        // 括号
        binding.btnLeftParen.setOnClickListener { appendToExpression("(") }
        binding.btnRightParen.setOnClickListener { appendToExpression(")") }

        // 变量
        binding.btnX.setOnClickListener { appendToExpression("x") }

        // 常数
        binding.btnPi.setOnClickListener { appendToExpression("π") }

        // 小数点
        binding.btnDot.setOnClickListener { appendToExpression(".") }

        // 控制键
        binding.btnClear.setOnClickListener { clearExpression() }
        binding.btnBackspace.setOnClickListener { backspace() }
    }

    /**
     * 设置函数按钮监听器
     */
    private fun setupFunctionButtons() {
        // 三角函数
        binding.btnSin.setOnClickListener { appendToExpression("sin(") }
        binding.btnCos.setOnClickListener { appendToExpression("cos(") }
        binding.btnTan.setOnClickListener { appendToExpression("tan(") }

        // 对数函数
        binding.btnLn.setOnClickListener { appendToExpression("ln(") }
        binding.btnLog.setOnClickListener { appendToExpression("log(") }

        // 指数函数
        binding.btnE.setOnClickListener { appendToExpression("exp(") }

        // 其他函数
        binding.btnSqrt.setOnClickListener { appendToExpression("sqrt(") }
        binding.btnAbs.setOnClickListener { appendToExpression("abs(") }

        // 微分按钮
        binding.btnDerivative.setOnClickListener { calculateDerivative() }
    }

    /**
     * 处理幂次输入（特殊处理）
     */
    private fun handlePowerInput() {
        // Bug 2 修复：如果已有结果，清空后再输入
        if (hasResult) {
            currentExpression = ""
            hasResult = false
            enableDerivativeButton()
        }

        // 添加 ^n（n是占位符）
        currentExpression += "^n"
        updateDisplayWithBlink()
    }

    /**
     * 添加字符到表达式
     */
    private fun appendToExpression(value: String) {
        // Bug 2 修复：如果已有结果，清空后再输入
        if (hasResult) {
            currentExpression = ""
            hasResult = false
            enableDerivativeButton()
        }

        // 停止闪烁动画
        stopBlinkAnimation()

        // 检查是否需要替换占位符 n
        if (currentExpression.endsWith("^n") && value[0].isDigit()) {
            // 删除占位符 n，添加实际数字
            currentExpression = currentExpression.dropLast(1) + value
        } else {
            currentExpression += value
        }

        updateDisplay()
    }

    /**
     * 清空表达式
     */
    private fun clearExpression() {
        currentExpression = ""
        hasResult = false
        stopBlinkAnimation()
        updateDisplay()
        enableDerivativeButton()
    }

    /**
     * 退格（删除最后一个字符）
     */
    private fun backspace() {
        if (currentExpression.isNotEmpty()) {
            // Bug 2 修复：如果已有结果，退格时先清空
            if (hasResult) {
                currentExpression = ""
                hasResult = false
                enableDerivativeButton()
            } else {
                // 如果最后两个字符是 ^n，一起删除
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

    /**
     * 更新显示（带上标格式和语法高亮）
     */
    private fun updateDisplay() {
        val formattedText = formatExpressionWithHighlight(currentExpression, false)
        binding.tvDisplay.text = formattedText
    }

    /**
     * 更新显示并闪烁占位符
     */
    private fun updateDisplayWithBlink() {
        startBlinkAnimation()
    }

    /**
     * 开始闪烁动画
     */
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

    /**
     * 停止闪烁动画
     */
    private fun stopBlinkAnimation() {
        blinkAnimator?.cancel()
        blinkAnimator = null
    }

    /**
     * 格式化表达式：上标显示 + 语法高亮
     *
     * @param expression 表达式字符串
     * @param shouldBlink 是否需要闪烁占位符
     * @param blinkAlpha 闪烁透明度 (0.0 - 1.0)
     * @return 格式化后的SpannableString
     */
    private fun formatExpressionWithHighlight(
        expression: String,
        shouldBlink: Boolean = false,
        blinkAlpha: Float = 1.0f
    ): SpannableString {
        // 第一步：解析表达式，识别每个字符的类型
        val charInfoList = mutableListOf<CharInfo>()
        var i = 0

        while (i < expression.length) {
            val char = expression[i]

            when {
                // 处理幂次（^后面的是上标）
                char == '^' -> {
                    i++
                    // 跳过^，读取指数部分
                    val isPlaceholder = i < expression.length && expression[i] == 'n'

                    // 读取负号
                    if (i < expression.length && expression[i] == '-') {
                        charInfoList.add(CharInfo(expression[i], CharType.OPERATOR, true, false))
                        i++
                    }

                    // 读取数字或占位符n
                    while (i < expression.length && (expression[i].isDigit() || expression[i] == '.' || expression[i] == 'n')) {
                        val type = if (expression[i] == 'n') CharType.PLACEHOLDER else CharType.NUMBER
                        charInfoList.add(CharInfo(expression[i], type, true, isPlaceholder))
                        i++
                    }
                }

                // 数字
                char.isDigit() || char == '.' -> {
                    charInfoList.add(CharInfo(char, CharType.NUMBER))
                    i++
                }

                // 字母（可能是函数或变量）
                char.isLetter() -> {
                    val start = i
                    val nameBuilder = StringBuilder()
                    while (i < expression.length && expression[i].isLetter()) {
                        nameBuilder.append(expression[i])
                        i++
                    }
                    val name = nameBuilder.toString()

                    // 判断是否是函数
                    val isFunctionName = name in listOf("sin", "cos", "tan", "cot", "sec", "csc",
                                                        "ln", "log", "sqrt", "exp", "abs")
                    val type = if (isFunctionName) CharType.FUNCTION else CharType.VARIABLE

                    // 添加所有字符
                    for (c in name) {
                        charInfoList.add(CharInfo(c, type))
                    }
                }

                // 运算符
                char in "+-×/÷·" -> {
                    charInfoList.add(CharInfo(char, CharType.OPERATOR))
                    i++
                }

                // 括号
                char in "()" -> {
                    charInfoList.add(CharInfo(char, CharType.PAREN))
                    i++
                }

                // 特殊常数
                char == 'π' || char == 'e' -> {
                    charInfoList.add(CharInfo(char, CharType.NUMBER))
                    i++
                }

                else -> {
                    i++
                }
            }
        }

        // 第二步：构建SpannableString
        val displayText = charInfoList.map { it.char }.joinToString("")
        val spannableString = SpannableString(displayText)

        var currentPos = 0
        for (info in charInfoList) {
            val start = currentPos
            val end = currentPos + 1

            // 应用上标
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

            // 应用颜色（语法高亮）
            val color = when {
                info.isPlaceholder && shouldBlink -> {
                    Color.argb(
                        (255 * blinkAlpha).toInt(),
                        255, 100, 0  // 橙色闪烁
                    )
                }
                info.type == CharType.FUNCTION -> COLOR_FUNCTION      // 蓝色
                info.type == CharType.VARIABLE -> COLOR_VARIABLE      // 黑色
                info.type == CharType.NUMBER -> COLOR_NUMBER          // 红色
                info.type == CharType.OPERATOR -> COLOR_OPERATOR      // 深绿色
                info.type == CharType.PLACEHOLDER -> COLOR_PLACEHOLDER // 橙色
                else -> COLOR_VARIABLE  // 默认黑色（括号等）
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

    /**
     * 计算微分
     */
    private fun calculateDerivative() {
        if (currentExpression.isEmpty()) {
            Toast.makeText(
                requireContext(),
                "请先输入表达式",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        // 检查是否有未完成的占位符
        if (currentExpression.contains("^n")) {
            Toast.makeText(
                requireContext(),
                "请完成指数输入",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        // Bug 1 修复：如果已有结果，不允许重复计算
        if (hasResult) {
            return
        }

        // 停止闪烁动画
        stopBlinkAnimation()

        // 调用计算引擎
        when (val result = calculusEngine.calculateDerivative(currentExpression)) {
            is CalculationResult.Success -> {
                // 显示结果（使用SpannableString显示上标）
                appendResultToDisplay(result.displayText)
                hasResult = true
                disableDerivativeButton()
            }
            is CalculationResult.Error -> {
                // 显示错误信息
                Toast.makeText(
                    requireContext(),
                    result.message,
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    /**
     * 添加结果到显示区域
     *
     * @param displayText SpannableString格式的结果文本（带上标）
     */
    private fun appendResultToDisplay(displayText: SpannableString) {
        val resultPrefix = "\nd/dx = "

        // 获取当前显示内容
        val currentText = binding.tvDisplay.text

        // 创建新的SpannableString
        val newText = SpannableStringBuilder()
        newText.append(currentText)
        newText.append(resultPrefix)
        newText.append(displayText)

        // 将完整的SpannableString设置到TextView
        binding.tvDisplay.text = newText
    }

    /**
     * 禁用微分按钮（计算后）
     */
    private fun disableDerivativeButton() {
        binding.btnDerivative.isEnabled = false
        binding.btnDerivative.alpha = 0.5f
    }

    /**
     * 启用微分按钮（新输入时）
     */
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