// app/src/main/java/com/mathsnew/mathsnew/FractionSpan.kt
// 自定义分数线 Span - 完美显示数学分式（支持格式化文本）

package com.mathsnew.mathsnew

import android.graphics.Canvas
import android.graphics.Paint
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.ReplacementSpan
import kotlin.math.max

/**
 * 自定义分数线 Span
 *
 * 使用 StaticLayout 渲染分子分母，完整保留 SpannableString 的格式
 * （上标、颜色、字体大小等）
 *
 * 用法示例：
 * val numerator = createSimpleSpannable("x·cos(x) - sin(x)")
 * val denominator = createSimpleSpannable("x²")
 * val fraction = FractionSpan(numerator, denominator)
 */
class FractionSpan(
    private val numerator: CharSequence,
    private val denominator: CharSequence,
    private val textSize: Float = 48f,
    private val lineThickness: Float = 3f,
    private val padding: Float = 12f
) : ReplacementSpan() {

    private var numeratorWidth = 0f
    private var denominatorWidth = 0f
    private var fractionWidth = 0f
    private var fractionHeight = 0f

    private var numeratorHeight = 0f
    private var denominatorHeight = 0f

    private var numeratorLayout: StaticLayout? = null
    private var denominatorLayout: StaticLayout? = null

    override fun getSize(
        paint: Paint,
        text: CharSequence?,
        start: Int,
        end: Int,
        fm: Paint.FontMetricsInt?
    ): Int {
        val textPaint = TextPaint(paint)
        textPaint.textSize = textSize

        // 创建 StaticLayout 来测量分子和分母的尺寸
        numeratorLayout = StaticLayout.Builder
            .obtain(numerator, 0, numerator.length, textPaint, Int.MAX_VALUE)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(0f, 1f)
            .setIncludePad(false)
            .build()

        denominatorLayout = StaticLayout.Builder
            .obtain(denominator, 0, denominator.length, textPaint, Int.MAX_VALUE)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(0f, 1f)
            .setIncludePad(false)
            .build()

        // 获取实际渲染宽度
        numeratorWidth = numeratorLayout?.getLineWidth(0) ?: 0f
        denominatorWidth = denominatorLayout?.getLineWidth(0) ?: 0f

        // 获取实际渲染高度
        numeratorHeight = (numeratorLayout?.height?.toFloat() ?: 0f)
        denominatorHeight = (denominatorLayout?.height?.toFloat() ?: 0f)

        // 分数总宽度取较大者
        fractionWidth = max(numeratorWidth, denominatorWidth) + padding * 2

        // 分数总高度 = 分子高度 + 分数线 + 分母高度 + 内边距
        fractionHeight = numeratorHeight + lineThickness + denominatorHeight + padding * 2

        // 设置 FontMetrics 以正确对齐
        if (fm != null) {
            // 让分数垂直居中对齐
            val halfHeight = (fractionHeight / 2).toInt()
            fm.ascent = -halfHeight
            fm.descent = halfHeight
            fm.top = fm.ascent
            fm.bottom = fm.descent
        }

        return fractionWidth.toInt()
    }

    override fun draw(
        canvas: Canvas,
        text: CharSequence?,
        start: Int,
        end: Int,
        x: Float,
        top: Int,
        y: Int,
        bottom: Int,
        paint: Paint
    ) {
        // 确保 Layout 已创建
        if (numeratorLayout == null || denominatorLayout == null) {
            return
        }

        val textPaint = TextPaint(paint)
        textPaint.textSize = textSize
        textPaint.isAntiAlias = true

        // 基线位置
        val centerY = y.toFloat()

        // 分数线的 Y 坐标（在基线位置）
        val lineY = centerY

        // 分子的 Y 坐标（分数线上方）
        val numeratorY = lineY - lineThickness / 2 - padding - numeratorHeight

        // 分母的 Y 坐标（分数线下方）
        val denominatorY = lineY + lineThickness / 2 + padding

        // 分子的 X 坐标（居中对齐）
        val numeratorX = x + (fractionWidth - numeratorWidth) / 2

        // 分母的 X 坐标（居中对齐）
        val denominatorX = x + (fractionWidth - denominatorWidth) / 2

        // 保存 canvas 状态
        canvas.save()

        // 绘制分子（使用 StaticLayout 保留格式）
        canvas.translate(numeratorX, numeratorY)
        numeratorLayout?.draw(canvas)
        canvas.restore()

        // 绘制分数线
        canvas.save()
        val lineStartX = x + padding / 2
        val lineEndX = x + fractionWidth - padding / 2
        textPaint.strokeWidth = lineThickness
        canvas.drawLine(lineStartX, lineY, lineEndX, lineY, textPaint)
        canvas.restore()

        // 绘制分母（使用 StaticLayout 保留格式）
        canvas.save()
        canvas.translate(denominatorX, denominatorY)
        denominatorLayout?.draw(canvas)
        canvas.restore()
    }
}

/**
 * 分数信息数据类
 */
data class FractionInfo(
    val numerator: String,
    val denominator: String,
    val startIndex: Int,
    val endIndex: Int
)