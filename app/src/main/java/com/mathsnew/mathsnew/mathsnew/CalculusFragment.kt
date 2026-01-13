// app/src/main/java/com/mathsnew/mathsnew/CalculusFragment.kt
// 微积分计算器Fragment - 集成粘贴功能
// ✅ 彻底修复：导出格式优化（表达式之间真正加两个空行）

package com.mathsnew.mathsnew

import android.animation.ValueAnimator
import android.content.ClipboardManager
import android.content.ClipDescription
import android.content.Context
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

    // ✅ 新增：保存导出用的纯文本版本
    private var exportableText = ""

    private lateinit var historyManager: CalculationHistoryManager
    private lateinit var exportHelper: ExportHelper

    private lateinit var clipboardManager: ClipboardManager
    private var clipboardListener: ClipboardManager.OnPrimaryClipChangedListener? = null

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
        setupClipboardListener()
        setupPasteButton()
        setupKeyboardListeners()
        setupFunctionButtons()
    }

    private fun setupDisplayEditText() {
        binding.etInput.apply {
            showSoftInputOnFocus = false
            setHorizontallyScrolling(true)
            isHorizontalScrollBarEnabled = true
        }

        binding.tvDisplay.apply {
            setHorizontallyScrolling(true)
            isHorizontalScrollBarEnabled = true
            movementMethod = android.text.method.ScrollingMovementMethod.getInstance()
            setSingleLine(false)
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

    private fun setupClipboardListener() {
        clipboardManager = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

        clipboardListener = ClipboardManager.OnPrimaryClipChangedListener {
            updatePasteButtonState()
        }

        clipboardManager.addPrimaryClipChangedListener(clipboardListener!!)

        updatePasteButtonState()
    }

    private fun updatePasteButtonState() {
        val hasText = clipboardManager.hasPrimaryClip() &&
                      clipboardManager.primaryClipDescription?.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN) == true

        binding.btnPasteToolbar?.apply {
            isEnabled = hasText
            alpha = if (hasText) 1.0f else 0.3f

            imageTintList = if (hasText) {
                android.content.res.ColorStateList.valueOf(
                    Color.parseColor("#4CAF50")
                )
            } else {
                android.content.res.ColorStateList.valueOf(
                    Color.WHITE
                )
            }
        }
    }

    private fun setupPasteButton() {
        binding.btnPasteToolbar?.setOnClickListener {
            handlePaste()
        }
    }

    private fun handlePaste() {
        Log.d(TAG, "📋 handlePaste() 被调用")

        val clipData = clipboardManager.primaryClip
        if (clipData == null || clipData.itemCount == 0) {
            Toast.makeText(requireContext(), "剪贴板为空", Toast.LENGTH_SHORT).show()
            return
        }

        val pastedText = clipData.getItemAt(0).text?.toString() ?: ""
        Log.d(TAG, "  原始剪贴板内容: '$pastedText'")

        if (pastedText.isEmpty()) {
            Toast.makeText(requireContext(), "剪贴板内容为空", Toast.LENGTH_SHORT).show()
            return
        }

        val cleanedText = cleanPastedExpression(pastedText)
        Log.d(TAG, "  清理后的内容: '$cleanedText'")

        if (currentExpression.isNotEmpty()) {
            showPasteConfirmDialog(cleanedText)
        } else {
            applyPastedText(cleanedText)
        }
    }

    private fun cleanPastedExpression(text: String): String {
        var cleaned = text.trim()

        Log.d(TAG, "清理表达式开始: '$cleaned'")

        cleaned = cleaned.replace("\\s+".toRegex(), "")
        Log.d(TAG, "  移除空格: '$cleaned'")

        cleaned = cleaned.replace("²", "^2")
        cleaned = cleaned.replace("³", "^3")
        cleaned = cleaned.replace("⁴", "^4")
        cleaned = cleaned.replace("⁵", "^5")
        cleaned = cleaned.replace("⁶", "^6")
        cleaned = cleaned.replace("⁷", "^7")
        cleaned = cleaned.replace("⁸", "^8")
        cleaned = cleaned.replace("⁹", "^9")
        cleaned = cleaned.replace("⁰", "^0")
        cleaned = cleaned.replace("¹", "^1")
        Log.d(TAG, "  转换上标: '$cleaned'")

        cleaned = cleaned.replace("·", "×")
        cleaned = cleaned.replace("*", "×")
        cleaned = cleaned.replace("÷", "/")
        Log.d(TAG, "  符号替换: '$cleaned'")

        Log.d(TAG, "清理表达式完成: '$cleaned'")
        return cleaned
    }

    private fun validateMathExpression(expr: String): ValidationResult {
        if (expr.isEmpty()) {
            return ValidationResult(false, "表达式为空")
        }

        val containsLettersPattern = Regex("[a-z]+")
        val letters = containsLettersPattern.findAll(expr)
            .map { it.value }
            .toSet()

        val validFunctions = setOf(
            "sin", "cos", "tan", "cot", "sec", "csc",
            "arcsin", "arccos", "arctan", "arccot", "arcsec", "arccsc",
            "ln", "log", "sqrt", "exp", "abs", "x", "e"
        )

        val invalidFunctions = letters.filter { it !in validFunctions }
        if (invalidFunctions.isNotEmpty()) {
            return ValidationResult(false, "包含不支持的函数或变量: ${invalidFunctions.joinToString(", ")}")
        }

        val basicPattern = Regex("^[0-9x+\\-×/()^.πesincostaqlgxprbqrtabs]+$")
        if (!basicPattern.matches(expr)) {
            return ValidationResult(false, "包含无效字符，仅支持数学表达式")
        }

        var openParens = 0
        for (char in expr) {
            when (char) {
                '(' -> openParens++
                ')' -> openParens--
            }
            if (openParens < 0) {
                return ValidationResult(false, "括号不匹配：右括号过多")
            }
        }
        if (openParens != 0) {
            return ValidationResult(false, "括号不匹配：缺少${if (openParens > 0) "右" else "左"}括号")
        }

        val consecutiveOpsPattern = Regex("[+\\-×/]{2,}")
        if (consecutiveOpsPattern.containsMatchIn(expr)) {
            return ValidationResult(false, "存在连续的运算符")
        }

        if (expr.matches(Regex("^[+\\-×/].*")) && !expr.matches(Regex("^-[0-9x(].*"))) {
            return ValidationResult(false, "表达式不能以运算符开头")
        }

        if (expr.matches(Regex(".*[+\\-×/^]$"))) {
            return ValidationResult(false, "表达式不能以运算符结尾")
        }

        val emptyParensPattern = Regex("\\(\\)")
        if (emptyParensPattern.containsMatchIn(expr)) {
            return ValidationResult(false, "存在空括号")
        }

        val onlyNumbers = Regex("^[0-9.]+$")
        val onlyOperators = Regex("^[+\\-×/^()]+$")
        if (onlyNumbers.matches(expr) || onlyOperators.matches(expr)) {
            return ValidationResult(false, "表达式不完整")
        }

        return ValidationResult(true, "")
    }

    private data class ValidationResult(
        val isValid: Boolean,
        val errorMessage: String
    )

    private fun showPasteConfirmDialog(cleanedText: String) {
        AlertDialog.Builder(requireContext())
            .setTitle("粘贴表达式")
            .setMessage("当前输入将被替换为：\n\n$cleanedText\n\n确认吗？")
            .setPositiveButton("确认") { _, _ ->
                applyPastedText(cleanedText)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun applyPastedText(text: String) {
        Log.d(TAG, "✅ 开始验证粘贴的文本: '$text'")

        val validationResult = validateMathExpression(text)

        if (!validationResult.isValid) {
            Log.e(TAG, "  ❌ 验证失败: ${validationResult.errorMessage}")
            AlertDialog.Builder(requireContext())
                .setTitle("无效的数学表达式")
                .setMessage("${validationResult.errorMessage}\n\n请确保粘贴的是有效的数学表达式。")
                .setPositiveButton("确定", null)
                .show()
            return
        }

        try {
            val parser = ExpressionParser()
            parser.parse(text)
            Log.d(TAG, "  ✅ 解析器验证通过")
        } catch (e: Exception) {
            Log.e(TAG, "  ❌ 解析器验证失败: ${e.message}")
            AlertDialog.Builder(requireContext())
                .setTitle("无法解析表达式")
                .setMessage("表达式格式错误：\n${e.message}\n\n请检查表达式是否正确。")
                .setPositiveButton("确定", null)
                .show()
            return
        }

        if (hasResult) {
            clearResults()
        }

        currentExpression = text
        updateDisplay()

        Toast.makeText(requireContext(), "已粘贴", Toast.LENGTH_SHORT).show()
        Log.d(TAG, "  ✅ 粘贴成功")
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
        exportableText = ""
        stopBlinkAnimation()

        binding.graphView.clearGraph()
        binding.graphView.visibility = View.GONE

        currentExpression = expression
        updateDisplay()

        enableDerivativeButton()

        binding.etInput.visibility = View.VISIBLE
        binding.tvDisplay.visibility = View.GONE

        Toast.makeText(requireContext(), "已加载历史表达式", Toast.LENGTH_SHORT).show()
    }

    private fun clearAll() {
        Log.d(TAG, "🔄 clearAll() 开始")

        currentExpression = ""
        hasResult = false
        lastResult = null
        exportableText = ""

        stopBlinkAnimation()

        updateDisplay()
        enableDerivativeButton()

        binding.graphView.clearGraph()
        binding.graphView.visibility = View.GONE

        binding.etInput.setText("")
        binding.tvDisplay.setText("")

        binding.etInput.visibility = View.VISIBLE
        binding.tvDisplay.visibility = View.GONE

        Log.d(TAG, "✅ clearAll() 完成: hasResult=$hasResult, expr='$currentExpression'")
    }

    private fun exportResults() {
        if (!hasResult) {
            Toast.makeText(requireContext(), "没有可导出的内容", Toast.LENGTH_SHORT).show()
            return
        }

        if (exportableText.isEmpty()) {
            Toast.makeText(requireContext(), "导出内容为空", Toast.LENGTH_SHORT).show()
            return
        }

        // ✅ 使用保存的导出文本
        exportHelper.exportAndCopy(exportableText)
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

        if (currentExpression.endsWith("^n") && value.length == 1 && value[0].isDigit()) {
            currentExpression = currentExpression.dropLast(1) + value
            Log.d(TAG, "  → 替换 ^n 为 ^$value: expr='$currentExpression'")
        } else {
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
        exportableText = ""

        stopBlinkAnimation()
        updateDisplay()
        enableDerivativeButton()

        binding.graphView.clearGraph()
        binding.graphView.visibility = View.GONE

        binding.etInput.setText("")
        binding.tvDisplay.setText("")

        binding.etInput.visibility = View.VISIBLE
        binding.tvDisplay.visibility = View.GONE

        Log.d(TAG, "✅ clearExpression() 完成")
    }

    private fun clearResults() {
        Log.d(TAG, "🔄 clearResults() 开始")

        currentExpression = ""
        hasResult = false
        lastResult = null
        exportableText = ""

        stopBlinkAnimation()

        updateDisplay()
        enableDerivativeButton()

        binding.graphView.clearGraph()
        binding.graphView.visibility = View.GONE

        binding.etInput.setText("")
        binding.tvDisplay.setText("")

        binding.etInput.visibility = View.VISIBLE
        binding.tvDisplay.visibility = View.GONE

        Log.d(TAG, "✅ clearResults() 完成: hasResult=$hasResult, expr='$currentExpression'")
    }

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
        binding.etInput.setText(formattedText)
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
                binding.etInput.setText(formattedText)
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

        builder.append(binding.etInput.text)

        builder.append("\n\nf(x) = ")
        builder.append(currentExpression)
        builder.append("\n\n")

        builder.append("f'(x) = ")
        builder.append(result.displayText)

        if (result.secondDerivativeDisplayText != null) {
            builder.append("\n\nf''(x) = ")
            builder.append(result.secondDerivativeDisplayText)
        }

        binding.tvDisplay.setText(builder)

        binding.etInput.visibility = View.GONE
        binding.tvDisplay.visibility = View.VISIBLE
    }

    private fun appendMultiFormResultToDisplay(result: CalculationResultV2.Success) {
        val displayBuilder = SpannableStringBuilder()
        val exportBuilder = StringBuilder()

        // ✅ 第一行：原始输入表达式
        displayBuilder.append(binding.etInput.text)
        exportBuilder.append(currentExpression)
        exportBuilder.append("\n\n\n")  // ✅ 空两行

        // ✅ f(x) = ...
        displayBuilder.append("\n\nf(x) = ")
        displayBuilder.append(currentExpression)
        exportBuilder.append("f(x) = ")
        exportBuilder.append(currentExpression)
        exportBuilder.append("\n\n\n")  // ✅ 空两行

        val displayForms = result.forms.getDisplayForms()

        // ✅ f'(x) = ...
        displayBuilder.append("\n\n")
        displayBuilder.append("f'(x) = ")
        exportBuilder.append("f'(x) = ")

        for ((index, form) in displayForms.withIndex()) {
            if (index > 0) {
                displayBuilder.append("\n\n      = ")
                exportBuilder.append("\n\n      = ")  // ✅ 同一表达式不同形式：一个空行
            }

            val formatted = formatter.format(form.expression.toString())

            Log.d(TAG, "🔍 f'(x) form[$index]:")
            Log.d(TAG, "  原始表达式: ${form.expression}")
            Log.d(TAG, "  格式化plainText: ${formatted.plainText}")
            Log.d(TAG, "  格式化displayText: ${formatted.displayText}")
            Log.d(TAG, "  导出文本: ${formatted.exportText}")

            displayBuilder.append(formatted.displayText)
            exportBuilder.append(formatted.exportText)
        }

        // ✅ f''(x) = ...
        if (result.secondDerivativeForms != null) {
            val secondDisplayForms = result.secondDerivativeForms.getDisplayForms()

            displayBuilder.append("\n\n")
            exportBuilder.append("\n\n\n\n")  // ✅ 空两行

            for ((index, form) in secondDisplayForms.withIndex()) {
                val formatted = formatter.format(form.expression.toString())

                if (index == 0) {
                    displayBuilder.append("f''(x) = ")
                    exportBuilder.append("f''(x) = ")

                    Log.d(TAG, "🔍 f''(x) form[$index]:")
                    Log.d(TAG, "  原始表达式: ${form.expression}")
                    Log.d(TAG, "  格式化plainText: ${formatted.plainText}")
                    Log.d(TAG, "  格式化displayText: ${formatted.displayText}")
                    Log.d(TAG, "  导出文本: ${formatted.exportText}")
                } else {
                    displayBuilder.append("\n\n       = ")
                    exportBuilder.append("\n\n       = ")  // ✅ 同一表达式不同形式：一个空行
                }

                displayBuilder.append(formatted.displayText)
                exportBuilder.append(formatted.exportText)
            }
        } else if (result.secondDerivativeDisplayText != null) {
            displayBuilder.append("\n\n")
            displayBuilder.append("f''(x) = ")
            displayBuilder.append(result.secondDerivativeDisplayText)
            exportBuilder.append("\n\n\n\nf''(x) = ")
            exportBuilder.append(result.secondDerivativeDisplayText.toString())
        }

        // ✅ 保存导出文本
        exportableText = exportBuilder.toString()

        Log.d(TAG, "🔍 最终构建的文本:")
        Log.d(TAG, "  显示总长度: ${displayBuilder.length}")
        Log.d(TAG, "  导出总长度: ${exportableText.length}")
        Log.d(TAG, "  导出文本:\n$exportableText")

        binding.tvDisplay.setText(displayBuilder)

        binding.tvDisplay.post {
            binding.tvDisplay.apply {
                setHorizontallyScrolling(true)
                requestLayout()
            }
        }

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

        clipboardListener?.let {
            clipboardManager.removePrimaryClipChangedListener(it)
        }

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