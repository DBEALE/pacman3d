package pacman3d.entities

import pacman3d.logic.Position
import pacman3d.renderables.SpeedPowerUpRenderable

/**
 * A lightning bolt that appears once per maze. Eating it makes Pac-Man faster for a few seconds.
 */
class SpeedPowerUp : AbsGameEntity<SpeedPowerUpRenderable>() {

    companion object {
        private const val VISIBLE_TIME = 12.0
    }

    val position = Position()

    private var timeLeft = 0.0

    var isShown = false
        private set

    override fun createRenderable() = SpeedPowerUpRenderable()

    override fun setup(world: World) {
        super.setup(world)
        hide()
    }

    fun show(where: Position) {
        position.copy(where)
        timeLeft = VISIBLE_TIME
        isShown = true
        renderable.show(where)
    }

    fun hide() {
        isShown = false
        renderable.hide()
    }

    override fun resetState(world: World) = hide()

    override fun update(world: World, time: Double) {
        if (!isShown) return

        timeLeft -= time
        if (timeLeft <= 0) hide()
    }

    fun isEatenBy(pacmanPosition: Position) = isShown && pacmanPosition.mazeIndexEquals(position)
}
