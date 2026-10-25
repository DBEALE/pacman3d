package pacman3d.renderables

import pacman3d.ext.*
import pacman3d.maze.MazeConst
import three.js.Group
import three.js.Mesh
import three.js.SphereBufferGeometry
import kotlin.math.PI

class PacManMesh : Group() {

    companion object {
        const val SIZE = 1.6 * MazeConst.UNIT_SIZE
        private const val MAX_MOUTH_ANGLE = 115.0 * PI / 180
        private const val SEGMENTS = 32

        private val mouthOpenGeometry = SphereBufferGeometry(
                radius = SIZE / 2,
                widthSegments = SEGMENTS,
                heightSegments = SEGMENTS,
                phiStart = MAX_MOUTH_ANGLE / 2,
                phiLength = TWO_PI - MAX_MOUTH_ANGLE,
                thetaStart = PI,
                thetaLength = PI,
        ).apply {
            rotateX(-HALF_PI)
            computeVertexNormals()
        }

        internal val geometry = SphereBufferGeometry(
                radius = SIZE / 2,
                widthSegments = SEGMENTS,
                heightSegments = SEGMENTS,
                phiStart = 0,
                phiLength = TWO_PI,
                thetaStart = PI,
                thetaLength = PI,
        ).apply {
            morphAttributes.asDynamic().position = arrayOf(mouthOpenGeometry.getAttribute("position"))
            rotateX(-HALF_PI)
            computeVertexNormals()
        }

        private const val EYE_RADIUS = SIZE * 0.07
        private val eyeGeometry = SphereBufferGeometry(radius = EYE_RADIUS, widthSegments = 12, heightSegments = 12)
        private val eyeMaterial = 0x050505.toMeshStandardMaterial(roughness = 0.15).apply { transparent = true }

        private val outsideMaterial = 0xFFE93A.toMeshStandardMaterial(roughness = 0.3, envMapIntensity = 0.9).apply {
            morphTargets = true
            transparent = true
            materialSide = BackSide
        }

        private val insideMaterial = 0xD01020.toMeshStandardMaterial(roughness = 0.5).apply {
            emissive.setHex(0x3A0206)
            morphTargets = true
            transparent = true
            materialSide = FrontSide
        }
    }

    private var mouthOpenSpeed = 10.0
    private var mouthOpenInfluence = 0.0 // range: 0 - 1.0

    private val insideMesh = Mesh(geometry, insideMaterial)
    private val outsideMesh = Mesh(geometry, outsideMaterial)

    // Mouth faces +x and opens up/down; eyes sit on the upper jaw, outside the mouth wedge (>57 degrees above the facing axis)
    private val leftEye = createEye(z = -0.509)
    private val rightEye = createEye(z = 0.509)

    var opacity: Double
        set(value) {
            outsideMaterial.opacity = value
            insideMaterial.opacity = value
            eyeMaterial.opacity = value
        }
        get() = outsideMaterial.opacity.toDouble()

    init {
        add(insideMesh, outsideMesh, leftEye, rightEye)
        outsideMesh.castShadow = true
        insideMesh.castShadow = true
    }

    private fun createEye(z: Double) = Mesh(eyeGeometry, eyeMaterial).apply {
        position.set(0.179 * SIZE / 2, 0.842 * SIZE / 2, z * SIZE / 2)
    }

    fun updateMouthOpen(time: Double) {
        mouthOpenInfluence += time * mouthOpenSpeed

        if (mouthOpenInfluence >= 1.0) {
            mouthOpenInfluence = 1.0
            mouthOpenSpeed = -mouthOpenSpeed
        }
        else if (mouthOpenInfluence <= 0) {
            mouthOpenInfluence = 0.0
            mouthOpenSpeed = -mouthOpenSpeed
        }

        insideMesh.morphTargetInfluences[0] = mouthOpenInfluence
        outsideMesh.morphTargetInfluences[0] = mouthOpenInfluence
    }
}