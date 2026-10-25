package pacman3d

import kotlin.math.ceil
import kotlin.math.pow

/**
 * A short looping tune with a lead voice and a bass voice. Notes are written as "name octave:beats", for example "C#5:0.5",
 * and "R:1" is a rest.
 */
class Tune(
    val bpm: Double,
    val lead: String,
    val bass: String,
    val leadWave: String = "square",
    val bassWave: String = "triangle",
) {
    val beats get() = maxOf(beatsOf(lead), beatsOf(bass))

    private fun beatsOf(notes: String) = notes.split(" ").filter { it.isNotBlank() }.sumByDouble { it.substringAfter(":").toDouble() }
}

/** Synthesizes the tunes of the cut scenes with the Web Audio API, so that no audio assets are needed */
object Music {

    private const val VOLUME = 0.16
    private const val LEAD_VOLUME = 0.55
    private const val BASS_VOLUME = 0.9
    private const val FADE_OUT_TIME = 0.15

    private val NOTE_OFFSETS = mapOf('C' to 0, 'D' to 2, 'E' to 4, 'F' to 5, 'G' to 7, 'A' to 9, 'B' to 11)

    private val context get() = SoundPlayer.audioListener.context.asDynamic()

    private var master: dynamic = null

    private val voices = ArrayList<dynamic>()

    /** Plays the tune over and over, for about [seconds] */
    fun play(tune: Tune, seconds: Double) {
        stop()

        val ctx = context
        val gain = ctx.createGain()
        gain.gain.value = VOLUME
        gain.connect(ctx.destination)
        master = gain

        val beatTime = 60.0 / tune.bpm
        val loops = ceil(seconds / (tune.beats * beatTime)).toInt().coerceAtLeast(1)
        val start = (ctx.currentTime as Double) + 0.05

        for (loop in 0 until loops) {
            val loopStart = start + loop * tune.beats * beatTime
            schedule(tune.lead, tune.leadWave, LEAD_VOLUME, loopStart, beatTime)
            schedule(tune.bass, tune.bassWave, BASS_VOLUME, loopStart, beatTime)
        }
    }

    fun stop() {
        val gain = master ?: return
        master = null

        // Fade out instead of cutting off, to avoid a click
        val now = context.currentTime as Double
        gain.gain.cancelScheduledValues(now)
        gain.gain.setValueAtTime(gain.gain.value, now)
        gain.gain.linearRampToValueAtTime(0.0, now + FADE_OUT_TIME)

        val stopping = voices.toList()
        voices.clear()
        kotlinx.browser.window.setTimeout({
            stopping.forEach { try { it.stop() } catch (e: Throwable) { } }
            gain.disconnect()
        }, (FADE_OUT_TIME * 1000).toInt() + 50)
    }

    private fun schedule(notes: String, wave: String, volume: Double, start: Double, beatTime: Double) {
        var time = start
        notes.split(" ").filter { it.isNotBlank() }.forEach { token ->
            val duration = token.substringAfter(":").toDouble() * beatTime
            val name = token.substringBefore(":")
            if (name != "R") playNote(frequencyOf(name), wave, volume, time, duration)
            time += duration
        }
    }

    private fun playNote(frequency: Double, wave: String, volume: Double, start: Double, duration: Double) {
        val ctx = context
        val oscillator = ctx.createOscillator()
        oscillator.type = wave
        oscillator.frequency.value = frequency

        val envelope = ctx.createGain()
        envelope.gain.setValueAtTime(0.0, start)
        envelope.gain.linearRampToValueAtTime(volume, start + 0.01)
        envelope.gain.setValueAtTime(volume, start + duration * 0.6)
        envelope.gain.linearRampToValueAtTime(0.0, start + duration * 0.95)

        oscillator.connect(envelope)
        envelope.connect(master)
        oscillator.start(start)
        oscillator.stop(start + duration)
        voices.add(oscillator)
    }

    // "A4" is 440hz
    private fun frequencyOf(name: String): Double {
        var semitone = NOTE_OFFSETS.getValue(name[0])
        var index = 1
        when (name[index]) {
            '#' -> { semitone++; index++ }
            'b' -> { semitone--; index++ }
        }
        val octave = name.substring(index).toInt()
        return 440.0 * 2.0.pow((semitone + (octave - 4) * 12 - 9) / 12.0)
    }
}

object Tunes {
    val CHASE = Tune(
        bpm = 150.0,
        lead = "A4:.5 C5:.5 E5:.5 C5:.5 A4:.5 C5:.5 E5:.5 C5:.5 G4:.5 B4:.5 D5:.5 B4:.5 G4:.5 B4:.5 D5:.5 B4:.5 " +
            "F4:.5 A4:.5 C5:.5 A4:.5 F4:.5 A4:.5 C5:.5 A4:.5 E4:.5 G#4:.5 B4:.5 G#4:.5 E5:1 R:1",
        bass = "A2:2 A2:2 G2:2 G2:2 F2:2 F2:2 E2:2 E2:2",
    )

    val CONGA = Tune(
        bpm = 132.0,
        lead = "C5:.5 C5:.5 E5:.5 G5:.5 E5:1 C5:1 D5:.5 D5:.5 F5:.5 A5:.5 F5:1 D5:1 E5:.5 E5:.5 G5:.5 C6:.5 G5:1 E5:1 D5:1 B4:1 C5:2",
        bass = "C3:1 G3:1 C3:1 G3:1 D3:1 A3:1 D3:1 A3:1 C3:1 G3:1 C3:1 G3:1 G2:1 G3:1 C3:2",
    )

    val GIANT = Tune(
        bpm = 110.0,
        lead = "E4:1 E4:.5 G4:.5 B4:1 G4:1 E4:1 E4:.5 G4:.5 B4:1 E5:1 D5:1 D5:.5 C5:.5 B4:1 A4:1 B4:2 R:2",
        bass = "E2:2 E2:2 E2:2 E2:2 D2:2 D2:2 B1:2 B1:2",
        leadWave = "sawtooth",
    )

    val ORBIT = Tune(
        bpm = 156.0,
        lead = "D5:1 F#5:1 A5:1 G5:1 F#5:1 E5:1 F#5:1 A5:1 D6:1 B5:1 A5:1 G5:1 A5:3 R:3",
        bass = "D3:1 A3:1 A3:1 D3:1 A3:1 A3:1 G3:1 D4:1 D4:1 G3:1 D4:1 D4:1 A2:1 E3:1 E3:1 D3:3",
    )

    val BALLOONS = Tune(
        bpm = 144.0,
        lead = "F5:.5 A5:.5 C6:.5 A5:.5 F5:1 R:1 G5:.5 Bb5:.5 D6:.5 Bb5:.5 G5:1 R:1 A5:.5 C6:.5 F6:.5 C6:.5 A5:1 F5:1 G5:1 E5:1 F5:2",
        bass = "F3:1 C4:1 F3:1 C4:1 G3:1 D4:1 G3:1 D4:1 F3:1 C4:1 A3:1 F3:1 C3:1 G3:1 F3:2",
        leadWave = "triangle",
        bassWave = "square",
    )
}
