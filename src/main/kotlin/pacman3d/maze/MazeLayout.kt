package pacman3d.maze

import three.js.Geometry

class MazeLayout(
    val name: String,
    val wallColor: Int,
    val rows: List<String>,
    private val geometry: (rows: List<String>) -> Geometry
) {
    init {
        require(rows.all { it.length == MazeConst.WIDTH_UNITS }) { "$name: Every maze row must be ${MazeConst.WIDTH_UNITS} tiles wide" }
    }

    fun buildWallGeometry() = geometry(rows)
}
