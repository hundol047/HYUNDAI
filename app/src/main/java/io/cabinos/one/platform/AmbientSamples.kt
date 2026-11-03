package io.cabinos.one.platform

import kotlin.math.*
import kotlin.random.Random

/** Original local synthesis, no third-party recordings or melodies. */
internal object AmbientSamples {
    const val RATE=22050
    val presets=listOf("백색 소음","자연의 소리","잔잔한 톤","빗소리","계곡 소리","파도 소리")
    fun create(preset:String):ShortArray {
        require(preset in presets)
        val random=Random(42)
        var smooth=0.0
        val wave=DoubleArray(RATE*4) {i->
            val noise=random.nextDouble()*2-1
            smooth=.9*smooth+.1*noise
            val t=i.toDouble()/RATE
            when(preset) {
                "백색 소음"->noise
                "자연의 소리"->smooth*(.7+.3*sin(2*PI*.25*t))
                "빗소리"->noise*.3+smooth*.8
                "계곡 소리"->smooth*.8+sin(2*PI*(650+120*sin(2*PI*2*t))*t)*.025+noise*.06
                "파도 소리"->smooth*(.35+.65*(1-cos(2*PI*.25*t))/2)
                else->sin(2*PI*440*t)*.65+sin(2*PI*660*t)*.35
            }
        }
        val rms=sqrt(wave.sumOf {it*it}/wave.size)
        return ShortArray(wave.size) {i->
            val fade=minOf(1.0,i/220.0,(wave.lastIndex-i)/220.0)
            ((wave[i]*.14/rms).coerceIn(-.65,.65)*fade*Short.MAX_VALUE).roundToInt().toShort()
        }
    }
}
