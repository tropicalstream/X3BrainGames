package com.x3braingames.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import com.x3braingames.R

/**
 * Real recordings (see SOUND_CREDITS.md), shipped as PCM WAV — SoundPool on
 * this hardware never finishes decoding ffmpeg oggs (the TapKugelbahn mute
 * lesson), and `loaded` waits for actual decode completion, not queueing.
 * Rate control repitches one sample into a whole family of game feedback.
 */
class Sfx(private val context: Context) {

    companion object {
        const val TICK = 0      // cursor step / soft blip
        const val PLACE = 1     // commit / merge / reveal
        const val ERROR = 2     // invalid / conflict / game over
        const val BOOM = 3      // mine
        const val FLAG = 4      // flag toggle
        const val WIN = 5       // solved / cleared
        private const val COUNT = 6
    }

    private val pool = SoundPool.Builder()
        .setMaxStreams(8)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        ).build()

    private val ids = IntArray(COUNT)
    @Volatile private var loadedCount = 0
    private val loaded get() = loadedCount >= COUNT
    @Volatile var volume = 0.9f

    fun loadAsync() {
        pool.setOnLoadCompleteListener { _, _, status -> if (status == 0) loadedCount++ }
        ids[TICK] = pool.load(context, R.raw.tick, 1)
        ids[PLACE] = pool.load(context, R.raw.place, 1)
        ids[ERROR] = pool.load(context, R.raw.error, 1)
        ids[BOOM] = pool.load(context, R.raw.boom, 1)
        ids[FLAG] = pool.load(context, R.raw.flag, 1)
        ids[WIN] = pool.load(context, R.raw.win, 1)
    }

    fun play(id: Int, pitch: Float, vol: Float) {
        if (!loaded || id !in 0 until COUNT) return
        val v = (vol * volume).coerceIn(0f, 1f)
        pool.play(ids[id], v, v, 1, 0, pitch.coerceIn(0.5f, 2f))
    }

    fun release() = pool.release()
}
