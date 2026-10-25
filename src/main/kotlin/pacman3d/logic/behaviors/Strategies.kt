package pacman3d.logic.behaviors

import pacman3d.entities.Ghost
import pacman3d.entities.World
import pacman3d.logic.Direction
import pacman3d.logic.Direction.*
import pacman3d.logic.Position
import kotlin.random.Random

object ChaseMovementStrategy : GhostMovementStrategyWithLookAhead() {

    override fun updateTargetTile(world: World, ghost: Ghost, targetTile: Position) {
        ghost.getChaseTargetTile(world, targetTile)
    }
}

object ScatterMovementStrategy : GhostMovementStrategyWithLookAhead() {

    override fun updateTargetTile(world: World, ghost: Ghost, targetTile: Position) {
        targetTile.copy(ghost.scatterTargetTile)
    }
}

/**
 * The PRNG generates an pseudo-random memory address to read the last few bits from. These bits are translated into the
 * direction a frightened ghost must first try. If a wall blocks the chosen direction, the ghost then attempts the
 * remaining directions in this order: up, left, down, and right, until a passable direction is found. The PRNG gets
 * reset with an identical seed value every new level and every new life, causing predictable results.
 */
object FrightenedMovementStrategy : GhostMovementStrategy {

    private val directions = arrayOf(UP, LEFT, DOWN, RIGHT)

    override fun onPositionUpdated(world: World, ghost: Ghost, mazePositionChanged: Boolean) = with (ghost) {

        fun Direction.isValid()
            = ghost.currentDirection.oppositeDirection != this && isLegalMove(world.maze, ghost.position, this)

        // Smart ghosts run away from Pac-Man instead of wandering randomly
        if (smartness > 0 && Random.nextDouble() < smartness * 0.8) {
            val fleeDirection = directions.filter { it.isValid() }.maxByOrNull {
                position.sqrDistanceFromDirectionTo(it, world.pacman.position)
            }
            if (fleeDirection != null) {
                nextDirection = fleeDirection
                return@with
            }
        }

        val randomDirection = getRandomDirection()

        nextDirection = if (randomDirection.isValid()) randomDirection else directions.first { it.isValid() }
    }

    private fun getRandomDirection() = directions[Random.nextInt(4)]
}

object InGhostHouseMovementStrategy: GhostMovementStrategy {

    override fun onPositionUpdated(world: World, ghost: Ghost, mazePositionChanged: Boolean) = with (ghost) {
        when {
            position.y <= 16.5 -> nextDirection = DOWN
            position.y >= 18.0 -> nextDirection = UP
        }
    }
}

object ReturnToGhostHouseMovementStrategy : GhostMovementStrategyWithLookAhead() {

    override fun updateTargetTile(world: World, ghost: Ghost, targetTile: Position) = with (ghost) {
        when {
            movementStrategyStep == 0 -> {
                targetTile.set(14.5, 14.5)
                movementStrategyStep = 1
            }

            movementStrategyStep == 1 && position.isAt(14.5, 14.5) -> {
                targetTile.set(14.5, 16.0)
                movementStrategyStep = 2
            }
        }
    }

    override fun hasReachedTarget(ghost: Ghost) = ghost.position.isAt(14.5, 16.5, xRange = 1.0, yRange = 1.5)
}

object LeaveGhostHouseMovementStrategy: GhostMovementStrategy {

    override fun onPositionUpdated(world: World, ghost: Ghost, mazePositionChanged: Boolean) = with(ghost) {
        when {
            // Every x position must be covered, or the ghost gets stuck in the den when it ends up in a gap
            position.x < 14.5 -> nextDirection = RIGHT
            position.x > 15.5 -> nextDirection = LEFT
            else -> nextDirection = UP
        }
    }

    override fun hasReachedTarget(ghost: Ghost) = ghost.position.mazeY == 14
}

