package pacman3d.logic.behaviors

import pacman3d.entities.Ghost
import pacman3d.entities.Maze.Companion.isValid
import pacman3d.entities.World
import pacman3d.logic.Direction
import pacman3d.logic.Direction.*
import pacman3d.maze.MazeConst

/**
 * Finds the real shortest path through the maze, instead of the original game's "pick the tile closest as the crow
 * flies" rule. The buffers are reused to prevent object allocation during the game.
 */
object PathFinder {

    private const val WIDTH = MazeConst.WIDTH_UNITS
    private const val HEIGHT = MazeConst.LENGTH_UNITS
    private const val UNREACHED = -1

    private val distances = IntArray(WIDTH * HEIGHT)
    private val queue = IntArray(WIDTH * HEIGHT)
    private val directions = arrayOf(UP, LEFT, DOWN, RIGHT)

    /**
     * The best direction to leave from the tile at [fromX], [fromY] to get to [targetX], [targetY] without reversing,
     * or null if the target can't be reached
     */
    fun firstStep(world: World, ghost: Ghost, fromX: Int, fromY: Int, current: Direction, targetX: Int, targetY: Int): Direction? {
        val maze = world.maze
        if (targetX !in 0 until WIDTH || targetY !in 0 until HEIGHT || !maze[targetX, targetY].isValid) return null

        distances.fill(UNREACHED)
        distances[targetY * WIDTH + targetX] = 0
        queue[0] = targetY * WIDTH + targetX
        var head = 0
        var tail = 1

        // Flood outwards from the target, so a single pass gives the distance to the target from every tile
        while (head < tail) {
            val index = queue[head++]
            val x = index % WIDTH
            val y = index / WIDTH

            for (d in directions) {
                val nx = x + d.x
                val ny = y + d.y
                if (nx !in 0 until WIDTH || ny !in 0 until HEIGHT || distances[ny * WIDTH + nx] != UNREACHED) continue
                if (!maze[nx, ny].isValid || !ghost.isLegalMove(maze, nx, ny, x, y)) continue

                distances[ny * WIDTH + nx] = distances[index] + 1
                queue[tail++] = ny * WIDTH + nx
            }
        }

        var best: Direction? = null
        var bestDistance = Int.MAX_VALUE
        for (d in directions) {
            val nx = fromX + d.x
            val ny = fromY + d.y
            if (d == current.oppositeDirection || nx !in 0 until WIDTH || ny !in 0 until HEIGHT) continue
            if (!maze[nx, ny].isValid || !ghost.isLegalMove(maze, fromX, fromY, nx, ny)) continue

            val distance = distances[ny * WIDTH + nx]
            if (distance != UNREACHED && distance < bestDistance) {
                bestDistance = distance
                best = d
            }
        }
        return best
    }
}
