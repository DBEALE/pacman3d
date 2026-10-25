package pacman3d.logic.controllers

import pacman3d.Music
import pacman3d.Tune
import pacman3d.Tunes
import pacman3d.entities.Ghost
import pacman3d.entities.World
import pacman3d.logic.Direction
import pacman3d.logic.Direction.*
import pacman3d.logic.GhostState
import pacman3d.logic.GhostState.Chase
import pacman3d.logic.GhostState.Frightened
import pacman3d.renderables.GhostRenderable
import pacman3d.renderables.PacManMesh
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Played when the last dot is eaten: the maze flashes, and then an act with a tune is played on the empty stage. Acts
 * are only played after the 1st, 3rd, 5th... cleared level, and each time the next one in the list is chosen. The levels in between only flash.
 */
class LevelCompleteCutScene(private val world: World, act: ((Stage) -> CutSceneAct)?, private val onFinished: () -> Unit) {

    companion object {
        private const val FLASH_TIME = 1.6
        private const val FLASH_INTERVAL = 0.2

        private const val ACTS_EVERY_N_LEVELS = 2

        private val ACTS: List<(Stage) -> CutSceneAct> = listOf(
            ::ChaseAct,
            ::CongaAct,
            ::GiantAct,
            ::OrbitAct,
            ::BalloonsAct,
        )

        /** [level] is the zero-based index of the level that was just completed */
        fun forLevel(level: Int, world: World, onFinished: () -> Unit): LevelCompleteCutScene {
            val hasAct = level % ACTS_EVERY_N_LEVELS == 0
            val act = if (hasAct) ACTS[(level / ACTS_EVERY_N_LEVELS) % ACTS.size] else null
            return LevelCompleteCutScene(world, act, onFinished)
        }
    }

    private val stage = Stage(world)

    private val act = act?.invoke(stage)

    private var isFlashing = true

    private var elapsed = 0.0

    init {
        world.setPacmanAndGhostsActive(false)
        world.bonusPoints.hide()
        stage.ghosts.forEach { it.isVisible = false }
    }

    fun update(time: Double) {
        if (isFlashing) updateFlash(time) else if (act?.update(time) == true) finish()
    }

    private fun updateFlash(time: Double) {
        elapsed += time
        world.maze.renderable.setWallsHighlighted((elapsed / FLASH_INTERVAL).toInt() % 2 == 1)

        if (elapsed < FLASH_TIME) return

        world.maze.renderable.setWallsHighlighted(false)
        isFlashing = false

        if (act == null) return finish()

        world.maze.renderable.setWallsVisible(false)
        stage.ghosts.forEach { it.isVisible = true }
        act.begin()
        Music.play(act.tune, act.approximateDuration)
    }

    private fun finish() {
        Music.stop()
        stage.restore()
        world.maze.renderable.setWallsVisible(true)

        onFinished()
    }
}

interface CutSceneAct {
    val tune: Tune

    /** Used to decide how many times to repeat the tune */
    val approximateDuration: Double

    fun begin()

    /** Returns true when the act has ended */
    fun update(time: Double): Boolean
}

/** The actors of the cut scenes, which are moved around on the empty stage. The coordinates are in maze tiles. */
class Stage(private val world: World) {

    companion object {
        /** The row of the tunnel, which is the vertical center of the maze */
        const val ROW = 17.5
        const val CENTER_X = 15.0
    }

    val blinky get() = world.blinky
    val pinky get() = world.pinky
    val inky get() = world.inky
    val clyde get() = world.clyde

    val ghosts: Array<Ghost> = arrayOf(world.blinky, world.pinky, world.inky, world.clyde)

    private val pacmanMesh get() = world.pacman.renderable.sceneObject

    fun updateMouth(time: Double) = pacmanMesh.updateMouthOpen(time)

    fun setGhostStates(state: GhostState) = ghosts.forEach { it.state = state }

    fun placePacMan(x: Double, row: Double, direction: Direction, scale: Double = 1.0, lift: Double = 0.0) {
        world.pacman.placeAt(x, row, direction)
        pacmanMesh.apply {
            this.scale.set(scale, scale, scale)
            position.y = PacManMesh.SIZE / 2 * scale + lift
        }
    }

