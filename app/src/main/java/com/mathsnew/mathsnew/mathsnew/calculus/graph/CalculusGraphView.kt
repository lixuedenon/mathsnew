// app/src/main/java/com/mathsnew/mathsnew/calculus/graph/CalculusGraphView.kt
// 微积分图形视图

package com.mathsnew.mathsnew.calculus.graph

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class CalculusGraphView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var graphData: GraphData? = null

    private val originalCurvePaint = Paint().apply {
        color = Color.parseColor("#2196F3")
        strokeWidth = 4f
        style = Paint.Style.STROKE
        isAntiAlias = true
    }

    private val firstDerivCurvePaint = Paint().apply {
        color = Color.parseColor("#4CAF50")
        strokeWidth = 3f
        style = Paint.Style.STROKE
        isAntiAlias = true
    }

    private val secondDerivCurvePaint = Paint().apply {
        color = Color.parseColor("#FF9800")
        strokeWidth = 3f
        style = Paint.Style.STROKE
        isAntiAlias = true
    }

    private val axisPaint = Paint().apply {
        color = Color.GRAY
        strokeWidth = 2f
        style = Paint.Style.STROKE
        isAntiAlias = true
    }

    private val gridPaint = Paint().apply {
        color = Color.LTGRAY
        strokeWidth = 1f
        style = Paint.Style.STROKE
        isAntiAlias = true
    }

    private val criticalPointPaint = Paint().apply {
        color = Color.RED
        strokeWidth = 3f
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val textPaint = Paint().apply {
        color = Color.BLACK
        textSize = 24f
        isAntiAlias = true
    }

    private var xMin = -10.0
    private var xMax = 10.0
    private var yMin = -10.0
    private var yMax = 10.0

    fun setGraphData(data: GraphData) {
        this.graphData = data
        this.xMin = data.xMin
        this.xMax = data.xMax
        this.yMin = data.yMin
        this.yMax = data.yMax
        invalidate()
    }

    fun clearGraph() {
        this.graphData = null
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val data = graphData ?: return

        canvas.drawColor(Color.WHITE)

        drawGrid(canvas)
        drawAxes(canvas)
        drawCurves(canvas, data)
        drawCriticalPoints(canvas, data)
    }

    private fun drawGrid(canvas: Canvas) {
        val padding = 50f
        val width = width.toFloat() - 2 * padding
        val height = height.toFloat() - 2 * padding

        val gridSpacing = min(width, height) / 10

        var y = padding
        while (y <= height + padding) {
            canvas.drawLine(padding, y, width + padding, y, gridPaint)
            y += gridSpacing
        }

        var x = padding
        while (x <= width + padding) {
            canvas.drawLine(x, padding, x, height + padding, gridPaint)
            x += gridSpacing
        }
    }

    private fun drawAxes(canvas: Canvas) {
        val padding = 50f
        val width = width.toFloat() - 2 * padding
        val height = height.toFloat() - 2 * padding

        val zeroX = padding + width * (-xMin / (xMax - xMin)).toFloat()
        val zeroY = padding + height * (1 - (-yMin / (yMax - yMin))).toFloat()

        canvas.drawLine(padding, zeroY, width + padding, zeroY, axisPaint)
        canvas.drawLine(zeroX, padding, zeroX, height + padding, axisPaint)
    }

    private fun drawCurves(canvas: Canvas, data: GraphData) {
        drawCurve(canvas, data.originalCurve, originalCurvePaint)
        drawCurve(canvas, data.firstDerivativeCurve, firstDerivCurvePaint)
        drawCurve(canvas, data.secondDerivativeCurve, secondDerivCurvePaint)
    }

    private fun drawCurve(canvas: Canvas, points: List<Point>, paint: Paint) {
        if (points.isEmpty()) return

        val path = Path()
        var isFirstPoint = true
        var lastValidPoint: PointF? = null

        for (point in points) {
            if (point.y.isNaN()) {
                isFirstPoint = true
                lastValidPoint = null
                continue
            }

            val screenPoint = toScreenCoordinates(point.x, point.y)

            if (isFirstPoint) {
                path.moveTo(screenPoint.x, screenPoint.y)
                isFirstPoint = false
            } else {
                path.lineTo(screenPoint.x, screenPoint.y)
            }

            lastValidPoint = screenPoint
        }

        canvas.drawPath(path, paint)
    }

    private fun drawCriticalPoints(canvas: Canvas, data: GraphData) {
        for (point in data.criticalPoints) {
            val screenPoint = toScreenCoordinates(point.x.toFloat(), point.y.toFloat())

            val paint = when {
                point.isMaximum -> Paint().apply {
                    color = Color.RED
                    style = Paint.Style.FILL
                    isAntiAlias = true
                }
                point.isMinimum -> Paint().apply {
                    color = Color.BLUE
                    style = Paint.Style.FILL
                    isAntiAlias = true
                }
                else -> Paint().apply {
                    color = Color.GREEN
                    style = Paint.Style.FILL
                    isAntiAlias = true
                }
            }

            canvas.drawCircle(screenPoint.x, screenPoint.y, 8f, paint)
        }

        for (point in data.inflectionPoints) {
            val screenPoint = toScreenCoordinates(point.x.toFloat(), point.y.toFloat())

            val paint = Paint().apply {
                color = Color.MAGENTA
                style = Paint.Style.FILL
                isAntiAlias = true
            }

            canvas.drawCircle(screenPoint.x, screenPoint.y, 6f, paint)
        }
    }

    private fun toScreenCoordinates(x: Float, y: Float): PointF {
        val padding = 50f
        val width = width.toFloat() - 2 * padding
        val height = height.toFloat() - 2 * padding

        val xRange = xMax - xMin
        val yRange = yMax - yMin

        val screenX = padding + width * ((x - xMin) / xRange).toFloat()
        val screenY = padding + height * (1 - ((y - yMin) / yRange).toFloat())

        return PointF(screenX, screenY)
    }
}