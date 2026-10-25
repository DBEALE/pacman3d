package pacman3d.maze

import org.w3c.dom.CanvasRenderingContext2D
import org.w3c.dom.HTMLCanvasElement
import pacman3d.ext.*
import three.js.Texture
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private typealias Path = List<Pair<Double, Double>>

/** The maps of a material. All of them cover the same area and use the same orientation. */
class TextureSet(val color: Texture, val bump: Texture? = null, val emissive: Texture? = null)

/**
 * Procedurally drawn textures for the walls and floors of the mazes, so that no image assets are required. All the
 * textures tile seamlessly, and one repetition covers [TILE] world units.
 */
object MazeTextures {

    /** The size in world units covered by one repetition of a wall texture */
    const val TILE = 2.0

    /** The size in world units covered by one repetition of the pattern of the glowing trim on top of the walls */
    const val TRIM_TILE = 0.9

    private const val SIZE = 256
    private const val S = SIZE.toDouble()

    private val cache = HashMap<String, TextureSet>()

    private val DX = doubleArrayOf(1.0, 1.0, 0.0, -1.0, -1.0, -1.0, 0.0, 1.0)
    private val DY = doubleArrayOf(0.0, 1.0, 1.0, 1.0, 0.0, -1.0, -1.0, -1.0)

    fun bricks() = cache.getOrPut("bricks") { makeBricks() }
    fun circuit() = cache.getOrPut("circuit") { makeCircuit() }
    fun magma() = cache.getOrPut("magma") { makeMagma() }
    fun ice() = cache.getOrPut("ice") { makeIce() }
    fun ornate() = cache.getOrPut("ornate") { makeOrnate() }
    fun hologram() = cache.getOrPut("hologram") { makeHologram() }
    fun cosmic() = cache.getOrPut("cosmic") { makeCosmic() }

    /** Diagonal stripes, which look like running lights when scrolled along the glowing trim of the walls */
    fun trimStripes() = cache.getOrPut("trim") {
        val stripes = createCanvas(64) { ctx ->
            ctx.fillStyle = "#5a5a5a"
            ctx.fillRect(0.0, 0.0, 64.0, 64.0)
            ctx.drawTiled(64, 64) {
                ctx.strokeStyle = "#ffffff"
                ctx.lineWidth = 14.0
                ctx.strokePath(listOf(-20.0 to 84.0, 84.0 to -20.0))
            }
        }
        TextureSet(stripes.toTexture(), emissive = stripes.toTexture())
    }

    /** A soft round dot, used for the particles */
    val sparkle: Texture by lazy {
        createCanvas(64) { ctx ->
            val gradient = ctx.createRadialGradient(32.0, 32.0, 0.0, 32.0, 32.0, 32.0)
            gradient.addColorStop(0.0, "rgba(255,255,255,1)")
            gradient.addColorStop(0.25, "rgba(255,255,255,0.7)")
            gradient.addColorStop(1.0, "rgba(255,255,255,0)")
            ctx.fillStyle = gradient
            ctx.fillRect(0.0, 0.0, 64.0, 64.0)
        }.toTexture(repeat = false)
    }

