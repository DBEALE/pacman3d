package pacman3d.renderables

import pacman3d.entities.Fruit
import pacman3d.entities.World
import pacman3d.logic.Position
import kotlinx.browser.window
import three.js.Camera
import kotlin.math.hypot
import kotlin.math.sin
import three.js.Group

class FruitRenderable : Renderable {

    private companion object {
        const val FRUIT_HEIGHT = 0.6
        const val SPIN_SPEED = 2.5
        const val BOB_SPEED = 4.0
        const val BOB_HEIGHT = 0.1

        // The most fruits shown in the row of cleared levels, which makes room for the lives next to it
        const val MAX_INDICATORS = 5
        const val INDICATOR_SPACING = 2.6
        const val INDICATOR_SCALE = 1.8
        const val FRUIT_SCALE = 1.4
        const val INDICATOR_SPIN_SPEED = 0.8

        // The size of the area that can be clicked around a fruit, as a fraction of the screen height
        const val HIT_RADIUS = 0.05
        val INDICATOR_RIGHT_EDGE = Position(27.0, 35.0)
    }

    // The fruit that appears in the maze
    private val fruitGroup = Group().apply {
        visible = false
        scale.set(FRUIT_SCALE, FRUIT_SCALE, FRUIT_SCALE)
        position.set(Fruit.POSITION.worldX, 0.0, Fruit.POSITION.worldY)
    }

    // A fruit for each of the cleared levels, displayed next to the lives. The newest fruit is on the right
    private val levelIndicator = Group()

    override val sceneObject = Group().add(fruitGroup, levelIndicator)

    private var age = 0.0

    override fun setup(world: World) = Unit

    fun setKind(kind: Int) = replaceChildren(fruitGroup, kind)

    fun setClearedLevels(clearedLevels: Int) {
        levelIndicator.children.toList().forEach { levelIndicator.remove(it) }

        val shown = minOf(clearedLevels, MAX_INDICATORS)
        for (i in 0 until shown) {
            val level = clearedLevels - shown + i
            val x = INDICATOR_RIGHT_EDGE.worldX - (shown - 1 - i) * INDICATOR_SPACING

            levelIndicator.add(Group().apply {
                position.set(x, FRUIT_HEIGHT, INDICATOR_RIGHT_EDGE.worldY)
                scale.set(INDICATOR_SCALE, INDICATOR_SCALE, INDICATOR_SCALE)
                add(FruitMeshes.create(minOf(level, FruitMeshes.COUNT - 1)))
            })
        }
    }

    /** True if the screen position is on top of one of the fruits of the cleared levels */
    fun isIndicatorAt(clientX: Int, clientY: Int, camera: Camera): Boolean {
        val width = window.innerWidth.toDouble()
        val height = window.innerHeight.toDouble()
        val radius = height * HIT_RADIUS

        return levelIndicator.children.any { fruit ->
            val projected = fruit.position.clone().asDynamic()
            projected.project(camera)
            val screenX = (projected.x.unsafeCast<Double>() + 1) / 2 * width
            val screenY = (1 - projected.y.unsafeCast<Double>()) / 2 * height
            hypot(screenX - clientX, screenY - clientY) <= radius
        }
    }

    fun setShown(shown: Boolean) {
        fruitGroup.visible = shown
    }

    override fun update(world: World, time: Double) {
        levelIndicator.children.forEach { it.rotateY(time * INDICATOR_SPIN_SPEED) }

        if (!fruitGroup.visible) return

        age += time
        fruitGroup.rotateY(time * SPIN_SPEED)
        fruitGroup.position.y = FRUIT_HEIGHT + sin(age * BOB_SPEED) * BOB_HEIGHT
    }

    private fun replaceChildren(group: Group, kind: Int) {
        while (group.children.isNotEmpty()) group.remove(group.children[0])
        group.add(FruitMeshes.create(kind))
    }
}
