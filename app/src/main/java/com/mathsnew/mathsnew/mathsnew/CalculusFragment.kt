// app/src/main/java/com/mathsnew/mathsnew/CalculusFragment.kt
// 微积分计算器页面 - 支持SpannableString上标显示

package com.mathsnew.mathsnew

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.mathsnew.mathsnew.databinding.FragmentCalculusBinding

/**
 * 微积分计算器Fragment
 *
 * 功能：
 * 1. 数学键盘输入
 * 2. 表达式显示
 * 3. 微分计算
 * 4. 结果显示（支持上标格式）
 */
class CalculusFragment : Fragment() {

    private var _binding: FragmentCalculusBinding? = null
    private val binding get() = _binding!!

    private var currentExpression = ""
    private val calculusEngine = CalculusEngine()

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
        binding.btnDivide.setOnClickListener { appendToExpression("÷") }
        binding.btnPower.setOnClickListener { appendToExpression("^") }

        // 括号
        binding.btnLeftParen.setOnClickListener { appendToExpression("(") }
        binding.btnRightParen.setOnClickListener { appendToExpression(")") }

        // 变量
        binding.btnX.setOnClickListener { appendToExpression("x") }

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

        // 微分按钮
        binding.btnDerivative.setOnClickListener { calculateDerivative() }
    }

    /**
     * 添加字符到表达式
     */
    private fun appendToExpression(value: String) {
        currentExpression += value
        updateDisplay()
    }

    /**
     * 清空表达式
     */
    private fun clearExpression() {
        currentExpression = ""
        updateDisplay()
    }

    /**
     * 退格（删除最后一个字符）
     */
    private fun backspace() {
        if (currentExpression.isNotEmpty()) {
            currentExpression = currentExpression.dropLast(1)
            updateDisplay()
        }
    }

    /**
     * 更新显示
     */
    private fun updateDisplay() {
        binding.tvDisplay.text = currentExpression
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

        // 调用计算引擎
        when (val result = calculusEngine.calculateDerivative(currentExpression)) {
            is CalculationResult.Success -> {
                // 显示结果（使用SpannableString显示上标）
                appendResultToDisplay(result.displayText)
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
    private fun appendResultToDisplay(displayText: android.text.SpannableString) {
        val resultPrefix = "\nd/dx = "

        // 获取当前显示内容
        val currentText = binding.tvDisplay.text.toString()

        // 创建新的SpannableString
        val newText = android.text.SpannableStringBuilder()
        newText.append(currentText)
        newText.append(resultPrefix)
        newText.append(displayText)

        // 将完整的SpannableString设置到TextView
        binding.tvDisplay.text = newText
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}