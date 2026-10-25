package pacman3d.maze

import pacman3d.ext.toMeshStandardMaterial
import three.js.Color
import three.js.MeshStandardMaterial
import three.js.Texture
import kotlin.math.sin

enum class ParticleKind { Embers, Snow, Motes, Stars }

/** The materials of a maze that a style can animate */
class MazeMaterials(
    val wall: MeshStandardMaterial,
    val trim: MeshStandardMaterial,
    val wallMaps: TextureSet?,
    val trimMap: Texture,
)

/**
 * How the mazes look. The styles get more and more elaborate as the levels progress: from plain glossy enamel, through
 * bump-mapped bricks, glowing circuit boards, cracked lava, translucent ice and engraved gold, up to animated
 * holograms and a nebula. Once the styles run out they start over with a new color, more particles and more lights.
 */
class MazeStyle(
    val name: String,
    /** The color of the walls when they're highlighted back to normal */
    val wallColor: Int,
    val trimColor: Int,
    /** The color of the lights that float above the maze */
    val accentColor: Int,
    val floorColor: Int,
    /** The color of the glowing outline of the corridors */
    val laneColor: Int,
    val particleKind: ParticleKind?,
    val particleCount: Int,
    val createWallMaterial: () -> Pair<MeshStandardMaterial, TextureSet?>,
    val animate: (MazeMaterials, Double) -> Unit,
)

object MazeStyles {

    private const val STYLE_COUNT = 8

    // The hue change applied each time that all the styles were played
    private const val HUE_SHIFT_PER_CYCLE = 0.13

    fun forLevel(level: Int): MazeStyle {
        val cycle = level / STYLE_COUNT
        val base = when (level % STYLE_COUNT) {
            0 -> enamel()
            1 -> bricks()
            2 -> circuit()
            3 -> magma()
            4 -> ice()
            5 -> gold()
            6 -> hologram()
            else -> cosmic()
        }

        if (cycle == 0) return base

        // Every new cycle adds more particles and shifts all the glowing colors
        return MazeStyle(
            name = "${base.name} ${cycle + 1}",
            wallColor = base.wallColor,
            trimColor = base.trimColor.hueShifted(cycle * HUE_SHIFT_PER_CYCLE),
            accentColor = base.accentColor.hueShifted(cycle * HUE_SHIFT_PER_CYCLE),
            floorColor = base.floorColor,
            laneColor = base.laneColor.hueShifted(cycle * HUE_SHIFT_PER_CYCLE),
            particleKind = base.particleKind ?: ParticleKind.Motes,
            particleCount = base.particleCount + cycle * 70,
            createWallMaterial = base.createWallMaterial,
            animate = base.animate,
        )
    }

    private fun Int.hueShifted(amount: Double) = Color(this).apply { offsetHSL(amount, 0.0, 0.0) }.getHex().toInt()

    private fun pulse(age: Double, speed: Double, low: Double, high: Double) = low + (high - low) * (0.5 + 0.5 * sin(age * speed))

    // The running lights on top of the walls
    private fun MazeMaterials.runTrim(age: Double, speed: Double = 0.6) = trimMap.scrollTo(age * speed, 0.0)

    private fun Texture?.scrollTo(x: Double, y: Double) {
        if (this != null) asDynamic().offset.set(x, y)
    }

    // All the maps of a material share the transform of one of them (the color map takes precedence). Materials with a
    // scrolling emissive map therefore don't use a color map, so that it can scroll freely
    private fun MeshStandardMaterial.textured(maps: TextureSet, bumpScale: Double = 2.0) = apply {
        if (maps.emissive == null || maps.bump != null) map = maps.color
        maps.bump?.let {
            this.bumpMap = it
            this.bumpScale = bumpScale
        }
        maps.emissive?.let { emissiveMap = it }
    }

    private fun enamel() = MazeStyle(
        name = "Enamel",
        wallColor = 0x1830E8,
        trimColor = 0x4FE3FF,
        accentColor = 0x4466FF,
        floorColor = 0x04050F,
        laneColor = 0x4FA0FF,
        particleKind = null,
        particleCount = 0,
        createWallMaterial = {
            0x1830E8.toMeshStandardMaterial(roughness = 0.2, metalness = 0.35, envMapIntensity = 1.4).apply {
                emissive.setHex(0x0A1488)
                emissiveIntensity = 0.5
            } to null
        },
        animate = { m, age ->
            m.trim.emissiveIntensity = pulse(age, 3.0, 0.7, 1.0)
            m.runTrim(age, 0.3)
        },
    )

    private fun bricks() = MazeStyle(
        name = "Bricks",
        wallColor = 0xB580FF,
        trimColor = 0xFFC247,
        accentColor = 0xB066FF,
        floorColor = 0x08040F,
        laneColor = 0xA060FF,
        particleKind = null,
        particleCount = 0,
        createWallMaterial = {
            val maps = MazeTextures.bricks()
            0xFFFFFF.toMeshStandardMaterial(roughness = 0.8, metalness = 0.05, envMapIntensity = 0.5).textured(maps, bumpScale = 3.0) to maps
        },
        animate = { m, age ->
            m.trim.emissiveIntensity = pulse(age, 2.0, 0.8, 1.1)
            m.runTrim(age, 0.4)
        },
    )

