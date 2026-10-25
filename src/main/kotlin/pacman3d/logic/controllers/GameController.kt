package pacman3d.logic.controllers

import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.events.KeyboardEvent
import pacman3d.*
import pacman3d.logic.HighScores
import pacman3d.entities.AbsGameEntity
import pacman3d.entities.Fruit
import pacman3d.entities.GameStateBanner
import pacman3d.entities.GameStateBanner.Companion.GAME_OVER
import pacman3d.entities.Maze.Companion.isDot
import pacman3d.entities.Maze.Companion.isDotOrEnergizer
import pacman3d.entities.Maze.Companion.isEnergizer
import pacman3d.entities.Maze.Companion.isGhostHouse
import pacman3d.entities.Maze.Companion.isValid
import pacman3d.entities.PacManLives
import pacman3d.entities.World
import pacman3d.logic.Direction
import pacman3d.logic.Position
import pacman3d.logic.controllers.GameState.*
import pacman3d.maze.MazeLayouts
import three.js.Camera
import three.js.Scene
import kotlin.random.Random

/**
 * For each ghost:
 * - Dot counter with a dot limit
 * If preferred ghost counter reaches limit, ghost leaves house immediately. Dot counter stops (not reset)
 *
 * Dot limit:
 * - Pinky: 0 (always) - Leaves immediately as level begins
 * - Inky: Level 1 - 30, Level 2 - 0, Level 3 - 0
 * - Clyde: Level 1 - 60, Level 2 - 50, Level 3 - 0
 *
 * if (life is lost) - use a system-level dot counter instead of ghost-individual dot counter
 * This counter is enabled and set to zero when a life is lost
 * Pinky released when counter = 7
 * Inky released when counter = 17
 * if (Clyde in house && counter == 32) disableCounter() -> Switch back to ghost-individual counters
 *
 * TODO:
 * The first ghost captured after an energizer has been eaten is always worth 200 points. Each additional ghost
 * captured from the same energizer will then be worth twice as many points as the one before it-400, 800, and 1,600
 * points, respectively. If all four ghosts are captured at all four energizers, an additional 12,000 points can be
 * earned on these earlier levels
 */

private enum class GameState {
    WaitingForPlayer,
    Playing,
    LevelComplete,
    GameOver,
}

class GameController {

    companion object {
        private const val PACMAN_EATEN_TIME_TIME = 1.4
        private const val GHOST_EATEN_EFFECT_TIME = 0.8

        private const val FREEZE_TIME = 6.0
        private const val SPEED_BOOST_TIME = 6.0
        private const val SPEED_BOOST_FACTOR = 1.4
        private const val PACMAN_SPEED = 7.0
        private const val BONUS_POINTS_TIME = 1.5
        private const val EXTRA_LIFE_SCORE = 10000

        // The fruit appears after this fraction of the dots were eaten
        private const val FIRST_FRUIT_DOTS_FRACTION = 0.3
        private const val SECOND_FRUIT_DOTS_FRACTION = 0.7
        private const val FREEZE_POWER_UP_DOTS_FRACTION = 0.5
        private const val SPEED_POWER_UP_DOTS_FRACTION = 0.8

        // The freeze power-up never appears this close to Pac-Man
        private const val FREEZE_POWER_UP_MIN_DISTANCE = 6.0

        // The row of the tunnel, where nothing is placed
        private const val TUNNEL_ROW = 17

        private const val GAME_OVER_BANNER_TIME = 2.5
        private const val ATTRACT_GAME_OVER_TIME = 3.0
        private const val ATTRACT_READY_TIME = 1.5
        private const val SCORES_SCREEN_TIME = 12.0
        private const val ATTRACT_LEVELS = 5
        private const val STARTING_LIVES = 3

        private const val KEY_ENTER = 13
        private const val KEY_SPACE = 32
        private const val KEY_ARROW_LEFT = 37
        private const val KEY_ARROW_RIGHT = 39
        private const val KEY_ARROW_UP = 38
        private const val KEY_ARROW_DOWN = 40

        private val ALLOWED_KEYS = arrayOf(
            KEY_ARROW_LEFT,
            KEY_ARROW_RIGHT,
            KEY_ARROW_UP,
            KEY_ARROW_DOWN
        )
    }

    private val world = World()

    // Starts at a later level when the page is opened with a level parameter, e.g. http://localhost:8080/?level=3
    private var level = Regex("[?&]level=(\\d+)").find(window.location.search)
        ?.groupValues?.get(1)?.toInt()?.minus(1)?.coerceAtLeast(0) ?: 0

