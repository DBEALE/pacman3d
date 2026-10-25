package pacman3d.renderables

import pacman3d.ext.HALF_PI
import pacman3d.ext.toMeshStandardMaterial
import three.js.*
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Bonus fruit models, roughly one tile in size and centered around the origin. */
object FruitMeshes {

    const val COUNT = 7

    private const val RED = 0xE8112D
    private const val GREEN = 0x2E9B3C
    private const val BROWN = 0x6B3E1E
    private const val ORANGE = 0xFF8C1A
    private const val YELLOW = 0xFFD62E
    private const val LIGHT_GREEN = 0x7ACC4B
    private const val CYAN = 0x5CE1E6

    fun create(kind: Int): Group = when (kind) {
        0 -> cherries()
        1 -> strawberry()
        2 -> orange()
        3 -> apple()
        4 -> melon()
        5 -> bell()
        else -> key()
    }

    private fun part(geometry: Geometry, color: Int) = Mesh(geometry, color.toMeshStandardMaterial(roughness = 0.3)).apply { castShadow = true }

    private fun ball(radius: Double, color: Int) = part(SphereGeometry(radius, 14, 14), color)

    private fun leaf(color: Int = GREEN) = ball(0.1, color).apply { scale.set(1.6, 0.35, 1.0) }

    private fun cherries() = Group().apply {
        add(ball(0.22, RED).apply { position.set(-0.22, -0.1, 0.08) })
        add(ball(0.22, RED).apply { position.set(0.22, -0.1, -0.06) })

        // The stems lean towards each other and meet at the top
        add(part(CylinderGeometry(0.025, 0.025, 0.55, 6), GREEN).apply {
            position.set(-0.12, 0.2, 0.07)
            rotateZ(-0.35)
        })
        add(part(CylinderGeometry(0.025, 0.025, 0.55, 6), GREEN).apply {
            position.set(0.12, 0.2, -0.05)
            rotateZ(0.35)
        })
        add(leaf().apply { position.set(0.05, 0.48, 0.0) })
    }

    private fun strawberry() = Group().apply {
        add(ball(0.32, RED).apply { scale.set(0.95, 1.15, 0.95) })
        add(leaf().apply { position.y = 0.34 })

        for (i in 0 until 6) {
            val a = i * 2 * PI / 6
            add(ball(0.03, YELLOW).apply { position.set(cos(a) * 0.27, -0.12 + (i % 2) * 0.2, sin(a) * 0.27) })
        }
    }

    private fun orange() = Group().apply {
        add(ball(0.36, ORANGE))
        add(leaf().apply { position.set(0.06, 0.36, 0.0) })
    }

    private fun apple() = Group().apply {
        add(ball(0.34, RED).apply { scale.set(1.0, 0.92, 1.0) })
        add(part(CylinderGeometry(0.02, 0.02, 0.18, 6), BROWN).apply { position.y = 0.38 })
        add(leaf().apply {
            position.set(0.1, 0.42, 0.0)
            rotateZ(0.4)
        })
    }

    private fun melon() = Group().apply {
        add(ball(0.35, LIGHT_GREEN))

        // Stripes running from pole to pole
        for (i in 0 until 3) {
            add(part(TorusGeometry(0.355, 0.015, 6, 20), GREEN).apply { rotateY(i * PI / 3) })
        }
        add(part(CylinderGeometry(0.025, 0.025, 0.12, 6), BROWN).apply { position.y = 0.38 })
    }

    private fun bell() = Group().apply {
        add(part(CylinderGeometry(0.15, 0.36, 0.5, 20), YELLOW).apply { position.y = 0.0 })
        add(ball(0.15, YELLOW).apply { position.y = 0.25 })
        add(part(TorusGeometry(0.36, 0.04, 8, 24), YELLOW).apply {
            rotateX(HALF_PI)
            position.y = -0.25
        })
        add(ball(0.09, BROWN).apply { position.y = -0.3 })
    }

    private fun key() = Group().apply {
        add(part(TorusGeometry(0.17, 0.05, 8, 16), CYAN).apply { position.y = 0.32 })
        add(part(CylinderGeometry(0.04, 0.04, 0.55, 8), YELLOW).apply { position.y = -0.05 })
        add(part(BoxGeometry(0.2, 0.07, 0.06), YELLOW).apply { position.set(0.1, -0.3, 0.0) })
        add(part(BoxGeometry(0.14, 0.07, 0.06), YELLOW).apply { position.set(0.07, -0.18, 0.0) })
    }
}
