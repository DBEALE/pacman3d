package pacman3d.renderables

import pacman3d.entities.World
import pacman3d.ext.toMeshStandardMaterial
import pacman3d.logic.Position
import three.js.ConeGeometry
import three.js.Group
import three.js.Mesh
import three.js.TorusGeometry
import kotlin.math.sin

class SpeedPowerUpRenderable : Renderable {

    private companion object {
        const val COLOR = 0xFFD21E
        const val GLOW_COLOR = 0xAA5500
        const val HEIGHT = 0.8
        const val SPIN_SPEED = 3.0
        const val BOB_SPEED = 3.0
        const val BOB_HEIGHT = 0.1
    }

    private val material = COLOR.toMeshStandardMaterial(roughness = 0.2).apply {
        emissive.setHex(GLOW_COLOR)
    }

    // Two stacked cones pointing opposite ways make a simple bolt-like shape
    private val upper = Mesh(ConeGeometry(0.3, 0.6, 4), material).apply { position.y = 0.25 }

    private val lower = Mesh(ConeGeometry(0.3, 0.6, 4), material).apply {
        position.y = -0.25
        rotateX(kotlin.math.PI)
    }

    private val ring = Mesh(TorusGeometry(0.5, 0.03, 6, 24), material).apply { rotateX(kotlin.math.PI / 2) }

    private val bolt = Group().add(upper, lower)

    override val sceneObject = Group().apply {
        visible = false
        add(bolt, ring)
    }

    private var age = 0.0

    override fun setup(world: World) = Unit

    fun show(where: Position) {
        sceneObject.position.set(where.worldX, HEIGHT, where.worldY)
        sceneObject.visible = true
    }

    fun hide() {
        sceneObject.visible = false
    }

    override fun update(world: World, time: Double) {
        age += time
        bolt.rotateY(time * SPIN_SPEED)
        sceneObject.position.y = HEIGHT + sin(age * BOB_SPEED) * BOB_HEIGHT
    }
}
