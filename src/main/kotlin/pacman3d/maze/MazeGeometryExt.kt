package pacman3d.maze

import three.js.Color
import three.js.Face3
import three.js.Geometry
import three.js.Vector2
import three.js.Vector3
import kotlin.math.abs

/**
 * Gives the geometry texture coordinates that are projected along the axis that each face is mostly facing, so that a
 * texture has the same size on all the walls no matter how long they are.
 */
fun Geometry.projectUvs(tile: Double) = apply {
    computeFaceNormals()

    val uvs = faces.map { face ->
        val nx = abs(face.normal.x.toDouble())
        val ny = abs(face.normal.y.toDouble())
        val nz = abs(face.normal.z.toDouble())
        arrayOf(face.a, face.b, face.c).map { index ->
            val v = vertices[index.toInt()]
            val x = v.x.toDouble() / tile
            val y = v.y.toDouble() / tile
            val z = v.z.toDouble() / tile
            when {
                ny >= nx && ny >= nz -> Vector2(x, z)
                nx >= nz -> Vector2(z, y)
                else -> Vector2(x, y)
            }
        }.toTypedArray()
    }.toTypedArray()

    faceVertexUvs = arrayOf(uvs)
    uvsNeedUpdate = true
}

/** Creates a new geometry out of the faces that face upwards, raised slightly to prevent them from fighting with the originals */
fun Geometry.topFaces(raise: Double): Geometry {
    computeFaceNormals()

    val newVertices = ArrayList<Vector3>()
    val newFaces = ArrayList<Face3>()

    faces.filter { it.normal.y.toDouble() > 0.9 }.forEach { face ->
        val first = newVertices.size
        arrayOf(face.a, face.b, face.c).forEach { index ->
            val v = vertices[index.toInt()]
            newVertices.add(Vector3(v.x.toDouble(), v.y.toDouble() + raise, v.z.toDouble()))
        }
        newFaces.add(Face3(first, first + 1, first + 2, normal = Vector3(0.0, 1.0, 0.0), color = Color(0xFFFFFF)))
    }

    return Geometry().apply {
        vertices = newVertices.toTypedArray()
        faces = newFaces.toTypedArray()
    }
}