    /**
     * The floor shows the corridors of the maze as slightly raised lanes that are outlined with a glow. The walls are
     * drawn along the center of the wall tiles, so the lanes reach half a tile into them.
     */
    fun floor(rows: List<String>, floorColor: Int, laneColor: Int): TextureSet {
        val firstColumn = 1
        val columns = MazeConst.VISIBLE_WIDTH_UNITS
        val firstRow = MazeConst.FIRST_EFFECTIVE_LINE
        val rowCount = MazeConst.LAST_EFFECTIVE_LINE - MazeConst.FIRST_EFFECTIVE_LINE + 1

        val width = 1024
        val tw = width / columns.toDouble()
        val th = width / rowCount.toDouble()

        fun isWall(x: Int, y: Int) = y !in rows.indices || x !in 0 until MazeConst.WIDTH_UNITS || rows[y][x] == '#'

        val canvas = createCanvas(width) { ctx ->
            ctx.fillStyle = floorColor.toCss()
            ctx.fillRect(0.0, 0.0, width.toDouble(), width.toDouble())

            // The lanes
            for (y in firstRow until firstRow + rowCount) for (x in firstColumn until firstColumn + columns) {
                if (isWall(x, y)) continue

                val left = (x - firstColumn) * tw - if (isWall(x - 1, y)) tw / 2 else 0.0
                val right = (x - firstColumn + 1) * tw + if (isWall(x + 1, y)) tw / 2 else 0.0
                val top = (y - firstRow) * th - if (isWall(x, y - 1)) th / 2 else 0.0
                val bottom = (y - firstRow + 1) * th + if (isWall(x, y + 1)) th / 2 else 0.0

                ctx.fillStyle = floorColor.mixWith(laneColor, if ((x + y) % 2 == 0) 0.07 else 0.11).toCss()
                ctx.fillRect(left, top, right - left, bottom - top)
            }

            // The glowing outline
            ctx.strokeStyle = laneColor.toCss(0.85)
            ctx.lineWidth = 3.0
            ctx.shadowColor = laneColor.toCss()
            ctx.shadowBlur = 14.0
            for (y in firstRow until firstRow + rowCount) for (x in firstColumn until firstColumn + columns) {
                if (isWall(x, y)) continue

                val left = (x - firstColumn) * tw - if (isWall(x - 1, y)) tw / 2 else 0.0
                val right = (x - firstColumn + 1) * tw + if (isWall(x + 1, y)) tw / 2 else 0.0
                val top = (y - firstRow) * th - if (isWall(x, y - 1)) th / 2 else 0.0
                val bottom = (y - firstRow + 1) * th + if (isWall(x, y + 1)) th / 2 else 0.0

                if (isWall(x - 1, y)) ctx.strokePath(listOf(left to top, left to bottom))
                if (isWall(x + 1, y)) ctx.strokePath(listOf(right to top, right to bottom))
                if (isWall(x, y - 1)) ctx.strokePath(listOf(left to top, right to top))
                if (isWall(x, y + 1)) ctx.strokePath(listOf(left to bottom, right to bottom))
            }
        }

        return TextureSet(canvas.toTexture(repeat = false).apply { asDynamic().anisotropy = 8 })
    }

    private fun CanvasRenderingContext2D.strokePath(points: Path) {
        beginPath()
        moveTo(points[0].first, points[0].second)
        for (i in 1 until points.size) lineTo(points[i].first, points[i].second)
        stroke()
    }

    private fun CanvasRenderingContext2D.lineStyle(width: Double, color: String) {
        strokeStyle = color
        lineWidth = width
        asDynamic().lineCap = "round"
        asDynamic().lineJoin = "round"
    }

    private fun CanvasRenderingContext2D.glow(color: String, blur: Double) {
        shadowColor = color
        shadowBlur = blur
    }

    private fun CanvasRenderingContext2D.noGlow() = glow("transparent", 0.0)

    private fun CanvasRenderingContext2D.paint(color: String) {
        fillStyle = color
        fillRect(0.0, 0.0, S, S)
    }

    private fun CanvasRenderingContext2D.speckle(rnd: Random, count: Int, color: String, maxSize: Double = 2.5) {
        fillStyle = color
        repeat(count) { fillRect(rnd.nextDouble() * S, rnd.nextDouble() * S, 1 + rnd.nextDouble() * maxSize, 1 + rnd.nextDouble() * maxSize) }
    }

    private fun makeBricks(): TextureSet {
        val rows = 8
        val brickH = S / rows
        val brickW = S / 4
        val rnd = Random(7)
        val shades = List(rows * 4) { 0.78 + rnd.nextDouble() * 0.44 }

        fun CanvasRenderingContext2D.eachBrick(draw: (x: Double, y: Double, shade: Double) -> Unit) {
            for (r in 0 until rows) for (c in 0..4) {
                val x = c * brickW - if (r % 2 == 1) brickW / 2 else 0.0
                draw(x, r * brickH, shades[r * 4 + c % 4])
            }
        }

        val color = createCanvas(SIZE) { ctx ->
            ctx.paint("#1d1426")
            ctx.eachBrick { x, y, shade ->
                ctx.fillStyle = 0x9B5FE0.shade(shade).toCss()
                ctx.fillRect(x + 3, y + 3, brickW - 6, brickH - 6)
                ctx.fillStyle = "rgba(255,255,255,0.2)"
                ctx.fillRect(x + 3, y + 3, brickW - 6, 3.0)
                ctx.fillStyle = "rgba(0,0,0,0.25)"
                ctx.fillRect(x + 3, y + brickH - 8, brickW - 6, 5.0)
            }
            ctx.speckle(Random(3), 700, "rgba(0,0,0,0.12)")
            ctx.speckle(Random(4), 400, "rgba(255,255,255,0.07)")
        }

        val bump = createCanvas(SIZE) { ctx ->
            ctx.paint("#000000")
            ctx.eachBrick { x, y, shade ->
                val g = (170 * shade).toInt().coerceIn(0, 255)
                ctx.fillStyle = "rgb($g,$g,$g)"
                ctx.fillRect(x + 4, y + 4, brickW - 8, brickH - 8)
            }
            ctx.speckle(Random(5), 1200, "rgba(0,0,0,0.25)", 3.0)
            ctx.speckle(Random(6), 800, "rgba(255,255,255,0.2)", 2.0)
        }

        return TextureSet(color.toTexture(), bump.toTexture())
    }

