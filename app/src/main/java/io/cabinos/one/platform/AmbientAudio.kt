package io.cabinos.one.platform

import android.content.Context
import android.media.*
import android.os.Handler
import android.os.Looper
import kotlin.random.Random

/** Local PCM ambience. It never owns vehicle audio zones or starts playback in the background. */
class AmbientAudio(context: Context) {
    private val manager = context.getSystemService(AudioManager::class.java)
    private var track: AudioTrack? = null
    private var focus: AudioFocusRequest? = null
    var playing = false; private set

    fun play(preset: String): Boolean {
        stop()
        val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(attributes).setWillPauseWhenDucked(true)
            .setOnAudioFocusChangeListener({ change -> if (change < 0) stop() }, Handler(Looper.getMainLooper())).build()
        focus = request
        if (manager.requestAudioFocus(request) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) { stop(); return false }
        return try {
            val rate = 22050
            val samples = ShortArray(rate * 4)
            var smooth = 0.0
            val random = Random(42)
            samples.indices.forEach { i ->
                smooth = .98 * smooth + .02 * (random.nextDouble() * 2 - 1)
                val value = when (preset) {
                    "자연의 소리" -> smooth * .6 + kotlin.math.sin(i * 2 * Math.PI * 220 / rate) * .006 * kotlin.math.sin(i * 2 * Math.PI * .5 / rate)
                    "잔잔한 톤" -> kotlin.math.sin(i * 2 * Math.PI * 110 / rate) * .025 + kotlin.math.sin(i * 2 * Math.PI * 165 / rate) * .015
                    else -> smooth * .65
                }
                val fade = minOf(1.0, i / 220.0, (samples.lastIndex - i) / 220.0)
                samples[i] = (value * fade * Short.MAX_VALUE).toInt().toShort()
            }
            val audio = AudioTrack.Builder().setAudioAttributes(attributes)
                .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(rate).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                .setTransferMode(AudioTrack.MODE_STATIC).setBufferSizeInBytes(samples.size * 2).build()
            track = audio
            check(audio.state == AudioTrack.STATE_INITIALIZED)
            check(audio.write(samples, 0, samples.size) == samples.size)
            check(audio.setLoopPoints(0, samples.size, -1) == AudioTrack.SUCCESS)
            audio.play(); playing = true; true
        } catch (_: RuntimeException) { stop(); false }
    }
    fun stop() {
        playing = false
        track?.let { audio ->
            try { if (audio.playState == AudioTrack.PLAYSTATE_PLAYING) audio.stop() } finally { audio.release() }
        }
        track = null
        val previousFocus=focus
        focus = null
        previousFocus?.let { manager.abandonAudioFocusRequest(it) }
    }
}
