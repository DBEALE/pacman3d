package pacman3d.ext

import kotlinx.browser.document
import org.w3c.dom.CanvasRenderingContext2D
import org.w3c.dom.HTMLCanvasElement
import three.js.CanvasTexture

private const val REPEAT_WRAPPING = 1000

fun createCanvas(width: Int, height: Int = width, draw: (CanvasRenderingContext2D) -> Unit): HTMLCanvasElement {
    val canvas = document.createElement("canvas") as HTMLCanvasElement
    canvas.width = width
    canvas.height = height
    draw(canvas.getContext("2d") as CanvasRenderingContext2D)
    return canvas
}

/** Draws at every neighboring offset too, so that features crossing an edge continue on the opposite edge */
fun CanvasRenderingContext2D.drawTiled(width: Int, height: Int, draw: () -> Unit) {
    for (dx in -1..1) for (dy in -1..1) {
        save()
        translate(dx * width.toDouble(), dy * height.toDouble())
        draw()
        restore()
    }
}

fun HTMLCanvasElement.toTexture(repeat: Boolean = true) = CanvasTexture(this).apply {
    if (repeat) {
        asDynamic().wrapS = REPEAT_WRAPPING
        asDynamic().wrapT = REPEAT_WRAPPING
    }
    asDynamic().anisotropy = 8
}

fun Int.toCss(alpha: Double = 1.0) = "rgba(${(this shr 16) and 0xFF},${(this shr 8) and 0xFF},${this and 0xFF},$alpha)"

/** Multiplies each of the color channels by [factor] */
fun Int.shade(factor: Double): Int {
    fun channel(shift: Int) = (((this shr shift) and 0xFF) * factor).toInt().coerceIn(0, 255)
    return (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
}

fun Int.mixWith(other: Int, amount: Double): Int {
    fun channel(shift: Int): Int {
        val a = (this shr shift) and 0xFF
        val b = (other shr shift) and 0xFF
        return (a + (b - a) * amount).toInt().coerceIn(0, 255)
    }
    return (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
}
