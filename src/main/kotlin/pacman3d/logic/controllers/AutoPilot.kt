package pacman3d.logic.controllers

import pacman3d.entities.Fruit
import pacman3d.entities.Ghost
import pacman3d.entities.Maze.Companion.isDotOrEnergizer
import pacman3d.entities.Maze.Companion.isEnergizer
import pacman3d.entities.Maze.Companion.isGhostHouse
import pacman3d.entities.Maze.Companion.isValid
import pacman3d.entities.World
import pacman3d.logic.Direction
import pacman3d.logic.Direction.*
import pacman3d.logic.Position
import pacman3d.maze.MazeConst

/**
 * Plays the game by itself for the attract mode. Pac-Man heads for the most worthwhile thing nearby, but only along
 * paths where he gets to every tile well before any of the dangerous ghosts could. If there is no such path he runs
 * away from the ghosts instead.
 */
class AutoPilot(private val world: World) {

    private companion object {
        const val WIDTH = MazeConst.WIDTH_UNITS
        const val HEIGHT = MazeConst.LENGTH_UNITS
        const val UNREACHED = Int.MAX_VALUE / 2

        // How many tiles Pac-Man must be ahead of a ghost on a tile to consider it safe
        const val SAFE_MARGIN = 2

        // A ghost this close makes Pac-Man reconsider his route immediately, instead of at the next tile
        const val DANGER_DISTANCE = 5.0
        const val ENERGIZER_DANGER_DISTANCE = 10

        // Added to the distance of a target, so that the lowest total is the most attractive
        const val ENERGIZER_PENALTY = 6
        const val ENERGIZER_BONUS = -8
        const val PICKUP_BONUS = -10
        const val FRIGHTENED_GHOST_BONUS = -12
    }

    private val directions = arrayOf(UP, LEFT, DOWN, RIGHT)

    private val ghostDistance = IntArray(WIDTH * HEIGHT)
    private val pacmanDistance = IntArray(WIDTH * HEIGHT)
    private val firstStep = IntArray(WIDTH * HEIGHT)
    private val queue = IntArray(WIDTH * HEIGHT)

    private var lastTile = -1

    private val Ghost.isDangerous get() = !isFrozen && state.canEatPacman

    fun reset() {
        lastTile = -1
    }

    fun update() {
        val pacman = world.pacman
        val tile = pacman.position.mazeIndex

        val ghostNearby = world.ghosts.any { it.isDangerous && it.position.distanceTo(pacman.position) < DANGER_DISTANCE }
        if (tile == lastTile && !ghostNearby) return
        lastTile = tile

        computeGhostDistances()

        val direction = findBestDirection(SAFE_MARGIN) ?: findBestDirection(0) ?: flee()
        if (direction != null) pacman.requestDirection(direction)
    }

    private fun isWalkable(x: Int, y: Int): Boolean {
        if (x !in 0 until WIDTH || y !in 0 until HEIGHT) return false
        val value = world.maze[x, y]
        return value.isValid && !value.isGhostHouse
    }

    /** How many tiles the closest dangerous ghost needs to get to every tile */
    private fun computeGhostDistances() {
        ghostDistance.fill(UNREACHED)
        var head = 0
        var tail = 0

        for (ghost in world.ghosts) {
            if (!ghost.isDangerous) continue
            val index = ghost.position.mazeY * WIDTH + ghost.position.mazeX
            if (ghostDistance[index] == 0) continue
            ghostDistance[index] = 0
            queue[tail++] = index
        }

        while (head < tail) {
            val index = queue[head++]
            val x = index % WIDTH
            val y = index / WIDTH

            for (d in directions) {
                val nx = x + d.x
                val ny = y + d.y
                if (!isWalkable(nx, ny) || ghostDistance[ny * WIDTH + nx] != UNREACHED) continue
                ghostDistance[ny * WIDTH + nx] = ghostDistance[index] + 1
                queue[tail++] = ny * WIDTH + nx
            }
        }
    }

    /** The first step on the way to the best target, going only through tiles that are [margin] tiles safe */
    private fun findBestDirection(margin: Int): Direction? {
        val pacman = world.pacman
        val start = pacman.position.mazeY * WIDTH + pacman.position.mazeX

        pacmanDistance.fill(UNREACHED)
        pacmanDistance[start] = 0
        queue[0] = start
        var head = 0
        var tail = 1

        while (head < tail) {
            val index = queue[head++]
            val x = index % WIDTH
            val y = index / WIDTH

            for ((i, d) in directions.withIndex()) {
                val nx = x + d.x
                val ny = y + d.y
                val next = ny * WIDTH + nx
                if (!isWalkable(nx, ny) || pacmanDistance[next] != UNREACHED) continue

                val distance = pacmanDistance[index] + 1
                if (ghostDistance[next] <= distance + margin) continue

                pacmanDistance[next] = distance
                firstStep[next] = if (index == start) i else firstStep[index]
                queue[tail++] = next
            }
        }

        val danger = ghostDistance[start] < ENERGIZER_DANGER_DISTANCE
        var bestScore = UNREACHED
        var bestTile = -1

        for (i in 0 until tail) {
            val index = queue[i]
            if (index == start) continue

            val score = pacmanDistance[index] + valueOfTile(index % WIDTH, index / WIDTH, danger)
            if (score < bestScore) {
                bestScore = score
                bestTile = index
            }
        }

        // Nothing worth going for is left within reach
        if (bestTile < 0 || bestScore >= UNREACHED) return null

        return directions[firstStep[bestTile]]
    }

    /** A bonus (negative) or penalty (positive) for heading to a tile. Tiles without anything to eat are not targets. */
    private fun valueOfTile(x: Int, y: Int, inDanger: Boolean): Int {
        var value = UNREACHED
        val maze = world.maze[x, y]

        if (maze.isDotOrEnergizer) {
            value = if (!maze.isEnergizer) 0 else if (inDanger) ENERGIZER_BONUS else ENERGIZER_PENALTY
        }

        if (isAt(world.fruit.isShown, Fruit.POSITION, x, y)) value = minOf(value, PICKUP_BONUS)
        if (isAt(world.freezePowerUp.isShown, world.freezePowerUp.position, x, y)) value = minOf(value, PICKUP_BONUS)
        if (isAt(world.speedPowerUp.isShown, world.speedPowerUp.position, x, y)) value = minOf(value, PICKUP_BONUS)

        for (ghost in world.ghosts) {
            if (ghost.state.isFrightened && !ghost.isFrozen && ghost.position.mazeX == x && ghost.position.mazeY == y) {
                value = minOf(value, FRIGHTENED_GHOST_BONUS)
            }
        }

        return value
    }

    private fun isAt(shown: Boolean, position: Position, x: Int, y: Int) = shown && position.mazeX == x && position.mazeY == y

    /** When there is nowhere safe to go, move to the neighboring tile that is the furthest from the ghosts */
    private fun flee(): Direction? {
        val pacman = world.pacman
        var best: Direction? = null
        var bestDistance = -1

        for (d in directions) {
            val nx = pacman.position.mazeX + d.x
            val ny = pacman.position.mazeY + d.y
            if (!isWalkable(nx, ny)) continue

            // Turning around is the last resort, unless it's clearly better
            val distance = ghostDistance[ny * WIDTH + nx] - if (d == pacman.currentDirection.oppositeDirection) 1 else 0
            if (distance > bestDistance) {
                bestDistance = distance
                best = d
            }
        }
        return best
    }
}
