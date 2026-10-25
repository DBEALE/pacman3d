package pacman3d.assets

import three.js.Font
import three.js.FontLoader

private typealias Callback = (Font) -> Unit

object AssetLoader {

    private const val FONT = "fonts/emulogic.json"

    // A clearer font for the numbers, because the pixel font is hard to read for digits
    private const val NUMBER_FONT = "fonts/helvetiker_bold.json"

    private class FontAsset(path: String) {
        private var font: Font? = null

        private val callbacks = ArrayList<Callback>()

        init {
            FontLoader().load(path, onLoad = { loaded ->
                font = loaded
                callbacks.forEach { it.invoke(loaded) }
                callbacks.clear()
            })
        }

        fun onLoaded(callback: Callback) {
            val loaded = font
            if (loaded != null) callback(loaded) else callbacks += callback
        }
    }

    private val textFont = FontAsset(FONT)

    private val numberFont = FontAsset(NUMBER_FONT)

    fun onFontLoaded(callback: Callback) = textFont.onLoaded(callback)

    fun onNumberFontLoaded(callback: Callback) = numberFont.onLoaded(callback)
}