    private fun makeCircuit(): TextureSet {
        val rnd = Random(11)
        val traces: List<Path> = List(18) {
            var x = rnd.nextInt(16) * 16.0
            var y = rnd.nextInt(16) * 16.0
            var dir = rnd.nextInt(8)
            val points = arrayListOf(x to y)
            repeat(rnd.nextInt(4, 9)) {
                if (rnd.nextDouble() < 0.55) dir = (dir + if (rnd.nextBoolean()) 1 else 7) % 8
                val length = rnd.nextInt(1, 4) * 16.0
                x += DX[dir] * length
                y += DY[dir] * length
                points += x to y
            }
            points
        }

        fun CanvasRenderingContext2D.pads(radius: Double, ring: Boolean) {
            traces.forEach { trace ->
                listOf(trace.first(), trace.last()).forEach { (px, py) ->
                    beginPath()
                    arc(px, py, radius, 0.0, 2 * PI)
                    if (ring) stroke() else fill()
                }
            }
        }

        val color = createCanvas(SIZE) { ctx ->
            ctx.paint("#0c1b24")
            ctx.lineStyle(1.0, "rgba(70,150,170,0.16)")
            for (i in 0..16) {
                ctx.strokePath(listOf(i * 16.0 to 0.0, i * 16.0 to S))
                ctx.strokePath(listOf(0.0 to i * 16.0, S to i * 16.0))
            }
            ctx.fillStyle = "rgba(255,255,255,0.05)"
            val plates = Random(12)
            repeat(10) { ctx.fillRect(plates.nextInt(14) * 16.0, plates.nextInt(14) * 16.0, 32.0 + plates.nextInt(3) * 16, 32.0) }
            ctx.drawTiled(SIZE, SIZE) {
                ctx.lineStyle(6.0, "#14606b")
                traces.forEach { ctx.strokePath(it) }
                ctx.fillStyle = "#14606b"
                ctx.pads(7.0, ring = false)
            }
        }

        val emissive = createCanvas(SIZE) { ctx ->
            ctx.paint("#000000")
            ctx.drawTiled(SIZE, SIZE) {
                ctx.glow("#35f0ff", 10.0)
                ctx.lineStyle(2.5, "#7ffaff")
                traces.forEach { ctx.strokePath(it) }
                ctx.fillStyle = "#c8ffff"
                ctx.pads(3.5, ring = false)
                ctx.lineStyle(1.5, "#7ffaff")
                ctx.pads(8.0, ring = true)
            }
        }

        val bump = createCanvas(SIZE) { ctx ->
            ctx.paint("#505050")
            ctx.drawTiled(SIZE, SIZE) {
                ctx.lineStyle(6.0, "#ffffff")
                traces.forEach { ctx.strokePath(it) }
            }
        }

        return TextureSet(color.toTexture(), bump.toTexture(), emissive.toTexture())
    }

