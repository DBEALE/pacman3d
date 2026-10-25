package pacman3d.renderables

import org.w3c.dom.CanvasRenderingContext2D
import pacman3d.ext.createCanvas
import three.js.CubeTexture

/**
 * A procedural studio-like environment, used for the reflections of all the glossy and metallic materials. The faces
 * are in the order that three.js expects: +x, -x, +y, -y, +z, -z.
 */
object Environment {

    private const val SIZE = 128

    val cubeMap: CubeTexture by lazy {
        CubeTexture(arrayOf(
            sideFace(), sideFace(), topFace(), bottomFace(), sideFace(), sideFace()
        )).apply { asDynamic().needsUpdate = true }
    }

    private fun CanvasRenderingContext2D.softBox(x: Double, y: Double, w: Double, h: Double, alpha: Double) {
        fillStyle = "rgba(255,255,255,$alpha)"
        fillRect(x, y, w, h)
    }

    private fun sideFace() = createCanvas(SIZE) { ctx ->
        val gradient = ctx.createLinearGradient(0.0, 0.0, 0.0, SIZE.toDouble())
        gradient.addColorStop(0.0, "#8aa2ff")
        gradient.addColorStop(0.45, "#3b4580")
        gradient.addColorStop(0.5, "#cfd6ff")
        gradient.addColorStop(0.56, "#1a1c33")
        gradient.addColorStop(1.0, "#07070f")
        ctx.fillStyle = gradient
        ctx.fillRect(0.0, 0.0, SIZE.toDouble(), SIZE.toDouble())

        ctx.softBox(14.0, 22.0, 26.0, 40.0, 0.9)
        ctx.softBox(86.0, 30.0, 30.0, 22.0, 0.6)
    }

    private fun topFace() = createCanvas(SIZE) { ctx ->
        ctx.fillStyle = "#b9c4ff"
        ctx.fillRect(0.0, 0.0, SIZE.toDouble(), SIZE.toDouble())
        ctx.softBox(30.0, 30.0, 68.0, 68.0, 1.0)
    }

    private fun bottomFace() = createCanvas(SIZE) { ctx ->
        ctx.fillStyle = "#05050a"
        ctx.fillRect(0.0, 0.0, SIZE.toDouble(), SIZE.toDouble())
    }
}