    // The level in which a new game starts
    private val firstLevel = level

    private var gameState = WaitingForPlayer

    private var pointsForEatenGhost = 200

    private var livesLeft = STARTING_LIVES

    private var extraLifeAwarded = false

    private val ghostBehaviorController = GhostBehaviorController(world)

    private var visualEffectTimer: Timer? = null

    private var freezeTimer: Timer? = null

    private var speedBoostTimer: Timer? = null

    private var bonusPointsTimer: Timer? = null

    private var cutScene: LevelCompleteCutScene? = null

    // In the attract mode the game plays itself, until a key is pressed
    private var isAttract = false

    // The game is over and the high scores are shown, a key press starts a new game
    private var awaitingRestart = false

    // Drives the steps between the games: the game over banner, the high scores, the demo restarting
    private var flowTimer: Timer? = null

    private val autoPilot = AutoPilot(world)

    private fun initLevel() = with (world) {
        maze.layout = MazeLayouts.forLevel(level)
        maze.level = level

        world.resetState()

        ghostBehaviorController.initLevel(level)

        freezeTimer = null
        speedBoostTimer = null
        bonusPointsTimer = null
        fruit.setLevel(level)

        pacmanLives.setLives(livesLeft)
        pacman.speed = PACMAN_SPEED

        waitForPlayer()
    }

    private fun waitForPlayer() {
        gameState = WaitingForPlayer

        world.gameStateBanner.show(GameStateBanner.READY)
        world.setPacmanAndGhostsActive(false)

        // Nobody presses a key in the demo
        if (isAttract) flowTimer = Timer(ATTRACT_READY_TIME, ::beginPlaying)
    }

    private fun beginPlaying() {
        gameState = Playing
        world.gameStateBanner.hide()
        world.setPacmanAndGhostsActive(true)
    }

    /** Starts the demo, in which the game plays itself. Pressing a key ends it */
    private fun beginAttract() {
        isAttract = true
        awaitingRestart = false
        SoundPlayer.muted = true

        Overlay.hideAll()
        Overlay.setDemo(true)
        Overlay.showBanner("PRESS AN ARROW KEY TO PLAY")
        Overlay.showTable()

        resetGame(Random.nextInt(ATTRACT_LEVELS))
    }

    private fun startNewGame() {
        isAttract = false
        awaitingRestart = false
        SoundPlayer.muted = false

        Overlay.hideAll()

        resetGame(firstLevel)
    }

    private fun resetGame(startLevel: Int) {
        flowTimer = null
        visualEffectTimer = null
        unfreezeGhosts()
        endSpeedBoost()

        world.pacman.isVisible = true
        world.ghosts.forEach { it.isVisible = true }

        world.score.reset()
        Overlay.setHighScore(HighScores.top)

        level = startLevel
        livesLeft = STARTING_LIVES
        extraLifeAwarded = false
        autoPilot.reset()

        initLevel()
    }

    fun update(time: Double) {

        flowTimer?.update(time)

        cutScene?.let { scene ->
            scene.update(time)
            world.update(time)
            return
        }

        // When an effect is in progress we don't update the game logic
        visualEffectTimer?.let { timer ->
            timer.update(time)
            world.update(time)
            return
        }

        // TODO: Just temporary
        if (gameState == GameOver) {
            world.update(time)
            return
        }

        updateTimers(time)

        if (isAttract && gameState == Playing) autoPilot.update()

        updateGameLogic(time)

        ghostBehaviorController.update(time)

        world.update(time)
    }

    private fun updateTimers(time: Double) {
        freezeTimer?.update(time)
        speedBoostTimer?.update(time)
        bonusPointsTimer?.update(time)
    }

    private fun updateGameLogic(time: Double) {
        with (world) {
            handlePickups()

            if (maze[pacman.position].isDotOrEnergizer) {
                handleDotEaten()

                if (maze.dotsLeft == 0) {
                    beginLevelCompleteCutScene()
                    return
                }
            } else handleNoDotEaten(time)

            // Frozen ghosts are harmless, and can't be eaten either
            ghosts.firstOrNull { !it.isFrozen && it.position.mazeIndexEquals(pacman.position) }?.also { ghost ->
                when {
                    // Handle eaten ghosts
                    ghost.state.isFrightened -> {
                        SoundPlayer.play(Sound.EatGhost)
                        ghostBehaviorController.onGhostEaten(ghost)
                        bonusPoints.show(pointsForEatenGhost, pacman.position)
                        pointsForEatenGhost *= 2
                        beginVisualEffect(GHOST_EATEN_EFFECT_TIME, pacman, ghost)
                        return
                    }

                    // Handle captured pacman
                    ghost.state.canEatPacman -> {
                        SoundPlayer.play(Sound.LifeLost)
                        if (livesLeft == 0) gameOver() else {
                            pacmanLives.setLives(--livesLeft)
                            beginVisualEffect(PACMAN_EATEN_TIME_TIME, pacman, *ghosts, onEnd = ::restartAfterLifeLost)
                            return
                        }
                    }
                }
            }
        }
    }

