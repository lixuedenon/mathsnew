// app/src/main/java/com/mathsnew/mathsnew/calculus/graph/GraphEngine.kt
// 图像数据生成引擎

package com.mathsnew.mathsnew.calculus.graph

import android.graphics.PointF
import android.util.Log
import com.mathsnew.mathsnew.*
import kotlin.math.abs

/**
 * 图像数据生成引擎
 * 负责生成绘图所需的所有数据，包括曲线点、极值点、拐点等
 *
 * 工作流程：
 * 1. 解析原函数表达式
 * 2. 计算一阶导数和二阶导数
 * 3. 生成三条曲线的采样点
 * 4. 查找极值点（f'(x) = 0）
 * 5. 查找拐点（f''(x) = 0）
 * 6. 计算合适的坐标范围
 */
class GraphEngine {

    private val parser = ExpressionParser()
    private val derivativeCalculator = DerivativeCalculator()
    private val simplifier = ExpressionSimplifier()
    private val evaluator = MathEvaluator()

    companion object {
        private const val DEFAULT_X_MIN = -10.0
        private const val DEFAULT_X_MAX = 10.0
        private const val SAMPLE_STEP = 0.1
        private const val Y_LIMIT = 100.0
        private const val ZERO_THRESHOLD = 0.01
    }

    /**
     * 生成图像数据
     *
     * @param expression 原函数表达式
     * @param variable 求导变量（默认为"x"）
     * @return 图像数据对象
     * @throws ParseException 解析错误
     * @throws CalculationException 计算错误
     */
    fun generateGraphData(expression: String, variable: String = "x"): GraphData {
        Log.d("GraphEngine", "========================================")
        Log.d("GraphEngine", "开始生成图像数据")
        Log.d("GraphEngine", "表达式: $expression")

        // 步骤1: 解析原函数
        Log.d("GraphEngine", "步骤1: 解析原函数...")
        val originalAst = parser.parse(expression)
        Log.d("GraphEngine", "原函数AST: $originalAst")

        // 步骤2: 计算一阶导数
        Log.d("GraphEngine", "步骤2: 计算一阶导数...")
        val firstDerivativeAst = derivativeCalculator.differentiate(originalAst, variable)
        val simplifiedFirstDerivative = simplifier.simplify(firstDerivativeAst)
        Log.d("GraphEngine", "一阶导AST: $simplifiedFirstDerivative")

        // 步骤3: 计算二阶导数
        Log.d("GraphEngine", "步骤3: 计算二阶导数...")
        val secondDerivativeAst = derivativeCalculator.differentiate(simplifiedFirstDerivative, variable)
        val simplifiedSecondDerivative = simplifier.simplify(secondDerivativeAst)
        Log.d("GraphEngine", "二阶导AST: $simplifiedSecondDerivative")

        // 步骤4: 生成曲线数据
        Log.d("GraphEngine", "步骤4: 生成曲线数据...")
        val originalPoints = generateCurvePoints(originalAst)
        val firstDerivativePoints = generateCurvePoints(simplifiedFirstDerivative)
        val secondDerivativePoints = generateCurvePoints(simplifiedSecondDerivative)

        Log.d("GraphEngine", "原函数点数: ${originalPoints.size}")
        Log.d("GraphEngine", "一阶导点数: ${firstDerivativePoints.size}")
        Log.d("GraphEngine", "二阶导点数: ${secondDerivativePoints.size}")

        // 步骤5: 查找极值点
        Log.d("GraphEngine", "步骤5: 查找极值点...")
        val criticalPoints = findCriticalPoints(originalAst, simplifiedFirstDerivative, simplifiedSecondDerivative)
        Log.d("GraphEngine", "找到${criticalPoints.size}个极值点")

        // 步骤6: 查找拐点
        Log.d("GraphEngine", "步骤6: 查找拐点...")
        val inflectionPoints = findInflectionPoints(originalAst, simplifiedSecondDerivative)
        Log.d("GraphEngine", "找到${inflectionPoints.size}个拐点")

        // 步骤7: 计算坐标范围
        Log.d("GraphEngine", "步骤7: 计算坐标范围...")
        val allPoints = originalPoints + firstDerivativePoints + secondDerivativePoints
        val (xMin, xMax, yMin, yMax) = calculateAxisRange(allPoints)

        Log.d("GraphEngine", "坐标范围: x[$xMin, $xMax], y[$yMin, $yMax]")
        Log.d("GraphEngine", "✅ 图像数据生成完成")
        Log.d("GraphEngine", "========================================")

        return GraphData(
            originalAst = originalAst,
            firstDerivativeAst = simplifiedFirstDerivative,
            secondDerivativeAst = simplifiedSecondDerivative,
            originalPoints = originalPoints,
            firstDerivativePoints = firstDerivativePoints,
            secondDerivativePoints = secondDerivativePoints,
            criticalPoints = criticalPoints,
            inflectionPoints = inflectionPoints,
            xMin = xMin,
            xMax = xMax,
            yMin = yMin,
            yMax = yMax
        )
    }

