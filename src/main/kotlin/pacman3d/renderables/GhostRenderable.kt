package pacman3d.renderables

import pacman3d.entities.Ghost
import pacman3d.entities.World
import pacman3d.ext.*
import pacman3d.logic.Direction
import pacman3d.logic.Direction.RIGHT
import pacman3d.logic.GhostState
import pacman3d.logic.GhostState.Eaten
import pacman3d.logic.GhostState.Frightened
import pacman3d.maze.MazeConst
import three.js.*
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class GhostRenderable(private val entity: Ghost, private val color: Int) : Renderable {

    companion object {
        // The percentage the wavy part of the ghost spans out of its entire height
        private const val WAVE_FRACTION = 0.1
        private const val MOUTH_FRACTION = 0.35
        internal const val HEAD_FRACTION = 0.5
        private const val EYE_FRACTION = 0.55

        private const val WIDTH = MazeConst.UNIT_SIZE * 1.6
        internal const val HEIGHT = WIDTH * 1.2
        // The height at which the ghosts normally float above the floor
        internal const val BASE_Y = WAVE_FRACTION * HEIGHT
        internal const val RADIUS = WIDTH / 2.0
        internal const val EYE_DEPTH = 0.02
        internal const val EYE_HEIGHT = EYE_FRACTION * HEIGHT
        private const val EYE_WIDTH_RADIUS = RADIUS * 0.25
        private const val EYE_HEIGHT_RADIUS = EYE_WIDTH_RADIUS * 1.15
        private const val IRIS_RADIUS = EYE_WIDTH_RADIUS / 2
        internal val EYE_SPACING_ANGLE = (20.0).toRadians()
        internal val EYE_SPACING = sin(EYE_SPACING_ANGLE) * RADIUS

        private const val SLICES = 60
        private const val STACKS = 20

        private const val BODY_GLOW = 0.2

        private const val BODY_FRIGHTENED_COLOR = 0x1B1AD4
        private const val BODY_FROZEN_COLOR = 0x9FE8FF
        private const val FACE_FRIGHTENED_COLOR = 0xCAD5FF

        // Color swaps per second while a frightened ghost flashes
        private const val FLASH_RATE = 5.0

        private const val BODY_FRIGHTENED_FADE_COLOR = 0xB3B5D1
        private const val FACE_FRIGHTENED_FADE_COLOR = 0xAA0916
    }

    private val mouthCurve = CatmullRomCurve3(arrayOf(
        Vector2(RADIUS * -0.7, 0),
        Vector2(RADIUS * -0.5, 0.2),
        Vector2(RADIUS * -0.36, 0),
        Vector2(RADIUS * -0.2, 0),
        Vector2(RADIUS * 0, 0.2),
        Vector2(RADIUS * 0.2, 0),
        Vector2(RADIUS * 0.36, 0),
        Vector2(RADIUS * 0.5, 0.2),
        Vector2(RADIUS * 0.7, 0),
    ).mapIndexed { i, v2 -> Vector3(v2.x, MOUTH_FRACTION + v2.y, cos((i - 4) * (40 / 4).toRadians()) * RADIUS) }.toTypedArray())

    private val shape = Shape(arrayOf(
        Vector2(-0.05, 0.0),
        Vector2(0.0, 0.05),
        Vector2(0.05, 0.0),
    ))

    private val options = ExtrudeOptions(
        steps = 300,
        curveSegments = 300,
        bevelEnabled = false,
        extrudePath =  mouthCurve
    )

    private val mouthGeometry = ExtrudeGeometry(shape, options)

    private val bodyGeometry = parametricGeometry(SLICES, STACKS) { u, v, p ->
        val a = u * TWO_PI
        when {
            // Lower-wavy part
            v < WAVE_FRACTION -> p.set(
                x = cos(a) * RADIUS,
                z = sin(a) * RADIUS,
                // We need to consistently go up in height, while simultaneously reducing
                // the sine amplitude to prevent from points from colliding
                y = (v + (WAVE_FRACTION - v) * sin(a * 8)) * HEIGHT
            )
            // Middle part, simply a tube
            v >= WAVE_FRACTION && v < HEAD_FRACTION -> p.set(
                x = cos(a) * RADIUS,
                z = sin(a) * RADIUS,
                y = v * HEIGHT
            )
            else -> {
                // Head part - upper half of a sphere
                val headHeight = 1.0 - HEAD_FRACTION
                val h = PI / 2 * (v - HEAD_FRACTION) / headHeight // Remap v to 0..PI/2
                p.set(
                    x = RADIUS * cos(h) * cos(a),
                    z = RADIUS * cos(h) * sin(a),
                    y = HEAD_FRACTION * HEIGHT + sin(h) * headHeight * HEIGHT
                )
            }
        }
    }

    private val bodyMaterial = color.toMeshStandardMaterial(roughness = 0.3).apply {
        materialSide = DoubleSide
        emissive.setHex(this@GhostRenderable.color.shade(BODY_GLOW))
    }

    private val eyeballShape = Shape().apply {
        add(EllipseCurve(0, 0, EYE_WIDTH_RADIUS, EYE_HEIGHT_RADIUS, 0, TWO_PI, false, 0))
    }

    private val whiteMaterial = 0xffffff.toMeshStandardMaterial(roughness = 0.2)

    private val eyeballGeometry = ExtrudeGeometry(eyeballShape, ExtrudeOptions(steps = 10, depth = EYE_DEPTH, bevelEnabled = false))

    private val irisShape = Shape().apply {
        add(EllipseCurve(0, 0, IRIS_RADIUS, IRIS_RADIUS, 0, TWO_PI, false, 0))
    }

    private val irisMaterial = 0x1B1BFE.toMeshStandardMaterial(roughness = 0.2)

    private val irisGeometry = ExtrudeGeometry(irisShape, ExtrudeOptions(steps = 10, depth = EYE_DEPTH / 2, bevelEnabled = false))

    private val rightIris = Mesh(irisGeometry, irisMaterial)

    private val leftIris = Mesh(irisGeometry, irisMaterial)

    private val mouth = Mesh(mouthGeometry, whiteMaterial)

    private val body = Mesh(bodyGeometry, bodyMaterial).apply { castShadow = true }

    private var isFrightened = false

    private var isFrozen = false

    private var isFlashing = false

    private var flashAge = 0.0

    private val accessories = GhostAccessories.create(entity.name)

    override val sceneObject = Group().add(
            body,
            accessories,
            Mesh(eyeballGeometry, whiteMaterial).apply {
                position.set(
                    x = -EYE_SPACING,
                    y = EYE_HEIGHT,
                    z = RADIUS - EYE_DEPTH * 2
                )
                rotateY(-EYE_SPACING_ANGLE)
            },
            Mesh(eyeballGeometry, whiteMaterial).apply {
                position.set(
                    x = EYE_SPACING,
                    y = EYE_HEIGHT,
                    z = RADIUS - EYE_DEPTH * 2
                )
                rotateY(EYE_SPACING_ANGLE)
            },
            leftIris.apply { rotateY(EYE_SPACING_ANGLE) },
            rightIris.apply { rotateY(-EYE_SPACING_ANGLE) },
            mouth.apply { visible = false },
    )

    private fun look(direction: Direction) {
        val deltaX = direction.x * (EYE_WIDTH_RADIUS - IRIS_RADIUS)
        val deltaY = direction.y * (EYE_HEIGHT_RADIUS - IRIS_RADIUS)

        fun Mesh.adjustLook(x: Double) {
            position.set(x, EYE_HEIGHT, RADIUS + EYE_DEPTH)
            translateX(deltaX)
            translateY(-deltaY)
        }

        leftIris.adjustLook(EYE_SPACING)
        rightIris.adjustLook(-EYE_SPACING)
    }

    /** Frightened ghosts flash to warn that they are about to turn back to normal */
    fun setFlashing(flashing: Boolean) {
        if (flashing == isFlashing) return
        isFlashing = flashing
        flashAge = 0.0
        applyFrightenedLook()
    }

    private fun applyFrightenedLook() {
        val flashOn = isFlashing && isFrightened && ((flashAge * FLASH_RATE).toInt() % 2 == 0)
        whiteMaterial.color.setHex(when {
            !isFrightened -> 0xffffff
            flashOn -> FACE_FRIGHTENED_FADE_COLOR
            else -> FACE_FRIGHTENED_COLOR
        })
        updateBodyColor()
    }

    private fun setFrightened(frightened: Boolean) {
        isFrightened = frightened
        if (!frightened) isFlashing = false
        mouth.visible = frightened
        leftIris.visible = !frightened
        rightIris.visible = !frightened

        applyFrightenedLook()
    }

    fun setFrozen(frozen: Boolean) {
        isFrozen = frozen
        updateBodyColor()
    }

    private fun updateBodyColor() {
        val bodyColor = when {
            isFrozen -> BODY_FROZEN_COLOR
            isFrightened && isFlashing && (flashAge * FLASH_RATE).toInt() % 2 == 0 -> BODY_FRIGHTENED_FADE_COLOR
            isFrightened -> BODY_FRIGHTENED_COLOR
            else -> color
        }

        bodyMaterial.color.setHex(bodyColor)
        bodyMaterial.emissive.setHex(bodyColor.shade(BODY_GLOW))
    }

    override fun setup(world: World) {
        look(RIGHT)
        sceneObject.position.set(entity.position.worldX, BASE_Y, entity.position.worldY)
    }

    override fun update(world: World, time: Double) {
        sceneObject.position.x = entity.position.worldX
        sceneObject.position.z = entity.position.worldY

        look(entity.currentDirection)

        if (isFlashing) {
            val phaseBefore = (flashAge * FLASH_RATE).toInt()
            flashAge += time
            if ((flashAge * FLASH_RATE).toInt() != phaseBefore) applyFrightenedLook()
        }
    }

    fun onStateChanged(newState: GhostState, previousState: GhostState) {
        if (newState == Frightened && previousState != Frightened) setFrightened(true)
        if (newState != Frightened && previousState == Frightened) setFrightened(false)
        if (newState == Eaten) setEaten(true)
        if (previousState == Eaten) setEaten(false)

        // Frightened and eaten ghosts look the same for all characters
        accessories.visible = newState != Frightened && newState != Eaten
    }

    private fun setEaten(eaten: Boolean) {
        body.visible = !eaten
    }
}