    private fun makeMagma(): TextureSet {
        val rnd = Random(21)
        val cracks = ArrayList<Pair<Path, Double>>()

        fun grow(startX: Double, startY: Double, startAngle: Double, steps: Int, width: Double) {
            var x = startX
            var y = startY
            var angle = startAngle
            val points = arrayListOf(x to y)
            repeat(steps) { step ->
                angle += (rnd.nextDouble() - 0.5) * 1.0
                x += cos(angle) * 15
                y += sin(angle) * 15
                points += x to y
                if (width > 1.3 && step > 1 && rnd.nextDouble() < 0.2) {
                    grow(x, y, angle + (if (rnd.nextBoolean()) 0.8 else -0.8), steps / 2, width * 0.6)
                }
            }
            cracks += points to width
        }

        repeat(7) { grow(rnd.nextDouble() * S, rnd.nextDouble() * S, rnd.nextDouble() * 2 * PI, 16, 4.5) }

        val color = createCanvas(SIZE) { ctx ->
            ctx.paint("#1a1210")
            ctx.speckle(Random(22), 1500, "rgba(0,0,0,0.3)", 4.0)
            ctx.speckle(Random(23), 700, "rgba(255,255,255,0.05)", 3.0)
            ctx.drawTiled(SIZE, SIZE) { cracks.forEach { (path, width) -> ctx.lineStyle(width + 3, "#070404"); ctx.strokePath(path) } }
        }

        val emissive = createCanvas(SIZE) { ctx ->
            ctx.paint("#000000")
            ctx.drawTiled(SIZE, SIZE) {
                ctx.glow("#ff3300", 12.0)
                cracks.forEach { (path, width) -> ctx.lineStyle(width, "#ff6a00"); ctx.strokePath(path) }
                ctx.glow("#ffb000", 4.0)
                cracks.forEach { (path, width) -> ctx.lineStyle(width * 0.4, "#ffe08a"); ctx.strokePath(path) }
            }
        }

        val bump = createCanvas(SIZE) { ctx ->
            ctx.paint("#b0b0b0")
            ctx.speckle(Random(24), 1500, "rgba(0,0,0,0.3)", 4.0)
            ctx.drawTiled(SIZE, SIZE) { cracks.forEach { (path, width) -> ctx.lineStyle(width + 4, "#000000"); ctx.strokePath(path) } }
        }

        return TextureSet(color.toTexture(), bump.toTexture(), emissive.toTexture())
    }

    private fun makeIce(): TextureSet {
        val rnd = Random(31)
        class Facet(val points: Path, val shade: Double)

        val facets = List(42) {
            val cx = rnd.nextDouble() * S
            val cy = rnd.nextDouble() * S
            Facet(List(3) { cx + (rnd.nextDouble() - 0.5) * 90 to cy + (rnd.nextDouble() - 0.5) * 90 }, rnd.nextDouble())
        }

        fun CanvasRenderingContext2D.facet(f: Facet) {
            beginPath()
            moveTo(f.points[0].first, f.points[0].second)
            lineTo(f.points[1].first, f.points[1].second)
            lineTo(f.points[2].first, f.points[2].second)
            closePath()
        }

        val color = createCanvas(SIZE) { ctx ->
            val gradient = ctx.createLinearGradient(0.0, 0.0, S, S)
            gradient.addColorStop(0.0, "#eafcff")
            gradient.addColorStop(0.5, "#a9e4f7")
            gradient.addColorStop(1.0, "#d9f6ff")
            ctx.fillStyle = gradient
            ctx.fillRect(0.0, 0.0, S, S)
            ctx.drawTiled(SIZE, SIZE) {
                facets.forEach { f ->
                    ctx.facet(f)
                    ctx.fillStyle = if (f.shade < 0.5) "rgba(255,255,255,${0.1 + f.shade * 0.4})" else "rgba(40,120,200,${(f.shade - 0.5) * 0.35})"
                    ctx.fill()
                    ctx.lineStyle(1.2, "rgba(255,255,255,0.55)")
                    ctx.stroke()
                }
            }
        }

        val bump = createCanvas(SIZE) { ctx ->
            ctx.paint("#808080")
            ctx.drawTiled(SIZE, SIZE) {
                facets.forEach { f ->
                    ctx.facet(f)
                    val g = (60 + f.shade * 160).toInt()
                    ctx.fillStyle = "rgba($g,$g,$g,0.9)"
                    ctx.fill()
                }
            }
        }

        return TextureSet(color.toTexture(), bump.toTexture())
    }