    private fun circuit() = MazeStyle(
        name = "Circuit",
        wallColor = 0x2FBFCB,
        trimColor = 0x38F0FF,
        accentColor = 0x00E5FF,
        floorColor = 0x02090C,
        laneColor = 0x20D8E0,
        particleKind = null,
        particleCount = 0,
        createWallMaterial = {
            val maps = MazeTextures.circuit()
            0xFFFFFF.toMeshStandardMaterial(roughness = 0.35, metalness = 0.6, envMapIntensity = 1.2).textured(maps, bumpScale = 2.5).apply {
                emissive.setHex(0xFFFFFF)
            } to maps
        },
        animate = { m, age ->
            m.wall.emissiveIntensity = pulse(age, 2.2, 0.6, 1.5)
            m.trim.emissiveIntensity = pulse(age, 4.0, 0.8, 1.1)
            m.runTrim(age, 0.9)
        },
    )

    private fun magma() = MazeStyle(
        name = "Magma",
        wallColor = 0xE05A14,
        trimColor = 0xFF5A14,
        accentColor = 0xFF4400,
        floorColor = 0x0D0402,
        laneColor = 0xFF6A1A,
        particleKind = ParticleKind.Embers,
        particleCount = 90,
        createWallMaterial = {
            val maps = MazeTextures.magma()
            0xFFFFFF.toMeshStandardMaterial(roughness = 0.55, metalness = 0.25, envMapIntensity = 0.7).textured(maps, bumpScale = 3.0).apply {
                emissive.setHex(0xFFFFFF)
            } to maps
        },
        animate = { m, age ->
            m.wall.emissiveIntensity = pulse(age, 1.6, 0.55, 1.4) * pulse(age, 5.3, 0.85, 1.0)
            m.trim.emissiveIntensity = pulse(age, 6.0, 0.7, 1.2)
            m.runTrim(age, 0.5)
        },
    )

    private fun ice() = MazeStyle(
        name = "Ice",
        wallColor = 0xBFEAFF,
        trimColor = 0xE6FFFF,
        accentColor = 0x7FE0FF,
        floorColor = 0x03080F,
        laneColor = 0x9FE8FF,
        particleKind = ParticleKind.Snow,
        particleCount = 160,
        createWallMaterial = {
            val maps = MazeTextures.ice()
            0xD6F4FF.toMeshStandardMaterial(roughness = 0.08, metalness = 0.15, envMapIntensity = 1.9).textured(maps, bumpScale = 1.5).apply {
                emissive.setHex(0x4C93B8)
                emissiveIntensity = 0.45
                transparent = true
                opacity = 0.86
            } to maps
        },
        animate = { m, age ->
            m.wall.emissiveIntensity = pulse(age, 1.3, 0.3, 0.6)
            m.trim.emissiveIntensity = pulse(age, 2.5, 0.8, 1.1)
            m.runTrim(age, 0.5)
        },
    )

    private fun gold() = MazeStyle(
        name = "Gold",
        wallColor = 0xF1C43C,
        trimColor = 0xFFF2B0,
        accentColor = 0xFFC040,
        floorColor = 0x0C0803,
        laneColor = 0xFFC24A,
        particleKind = ParticleKind.Motes,
        particleCount = 90,
        createWallMaterial = {
            val maps = MazeTextures.ornate()
            0xFFFFFF.toMeshStandardMaterial(roughness = 0.26, metalness = 1.0, envMapIntensity = 1.8).textured(maps, bumpScale = 2.5) to maps
        },
        animate = { m, age ->
            m.trim.emissiveIntensity = pulse(age, 3.0, 0.8, 1.2)
            m.runTrim(age, 0.7)
        },
    )

    private fun hologram() = MazeStyle(
        name = "Hologram",
        wallColor = 0x40D8FF,
        trimColor = 0xFFFFFF,
        accentColor = 0xFF40E0,
        floorColor = 0x03020C,
        laneColor = 0xFF40E0,
        particleKind = ParticleKind.Motes,
        particleCount = 120,
        createWallMaterial = {
            val maps = MazeTextures.hologram()
            0x06121F.toMeshStandardMaterial(roughness = 0.1, metalness = 0.8, envMapIntensity = 1.5).textured(maps).apply {
                emissive.setHex(0x40D8FF)
                emissiveIntensity = 1.0
                transparent = true
                opacity = 0.78
            } to maps
        },
        animate = { m, age ->
            // The colors of the whole hologram slowly cycle through the rainbow, and the grid scrolls upwards
            val hue = (age * 0.08) % 1.0
            m.wall.emissive.setHSL(hue, 0.9, 0.55)
            m.trim.emissive.setHSL((hue + 0.5) % 1.0, 0.9, 0.65)
            m.wall.emissiveIntensity = pulse(age, 2.0, 0.8, 1.3)
            m.wallMaps?.emissive.scrollTo(0.0, age * 0.25)
            m.runTrim(age, 1.2)
        },
    )

    private fun cosmic() = MazeStyle(
        name = "Cosmic",
        wallColor = 0x9B6BFF,
        trimColor = 0xFFFFFF,
        accentColor = 0xB06BFF,
        floorColor = 0x02010A,
        laneColor = 0x9B6BFF,
        particleKind = ParticleKind.Stars,
        particleCount = 220,
        createWallMaterial = {
            val maps = MazeTextures.cosmic()
            0x090516.toMeshStandardMaterial(roughness = 0.25, metalness = 0.4, envMapIntensity = 1.4).textured(maps).apply {
                emissive.setHex(0xFFFFFF)
                emissiveIntensity = 1.1
            } to maps
        },
        animate = { m, age ->
            m.wallMaps?.emissive.scrollTo(age * 0.03, age * 0.015)
            m.wall.emissiveIntensity = pulse(age, 1.1, 0.9, 1.4)
            m.trim.emissive.setHSL((age * 0.12) % 1.0, 0.8, 0.7)
            m.runTrim(age, 0.8)
        },
    )
}
