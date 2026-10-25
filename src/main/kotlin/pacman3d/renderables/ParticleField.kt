package pacman3d.renderables

import pacman3d.maze.MazeConst
import pacman3d.maze.MazeTextures
import pacman3d.maze.ParticleKind
import three.js.BufferAttribute
import three.js.BufferGeometry
import three.js.Points
import three.js.PointsMaterial
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** Embers, snow, sparkles or stars that float above the maze */
class ParticleField(private val kind: ParticleKind, count: Int, color: Int) {

    private companion object {
        const val MIN_Y = 0.2
        const val MAX_Y = 6.5
        const val ADDITIVE_BLENDING = 2
    }

    private val rnd = Random(count)

    private val positions = FloatArray(count * 3)

    // Each particle has a speed and a phase that makes it sway differently from the others
    private val speeds = DoubleArray(count) { 0.4 + rnd.nextDouble() }
    private val phases = DoubleArray(count) { rnd.nextDouble() * 2 * PI }

    private val geometry = BufferGeometry()

    private val material = PointsMaterial().apply {
        this.color.setHex(color)
        size = if (kind == ParticleKind.Stars) 0.3 else 0.42
        map = MazeTextures.sparkle
        transparent = true
        depthWrite = false
        asDynamic().blending = ADDITIVE_BLENDING
    }

    private var age = 0.0

    val points = Points(geometry, material).apply { asDynamic().frustumCulled = false }

    init {
        for (i in 0 until count) {
            positions[i * 3] = ((rnd.nextDouble() - 0.5) * (MazeConst.VISIBLE_WIDTH + 2)).toFloat()
            positions[i * 3 + 1] = (MIN_Y + rnd.nextDouble() * (MAX_Y - MIN_Y)).toFloat()
            positions[i * 3 + 2] = ((rnd.nextDouble() - 0.5) * (MazeConst.EFFECTIVE_LENGTH + 2)).toFloat()
        }

        val attribute = BufferAttribute(positions.asDynamic(), 3)
        val dynamicGeometry = geometry.asDynamic()
        // The method was renamed in later versions of three.js
        if (dynamicGeometry.setAttribute != undefined) dynamicGeometry.setAttribute("position", attribute)
        else dynamicGeometry.addAttribute("position", attribute)
    }

    fun update(time: Double) {
        age += time

        for (i in 0 until speeds.size) {
            val x = i * 3
            val y = x + 1
            val z = x + 2

            when (kind) {
                ParticleKind.Embers -> {
                    positions[y] = (positions[y] + speeds[i] * 1.2 * time).toFloat()
                    positions[x] = (positions[x] + sin(age * speeds[i] + phases[i]) * 0.4 * time).toFloat()
                    if (positions[y] > MAX_Y) positions[y] = MIN_Y.toFloat()
                }
                ParticleKind.Snow -> {
                    positions[y] = (positions[y] - speeds[i] * 0.9 * time).toFloat()
                    positions[x] = (positions[x] + sin(age * speeds[i] + phases[i]) * 0.5 * time).toFloat()
                    positions[z] = (positions[z] + cos(age * speeds[i] + phases[i]) * 0.5 * time).toFloat()
                    if (positions[y] < MIN_Y) positions[y] = MAX_Y.toFloat()
                }
                ParticleKind.Motes -> {
                    positions[x] = (positions[x] + sin(age * speeds[i] * 0.6 + phases[i]) * 0.7 * time).toFloat()
                    positions[y] = (positions[y] + cos(age * speeds[i] * 0.5 + phases[i]) * 0.4 * time).toFloat()
                    positions[z] = (positions[z] + cos(age * speeds[i] * 0.4 + phases[i]) * 0.7 * time).toFloat()
                    positions[y] = positions[y].coerceIn(MIN_Y.toFloat(), MAX_Y.toFloat())
                }
                ParticleKind.Stars -> positions[y] = (positions[y] + speeds[i] * 0.08 * time).toFloat().let { if (it > MAX_Y) MIN_Y.toFloat() else it }
            }
        }

        // Stars twinkle by changing the size of all of them together
        if (kind == ParticleKind.Stars) material.size = 0.26 + 0.08 * sin(age * 3.0)

        geometry.asDynamic().attributes.position.needsUpdate = true
    }
}