    private fun makeOrnate(): TextureSet {
        val cell = 64.0

        fun CanvasRenderingContext2D.pattern(line: String, accent: String, lineWidth: Double) {
            drawTiled(SIZE, SIZE) {
                lineStyle(lineWidth, line)
                var i = -4
                while (i <= 8) {
                    strokePath(listOf(i * cell to 0.0, (i + 4) * cell to S))
                    strokePath(listOf(i * cell to S, (i + 4) * cell to 0.0))
                    i++
                }
                // Rosettes where the lines cross
                for (cx in 0..4) for (cy in 0..4) {
                    val x = cx * cell
                    val y = cy * cell
                    if ((cx + cy) % 2 != 0) continue
                    for (ring in 1..3) {
                        beginPath()
                        arc(x, y, ring * 8.0, 0.0, 2 * PI)
                        stroke()
                    }
                    fillStyle = accent
                    beginPath()
                    arc(x, y, 5.0, 0.0, 2 * PI)
                    fill()
                }
            }
        }

        val color = createCanvas(SIZE) { ctx ->
            val gradient = ctx.createLinearGradient(0.0, 0.0, S, S)
            gradient.addColorStop(0.0, "#f1c43c")
            gradient.addColorStop(0.5, "#c8941a")
            gradient.addColorStop(1.0, "#f4cf55")
            ctx.fillStyle = gradient
            ctx.fillRect(0.0, 0.0, S, S)
            ctx.pattern("#7c5608", "#8a1f1f", 3.0)
        }

        val bump = createCanvas(SIZE) { ctx ->
            ctx.paint("#3c3c3c")
            ctx.pattern("#ffffff", "#ffffff", 4.0)
        }

        return TextureSet(color.toTexture(), bump.toTexture())
    }

    private fun makeHologram(): TextureSet {
        val rnd = Random(41)

        val color = createCanvas(SIZE) { ctx -> ctx.paint("#06121f") }

        val emissive = createCanvas(SIZE) { ctx ->
            ctx.paint("#000000")
            ctx.lineStyle(1.5, "rgba(255,255,255,0.7)")
            for (i in 0..8) {
                ctx.strokePath(listOf(i * 32.0 to 0.0, i * 32.0 to S))
                ctx.strokePath(listOf(0.0 to i * 32.0, S to i * 32.0))
            }
            ctx.fillStyle = "rgba(255,255,255,0.14)"
            for (i in 0 until SIZE step 4) ctx.fillRect(0.0, i.toDouble(), S, 1.0)
            ctx.fillStyle = "rgba(255,255,255,0.55)"
            repeat(26) { ctx.fillRect(rnd.nextInt(16) * 16.0 + 3, rnd.nextInt(16) * 16.0 + 3, 10.0, 10.0) }
        }

        return TextureSet(color.toTexture(), emissive = emissive.toTexture())
    }

    private fun makeCosmic(): TextureSet {
        val rnd = Random(51)
        val hues = listOf("120,60,255", "255,70,200", "60,140,255", "150,90,255")

        val color = createCanvas(SIZE) { ctx -> ctx.paint("#090516") }

        val emissive = createCanvas(SIZE) { ctx ->
            ctx.paint("#000000")
            repeat(16) {
                val x = rnd.nextDouble() * S
                val y = rnd.nextDouble() * S
                val radius = 50 + rnd.nextDouble() * 70
                val hue = hues[rnd.nextInt(hues.size)]
                for (dx in -1..1) for (dy in -1..1) {
                    val gradient = ctx.createRadialGradient(x + dx * S, y + dy * S, 0.0, x + dx * S, y + dy * S, radius)
                    gradient.addColorStop(0.0, "rgba($hue,0.55)")
                    gradient.addColorStop(1.0, "rgba($hue,0)")
                    ctx.fillStyle = gradient
                    ctx.fillRect(0.0, 0.0, S, S)
                }
            }
            repeat(150) {
                val x = rnd.nextDouble() * S
                val y = rnd.nextDouble() * S
                ctx.glow("#ffffff", 6.0)
                ctx.fillStyle = if (rnd.nextDouble() < 0.2) "#ffe9a8" else "#ffffff"
                ctx.fillRect(x, y, 1.0 + rnd.nextDouble() * 1.8, 1.0 + rnd.nextDouble() * 1.8)
            }
        }

        return TextureSet(color.toTexture(), emissive = emissive.toTexture())
    }
}