    /**
     * 生成曲线采样点
     *
     * @param ast 函数的AST
     * @return 曲线点列表
     */
    private fun generateCurvePoints(ast: MathNode): List<PointF> {
        val points = mutableListOf<PointF>()
        var x = DEFAULT_X_MIN

        while (x <= DEFAULT_X_MAX) {
            try {
                val y = evaluator.evaluate(ast, x)

                // 过滤无效值和超出范围的值
                if (y.isFinite() && abs(y) <= Y_LIMIT) {
                    points.add(PointF(x.toFloat(), y.toFloat()))
                } else {
                    // 如果遇到无效点，断开曲线（添加分隔标记）
                    if (points.isNotEmpty() && points.last().y.isFinite()) {
                        points.add(PointF(x.toFloat(), Float.NaN))
                    }
                }
            } catch (e: Exception) {
                Log.w("GraphEngine", "计算点失败 at x=$x: ${e.message}")
            }

            x += SAMPLE_STEP
        }

        return points
    }

    /**
     * 查找极值点
     * 通过求解 f'(x) = 0 找到候选点，然后用 f''(x) 判断类型
     *
     * @param originalAst 原函数AST
     * @param firstDerivativeAst 一阶导数AST
     * @param secondDerivativeAst 二阶导数AST
     * @return 极值点列表
     */
    private fun findCriticalPoints(
        originalAst: MathNode,
        firstDerivativeAst: MathNode,
        secondDerivativeAst: MathNode
    ): List<CriticalPoint> {
        val criticalPoints = mutableListOf<CriticalPoint>()

        // 使用扫描法查找 f'(x) = 0 的近似解
        val zeros = findZeros(firstDerivativeAst)

        for (x in zeros) {
            try {
                // 计算原函数在该点的值
                val y = evaluator.evaluate(originalAst, x)

                // 计算二阶导数判断极值类型
                val secondDerivValue = evaluator.evaluate(secondDerivativeAst, x)

                val type = when {
                    secondDerivValue < -ZERO_THRESHOLD -> CriticalPointType.MAX
                    secondDerivValue > ZERO_THRESHOLD -> CriticalPointType.MIN
                    else -> continue // 二阶导为0，无法判断，跳过
                }

                if (y.isFinite() && abs(y) <= Y_LIMIT) {
                    criticalPoints.add(CriticalPoint(x, y, type))
                    Log.d("GraphEngine", "  极值点: ($x, $y) 类型: $type")
                }
            } catch (e: Exception) {
                Log.w("GraphEngine", "计算极值点失败 at x=$x: ${e.message}")
            }
        }

        return criticalPoints
    }

    /**
     * 查找拐点
     * 通过求解 f''(x) = 0 找到拐点
     *
     * @param originalAst 原函数AST
     * @param secondDerivativeAst 二阶导数AST
     * @return 拐点列表
     */
    private fun findInflectionPoints(
        originalAst: MathNode,
        secondDerivativeAst: MathNode
    ): List<InflectionPoint> {
        val inflectionPoints = mutableListOf<InflectionPoint>()

        // 使用扫描法查找 f''(x) = 0 的近似解
        val zeros = findZeros(secondDerivativeAst)

        for (x in zeros) {
            try {
                // 计算原函数在该点的值
                val y = evaluator.evaluate(originalAst, x)

                if (y.isFinite() && abs(y) <= Y_LIMIT) {
                    inflectionPoints.add(InflectionPoint(x, y))
                    Log.d("GraphEngine", "  拐点: ($x, $y)")
                }
            } catch (e: Exception) {
                Log.w("GraphEngine", "计算拐点失败 at x=$x: ${e.message}")
            }
        }

        return inflectionPoints
    }

