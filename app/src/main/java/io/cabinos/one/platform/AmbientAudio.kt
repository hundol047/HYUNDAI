package io.cabinos.one.platform

import android.content.Context
import android.media.*
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Local PCM ambience. It never owns vehicle audio zones or starts playback in the background. */
class AmbientAudio(private val context: Context) {
    private val manager = context.getSystemService(AudioManager::class.java)
    private var track: AudioTrack? = null
    private var focus: AudioFocusRequest? = null
    var playing = false; private set
    private val mutableStatus=MutableStateFlow("배경 소리 꺼짐")
    val status=mutableStatus.asStateFlow()
    internal fun samples(name:String)=RecordedAmbience.load(context,name)
    fun showVolume() {manager.adjustVolume(AudioManager.ADJUST_SAME,AudioManager.FLAG_SHOW_UI)}
    fun stopByUser() {stop();mutableStatus.value="배경 소리 꺼짐"}

    fun play(preset: String,samples:ShortArray=AmbientSamples.create(preset)): Boolean {
        stop()
        val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(attributes).setWillPauseWhenDucked(true)
            .setOnAudioFocusChangeListener({ change -> if (change < 0) {stop();mutableStatus.value="다른 소리로 인해 중지됐습니다. 다시 재생해 주세요."} }, Handler(Looper.getMainLooper())).build()
        focus = request
        if (manager.requestAudioFocus(request) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) { stop();mutableStatus.value="다른 앱의 소리 사용이 끝난 뒤 다시 재생해 주세요."; return false }
        return try {
            val rate=AmbientSamples.RATE
            val audio = AudioTrack.Builder().setAudioAttributes(attributes)
                .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(rate).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                .setTransferMode(AudioTrack.MODE_STATIC).setBufferSizeInBytes(samples.size * 2).build()
            track = audio
            // MODE_STATIC starts in STATE_NO_STATIC_DATA. A successful write
            // moves it to INITIALIZED; checking earlier rejects every preset.
            check(audio.state != AudioTrack.STATE_UNINITIALIZED)
            val pcm=java.nio.ByteBuffer.allocate(samples.size*2).order(java.nio.ByteOrder.LITTLE_ENDIAN).apply {asShortBuffer().put(samples)}.array()
            check(audio.write(pcm, 0, pcm.size) == pcm.size)
            check(audio.state == AudioTrack.STATE_INITIALIZED)
            check(audio.setLoopPoints(0, samples.size, -1) == AudioTrack.SUCCESS)
            audio.play(); playing = true
            mutableStatus.value=if(manager.getStreamVolume(AudioManager.STREAM_MUSIC)==0)"재생 중이지만 미디어 음량이 0입니다. 음량을 올려 주세요." else "$preset 재생 중 · 안 들리면 미디어 음량과 블루투스 출력을 확인하세요."
            true
        } catch (error: RuntimeException) { android.util.Log.w("CabinAudio","배경 소리 초기화 실패",error);stop();mutableStatus.value="소리를 재생하지 못했습니다. 다른 오디오 앱을 닫고 다시 시도해 주세요."; false }
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