    fun placeGhost(
        ghost: Ghost,
        x: Double,
        row: Double,
        direction: Direction,
        scale: Double = 1.0,
        lift: Double = 0.0,
        spin: Double = 0.0,
        squash: Double = 1.0,
    ) {
        ghost.placeAt(x, row, direction)
        ghost.renderable.sceneObject.apply {
            this.scale.set(scale, scale * squash, scale)
            position.y = GhostRenderable.BASE_Y + lift
            rotation.y = spin
        }
    }

    fun restore() {
        pacmanMesh.apply {
            scale.set(1.0, 1.0, 1.0)
            position.y = PacManMesh.SIZE / 2
        }
        ghosts.forEach {
            it.isVisible = true
            it.renderable.sceneObject.apply {
                scale.set(1.0, 1.0, 1.0)
                position.y = GhostRenderable.BASE_Y
                rotation.y = 0.0
            }
        }
    }
}

/** The direction in which something moving with the given velocity is heading */
private fun directionOf(dx: Double, dz: Double) = when {
    abs(dx) >= abs(dz) -> if (dx >= 0) RIGHT else LEFT
    else -> if (dz >= 0) DOWN else UP
}

/**
 * The ghosts chase Pac-Man across the screen. Pac-Man then turns around, grows, and chases the now frightened ghosts back.
 */
class ChaseAct(private val stage: Stage) : CutSceneAct {

    private companion object {
        const val SPEED = 15.0
        const val GHOST_SPACING = 2.2
        const val BIG_PACMAN_SCALE = 2.5

        // The x coordinates are chosen so that everyone starts and ends outside of the visible area
        const val CHASE_START_X = -3.0
        const val CHASE_END_X = 40.0
        const val REVENGE_START_X = 42.0
        const val REVENGE_END_X = -3.0

        // The big Pac-Man needs more room between him and the first ghost
        const val CHASE_FIRST_GHOST_GAP = GHOST_SPACING
        const val REVENGE_FIRST_GHOST_GAP = 3.4
    }

    override val tune = Tunes.CHASE
    override val approximateDuration = 6.0

    private var isRevenge = false

    // The x coordinate of Pac-Man
    private var leadX = CHASE_START_X

    override fun begin() = stage.setGhostStates(Chase)

    override fun update(time: Double): Boolean {
        stage.updateMouth(time)

        if (!isRevenge) {
            leadX += SPEED * time
            place(RIGHT, CHASE_FIRST_GHOST_GAP, 1.0)

            if (leadX >= CHASE_END_X) {
                isRevenge = true
                leadX = REVENGE_START_X
                stage.setGhostStates(Frightened)
            }
        } else {
            leadX -= SPEED * time
            place(LEFT, REVENGE_FIRST_GHOST_GAP, BIG_PACMAN_SCALE)

            if (leadX <= REVENGE_END_X) return true
        }

        return false
    }

    // The ghosts always run ahead of Pac-Man when he moves left, and behind him when he moves right
    private fun place(direction: Direction, firstGhostGap: Double, pacmanScale: Double) {
        stage.placePacMan(leadX, Stage.ROW, direction, pacmanScale)
        stage.ghosts.forEachIndexed { i, ghost ->
            stage.placeGhost(ghost, leadX - (firstGhostGap + GHOST_SPACING * i), Stage.ROW, direction)
        }
    }
}

/**
 * Everybody dances across the screen in a hopping, weaving conga line. Halfway through the line turns around and the ghosts
 * lead Pac-Man back.
 */
class CongaAct(private val stage: Stage) : CutSceneAct {

    private companion object {
        const val SPEED = 9.0
        const val SPACING = 2.4
        const val HOP_SPEED = 7.0
        const val HOP_HEIGHT = 0.9
        const val WEAVE_SPEED = 3.0
        const val WEAVE_WIDTH = 3.5
        const val START_X = -3.0
        const val END_X = 45.0
    }

    override val tune = Tunes.CONGA
    override val approximateDuration = 12.0

    private var age = 0.0
    private var leadX = START_X
    private var isReturning = false

    override fun begin() = stage.setGhostStates(Chase)

