package io.cabinos.one.platform

import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import java.util.Locale
import kotlin.math.roundToInt

/** Never select a network voice implicitly: road names stay on this device. */
internal object NavigationSpeechVoice {
    fun select(voices:Set<Voice>,locale:Locale):Voice? = voices.asSequence().filter {
        it.locale.language==locale.language && !it.isNetworkConnectionRequired &&
            TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED !in it.features.orEmpty() &&
            (locale.script.isBlank()||it.locale.script.isBlank()||locale.script==it.locale.script)
    }.sortedWith(compareByDescending<Voice> {it.locale.toLanguageTag()==locale.toLanguageTag()}
        .thenByDescending {locale.country.isNotBlank()&&it.locale.country==locale.country}
        .thenByDescending {it.quality}.thenBy {it.latency}.thenBy {it.name}).firstOrNull()

    fun locale(tag:String):Locale {
        val requested=Locale.forLanguageTag(tag)
        val supported=Locale.forLanguageTag(io.cabinos.core.NavigationLanguages.resolve(tag))
        return if(requested.language==supported.language)requested else supported
    }

    /** Speech only: route progress and turn thresholds retain the exact distance. */
    fun roundedMeters(meters:Double):Int {
        require(meters.isFinite()&&meters>=0)
        val step=when {meters>=1000->100;meters>=100->10;else->1}
        return (meters/step).roundToInt()*step
    }

    fun koreanMeters(meters:Int):String {
        var value=meters.coerceAtLeast(0)
        if(value==0)return "영 미터"
        val groups=mutableListOf<String>()
        val digits="영일이삼사오육칠팔구"
        val units=listOf("","십","백","천")
        var groupIndex=0
        while(value>0) {
            var group=value%10000
            var text=""
            var place=0
            while(group>0) {
                val digit=group%10
                if(digit>0)text=(if(digit==1&&place>0)"" else digits[digit].toString())+units[place]+text
                group/=10;place++
            }
            if(text.isNotEmpty())groups.add(text+listOf("","만","억")[groupIndex])
            value/=10000;groupIndex++
        }
        return groups.asReversed().joinToString("")+" 미터"
    }
}
