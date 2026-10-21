package io.cabinos.one.platform

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.random.Random

/** Locally generated ambient sound: no copyrighted tracks, fabricated streaming service, or network. */
class AmbientAudio {
    private var track:AudioTrack?=null
    var playing=false; private set
    fun play(preset:String) {
        stop()
        val rate=22050;val samples=ShortArray(rate*4);var smooth=0.0
        val random=Random(42)
        samples.indices.forEach { i ->
            smooth=.98*smooth+.02*(random.nextDouble()*2-1)
            val value=when(preset) {
                "자연의 소리" -> smooth*.6 + kotlin.math.sin(i*2*Math.PI*220/rate)*.006*kotlin.math.sin(i*2*Math.PI*.5/rate)
                "잔잔한 톤" -> kotlin.math.sin(i*2*Math.PI*110/rate)*.025 + kotlin.math.sin(i*2*Math.PI*165/rate)*.015
                else -> smooth*.65
            }
            samples[i]=(value*Short.MAX_VALUE).toInt().toShort()
        }
        track=AudioTrack.Builder().setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(rate).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setTransferMode(AudioTrack.MODE_STATIC).setBufferSizeInBytes(samples.size*2).build().also {
                it.write(samples,0,samples.size);it.setLoopPoints(0,samples.size,-1);it.play()
            }
        playing=true
    }
    fun stop() { track?.let { if(it.playState==AudioTrack.PLAYSTATE_PLAYING) it.stop();it.release() };track=null;playing=false }
}
