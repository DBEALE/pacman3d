package pacman3d

import kotlinx.browser.document
import kotlinx.browser.window
import pacman3d.camera.CameraAnimator
import pacman3d.ext.*
import pacman3d.maze.MazeConst
import pacman3d.logic.controllers.GameController
import stats.js.Stats
import three.js.*

class Game {

    private companion object {
        const val PCF_SOFT_SHADOW_MAP = 2
        const val ACES_FILMIC_TONE_MAPPING = 5

        const val SHADOW_MAP_SIZE = 2048
        const val SHADOW_AREA_WIDTH = 19
        const val SHADOW_AREA_LENGTH = 22
    }

    private var paused = false

    private val clock = Clock()

    private val camera = PerspectiveCamera(75, window.aspectRatio, 0.5, 400).apply {
        position.set(0, MazeConst.LENGTH * 0.55, MazeConst.LENGTH * 0.5)
        lookAt(0, 0, 5)
        add(SoundPlayer)
    }

    private val cameraAnimator = CameraAnimator(camera)

    private val renderer = webGLRenderer(clearColor = 0x05050A).apply {
        val r = asDynamic()
        r.shadowMap.enabled = true
        r.shadowMap.type = PCF_SOFT_SHADOW_MAP
        r.toneMapping = ACES_FILMIC_TONE_MAPPING
        r.toneMappingExposure = 1.15
    }

    private val gameController = GameController()

    private val stats = Stats().apply {
        showPanel(0) // 0: fps, 1: ms, 2: mb, 3+: custom
        document.body?.appendChild(domElement)
        with(domElement.style) {
            position="fixed"
            top="0px"
            left="0px"
        }
    }

    private val scene = Scene().apply {
        // The main light, which also casts the shadows of everything onto the floor
        add(DirectionalLight(0xFFFFFF, 1.1).apply {
            position.set(-12, 34, 22)
            castShadow = true

            val shadow = asDynamic().shadow
            shadow.mapSize.set(SHADOW_MAP_SIZE, SHADOW_MAP_SIZE)
            shadow.bias = -0.0006

            val shadowCamera = shadow.camera
            shadowCamera.left = -SHADOW_AREA_WIDTH
            shadowCamera.right = SHADOW_AREA_WIDTH
            shadowCamera.top = SHADOW_AREA_LENGTH
            shadowCamera.bottom = -SHADOW_AREA_LENGTH
            shadowCamera.near = 5
            shadowCamera.far = 90
            shadowCamera.updateProjectionMatrix()
        })

        // A weaker, warmer light from the opposite side keeps the shaded sides of the walls from going black
        add(DirectionalLight(0xFFD2A8, 0.3).apply { position.set(15, 12, -12) })
        add(HemisphereLight(0xB8C4FF, 0x1A1030, 0.55))
    }

    init {
        window.onresize = {
            camera.onResize()
            renderer.onResize()
            updateScene()
        }

        window.ondblclick = { gameController.onDoubleClick(it.clientX, it.clientY, camera) }

        window.onblur = { pause() }

        window.onfocus = { resume() }

        gameController.setup(scene)

        gameController.start()
    }

    private fun pause() {
        clock.stop()
        paused = true
    }

    private fun resume() {
        paused = false
        clock.start()
        animate()
    }

    private fun updateScene() {
        stats.begin()

        val time = clock.getDelta().toDouble()

        gameController.update(time)

        //cameraAnimator.update(time)

        renderer.render(scene, camera)

        stats.end()
    }

    fun animate() {
        updateScene()

        if (!paused) window.requestAnimationFrame { animate() }
    }
}