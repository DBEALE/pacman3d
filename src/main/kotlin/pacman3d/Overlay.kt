package pacman3d

import kotlinx.browser.document
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLInputElement
import pacman3d.logic.HighScores

/** The HTML parts of the game on top of the 3D scene: the high score table, the name prompt and the demo banner */
object Overlay {

    private const val FONT = "'Courier New', monospace"
    private const val YELLOW = "#FFE93A"

    private fun element(css: String) = (document.createElement("div") as HTMLElement).apply {
        style.cssText = "position:fixed;font-family:$FONT;font-weight:bold;text-shadow:0 0 6px #000;$css"
        document.body!!.appendChild(this)
    }

    private val hud = element("top:8px;right:12px;color:#DFDFFF;font-size:18px;")

    private val table = element(
        "top:50%;right:3%;transform:translateY(-50%);min-width:240px;padding:14px 20px;display:none;" +
        "color:#DFDFFF;font-size:18px;background:rgba(5,5,20,0.72);border:2px solid $YELLOW;border-radius:8px;"
    )

    private val banner = element(
        "bottom:6%;left:0;right:0;text-align:center;display:none;color:$YELLOW;font-size:26px;" +
        "animation:pacman3d-blink 1s steps(2,start) infinite;"
    )

    private val demoLabel = element("top:8px;left:0;right:0;text-align:center;display:none;color:#FF5555;font-size:22px;")

    private val prompt = element(
        "top:50%;left:50%;transform:translate(-50%,-50%);display:none;padding:22px 30px;text-align:center;" +
        "color:$YELLOW;font-size:22px;background:rgba(5,5,20,0.88);border:2px solid $YELLOW;border-radius:8px;"
    )

    private val promptText = (document.createElement("div") as HTMLElement).also { prompt.appendChild(it) }

    private val nameInput = (document.createElement("input") as HTMLInputElement).also { input ->
        input.maxLength = HighScores.MAX_NAME_LENGTH
        input.style.cssText = "margin-top:14px;width:11em;text-align:center;text-transform:uppercase;font:inherit;" +
            "color:$YELLOW;background:#000;border:2px solid $YELLOW;padding:6px;outline:none;"
        prompt.appendChild(input)
    }

    private var onNameEntered: ((String) -> Unit)? = null

    private var shownHighScore = -1

    init {
        val style = document.createElement("style")
        style.textContent = "@keyframes pacman3d-blink { to { visibility: hidden; } }"
        document.head!!.appendChild(style)

        demoLabel.textContent = "DEMO"

        // The input must keep the key presses to itself, so that they don't steer the game
        nameInput.onkeydown = { event ->
            event.stopPropagation()
            if (event.key == "Enter") submitName()
            Unit
        }
    }

    fun setHighScore(points: Int) {
        if (points == shownHighScore) return
        shownHighScore = points
        hud.textContent = "HIGH SCORE  $points"
    }

    /** [highlight] is the row of the table to emphasize, if any */
    fun showTable(highlight: Int = -1) {
        val rows = HighScores.entries.mapIndexed { i, entry ->
            val color = if (i == highlight) "color:$YELLOW;" else ""
            "<tr style=\"$color\"><td style=\"padding-right:14px\">${i + 1}.</td><td style=\"padding-right:24px\">${entry.name}</td>" +
                "<td style=\"text-align:right\">${entry.score}</td></tr>"
        }.joinToString("")

        table.innerHTML = "<div style=\"text-align:center;color:$YELLOW;margin-bottom:10px;letter-spacing:2px\">HIGH SCORES</div>" +
            "<table style=\"border-collapse:collapse;width:100%\">$rows</table>"
        table.style.display = "block"
    }

    fun showBanner(text: String) {
        banner.textContent = text
        banner.style.display = "block"
    }

    fun setDemo(demo: Boolean) {
        demoLabel.style.display = if (demo) "block" else "none"
    }

    fun hideAll() {
        table.style.display = "none"
        banner.style.display = "none"
        demoLabel.style.display = "none"
        prompt.style.display = "none"
        onNameEntered = null
    }

    fun promptForName(score: Int, onEntered: (String) -> Unit) {
        onNameEntered = onEntered
        promptText.innerHTML = "NEW HIGH SCORE<br>$score<br><span style=\"font-size:16px;color:#DFDFFF\">ENTER YOUR NAME AND PRESS ENTER</span>"
        nameInput.value = ""
        prompt.style.display = "block"
        nameInput.focus()
    }

    private fun submitName() {
        val callback = onNameEntered ?: return
        onNameEntered = null
        prompt.style.display = "none"
        nameInput.blur()
        callback(nameInput.value)
    }
}