    override fun update(time: Double): Boolean {
        age += time
        stage.updateMouth(time)

        leadX += SPEED * time * (if (isReturning) -1 else 1)
        val direction = if (isReturning) LEFT else RIGHT

        // The first in line is Pac-Man when going right, and the last when returning
        val line = if (isReturning) stage.ghosts.reversed() + listOf<Ghost?>(null) else listOf<Ghost?>(null) + stage.ghosts

        line.forEachIndexed { i, ghost ->
            val x = leadX - SPACING * i * (if (isReturning) -1 else 1)
            val phase = age * WEAVE_SPEED - i * 0.7
            val row = Stage.ROW + sin(phase) * WEAVE_WIDTH
            val lift = abs(sin(age * HOP_SPEED - i * 0.8)) * HOP_HEIGHT

            if (ghost == null) stage.placePacMan(x, row, direction, lift = lift)
            else stage.placeGhost(ghost, x, row, direction, lift = lift)
        }

        if (!isReturning && leadX >= END_X) {
            isReturning = true
            // The line turns around, and the new leader starts off screen on the right
            leadX = END_X
        } else if (isReturning && leadX <= START_X - SPACING * 4) return true

        return false
    }
}

/**
 * A giant Blinky chases a tiny Pac-Man across the screen, with the other ghosts running behind. When they come back
 * Blinky has been flattened, and drags behind Pac-Man.
 */
class GiantAct(private val stage: Stage) : CutSceneAct {

    private companion object {
        const val SPEED = 10.0
        const val GIANT_SCALE = 3.2
        const val TINY_SCALE = 0.7
        const val SQUASH = 0.25
        const val GHOST_SPACING = 2.2
        const val START_X = -8.0
        const val END_X = 46.0
    }

    override val tune = Tunes.GIANT
    override val approximateDuration = 12.0

    private var age = 0.0
    private var leadX = START_X
    private var isReturning = false

    override fun begin() = stage.setGhostStates(Chase)

    override fun update(time: Double): Boolean {
        age += time
        stage.updateMouth(time)

        val bounce = abs(sin(age * 8)) * 0.25

        if (!isReturning) {
            leadX += SPEED * time

            stage.placePacMan(leadX, Stage.ROW, RIGHT, TINY_SCALE, lift = bounce)
            stage.placeGhost(stage.blinky, leadX - 5.0, Stage.ROW, RIGHT, GIANT_SCALE, lift = abs(sin(age * 4)) * 0.5)
            listOf(stage.pinky, stage.inky, stage.clyde).forEachIndexed { i, ghost ->
                stage.placeGhost(ghost, leadX - 11.0 - GHOST_SPACING * i, Stage.ROW, RIGHT, lift = abs(sin(age * 8 - i)) * 0.5)
            }

            if (leadX >= END_X) {
                isReturning = true
                leadX = END_X
                stage.setGhostStates(Frightened)
            }
        } else {
            leadX -= SPEED * time * 1.2

            stage.placePacMan(leadX, Stage.ROW, LEFT, lift = bounce)
            // Blinky is flattened and has to slide along on the floor
            stage.placeGhost(stage.blinky, leadX + 2.8, Stage.ROW, LEFT, GIANT_SCALE * 0.6, squash = SQUASH)
            listOf(stage.pinky, stage.inky, stage.clyde).forEachIndexed { i, ghost ->
                stage.placeGhost(ghost, leadX - 3.0 - GHOST_SPACING * i, Stage.ROW, LEFT, lift = abs(sin(age * 10 - i)) * 0.5)
            }

            if (leadX <= -22.0) return true
        }

        return false
    }
}

/**
 * The ghosts circle around a growing Pac-Man, faster and faster. Then they get scared, and are flung off the screen.
 */
class OrbitAct(private val stage: Stage) : CutSceneAct {

    private companion object {
        const val ORBIT_TIME = 4.0
        const val FLING_TIME = 2.2
        const val RADIUS = 6.5
        const val MIN_SPEED = 1.5
        const val MAX_SPEED = 6.5
        const val MAX_SCALE = 2.2
        const val FLING_DISTANCE = 32.0
    }

    override val tune = Tunes.ORBIT
    override val approximateDuration = 6.5

    private var age = 0.0
    private var angle = 0.0

    override fun begin() = stage.setGhostStates(Chase)

