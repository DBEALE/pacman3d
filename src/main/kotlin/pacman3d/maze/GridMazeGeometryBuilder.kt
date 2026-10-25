package pacman3d.maze

import pacman3d.maze.MazeConst.WALL_HEIGHT
import pacman3d.maze.MazeConst.WALL_THICKNESS
import three.js.BoxGeometry
import three.js.CylinderGeometry
import three.js.Geometry
import kotlin.math.abs

/**
 * Builds the walls of a maze from its tile layout. Walls are drawn along the center of every wall tile that borders a
 * corridor, which matches how the classic maze is drawn by [MazeGeometryBuilder].
 */
class GridMazeGeometryBuilder(private val rows: List<String>) {

    private companion object {
        const val POST_SEGMENTS = 8
    }

    private val pieces = ArrayList<Geometry>()

    private val width = MazeConst.WIDTH_UNITS

    fun build(): Geometry {
        val needsPost = Array(rows.size) { BooleanArray(width) }

        for (y in rows.indices) {
            for (x in 0 until width) {
                if (!isWall(x, y)) continue

                if (x + 1 < width && isWall(x + 1, y) && isOnBorder(x, y, x + 1, y)) {
                    wall(x, y, x + 1, y)
                    needsPost[y][x] = true
                    needsPost[y][x + 1] = true
                }

                if (y + 1 < rows.size && isWall(x, y + 1) && isOnBorder(x, y, x, y + 1)) {
                    wall(x, y, x, y + 1)
                    needsPost[y][x] = true
                    needsPost[y + 1][x] = true
                }

                // A lone wall tile has no wall to connect to, and is drawn as a pillar
                if (hasOpenNeighbor(x, y)) needsPost[y][x] = true
            }
        }

        for (y in rows.indices) for (x in 0 until width) if (needsPost[y][x]) post(x, y)

        return pieces.fold(Geometry()) { maze, piece -> maze.apply { merge(piece) } }.apply { mergeVertices() }
    }

    // Outside of the layout is considered a wall
    private fun isWall(x: Int, y: Int) = y !in rows.indices || x !in 0 until width || rows[y][x] == '#'

    private fun hasOpenNeighbor(x: Int, y: Int)
        = !isWall(x - 1, y) || !isWall(x + 1, y) || !isWall(x, y - 1) || !isWall(x, y + 1)

    // A line between two adjacent wall tiles is visible only if a corridor runs along either side of it
    private fun isOnBorder(x1: Int, y1: Int, x2: Int, y2: Int) = if (y1 == y2) {
        !isWall(x1, y1 - 1) || !isWall(x2, y1 - 1) || !isWall(x1, y1 + 1) || !isWall(x2, y1 + 1)
    } else {
        !isWall(x1 - 1, y1) || !isWall(x1 - 1, y2) || !isWall(x1 + 1, y1) || !isWall(x1 + 1, y2)
    }

    // The tunnel at each side of the maze is not visible, so the walls leading into it stop at the visible edge
    private fun worldX(x: Int) = (-MazeConst.HALF_WIDTH + (x + 0.5) * MazeConst.UNIT_SIZE)
        .coerceIn(-MazeConst.HALF_VISIBLE_WIDTH, MazeConst.HALF_VISIBLE_WIDTH)

    private fun worldZ(y: Int) = -MazeConst.HALF_LENGTH + (y + 0.5) * MazeConst.UNIT_SIZE

    private fun wall(x1: Int, y1: Int, x2: Int, y2: Int) {
        val length = abs(worldX(x2) - worldX(x1)) + abs(worldZ(y2) - worldZ(y1))
        if (length <= 0.0) return

        val isHorizontal = y1 == y2
        val box = if (isHorizontal) BoxGeometry(length, WALL_HEIGHT, WALL_THICKNESS) else BoxGeometry(WALL_THICKNESS, WALL_HEIGHT, length)

        pieces += box.apply { translate((worldX(x1) + worldX(x2)) / 2, 0, (worldZ(y1) + worldZ(y2)) / 2) }
    }

    // Posts round off the corners where walls meet
    private fun post(x: Int, y: Int) {
        val radius = WALL_THICKNESS / 2
        pieces += CylinderGeometry(radius, radius, WALL_HEIGHT, POST_SEGMENTS).apply { translate(worldX(x), 0, worldZ(y)) }
    }
}
