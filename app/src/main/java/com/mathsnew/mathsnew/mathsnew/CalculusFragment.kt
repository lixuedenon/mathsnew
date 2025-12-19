// app/src/main/java/com/mathsnew/mathsnew/CalculusFragment.kt
// 微积分计算器Fragment - 包含显示区域和键盘

package com.mathsnew.mathsnew

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.mathsnew.mathsnew.databinding.FragmentCalculusBinding

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
        binding.btnDivide.setOnClickListener { appendToExpression("÷") }

        binding.btnLeftParen.setOnClickListener { appendToExpression("(") }
        binding.btnRightParen.setOnClickListener { appendToExpression(")") }

        binding.btnDot.setOnClickListener { appendToExpression(".") }

        binding.btnX.setOnClickListener { appendToExpression("x") }
        binding.btnPi.setOnClickListener { appendToExpression("π") }
        binding.btnE.setOnClickListener { appendToExpression("e") }

        binding.btnSin.setOnClickListener { appendToExpression("sin(") }
        binding.btnCos.setOnClickListener { appendToExpression("cos(") }
        binding.btnTan.setOnClickListener { appendToExpression("tan(") }
        binding.btnLn.setOnClickListener { appendToExpression("ln(") }
        binding.btnLog.setOnClickListener { appendToExpression("log(") }
        binding.btnSqrt.setOnClickListener { appendToExpression("√(") }
        binding.btnPower.setOnClickListener { appendToExpression("^") }
        binding.btnAbs.setOnClickListener { appendToExpression("|") }

        binding.btnClear.setOnClickListener { clearExpression() }
        binding.btnBackspace.setOnClickListener { backspace() }
    }

    private fun setupFunctionButtons() {
        binding.btnDerivative.setOnClickListener {
            calculateDerivative()
        }

        binding.btnIntegral.setOnClickListener {
            calculateIntegral()
        }

        binding.btnShowSteps.setOnClickListener {
            showCalculationSteps()
        }
    }

    private fun appendToExpression(value: String) {
        currentExpression += value
        updateDisplay()
    }

    private fun clearExpression() {
        currentExpression = ""
        updateDisplay()
    }

    private fun backspace() {
        if (currentExpression.isNotEmpty()) {
            currentExpression = currentExpression.dropLast(1)
            updateDisplay()
        }
    }

    private fun updateDisplay() {
        binding.tvDisplay.text = if (currentExpression.isEmpty()) {
            getString(R.string.display_hint)
        } else {
            currentExpression
        }
    }

    /**
     * 计算微分
     */
    private fun calculateDerivative() {
        if (currentExpression.isEmpty()) {
            Toast.makeText(requireContext(), "请先输入表达式", Toast.LENGTH_SHORT).show()
            return
        }

        when (val result = calculusEngine.calculateDerivative(currentExpression)) {
            is CalculationResult.Success -> {
                appendResultToDisplay("d/dx = ${result.result}")
            }
            is CalculationResult.Error -> {
                Toast.makeText(requireContext(), result.message, Toast.LENGTH_LONG).show()
            }
        }
    }

    /**
     * 计算积分
     */
    private fun calculateIntegral() {
        if (currentExpression.isEmpty()) {
            Toast.makeText(requireContext(), "请先输入表达式", Toast.LENGTH_SHORT).show()
            return
        }

        when (val result = calculusEngine.calculateIntegral(currentExpression)) {
            is CalculationResult.Success -> {
                appendResultToDisplay("∫dx = ${result.result}")
            }
            is CalculationResult.Error -> {
                Toast.makeText(requireContext(), result.message, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun showCalculationSteps() {
        Toast.makeText(requireContext(), "计算步骤功能待实现", Toast.LENGTH_SHORT).show()
    }

    private fun appendResultToDisplay(result: String) {
        currentExpression += "\n$result"
        updateDisplay()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}