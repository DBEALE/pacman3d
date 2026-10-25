package pacman3d.logic

import kotlinx.browser.localStorage

class HighScoreEntry(val name: String, val score: Int)

/** The best scores, kept in the browser's local storage */
object HighScores {

    const val MAX_ENTRIES = 10
    const val MAX_NAME_LENGTH = 8

    private const val STORAGE_KEY = "pacman3d.highscores"

    private val defaults = listOf(
        HighScoreEntry("PAC", 8000),
        HighScoreEntry("BLINKY", 6000),
        HighScoreEntry("PINKY", 4500),
        HighScoreEntry("INKY", 3000),
        HighScoreEntry("CLYDE", 1500),
    )

    var entries: List<HighScoreEntry> = load()
        private set

    val top get() = entries.firstOrNull()?.score ?: 0

    fun qualifies(score: Int) = score > 0 && (entries.size < MAX_ENTRIES || score > entries.last().score)

    /** Returns the position of the new entry in the table */
    fun add(name: String, score: Int): Int {
        val entry = HighScoreEntry(sanitize(name).ifEmpty { "???" }, score)
        // Sorting is stable, so an entry that ties with an older one is placed below it
        entries = (entries + entry).sortedByDescending { it.score }.take(MAX_ENTRIES)
        save()
        return entries.indexOf(entry)
    }

    // Names end up in the page, so only harmless characters are allowed
    private fun sanitize(name: String) = name.filter { it in 'A'..'Z' || it in 'a'..'z' || it in '0'..'9' || it == ' ' || it == '-' || it == '_' || it == '.' }
        .trim().take(MAX_NAME_LENGTH).toUpperCase()

    private fun load(): List<HighScoreEntry> = try {
        val stored = localStorage.getItem(STORAGE_KEY)
        stored?.split("|")?.mapNotNull { item ->
            val separator = item.lastIndexOf(':')
            val score = if (separator > 0) item.substring(separator + 1).toIntOrNull() else null
            if (score == null) null else HighScoreEntry(sanitize(item.substring(0, separator)), score)
        }?.sortedByDescending { it.score }?.take(MAX_ENTRIES)?.takeIf { it.isNotEmpty() } ?: defaults
    } catch (e: Throwable) {
        defaults
    }

    private fun save() {
        try {
            localStorage.setItem(STORAGE_KEY, entries.joinToString("|") { "${it.name}:${it.score}" })
        } catch (e: Throwable) {
            // Storage may be unavailable (e.g. private browsing), the scores are then just kept until the page closes
        }
    }
}
