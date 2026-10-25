package pacman3d.renderables

import pacman3d.entities.World
import pacman3d.ext.HALF_PI
import pacman3d.ext.toMeshStandardMaterial
import pacman3d.maze.*
import three.js.Color
import three.js.Geometry
import three.js.Group
import three.js.Mesh
import three.js.MeshBasicMaterial
import three.js.MeshStandardMaterial
import three.js.PlaneGeometry
import three.js.PointLight
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class MazeRenderable(private var layout: MazeLayout) : Renderable {

    private companion object {
        const val WALL_HIGHLIGHT_COLOR = 0xFFFFFF

        // The glowing trim is slightly above the walls so that it doesn't fight with them
        const val TRIM_RAISE = 0.004

        // The center of the visible rows of the floor
        const val FLOOR_Z = 0.5

        const val LIGHT_HEIGHT = 3.2
        const val LIGHT_ORBIT_SPEED = 0.35
    }

    private var level = 0

    private var style = MazeStyles.forLevel(0)

    private var age = 0.0

    private var highlighted = false

    private lateinit var materials: MazeMaterials

    private val highlightMaterial = MeshBasicMaterial().apply { color = Color(WALL_HIGHLIGHT_COLOR) }

    private val walls = Mesh(Geometry(), highlightMaterial).apply {
        castShadow = true
        receiveShadow = true
    }

    private val trim = Mesh(Geometry(), highlightMaterial)

    private val floor = Mesh(PlaneGeometry(MazeConst.VISIBLE_WIDTH, MazeConst.EFFECTIVE_LENGTH), highlightMaterial).apply {
        rotateX(-HALF_PI)
        position.z = FLOOR_Z
        receiveShadow = true
    }

    // Covers everything around the maze
    private val ground = Mesh(PlaneGeometry(200.0, 200.0), 0x030308.toMeshStandardMaterial(roughness = 1.0)).apply {
        rotateX(-HALF_PI)
        position.y = -0.05
    }

    // Colored lights that float around above the maze
    private val lights = List(2) { PointLight(0xFFFFFF, 0.9, 26.0, 2.0) }

    private var particles: ParticleField? = null

    override val sceneObject = Group().apply {
        add(ground, floor, walls, trim)
        lights.forEach { add(it) }
    }

    init {
        rebuild()
    }

    override fun setup(world: World) = Unit

    /** The maze looks different for each [level], and also changes when the [newLayout] is different */
    fun setLayout(newLayout: MazeLayout, newLevel: Int) {
        if (newLayout === layout && newLevel == level) return

        layout = newLayout
        level = newLevel
        rebuild()
    }

    private fun rebuild() {
        style = MazeStyles.forLevel(level)

        val oldWallMaterial = walls.asDynamic().material
        val oldTrimMaterial = trim.asDynamic().material
        val oldFloorMaterial = floor.asDynamic().material
        val oldGeometries = listOf(walls.asDynamic().geometry, trim.asDynamic().geometry)

        val wallGeometry = layout.buildWallGeometry().projectUvs(MazeTextures.TILE)
        val trimGeometry = wallGeometry.topFaces(TRIM_RAISE).projectUvs(MazeTextures.TRIM_TILE)

        val (wallMaterial, wallMaps) = style.createWallMaterial()
        val trimMaps = MazeTextures.trimStripes()
        val trimMaterial = 0x101010.toMeshStandardMaterial(roughness = 0.3, metalness = 0.2).apply {
            emissive.setHex(style.trimColor)
            emissiveMap = trimMaps.emissive
        }
        materials = MazeMaterials(wallMaterial, trimMaterial, wallMaps, trimMaps.emissive!!)

        val floorMap = MazeTextures.floor(layout.rows, style.floorColor, style.laneColor).color
        val floorMaterial = 0xFFFFFF.toMeshStandardMaterial(roughness = 0.3, metalness = 0.5, envMapIntensity = 0.8).apply {
            map = floorMap
            emissiveMap = floorMap
            emissive.setHex(0xFFFFFF)
            emissiveIntensity = 0.6
        }

        walls.asDynamic().geometry = wallGeometry
        trim.asDynamic().geometry = trimGeometry
        floor.asDynamic().material = floorMaterial
        applyMaterials()

        oldGeometries.forEach { it.dispose() }
        if (oldWallMaterial != highlightMaterial) {
            oldWallMaterial.dispose()
            oldTrimMaterial.dispose()
        }
        oldFloorMaterial.map?.dispose()
        oldFloorMaterial.dispose()

        lights[0].color.setHex(style.accentColor)
        lights[1].color.setHex(style.trimColor)

        particles?.let { sceneObject.remove(it.points) }
        particles = style.particleKind?.let { ParticleField(it, style.particleCount, style.trimColor) }
        particles?.let { sceneObject.add(it.points) }
    }

    private fun applyMaterials() {
        walls.asDynamic().material = if (highlighted) highlightMaterial else materials.wall
        trim.asDynamic().material = if (highlighted) highlightMaterial else materials.trim
    }

    fun setWallsHighlighted(highlighted: Boolean) {
        this.highlighted = highlighted
        applyMaterials()
    }

    fun setWallsVisible(visible: Boolean) {
        walls.visible = visible
        trim.visible = visible
    }

    override fun update(world: World, time: Double) {
        age += time

        if (!highlighted) style.animate(materials, age)

        lights.forEachIndexed { i, light ->
            val angle = age * LIGHT_ORBIT_SPEED * (1 + i * 0.45) + i * PI
            light.position.set(cos(angle) * 11, LIGHT_HEIGHT, sin(angle * 1.3) * 13)
        }

        particles?.update(time)
    }
}
