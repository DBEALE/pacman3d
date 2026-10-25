package pacman3d.entities

import pacman3d.logic.Position
import pacman3d.renderables.FruitRenderable
import kotlin.math.abs

/**
 * A bonus fruit that appears twice per maze below the ghost house. The fruit gets more valuable with every level.
 */
class Fruit : AbsGameEntity<FruitRenderable>() {

    companion object {
        private const val VISIBLE_TIME = 9.5

        // The corridor below the ghost house, which all the mazes share
        val POSITION = Position(15.0, 20.5)

        private val POINTS = arrayOf(100, 300, 500, 700, 1000, 3000, 5000)
    }

    private var timeLeft = 0.0

    private var kind = 0

    var isShown = false
        private set

    val points get() = POINTS[kind]

    override fun createRenderable() = FruitRenderable()

    override fun setup(world: World) {
        super.setup(world)
        setLevel(0)
    }

    fun setLevel(level: Int) {
        kind = minOf(level, POINTS.size - 1)
        renderable.setKind(kind)
        renderable.setClearedLevels(level)
    }

    fun show() {
        timeLeft = VISIBLE_TIME
        isShown = true
        renderable.setShown(true)
    }

    fun hide() {
        isShown = false
        renderable.setShown(false)
    }

    override fun resetState(world: World) = hide()

    override fun update(world: World, time: Double) {
        if (!isShown) return

        timeLeft -= time
        if (timeLeft <= 0) hide()
    }

    fun isEatenBy(pacmanPosition: Position)
        = isShown && abs(pacmanPosition.x - POSITION.x) < 0.9 && abs(pacmanPosition.y - POSITION.y) < 0.6
}
