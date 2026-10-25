package pacman3d.entities

import pacman3d.entities.Maze.Companion.EMPTY
import pacman3d.entities.Maze.Companion.GHOST_HOUSE
import pacman3d.entities.Maze.Companion.isGhostHouse
import pacman3d.entities.Maze.Companion.isValid
import pacman3d.ext.truncate
import pacman3d.renderables.GhostRenderable
import pacman3d.logic.*
import pacman3d.logic.GhostState.Chase
import pacman3d.logic.GhostState.Eaten
import pacman3d.logic.GhostState.Scatter
import pacman3d.logic.GhostState.Frightened
import pacman3d.logic.behaviors.*
import kotlin.math.abs

// The fraction of their normal speed ghosts have in the goo
private const val SLIME_SPEED_FACTOR = 0.35

// The closest a ghost gets to the ghost in front of it, in tiles
private const val MIN_GHOST_GAP = 1.0

// How far off to the side another ghost may be and still count as being in front
private const val GHOST_LANE_WIDTH = 0.6

class DotCounter(var count: Int = 0, var limit: Int = 0) {

    val reachedLimit get() = count >= limit

    fun reset() { count = 0 }
}

abstract class Ghost(
    val name: String,
    private val color: Int,
    val initialState: GhostState,
    initialPosition: Position,
    initialDirection: Direction,
    val scatterTargetTile: Position
) : MovableGameEntity<GhostRenderable>(initialPosition, initialDirection) {

    var movementStrategyStep = 0

    val dotCounter = DotCounter()

    // 0 - 1, set by the level. See GameData.smartness
    var smartness = 0.0

    val targetTile = Position()

    var isFrozen = false
        set(value) {
            field = value
            renderable.setFrozen(value)
        }

    // Ghosts wading through goo are much slower. Eaten ghosts, which are only eyes, are not affected
    var isSlimed = false
        set(value) {
            field = value
            renderable.setSticky(value)
        }

    override val currentSpeed get() = if (isSlimed && state != Eaten) speed * SLIME_SPEED_FACTOR else speed

    var mandatoryDirectionOverride: Direction? = null

    var state: GhostState = initialState
        set(value) {
            val previousState = state
            field = value
            movementStrategy = value.movementStrategy
            renderable.onStateChanged(value, previousState)
        }

    private var movementStrategy: GhostMovementStrategy = state.movementStrategy
        set(value) {
            field = value
            nextDirection = currentDirection
            movementStrategyStep = 0
        }

    val hasReachedTarget get() = movementStrategy.hasReachedTarget(this)

    override fun resetState(world: World) {
        super.resetState(world)

        dotCounter.reset()

        isFrozen = false
        isSlimed = false
        mandatoryDirectionOverride = null

        state = initialState
    }

    override fun update(world: World, time: Double) {
        if (!isFrozen && !isBlockedByGhostAhead(world)) super.update(world, time)
    }

    private val isInMaze get() = state == Chase || state == Scatter || state == Frightened

    /**
     * Ghosts never overlap: a ghost that catches up with another one heading the same way holds back until there is
     * room again, so that they follow each other through the corridors.
     */
    private fun isBlockedByGhostAhead(world: World): Boolean {
        if (!isInMaze) return false

        return world.ghosts.any { other ->
            if (other === this || !other.isInMaze || other.currentDirection != currentDirection) return@any false

            val dx = other.position.x - position.x
            val dy = other.position.y - position.y
            val ahead = dx * currentDirection.x + dy * currentDirection.y
            val sideways = if (currentDirection.isHorizontal) abs(dy) else abs(dx)

            ahead > 0.0 && ahead < MIN_GHOST_GAP && sideways < GHOST_LANE_WIDTH
        }
    }

    abstract fun getChaseTargetTile(world: World, targetTile: Position)

    fun reverseDirection() {
        mandatoryDirectionOverride = currentDirection.oppositeDirection
    }

    override fun onPositionUpdated(world: World, time: Double, mazePositionChanged: Boolean) {
        // If the ghost is forced to change direction by the system, we don't need to call the movement strategy
        mandatoryDirectionOverride?.let { newDirection ->
            mandatoryDirectionOverride = null
            nextDirection = newDirection
        } ?: movementStrategy.onPositionUpdated(world, this, mazePositionChanged)
    }

    // TODO: Implement zones where the ghosts cannot turn upward (these are ignored in Frightened mode)
    override fun isLegalMove(maze: Maze, fromX: Int, fromY: Int, toX: Int, toY: Int): Boolean {
        require(maze[fromX, fromY].isValid) { "isLegalMove called with invalid position: $fromX, $fromY" }

        val currentMazeValue = maze[fromX, fromY]
        val nextMazeValue = maze[toX, toY]

        // Ghosts can only re-enter the ghost house when eaten
        if (currentMazeValue == EMPTY && nextMazeValue.isGhostHouse) return state == Eaten

        return nextMazeValue.isValid
    }

    override fun createRenderable() = GhostRenderable(this, color)

    override fun toString() = name
}