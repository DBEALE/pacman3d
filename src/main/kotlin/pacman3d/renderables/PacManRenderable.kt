package pacman3d.renderables

import pacman3d.ext.*
import pacman3d.logic.Direction
import pacman3d.maze.MazeConst
import pacman3d.entities.PacMan
import pacman3d.entities.World
import pacman3d.renderables.PacManMesh.Companion.SIZE
import three.js.PointLight
import kotlin.math.PI

class PacManRenderable(private val entity: PacMan) : Renderable {

    private val pacmanObject = PacManMesh().apply {
        // A warm glow that lights up the corridors around Pac-Man
        add(PointLight(0xFFE680, 0.9, 8.0, 2.0).apply { position.set(0.0, 1.6, 0.0) })
    }

    override val sceneObject = pacmanObject

    override fun setup(world: World) {
        sceneObject.position.set(entity.position.worldX, SIZE / 2, entity.position.worldY)
    }

    override fun update(world: World, time: Double) = with (pacmanObject) {
        val entityPosition = entity.position

        if (entity.isActive) updateMouthOpen(time)

        opacity = when {
            entityPosition.x < 2.0 -> entityPosition.x - 1
            entityPosition.x >= MazeConst.WIDTH_UNITS - 2 -> MazeConst.WIDTH_UNITS - 1 - entityPosition.x
            else -> 1.0
        }

        position.x = entityPosition.worldX
        position.z = entityPosition.worldY

        setRotationFromAxisAngle(Y_AXIS, when (entity.currentDirection) {
            Direction.DOWN -> 1.5 * PI
            Direction.RIGHT -> 0
            Direction.UP -> PI / 2
            Direction.LEFT -> PI
        })
    }
}