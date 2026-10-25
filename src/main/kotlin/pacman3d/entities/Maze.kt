package pacman3d.entities

import org.w3c.dom.ValidityState
import pacman3d.logic.Direction
import pacman3d.renderables.MazeRenderable
import pacman3d.logic.Position
import pacman3d.maze.MazeConst
import pacman3d.maze.MazeLayout
import pacman3d.maze.MazeLayouts
import kotlin.experimental.and
import kotlin.experimental.or

class Maze : AbsGameEntity<MazeRenderable>() {

    private val state = ByteArray(MazeConst.WIDTH_UNITS * MazeConst.LENGTH_UNITS)

    // Takes effect on the next resetState
    var layout: MazeLayout = MazeLayouts.CLASSIC

    // The look of the maze changes as the levels progress. Takes effect on the next resetState
    var level = 0

    var totalDots = 0
        private set

    var dotsLeft = 0
        private set

    override fun resetState(world: World) {
        state.fill(0)

        layout.rows.forEachIndexed { y, row ->
            row.forEachIndexed { x, tile -> state[MazeConst.indexOf(x, y)] = tileValue(tile) }
        }

        totalDots = state.count { it.isDotOrEnergizer }
        dotsLeft = totalDots

        renderable.setLayout(layout, level)
    }

    private fun tileValue(tile: Char): Byte = when (tile) {
        '.' -> D
        'o' -> R
        ' ' -> E
        'G' -> G
        else -> 0
    }

    operator fun get(x: Int, y: Int): Byte = state[MazeConst.indexOf(x, y)]
    operator fun get(x: Double, y: Double): Byte = state[MazeConst.indexOf(x.toInt(), y.toInt())]
    operator fun get(pos: Position): Byte = state[pos.mazeIndex]
    operator fun set(pos: Position, value: Byte) { state[pos.mazeIndex] = value }

    fun forEachTile(func: (state: Maze, x: Int, y: Int) -> Unit) {
        for (y in MazeConst.FIRST_EFFECTIVE_LINE until MazeConst.LAST_EFFECTIVE_LINE)
            for (x in 0 until MazeConst.WIDTH_UNITS)
                func(this, x, y)
    }

    fun isIntersection(position: Position) = isIntersection(position.mazeX, position.mazeY)

    fun isIntersection(x: Int, y: Int)
        = this[x, y].isValid && (this[x - 1, y].isValid || this[x + 1, y].isValid || this[x, y - 1].isValid || this[x, y + 1].isValid)

    fun isIntersection(position: Position, direction: Direction)
        = isIntersection(position.mazeX + direction.x, position.mazeY + direction.y)

    fun onDotEaten(position: Position) {
        this[position] = EMPTY
        dotsLeft--
    }

    companion object {
        const val VALID         : Byte = 0x01
        const val EMPTY         : Byte = 0x01
        const val DOT           : Byte = 0x02
        const val ENERGIZER     : Byte = 0x04
        const val GHOST_HOUSE   : Byte = 0x08

        private val E = EMPTY
        private val D = DOT or VALID
        private val R = ENERGIZER or VALID
        private val G = GHOST_HOUSE or VALID

        inline val Byte.isDot get() = (this and DOT) == DOT
        inline val Byte.isEnergizer get() = (this and ENERGIZER) == ENERGIZER
        inline val Byte.isDotOrEnergizer get() = isDot || isEnergizer
        inline val Byte.isValid get() = (this and VALID) == VALID
        inline val Byte.isGhostHouse get() = (this and GHOST_HOUSE) == GHOST_HOUSE
    }

    override fun createRenderable() = MazeRenderable(layout)
}