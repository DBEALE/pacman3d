package pacman3d.renderables

import pacman3d.entities.World
import pacman3d.ext.HALF_PI
import pacman3d.ext.toMeshStandardMaterial
import pacman3d.logic.Position
import three.js.Group
import three.js.Mesh
import three.js.OctahedronGeometry
import three.js.TorusGeometry
import kotlin.math.sin

class FreezePowerUpRenderable : Renderable {

    private companion object {
        const val COLOR = 0x7FE8FF
        const val GLOW_COLOR = 0x2288AA
        const val HEIGHT = 0.8
        const val SPIN_SPEED = 2.0
        const val BOB_SPEED = 3.0
        const val BOB_HEIGHT = 0.1
    }

    private val material = COLOR.toMeshStandardMaterial(roughness = 0.1).apply {
        emissive.setHex(GLOW_COLOR)
        transparent = true
        opacity = 0.9
    }

    private val crystal = Mesh(OctahedronGeometry(0.4), material).apply { scale.set(0.8, 1.4, 0.8) }

    private val ring = Mesh(TorusGeometry(0.5, 0.03, 6, 24), material).apply { rotateX(HALF_PI) }

    override val sceneObject = Group().apply {
        visible = false
        add(crystal, ring)
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
        crystal.rotateY(time * SPIN_SPEED)
        sceneObject.position.y = HEIGHT + sin(age * BOB_SPEED) * BOB_HEIGHT
    }
}
