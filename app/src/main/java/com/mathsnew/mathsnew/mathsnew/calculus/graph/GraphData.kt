// app/src/main/java/com/mathsnew/mathsnew/calculus/graph/GraphData.kt
// 图形数据模型

package com.mathsnew.mathsnew.calculus.graph

data class GraphData(
    val originalCurve: List<Point>,
    val firstDerivativeCurve: List<Point>,
    val secondDerivativeCurve: List<Point>,
    val criticalPoints: List<CriticalPoint>,
    val inflectionPoints: List<InflectionPoint>,
    val xMin: Double,
    val xMax: Double,
    val yMin: Double,
    val yMax: Double
)

data class Point(
    val x: Float,
    val y: Float
)

data class CriticalPoint(
    val x: Double,
    val y: Double,
    val isMaximum: Boolean,
    val isMinimum: Boolean
)

data class InflectionPoint(
    val x: Double,
    val y: Double
)