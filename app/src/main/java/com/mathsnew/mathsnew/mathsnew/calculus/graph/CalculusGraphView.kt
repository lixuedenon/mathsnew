// app/src/main/java/com/mathsnew/mathsnew/calculus/graph/CalculusGraphView.kt
// 微积分图像绘制View - 支持动态高度

package com.mathsnew.mathsnew.calculus.graph

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.util.Log
import android.view.View
import kotlin.math.abs
import kotlin.math.roundToInt

class CalculusGraphView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var graphData: GraphData? = null

    companion object {
        private const val PADDING = 80f
        private const val AXIS_TEXT_SIZE = 30f
        private const val LEGEND_TEXT_SIZE = 28f
        private const val POINT_RADIUS = 8f
        private const val LABEL_TEXT_SIZE = 24f

        private const val MIN_HEIGHT_DP = 400
        private const val MAX_HEIGHT_DP = 1000
        private const val HEIGHT_SCALE_FACTOR = 50

        private val COLOR_ORIGINAL = Color.parseColor("#2196F3")
        private val COLOR_FIRST_DERIV = Color.parseColor("#F44336")
        private val COLOR_SECOND_DERIV = Color.parseColor("#388E3C")
        private val COLOR_AXIS = Color.parseColor("#757575")
        private val COLOR_GRID = Color.parseColor("#E0E0E0")
        private val COLOR_CRITICAL_POINT = Color.parseColor("#F57C00")
        private val COLOR_INFLECTION_POINT = Color.parseColor("#7B1FA2")
    }

    private val axisPaint = Paint().apply {
        color = COLOR_AXIS
        strokeWidth = 3f
        style = Paint.Style.STROKE
        isAntiAlias = true
    }

    private val gridPaint = Paint().apply {
        color = COLOR_GRID
        strokeWidth = 1f
        style = Paint.Style.STROKE
        isAntiAlias = true
        pathEffect = DashPathEffect(floatArrayOf(10f, 10f), 0f)
    }

    private val originalCurvePaint = Paint().apply {
        color = COLOR_ORIGINAL
        strokeWidth = 4f
        style = Paint.Style.STROKE
        isAntiAlias = true
    }

    private val firstDerivCurvePaint = Paint().apply {
        color = COLOR_FIRST_DERIV
        strokeWidth = 4f
        style = Paint.Style.STROKE
        isAntiAlias = true
    }

    private val secondDerivCurvePaint = Paint().apply {
        color = COLOR_SECOND_DERIV
        strokeWidth = 4f
        style = Paint.Style.STROKE
        isAntiAlias = true
    }

    private val criticalPointPaint = Paint().apply {
        color = COLOR_CRITICAL_POINT
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val inflectionPointPaint = Paint().apply {
        color = COLOR_INFLECTION_POINT
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val textPaint = Paint().apply {
        color = COLOR_AXIS
        textSize = AXIS_TEXT_SIZE
        isAntiAlias = true
        textAlign = Paint.Align.CENTER
    }

    private val legendPaint = Paint().apply {
        color = Color.BLACK
        textSize = LEGEND_TEXT_SIZE
        isAntiAlias = true
    }

    private val labelPaint = Paint().apply {
        color = Color.BLACK
        textSize = LABEL_TEXT_SIZE
        isAntiAlias = true
        textAlign = Paint.Align.CENTER
    }

    fun setGraphData(data: GraphData) {
        Log.d("CalculusGraphView", "设置图像数据")
        this.graphData = data
        requestLayout()
        invalidate()
    }

    fun clearGraph() {
        Log.d("CalculusGraphView", "清空图像")
        this.graphData = null
        requestLayout()
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)

        val data = graphData
        val height = if (data != null) {
            val yRange = data.yMax - data.yMin
            val idealHeight = (yRange * HEIGHT_SCALE_FACTOR).toInt()

            idealHeight.coerceIn(
                dpToPx(MIN_HEIGHT_DP),
                dpToPx(MAX_HEIGHT_DP)
            )
        } else {
            dpToPx(MIN_HEIGHT_DP)
        }

        Log.d("CalculusGraphView", "计算高度: ${height}px (${pxToDp(height)}dp)")
        setMeasuredDimension(width, height)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val data = graphData
        if (data == null) {
            drawEmptyState(canvas)
            return
        }

        Log.d("CalculusGraphView", "开始绘制图像")

        drawGrid(canvas, data)
        drawAxes(canvas, data)
        drawCurves(canvas, data)
        drawCriticalPoints(canvas, data)
        drawInflectionPoints(canvas, data)
        drawLegend(canvas)

        Log.d("CalculusGraphView", "图像绘制完成")
    }

    private fun drawEmptyState(canvas: Canvas) {
        val centerX = width / 2f
        val centerY = height / 2f

        textPaint.textSize = 40f
        textPaint.color = Color.GRAY
        canvas.drawText("等待图像数据...", centerX, centerY, textPaint)
    }

    private fun drawGrid(canvas: Canvas, data: GraphData) {
        val gridSpacing = calculateGridSpacing(data)

        var x = data.xMin
        while (x <= data.xMax) {
            val screenX = toScreenX(x.toDouble(), data)
            if (screenX in PADDING..(width - PADDING)) {
                canvas.drawLine(screenX, PADDING, screenX, height - PADDING, gridPaint)
            }
            x += gridSpacing
        }

        var y = data.yMin
        while (y <= data.yMax) {
            val screenY = toScreenY(y.toDouble(), data)
            if (screenY in PADDING..(height - PADDING)) {
                canvas.drawLine(PADDING, screenY, width - PADDING, screenY, gridPaint)
            }
            y += gridSpacing
        }
    }

    private fun calculateGridSpacing(data: GraphData): Float {
        val xRange = data.xMax - data.xMin
        val yRange = data.yMax - data.yMin
        val range = maxOf(xRange, yRange)

        return when {
            range > 50 -> 10f
            range > 20 -> 5f
            range > 10 -> 2f
            else -> 1f
        }
    }

    private fun drawAxes(canvas: Canvas, data: GraphData) {
        val xAxisY = toScreenY(0.0, data)
        val yAxisX = toScreenX(0.0, data)

        canvas.drawLine(PADDING, xAxisY, width - PADDING, xAxisY, axisPaint)
        canvas.drawLine(yAxisX, PADDING, yAxisX, height - PADDING, axisPaint)

        drawAxisLabels(canvas, data, xAxisY, yAxisX)
    }

    private fun drawAxisLabels(canvas: Canvas, data: GraphData, xAxisY: Float, yAxisX: Float) {
        textPaint.textSize = AXIS_TEXT_SIZE

        val gridSpacing = calculateGridSpacing(data)

        var x = data.xMin
        while (x <= data.xMax) {
            if (abs(x) > 0.1f) {
                val screenX = toScreenX(x.toDouble(), data)
                if (screenX in PADDING..(width - PADDING)) {
                    canvas.drawLine(screenX, xAxisY - 10, screenX, xAxisY + 10, axisPaint)
                    canvas.drawText(
                        formatNumber(x.toDouble()),
                        screenX,
                        xAxisY + 40,
                        textPaint
                    )
                }
            }
            x += gridSpacing
        }

        var y = data.yMin
        while (y <= data.yMax) {
            if (abs(y) > 0.1f) {
                val screenY = toScreenY(y.toDouble(), data)
                if (screenY in PADDING..(height - PADDING)) {
                    canvas.drawLine(yAxisX - 10, screenY, yAxisX + 10, screenY, axisPaint)

                    textPaint.textAlign = Paint.Align.RIGHT
                    canvas.drawText(
                        formatNumber(y.toDouble()),
                        yAxisX - 15,
                        screenY + 10,
                        textPaint
                    )
                    textPaint.textAlign = Paint.Align.CENTER
                }
            }
            y += gridSpacing
        }
    }

    private fun drawCurves(canvas: Canvas, data: GraphData) {
        drawCurve(canvas, data.originalPoints, data, originalCurvePaint)
        drawCurve(canvas, data.firstDerivativePoints, data, firstDerivCurvePaint)
        drawCurve(canvas, data.secondDerivativePoints, data, secondDerivCurvePaint)
    }

    private fun drawCurve(canvas: Canvas, points: List<PointF>, data: GraphData, paint: Paint) {
        if (points.isEmpty()) return

        val path = Path()
        var isFirstPoint = true
        var lastValidPoint: PointF? = null

        for (point in points) {
            if (!point.y.isFinite()) {
                isFirstPoint = true
                lastValidPoint = null
                continue
            }

            val screenX = toScreenX(point.x.toDouble(), data)
            val screenY = toScreenY(point.y.toDouble(), data)

            if (screenX < PADDING || screenX > width - PADDING ||
                screenY < PADDING || screenY > height - PADDING) {
                continue
            }

            if (isFirstPoint) {
                path.moveTo(screenX, screenY)
                isFirstPoint = false
            } else {
                if (lastValidPoint != null) {
                    val lastScreenY = toScreenY(lastValidPoint.y.toDouble(), data)
                    val dy = abs(screenY - lastScreenY)

                    if (dy > height / 4) {
                        path.moveTo(screenX, screenY)
                    } else {
                        path.lineTo(screenX, screenY)
                    }
                } else {
                    path.lineTo(screenX, screenY)
                }
            }

            lastValidPoint = point
        }

        canvas.drawPath(path, paint)
    }

    private fun drawCriticalPoints(canvas: Canvas, data: GraphData) {
        for (point in data.criticalPoints) {
            val screenX = toScreenX(point.x, data)
            val screenY = toScreenY(point.y, data)

            if (screenX < PADDING || screenX > width - PADDING ||
                screenY < PADDING || screenY > height - PADDING) {
                continue
            }

            canvas.drawCircle(screenX, screenY, POINT_RADIUS, criticalPointPaint)

            val label = when (point.type) {
                CriticalPointType.MAX -> "极大值"
                CriticalPointType.MIN -> "极小值"
            }

            labelPaint.color = COLOR_CRITICAL_POINT
            canvas.drawText(
                "$label(${formatNumber(point.x)}, ${formatNumber(point.y)})",
                screenX,
                screenY - 20,
                labelPaint
            )
        }
    }

    private fun drawInflectionPoints(canvas: Canvas, data: GraphData) {
        for (point in data.inflectionPoints) {
            val screenX = toScreenX(point.x, data)
            val screenY = toScreenY(point.y, data)

            if (screenX < PADDING || screenX > width - PADDING ||
                screenY < PADDING || screenY > height - PADDING) {
                continue
            }

            canvas.drawCircle(screenX, screenY, POINT_RADIUS, inflectionPointPaint)

            labelPaint.color = COLOR_INFLECTION_POINT
            canvas.drawText(
                "拐点(${formatNumber(point.x)}, ${formatNumber(point.y)})",
                screenX,
                screenY + 35,
                labelPaint
            )
        }
    }

    private fun drawLegend(canvas: Canvas) {
        val legendX = width - PADDING - 150f
        val legendY = PADDING + 30f
        val lineLength = 40f
        val lineSpacing = 35f

        legendPaint.textSize = LEGEND_TEXT_SIZE

        canvas.drawLine(legendX, legendY, legendX + lineLength, legendY, originalCurvePaint)
        canvas.drawText("f(x)", legendX + lineLength + 10, legendY + 8, legendPaint)

        canvas.drawLine(
            legendX,
            legendY + lineSpacing,
            legendX + lineLength,
            legendY + lineSpacing,
            firstDerivCurvePaint
        )
        canvas.drawText("f'(x)", legendX + lineLength + 10, legendY + lineSpacing + 8, legendPaint)

        canvas.drawLine(
            legendX,
            legendY + lineSpacing * 2,
            legendX + lineLength,
            legendY + lineSpacing * 2,
            secondDerivCurvePaint
        )
        canvas.drawText("f''(x)", legendX + lineLength + 10, legendY + lineSpacing * 2 + 8, legendPaint)
    }

    private fun toScreenX(mathX: Double, data: GraphData): Float {
        val xRange = data.xMax - data.xMin
        val screenWidth = width - 2 * PADDING
        return PADDING + ((mathX - data.xMin) / xRange * screenWidth).toFloat()
    }

    private fun toScreenY(mathY: Double, data: GraphData): Float {
        val yRange = data.yMax - data.yMin
        val screenHeight = height - 2 * PADDING
        return height - PADDING - ((mathY - data.yMin) / yRange * screenHeight).toFloat()
    }

    private fun formatNumber(value: Double): String {
        return when {
            abs(value) < 0.01 -> "0"
            abs(value - value.roundToInt()) < 0.01 -> value.roundToInt().toString()
            else -> String.format("%.1f", value)
        }
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    private fun pxToDp(px: Int): Int {
        return (px / resources.displayMetrics.density).toInt()
    }
}