    override fun update(time: Double): Boolean {
        age += time
        stage.updateMouth(time)

        val orbitProgress = (age / ORBIT_TIME).coerceAtMost(1.0)
        val flingProgress = ((age - ORBIT_TIME) / FLING_TIME).coerceAtLeast(0.0)

        angle += (MIN_SPEED + (MAX_SPEED - MIN_SPEED) * orbitProgress) * time

        val radius = RADIUS + FLING_DISTANCE * flingProgress * flingProgress
        val pacmanScale = 1.0 + (MAX_SCALE - 1.0) * orbitProgress

        if (flingProgress > 0.0 && stage.blinky.state != Frightened) stage.setGhostStates(Frightened)

        stage.ghosts.forEachIndexed { i, ghost ->
            val a = angle + i * PI / 2
            val x = Stage.CENTER_X + cos(a) * radius
            val z = Stage.ROW + sin(a) * radius * 0.85
            val lift = flingProgress * 5.0 + abs(sin(age * 6 + i)) * 0.3
            stage.placeGhost(ghost, x, z, directionOf(-sin(a), cos(a)), lift = lift, spin = age * (3 + i) * flingProgress * 4)
        }

        // Pac-Man keeps his mouth towards Blinky
        val a = angle
        stage.placePacMan(Stage.CENTER_X, Stage.ROW, directionOf(cos(a), sin(a)), pacmanScale)

        return flingProgress >= 1.0
    }
}

/**
 * The ghosts float up like balloons, and Pac-Man pops them one by one, getting bigger with each of them.
 */
class BalloonsAct(private val stage: Stage) : CutSceneAct {

    private companion object {
        const val WALK_SPEED = 9.0
        const val START_X = -3.0
        const val RISE_TIME = 2.0
        const val MAX_LIFT = 5.5
        const val POP_INTERVAL = 0.9
        const val FIRST_POP_DELAY = 0.6
        const val EXIT_SPEED = 16.0
        const val END_X = -4.0

        val GHOST_X = doubleArrayOf(9.5, 12.5, 17.5, 20.5)
    }

    private enum class Phase { Walk, Rise, Pop, Exit }

    override val tune = Tunes.BALLOONS
    override val approximateDuration = 12.0

    private var phase = Phase.Walk
    private var phaseTime = 0.0
    private var age = 0.0
    private var pacmanX = START_X
    private var popped = 0

    override fun begin() = stage.setGhostStates(Chase)

    override fun update(time: Double): Boolean {
        age += time
        phaseTime += time
        stage.updateMouth(time)

        when (phase) {
            Phase.Walk -> {
                pacmanX += WALK_SPEED * time
                stage.ghosts.forEachIndexed { i, ghost -> stage.placeGhost(ghost, GHOST_X[i], Stage.ROW + 5.5, DOWN) }
                stage.placePacMan(pacmanX, Stage.ROW + 5.5, RIGHT)
                if (pacmanX >= Stage.CENTER_X) next(Phase.Rise)
            }

            Phase.Rise -> {
                val progress = (phaseTime / RISE_TIME).coerceAtMost(1.0)
                placeBalloons(MAX_LIFT * progress)
                stage.placePacMan(Stage.CENTER_X, Stage.ROW + 5.5, UP)
                if (progress >= 1.0) next(Phase.Pop)
            }

            Phase.Pop -> {
                val shouldBePopped = ((phaseTime - FIRST_POP_DELAY) / POP_INTERVAL).toInt().coerceIn(0, stage.ghosts.size)
                while (popped < shouldBePopped) stage.ghosts[popped++].isVisible = false

                placeBalloons(MAX_LIFT)

                // Pac-Man jumps up whenever he's about to pop a balloon
                val jump = abs(sin(phaseTime / POP_INTERVAL * PI)) * 2.5
                stage.placePacMan(Stage.CENTER_X, Stage.ROW + 5.5, UP, scale = 1.0 + popped * 0.25, lift = jump)

                if (popped >= stage.ghosts.size && phaseTime > FIRST_POP_DELAY + POP_INTERVAL * stage.ghosts.size) next(Phase.Exit)
            }

            Phase.Exit -> {
                pacmanX -= EXIT_SPEED * time
                stage.placePacMan(pacmanX, Stage.ROW + 5.5, LEFT, scale = 1.0 + stage.ghosts.size * 0.25)
                if (pacmanX <= END_X) return true
            }
        }

        return false
    }

    private fun next(newPhase: Phase) {
        phase = newPhase
        phaseTime = 0.0
        if (newPhase == Phase.Exit) pacmanX = Stage.CENTER_X
    }

    private fun placeBalloons(lift: Double) {
        stage.ghosts.forEachIndexed { i, ghost ->
            val sway = sin(age * 2 + i * 1.3) * 0.6
            stage.placeGhost(
                ghost,
                GHOST_X[i] + sway,
                Stage.ROW + 5.5,
                DOWN,
                scale = 1.0 - 0.15 * (lift / MAX_LIFT),
                lift = lift + sin(age * 3 + i) * 0.3,
                spin = sin(age * 2 + i) * 0.4,
            )
        }
    }
}