    // Everyone returns to the starting positions, and the player is given time to get ready again
    private fun restartAfterLifeLost() {
        unfreezeGhosts()
        endSpeedBoost()
        world.pacman.resetState(world)
        ghostBehaviorController.onLifeLost()
        world.bonusPoints.hide()

        waitForPlayer()
    }

    private fun beginLevelCompleteCutScene() {
        gameState = LevelComplete
        world.gameStateBanner.hide()

        unfreezeGhosts()
        endSpeedBoost()
        world.fruit.hide()
        world.freezePowerUp.hide()
        world.speedPowerUp.hide()

        // The demo has no time for the cut scenes
        if (isAttract) {
            level++
            initLevel()
            return
        }

        cutScene = LevelCompleteCutScene.forLevel(level, world) {
            cutScene = null
            level++
            initLevel()
        }
    }

    private fun gameOver() {
        gameState = GameOver
        world.setPacmanAndGhostsActive(false)
        world.gameStateBanner.show(GAME_OVER)

        flowTimer = if (isAttract) Timer(ATTRACT_GAME_OVER_TIME, ::beginAttract) else Timer(GAME_OVER_BANNER_TIME, ::showHighScores)
    }

    private fun showHighScores() {
        world.gameStateBanner.hide()
        val score = world.score.points

        if (HighScores.qualifies(score)) {
            Overlay.promptForName(score) { name ->
                val position = HighScores.add(name, score)
                Overlay.setHighScore(HighScores.top)
                waitForNewGame(position)
            }
        } else waitForNewGame()
    }

    private fun waitForNewGame(highlight: Int = -1) {
        awaitingRestart = true
        Overlay.showTable(highlight)
        Overlay.showBanner("PRESS AN ARROW KEY TO PLAY")

        flowTimer = Timer(SCORES_SCREEN_TIME, ::beginAttract)
    }

    private fun handleDotEaten() {
        with (world) {
            val mazeValue = maze[pacman.position]
            val isEnergizer = mazeValue.isEnergizer

            SoundPlayer.play(Sound.Chomp)

            // The first ghost captured after an energizer has been eaten is always worth 200 points
            if (isEnergizer) pointsForEatenGhost = 200

            addScore(valueOf(mazeValue))

            dots.onDotEaten(pacman.position)
            maze.onDotEaten(pacman.position)
            ghostBehaviorController.onDotEaten(isEnergizer)

            val dotsEaten = maze.totalDots - maze.dotsLeft
            if (dotsEaten == (maze.totalDots * FIRST_FRUIT_DOTS_FRACTION).toInt()
                || dotsEaten == (maze.totalDots * SECOND_FRUIT_DOTS_FRACTION).toInt()) fruit.show()

            if (dotsEaten == (maze.totalDots * FREEZE_POWER_UP_DOTS_FRACTION).toInt()) spawnFreezePowerUp()
            if (dotsEaten == (maze.totalDots * SPEED_POWER_UP_DOTS_FRACTION).toInt()) spawnSpeedPowerUp()
        }
    }

    private fun handlePickups() {
        with (world) {
            if (fruit.isEatenBy(pacman.position)) {
                SoundPlayer.play(Sound.EatGhost)
                addScore(fruit.points)
                bonusPoints.show(fruit.points, Fruit.POSITION)
                bonusPointsTimer = Timer(BONUS_POINTS_TIME) {
                    bonusPointsTimer = null
                    bonusPoints.hide()
                }
                fruit.hide()
            }

            if (freezePowerUp.isEatenBy(pacman.position)) {
                freezePowerUp.hide()
                freezeGhosts()
            }

            if (speedPowerUp.isEatenBy(pacman.position)) {
                speedPowerUp.hide()
                boostPacmanSpeed()
            }
        }
    }