    /**
     * 查找函数零点
     * 使用扫描法检测符号变化，然后用二分法精确定位
     *
     * @param ast 函数的AST
     * @return 零点列表
     */
    private fun findZeros(ast: MathNode): List<Double> {
        val zeros = mutableListOf<Double>()
        var x = DEFAULT_X_MIN

        while (x < DEFAULT_X_MAX) {
            try {
                val y1 = evaluator.evaluate(ast, x)
                val y2 = evaluator.evaluate(ast, x + SAMPLE_STEP)

                // 检测符号变化
                if (y1.isFinite() && y2.isFinite() && y1 * y2 < 0) {
                    // 使用二分法精确定位零点
                    val zero = bisectionMethod(ast, x, x + SAMPLE_STEP)
                    if (zero != null) {
                        zeros.add(zero)
                    }
                }

                // 检测函数值接近0的点
                if (y1.isFinite() && abs(y1) < ZERO_THRESHOLD) {
                    // 确保不是重复添加
                    if (zeros.isEmpty() || abs(zeros.last() - x) > SAMPLE_STEP) {
                        zeros.add(x)
                    }
                }
            } catch (e: Exception) {
                Log.w("GraphEngine", "查找零点失败 at x=$x: ${e.message}")
            }

            x += SAMPLE_STEP
        }

        return zeros
    }

    /**
     * 二分法求零点
     *
     * @param ast 函数的AST
     * @param left 左端点
     * @param right 右端点
     * @return 零点（如果找到），否则返回null
     */
    private fun bisectionMethod(ast: MathNode, left: Double, right: Double): Double? {
        var a = left
        var b = right
        var iterations = 0
        val maxIterations = 50
        val tolerance = 1e-6

        try {
            var fa = evaluator.evaluate(ast, a)
            var fb = evaluator.evaluate(ast, b)

            // 确保端点值符号相反
            if (!fa.isFinite() || !fb.isFinite() || fa * fb > 0) {
                return null
            }

            while (iterations < maxIterations && (b - a) > tolerance) {
                val mid = (a + b) / 2.0
                val fmid = evaluator.evaluate(ast, mid)

                if (!fmid.isFinite()) {
                    return null
                }

                if (abs(fmid) < tolerance) {
                    return mid
                }

                if (fa * fmid < 0) {
                    b = mid
                    fb = fmid
                } else {
                    a = mid
                    fa = fmid
                }

                iterations++
            }

            return (a + b) / 2.0
        } catch (e: Exception) {
            Log.w("GraphEngine", "二分法失败: ${e.message}")
            return null
        }
    }

    /**
     * 计算坐标轴范围
     * 自动根据所有点的坐标计算合适的显示范围
     *
     * @param allPoints 所有曲线点
     * @return (xMin, xMax, yMin, yMax)
     */
    private fun calculateAxisRange(allPoints: List<PointF>): AxisRange {
        val validPoints = allPoints.filter { it.y.isFinite() }

        if (validPoints.isEmpty()) {
            return AxisRange(
                DEFAULT_X_MIN.toFloat(),
                DEFAULT_X_MAX.toFloat(),
                -10f,
                10f
            )
        }

        val xMin = validPoints.minOf { it.x.toDouble() }
        val xMax = validPoints.maxOf { it.x.toDouble() }
        val yMin = validPoints.minOf { it.y.toDouble() }
        val yMax = validPoints.maxOf { it.y.toDouble() }

        // 留10%边距
        val xMargin = (xMax - xMin) * 0.1
        val yMargin = (yMax - yMin) * 0.1

        return AxisRange(
            (xMin - xMargin).toFloat(),
            (xMax + xMargin).toFloat(),
            (yMin - yMargin).toFloat(),
            (yMax + yMargin).toFloat()
        )
    }

    /**
     * 坐标轴范围数据类
     */
    private data class AxisRange(
        val xMin: Float,
        val xMax: Float,
        val yMin: Float,
        val yMax: Float
    )
}