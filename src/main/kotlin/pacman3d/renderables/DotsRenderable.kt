package pacman3d.renderables

import pacman3d.ext.plusAssign
import pacman3d.ext.toMeshStandardMaterial
import pacman3d.logic.Position
import pacman3d.maze.MazeConst
import pacman3d.entities.Dots
import pacman3d.entities.Maze.Companion.isDot
import pacman3d.entities.Maze.Companion.isDotOrEnergizer
import pacman3d.entities.World
import three.js.Group
import three.js.Mesh
import three.js.SphereGeometry
import kotlin.collections.set
import kotlin.math.sin

class DotsRenderable(private val dots: Dots) : Renderable {

    companion object {
        const val DOT_COLOR = 0xF5BCB2
        const val DOT_SIZE = 0.2 * MazeConst.UNIT_SIZE
        const val ENERGIZER_SIZE = 1.0 * MazeConst.UNIT_SIZE
        const val ENERGIZER_SEGMENTS = 16
        const val DOT_Y_POSITION = 0.5

        const val ENERGIZER_COLOR = 0xFFD9CF
        const val ENERGIZER_PULSE_SPEED = 5.0
        const val ENERGIZER_PULSE_SIZE = 0.12

        const val NUM_DOTS = 240
        const val NUM_ENERGIZERS = 4
        const val COUNT = NUM_DOTS + NUM_ENERGIZERS
    }

    private val dotGeometry = SphereGeometry(DOT_SIZE * 0.7, 12, 8)
    private val dotMaterial = DOT_COLOR.toMeshStandardMaterial(roughness = 0.35).apply {
        emissive.setHex(0x6B3A32)
    }

    private val energizerMaterial = ENERGIZER_COLOR.toMeshStandardMaterial(roughness = 0.2).apply {
        emissive.setHex(0xFF8A70)
    }

    private val energizers = ArrayList<Mesh>()

    private var age = 0.0

    private val energizerGeometry = SphereGeometry(ENERGIZER_SIZE / 2, ENERGIZER_SEGMENTS, ENERGIZER_SEGMENTS)

    private val group = Group()

    override val sceneObject = group

    private val mazeCoordinatesToDotMap = mutableMapOf<Int, Mesh>()

    override fun setup(world: World) = populate(world)

    fun reset(world: World) {
        mazeCoordinatesToDotMap.values.forEach { group.remove(it) }
        mazeCoordinatesToDotMap.clear()
        energizers.clear()
        populate(world)
    }

    private fun populate(world: World) {
        world.maze.forEachTile { maze, x, y ->
            if (maze[x, y].isDotOrEnergizer) {
                val isDot = maze[x, y].isDot
                group += Mesh(if (isDot) dotGeometry else energizerGeometry, if (isDot) dotMaterial else energizerMaterial).apply {
                    val pos = Position(x, y)
                    position.set(x = pos.worldX, z = pos.worldY, y = DOT_Y_POSITION)
                    mazeCoordinatesToDotMap[MazeConst.indexOf(x, y)] = this
                    if (!isDot) energizers += this
                }
            }
        }
    }

    fun eat(position: Position) {
        mazeCoordinatesToDotMap.remove(position.mazeIndex)?.let {
            group.remove(it)
            energizers.remove(it)
        }
    }

    // The energizers pulse to draw attention to themselves
    override fun update(world: World, time: Double) {
        age += time

        val pulse = sin(age * ENERGIZER_PULSE_SPEED)
        val scale = 1.0 + pulse * ENERGIZER_PULSE_SIZE
        energizers.forEach { it.scale.set(scale, scale, scale) }
        energizerMaterial.emissiveIntensity = 0.75 + pulse * 0.25
    }
}