// app/src/main/java/com/mathsnew/mathsnew/calculus/graph/GraphEngine.kt
// 图形生成引擎

package com.mathsnew.mathsnew.calculus.graph

import android.util.Log
import com.mathsnew.mathsnew.*

class GraphEngine {

    private val parser = ExpressionParser()
    private val derivativeCalculator = DerivativeCalculator()
    private val simplifier = ExpressionSimplifier()

    fun generateGraphData(expression: String, variable: String = "x"): GraphData {
        try {
            Log.d("GraphEngine", "========================================")
            Log.d("GraphEngine", "开始生成图形数据")
            Log.d("GraphEngine", "表达式: $expression")

            val originalAst = parser.parse(expression)
            Log.d("GraphEngine", "原始AST: $originalAst")

            val firstDerivativeAst = derivativeCalculator.differentiate(originalAst, variable)
            val firstForms = simplifier.simplifyToMultipleForms(firstDerivativeAst)
            val simplifiedFirstDerivative = firstForms.getDisplayForms().first().expression
            Log.d("GraphEngine", "一阶导AST: $simplifiedFirstDerivative")

            val secondDerivativeAst = derivativeCalculator.differentiate(firstDerivativeAst, variable)
            val secondForms = simplifier.simplifyToMultipleForms(secondDerivativeAst)
            val simplifiedSecondDerivative = secondForms.getDisplayForms().first().expression
            Log.d("GraphEngine", "二阶导AST: $simplifiedSecondDerivative")

            Log.d("GraphEngine", "生成曲线数据点...")
            val originalPoints = generateCurvePoints(originalAst)
            val firstDerivativePoints = generateCurvePoints(simplifiedFirstDerivative)
            val secondDerivativePoints = generateCurvePoints(simplifiedSecondDerivative)

            Log.d("GraphEngine", "原函数数点: ${originalPoints.size}")
            Log.d("GraphEngine", "一阶导数点: ${firstDerivativePoints.size}")
            Log.d("GraphEngine", "二阶导数点: ${secondDerivativePoints.size}")

            Log.d("GraphEngine", "查找关键点...")
            val criticalPoints = findCriticalPoints(originalAst, simplifiedFirstDerivative)
            val inflectionPoints = findInflectionPoints(originalAst, simplifiedSecondDerivative)

            Log.d("GraphEngine", "找到 ${criticalPoints.size} 个临界点")
            Log.d("GraphEngine", "找到 ${inflectionPoints.size} 个拐点")

            val xValues = originalPoints.map { it.x.toDouble() }
            val xMin = xValues.minOrNull() ?: -10.0
            val xMax = xValues.maxOrNull() ?: 10.0

            val yValues = originalPoints.filter { !it.y.isNaN() }.map { it.y.toDouble() }
            val yMin = yValues.minOrNull() ?: -10.0
            val yMax = yValues.maxOrNull() ?: 10.0

            Log.d("GraphEngine", "========================================")

            return GraphData(
                originalCurve = originalPoints,
                firstDerivativeCurve = firstDerivativePoints,
                secondDerivativeCurve = secondDerivativePoints,
                criticalPoints = criticalPoints,
                inflectionPoints = inflectionPoints,
                xMin = xMin,
                xMax = xMax,
                yMin = yMin,
                yMax = yMax
            )

        } catch (e: Exception) {
            Log.e("GraphEngine", "生成图形数据失败: ${e.message}", e)
            throw e
        }
    }

    private fun generateCurvePoints(ast: MathNode): List<Point> {
        val points = mutableListOf<Point>()
        val evaluator = MathEvaluator()

        val xMin = -10.0
        val xMax = 10.0
        val step = 0.1

        var x = xMin
        while (x <= xMax) {
            try {
                val y = evaluator.evaluate(ast, x)

                if (!y.isNaN() && !y.isInfinite() && Math.abs(y) < 100.0) {
                    points.add(Point(x.toFloat(), y.toFloat()))
                } else {
                    if (points.isNotEmpty()) {
                        points.add(Point(x.toFloat(), Float.NaN))
                    }
                }
            } catch (e: Exception) {
                if (points.isNotEmpty()) {
                    points.add(Point(x.toFloat(), Float.NaN))
                }
            }

            x += step
        }

        return points
    }

    private fun findCriticalPoints(
        originalAst: MathNode,
        firstDerivativeAst: MathNode
    ): List<CriticalPoint> {
        val criticalPoints = mutableListOf<CriticalPoint>()
        val evaluator = MathEvaluator()

        val xMin = -10.0
        val xMax = 10.0
        val step = 0.1
        val epsilon = 0.001

        var x = xMin
        while (x <= xMax) {
            try {
                val y1 = evaluator.evaluate(firstDerivativeAst, x)

                if (Math.abs(y1) < epsilon) {
                    val yOriginal = evaluator.evaluate(originalAst, x)

                    if (!yOriginal.isNaN() && !yOriginal.isInfinite()) {
                        val y0 = evaluator.evaluate(firstDerivativeAst, x - step)
                        val y2 = evaluator.evaluate(firstDerivativeAst, x + step)

                        criticalPoints.add(
                            CriticalPoint(
                                x = x,
                                y = yOriginal,
                                isMaximum = y0 > 0 && y2 < 0,
                                isMinimum = y0 < 0 && y2 > 0
                            )
                        )
                    }
                }
            } catch (e: Exception) {
            }

            x += step
        }

        return criticalPoints
    }

    private fun findInflectionPoints(
        originalAst: MathNode,
        secondDerivativeAst: MathNode
    ): List<InflectionPoint> {
        val inflectionPoints = mutableListOf<InflectionPoint>()
        val evaluator = MathEvaluator()

        val xMin = -10.0
        val xMax = 10.0
        val step = 0.1
        val epsilon = 0.001

        var x = xMin
        while (x <= xMax) {
            try {
                val y2 = evaluator.evaluate(secondDerivativeAst, x)

                if (Math.abs(y2) < epsilon) {
                    val yOriginal = evaluator.evaluate(originalAst, x)

                    if (!yOriginal.isNaN() && !yOriginal.isInfinite()) {
                        inflectionPoints.add(
                            InflectionPoint(
                                x = x,
                                y = yOriginal
                            )
                        )
                    }
                }
            } catch (e: Exception) {
            }

            x += step
        }

        return inflectionPoints
    }
}