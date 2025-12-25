// app/src/main/java/com/mathsnew/mathsnew/calculus/graph/GraphData.kt
// 图像数据模型

package com.mathsnew.mathsnew.calculus.graph

import android.graphics.PointF
import com.mathsnew.mathsnew.MathNode

/**
 * 图像数据封装类
 * 包含原函数、导数以及关键点信息
 *
 * @param originalAst 原函数的AST
 * @param firstDerivativeAst 一阶导数的AST
 * @param secondDerivativeAst 二阶导数的AST
 * @param originalPoints 原函数的曲线点列表
 * @param firstDerivativePoints 一阶导数的曲线点列表
 * @param secondDerivativePoints 二阶导数的曲线点列表
 * @param criticalPoints 极值点列表
 * @param inflectionPoints 拐点列表
 * @param xMin x轴最小值
 * @param xMax x轴最大值
 * @param yMin y轴最小值
 * @param yMax y轴最大值
 */
data class GraphData(
    val originalAst: MathNode,
    val firstDerivativeAst: MathNode,
    val secondDerivativeAst: MathNode,

    val originalPoints: List<PointF>,
    val firstDerivativePoints: List<PointF>,
    val secondDerivativePoints: List<PointF>,

    val criticalPoints: List<CriticalPoint>,
    val inflectionPoints: List<InflectionPoint>,

    val xMin: Float,
    val xMax: Float,
    val yMin: Float,
    val yMax: Float
)

/**
 * 极值点数据类
 *
 * @param x x坐标
 * @param y y坐标（原函数在此点的值）
 * @param type 极值点类型（最大值或最小值）
 */
data class CriticalPoint(
    val x: Double,
    val y: Double,
    val type: CriticalPointType
)

/**
 * 极值点类型枚举
 */
enum class CriticalPointType {
    MAX,    // 极大值点
    MIN     // 极小值点
}

/**
 * 拐点数据类
 *
 * @param x x坐标
 * @param y y坐标（原函数在此点的值）
 */
data class InflectionPoint(
    val x: Double,
    val y: Double
)