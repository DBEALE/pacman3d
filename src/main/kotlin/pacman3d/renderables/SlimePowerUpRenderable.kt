package pacman3d.renderables

import pacman3d.entities.World
import pacman3d.ext.toMeshStandardMaterial
import pacman3d.logic.Position
import three.js.Group
import three.js.Mesh
import three.js.SphereGeometry
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** A wobbling blob of green goo with bubbles rising through it */
class SlimePowerUpRenderable : Renderable {

    private companion object {
        const val COLOR = 0x62E02B
        const val GLOW_COLOR = 0x1B6A0A
        const val HEIGHT = 0.7
        const val BOB_SPEED = 3.0
        const val BOB_HEIGHT = 0.08
        const val WOBBLE_SPEED = 5.0
        const val BUBBLES = 3
    }

    private val material = COLOR.toMeshStandardMaterial(roughness = 0.05).apply {
        emissive.setHex(GLOW_COLOR)
        transparent = true
        opacity = 0.85
    }

    private val blob = Mesh(SphereGeometry(0.4, 20, 16), material)

    // A drop of goo hanging from the bottom, about to fall
    private val drip = Mesh(SphereGeometry(0.16, 12, 10), material).apply {
        position.y = -0.4
        scale.set(0.8, 1.5, 0.8)
    }

    private val bubbles = List(BUBBLES) { Mesh(SphereGeometry(0.07, 8, 8), material) }

    override val sceneObject = Group().apply {
        visible = false
        add(blob, drip)
        bubbles.forEach { add(it) }
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
        sceneObject.position.y = HEIGHT + sin(age * BOB_SPEED) * BOB_HEIGHT

        val wobble = sin(age * WOBBLE_SPEED) * 0.1
        blob.scale.set(1.0 + wobble, 1.0 - wobble, 1.0 + wobble)

        bubbles.forEachIndexed { i, bubble ->
            val phase = (age * 0.8 + i / BUBBLES.toDouble()) % 1.0
            val angle = age + i * 2 * PI / BUBBLES
            val size = 1.0 - phase * 0.6
            bubble.position.set(cos(angle) * 0.18, -0.3 + phase * 0.6, sin(angle) * 0.18)
            bubble.scale.set(size, size, size)
        }
    }
}
