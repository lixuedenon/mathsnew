// app/src/main/java/com/mathsnew/mathsnew/calculus/graph/GraphEngine.kt
// 图形生成引擎

package com.mathsnew.mathsnew.calculus.graph

import android.util.Log
import com.mathsnew.mathsnew.*

class GraphEngine {

    private val parser = ExpressionParser()

    /**
     * 生成图形数据（优化版）
     *
     * 接收已计算的导数 AST，避免重复计算
     *
     * @param originalExpression 原始表达式字符串
     * @param firstDerivativeAST 已计算的一阶导数 AST（可选）
     * @param secondDerivativeAST 已计算的二阶导数 AST（可选）
     * @param variable 变量名，默认为 "x"
     * @return 图形数据，包含原函数、导数曲线、关键点等
     */
    fun generateGraphData(
        originalExpression: String,
        firstDerivativeAST: MathNode? = null,
        secondDerivativeAST: MathNode? = null,
        variable: String = "x"
    ): GraphData {
        try {
            val totalStart = System.currentTimeMillis()
            Log.d(TAG, "========================================")
            Log.d(TAG, "⏱️ 开始生成图形数据")
            Log.d(TAG, "表达式: $originalExpression")

            val parseStart = System.currentTimeMillis()
            val originalAst = parser.parse(originalExpression)
            Log.d(TAG, "⏱️ 解析耗时: ${System.currentTimeMillis() - parseStart}ms")
            Log.d(TAG, "原始AST: $originalAst")

            Log.d(TAG, "⏱️ 开始生成曲线数据点...")
            val curveStart = System.currentTimeMillis()

            val originalPoints = generateCurvePoints(originalAst)
            Log.d(TAG, "   原函数: ${originalPoints.size} 点")

            val firstDerivativePoints = if (firstDerivativeAST != null) {
                generateCurvePoints(firstDerivativeAST)
            } else {
                emptyList()
            }
            Log.d(TAG, "   一阶导: ${firstDerivativePoints.size} 点")

            val secondDerivativePoints = if (secondDerivativeAST != null) {
                generateCurvePoints(secondDerivativeAST)
            } else {
                emptyList()
            }
            Log.d(TAG, "   二阶导: ${secondDerivativePoints.size} 点")

            Log.d(TAG, "⏱️ 曲线生成耗时: ${System.currentTimeMillis() - curveStart}ms")

            Log.d(TAG, "⏱️ 开始查找关键点...")
            val keyPointStart = System.currentTimeMillis()

            val criticalPoints = if (firstDerivativeAST != null) {
                findCriticalPoints(originalAst, firstDerivativeAST)
            } else {
                emptyList()
            }
            Log.d(TAG, "   找到 ${criticalPoints.size} 个临界点")

            val inflectionPoints = if (secondDerivativeAST != null) {
                findInflectionPoints(originalAst, secondDerivativeAST)
            } else {
                emptyList()
            }
            Log.d(TAG, "   找到 ${inflectionPoints.size} 个拐点")

            Log.d(TAG, "⏱️ 关键点查找耗时: ${System.currentTimeMillis() - keyPointStart}ms")

            val xValues = originalPoints.map { it.x.toDouble() }
            val xMin = xValues.minOrNull() ?: -10.0
            val xMax = xValues.maxOrNull() ?: 10.0

            val yValues = originalPoints.filter { !it.y.isNaN() }.map { it.y.toDouble() }
            val yMin = yValues.minOrNull() ?: -10.0
            val yMax = yValues.maxOrNull() ?: 10.0

            val totalTime = System.currentTimeMillis() - totalStart
            Log.d(TAG, "⏱️ 图形生成总耗时: ${totalTime}ms")
            Log.d(TAG, "========================================")

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
            Log.e(TAG, "❌ 生成图形数据失败: ${e.message}", e)
            throw e
        }
    }

    /**
     * 生成曲线数据点
     *
     * 在 [-10, 10] 区间内以 0.1 为步长采样
     * 过滤掉无效值（NaN、Infinite、过大值）
     *
     * @param ast 要绘制的表达式 AST
     * @return 曲线数据点列表
     */
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

    /**
     * 查找临界点（极值点）
     *
     * 通过一阶导数为 0 的点确定临界点
     * 通过左右两侧导数的符号判断是极大值还是极小值
     *
     * @param originalAst 原函数 AST
     * @param firstDerivativeAst 一阶导数 AST
     * @return 临界点列表
     */
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

    /**
     * 查找拐点
     *
     * 通过二阶导数为 0 的点确定拐点
     *
     * @param originalAst 原函数 AST
     * @param secondDerivativeAst 二阶导数 AST
     * @return 拐点列表
     */
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

    companion object {
        private const val TAG = "GraphEngine"
    }
}