    private fun addScore(points: Int) {
        world.score += points
        Overlay.setHighScore(maxOf(HighScores.top, world.score.points))

        if (!extraLifeAwarded && world.score.points >= EXTRA_LIFE_SCORE) {
            extraLifeAwarded = true
            if (livesLeft < PacManLives.MAX_LIVES) world.pacmanLives.setLives(++livesLeft)
        }
    }

    private fun spawnFreezePowerUp() = spawnPowerUp { world.freezePowerUp.show(it) }

    private fun spawnSpeedPowerUp() = spawnPowerUp { world.speedPowerUp.show(it) }

    private fun boostPacmanSpeed() {
        world.pacman.speed = PACMAN_SPEED * SPEED_BOOST_FACTOR
        speedBoostTimer = Timer(SPEED_BOOST_TIME, ::endSpeedBoost)
    }

    private fun endSpeedBoost() {
        speedBoostTimer = null
        world.pacman.speed = PACMAN_SPEED
    }

    private fun spawnPowerUp(show: (Position) -> Unit) {
        with (world) {
            val candidates = ArrayList<Position>()

            maze.forEachTile { maze, x, y ->
                val tile = maze[x, y]
                if (tile.isValid && !tile.isGhostHouse && y != TUNNEL_ROW) candidates += Position(x, y)
            }

            val spot = candidates.filter { it.distanceTo(pacman.position) >= FREEZE_POWER_UP_MIN_DISTANCE }.randomOrNull()
            if (spot != null) show(spot)
        }
    }

    private fun freezeGhosts() {
        world.ghosts.forEach { it.isFrozen = true }
        freezeTimer = Timer(FREEZE_TIME, ::unfreezeGhosts)
    }

    private fun unfreezeGhosts() {
        freezeTimer = null
        world.ghosts.forEach { it.isFrozen = false }
    }

    private fun handleNoDotEaten(time: Double) {
        ghostBehaviorController.onNoDotEaten(time)
    }

    private fun beginVisualEffect(effectTime: Double, vararg participants: AbsGameEntity<*>, onEnd: (() -> Unit)? = null) {
        world.onVisualEffectBegin()
        ghostBehaviorController.onVisualEffectBegin()

        visualEffectTimer = Timer(effectTime) {
            endVisualEffect(participants)
            onEnd?.invoke()
        }

        participants.forEach { it.isVisible = false }
    }

    private fun endVisualEffect(participants: Array<out AbsGameEntity<*>>) {
        visualEffectTimer = null
        world.bonusPoints.hide()
        world.onVisualEffectEnd()
        ghostBehaviorController.onVisualEffectEnd()

        participants.forEach { it.isVisible = true }
    }

    fun start() {

        Overlay.setHighScore(HighScores.top)
        beginAttract()

        document.onkeydown = ::handleKeyboardEvent
    }

    /** Cheat: double clicking a fruit at the bottom of the screen skips to the next maze */
    fun onDoubleClick(clientX: Int, clientY: Int, camera: Camera) {
        if (isAttract || awaitingRestart || cutScene != null || visualEffectTimer != null || gameState == GameOver || gameState == LevelComplete) return
        if (!world.fruit.renderable.isIndicatorAt(clientX, clientY, camera)) return

        unfreezeGhosts()
        endSpeedBoost()
        world.bonusPoints.hide()
        world.fruit.hide()
        world.freezePowerUp.hide()
        world.speedPowerUp.hide()

        level++
        initLevel()
    }

    private fun handleKeyboardEvent(event: KeyboardEvent) {

        val isStartKey = event.keyCode == KEY_ENTER || event.keyCode == KEY_SPACE

        if (!ALLOWED_KEYS.contains(event.keyCode) && !isStartKey) return

        if (isAttract || awaitingRestart) {
            startNewGame()
            return
        }

        if (!ALLOWED_KEYS.contains(event.keyCode)) return

        when (gameState) {
            WaitingForPlayer -> beginPlaying()

            GameOver, LevelComplete -> return
        }

        with (world.pacman) {
            when (event.keyCode) {
                KEY_ARROW_UP -> requestDirection(Direction.UP)
                KEY_ARROW_DOWN -> requestDirection(Direction.DOWN)
                KEY_ARROW_LEFT -> requestDirection(Direction.LEFT)
                KEY_ARROW_RIGHT -> requestDirection(Direction.RIGHT)
            }
        }
    }

    fun setup(scene: Scene) {
        world.addRenderablesToScene(scene)
    }

    private fun valueOf(mazeValue: Byte) = when {
        mazeValue.isDot -> 10
        mazeValue.isEnergizer -> 50
        else -> 0
    }
}