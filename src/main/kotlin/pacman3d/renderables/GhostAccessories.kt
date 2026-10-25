package pacman3d.renderables

import pacman3d.ext.HALF_PI
import pacman3d.ext.TWO_PI
import pacman3d.ext.toMeshStandardMaterial
import pacman3d.ext.toRadians
import pacman3d.renderables.GhostRenderable.Companion.EYE_DEPTH
import pacman3d.renderables.GhostRenderable.Companion.EYE_HEIGHT
import pacman3d.renderables.GhostRenderable.Companion.EYE_SPACING
import pacman3d.renderables.GhostRenderable.Companion.EYE_SPACING_ANGLE
import pacman3d.renderables.GhostRenderable.Companion.HEAD_FRACTION
import pacman3d.renderables.GhostRenderable.Companion.HEIGHT
import pacman3d.renderables.GhostRenderable.Companion.RADIUS
import three.js.*
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Per-ghost props that give each ghost its own character. All props are modeled in the ghost's local space,
 * where the face looks towards +z. Blinky is a devil, Pinky a girl with a flower, Inky a DJ and Clyde an old man.
 */
object GhostAccessories {

    private const val HEAD_Y = HEAD_FRACTION * HEIGHT
    private const val HEAD_HEIGHT = (1.0 - HEAD_FRACTION) * HEIGHT

    private val SIDES = listOf(-1.0, 1.0)

    fun create(ghostName: String): Group = when (ghostName) {
        "Blinky" -> devil()
        "Pinky" -> girl()
        "Inky" -> dj()
        "Clyde" -> oldMan()
        else -> Group()
    }

    private fun devil() = Group().apply {
        for (side in SIDES) {
            add(part(ConeGeometry(0.21, 0.8, 14), 0x8B0000).apply {
                onHead(y = 1.7, angleDeg = side * 35)
                rotateX(0.4)
                translateY(0.3)
            })
            // Angry eyebrows, slanting down towards the nose
            add(part(BoxGeometry(0.32, 0.07, 0.06), 0x1A0000).apply {
                onHead(y = 1.4, angleDeg = side * 21, outset = 0.02)
                rotateZ(side * 0.5)
            })
        }
    }

    private fun girl() = Group().apply {
        add(flower().apply {
            onHead(y = 1.68, angleDeg = 55.0, outset = 0.02)
            rotateX(-0.6)
        })

        for (side in SIDES) {
            // Rosy cheeks
            add(part(SphereGeometry(0.11, 12, 12), 0xFF6EA8).apply {
                onHead(y = 0.74, angleDeg = side * 32, outset = -0.02)
                scale.set(1.0, 0.6, 0.35)
            })
            // Eyelashes at the outer corner of each eye
            for (lashAngle in listOf(25.0, 60.0)) {
                add(Group().apply {
                    onHead(y = 1.22, angleDeg = side * 36, outset = 0.02)
                    rotateZ(side * lashAngle.toRadians())
                    add(part(BoxGeometry(0.15, 0.03, 0.03), 0x1A1A1A).apply { position.x = side * 0.075 })
                })
            }
        }
    }

    private fun flower() = Group().apply {
        add(part(SphereGeometry(0.07, 12, 12), 0xFFD400).apply { position.z = 0.04 })
        for (i in 0 until 6) {
            val a = i * TWO_PI / 6
            add(part(SphereGeometry(0.08, 12, 12), 0xFF2D6F).apply {
                position.set(cos(a) * 0.13, sin(a) * 0.13, 0.0)
                scale.set(1.0, 1.0, 0.4)
            })
        }
    }

    private fun dj() = Group().apply {
        // Headband follows the ellipsoid shape of the head
        add(part(TorusGeometry(0.84, 0.05, 10, 40, PI), 0x22223A).apply {
            position.y = HEAD_Y
            scale.set(1.0, 1.2, 1.0)
        })

        for (side in SIDES) {
            add(part(CylinderGeometry(0.26, 0.26, 0.16, 24), 0x22223A).apply {
                rotateZ(HALF_PI)
                position.set(side * 0.83, HEAD_Y + 0.04, 0.0)
            })
            add(part(CylinderGeometry(0.17, 0.17, 0.02, 24), 0xFF3DAB).apply {
                rotateZ(HALF_PI)
                position.set(side * 0.92, HEAD_Y + 0.04, 0.0)
            })
        }
    }

    private fun oldMan() = Group().apply {
        // Hat
        add(Group().apply {
            rotateZ(0.08)
            add(part(CylinderGeometry(0.95, 0.95, 0.05, 32), 0x5B3A29).apply { position.y = 1.62 })
            add(part(CylinderGeometry(0.52, 0.62, 0.42, 32), 0x5B3A29).apply { position.y = 1.83 })
            add(part(CylinderGeometry(0.63, 0.63, 0.08, 32), 0x1E1E1E).apply { position.y = 1.7 })
        })

        for (side in SIDES) {
            // Round glasses, in front of the eyes
            add(part(TorusGeometry(0.27, 0.025, 8, 24), 0x1E1E1E).apply {
                position.set(side * EYE_SPACING, EYE_HEIGHT, RADIUS - EYE_DEPTH * 2 + 0.03)
                rotateY(side * EYE_SPACING_ANGLE)
            })
            // Drooping white mustache
            add(part(SphereGeometry(0.14, 12, 12), 0xF2F2F2).apply {
                onHead(y = 0.68, angleDeg = side * 10, outset = 0.0)
                scale.set(1.2, 0.5, 0.6)
                rotateZ(-side * 0.25)
            })
            // White hair tufts under the hat
            add(part(SphereGeometry(0.12, 12, 12), 0xF2F2F2).apply {
                onHead(y = 1.4, angleDeg = side * 72, outset = 0.0)
            })
        }

        // Pipe
        add(part(CylinderGeometry(0.03, 0.03, 0.45, 8), 0x1E1E1E).apply {
            rotateX(HALF_PI)
            position.set(0.2, 0.55, RADIUS + 0.17)
        })
        add(part(CylinderGeometry(0.11, 0.08, 0.24, 16), 0x6B3E1E).apply {
            position.set(0.2, 0.65, RADIUS + 0.4)
        })
    }

    private fun part(geometry: Geometry, color: Int) = Mesh(geometry, color.toMeshStandardMaterial(roughness = 0.45)).apply { castShadow = true }

    // Horizontal radius of the ghost's body at the given height
    private fun bodyRadius(y: Double): Double {
        if (y <= HEAD_Y) return RADIUS
        val k = (y - HEAD_Y) / HEAD_HEIGHT
        return RADIUS * sqrt(1.0 - k * k)
    }

    // Places the object on the body surface at [y], [angleDeg] degrees away from the front, facing outwards
    private fun <T : Object3D> T.onHead(y: Double, angleDeg: Double, outset: Double = 0.0): T {
        val a = angleDeg.toRadians()
        val r = bodyRadius(y) + outset
        position.set(r * sin(a), y, r * cos(a))
        rotateY(a)
        return this
    }
}
