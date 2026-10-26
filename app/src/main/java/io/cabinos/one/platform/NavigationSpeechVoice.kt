package io.cabinos.one.platform

import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import java.util.Locale
import kotlin.math.roundToInt

/** Never select a network voice implicitly: road names stay on this device. */
internal object NavigationSpeechVoice {
    fun select(voices:Set<Voice>,locale:Locale,preferred:Voice?=null):Voice? = voices.asSequence().filter {
        it.locale.language==locale.language && !it.isNetworkConnectionRequired &&
            TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED !in it.features.orEmpty() &&
            (locale.script.isBlank()||it.locale.script.isBlank()||locale.script==it.locale.script)
    }.sortedWith(compareByDescending<Voice> {it.locale.toLanguageTag()==locale.toLanguageTag()}
        .thenByDescending {locale.country.isNotBlank()&&it.locale.country==locale.country}
        .thenByDescending {it.quality}.thenByDescending {it==preferred}
        .thenBy {it.latency}.thenBy {it.name}).firstOrNull()

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

    fun distance(meters:Double,tag:String):String {
        val rounded=roundedMeters(meters)
        val locale=locale(tag)
        if(locale.language=="fr") {
            if(rounded<1000)return SpokenDistanceNumbers.french(rounded)+if(rounded>1)" mètres" else " mètre"
            val whole=rounded/1000;val decimal=(rounded%1000)/100
            return SpokenDistanceNumbers.french(whole)+(if(decimal>0)" virgule "+SpokenDistanceNumbers.french(decimal) else "")+
                if(rounded>=2000)" kilomètres" else " kilomètre"
        }
        if(locale.language=="ja") {
            if(rounded<1000)return SpokenDistanceNumbers.japanese(rounded)+"メートル"
            val whole=rounded/1000;val decimal=(rounded%1000)/100
            return SpokenDistanceNumbers.japanese(whole)+(if(decimal>0)"てん"+SpokenDistanceNumbers.japanese(decimal) else "")+"キロメートル"
        }
        if(locale.language=="ko") {
            if(rounded<1000)return koreanMeters(rounded)
            val whole=rounded/1000
            val decimal=(rounded%1000)/100
            val number=koreanMeters(whole).removeSuffix(" 미터")
            return number+(if(decimal>0)" 점 "+koreanMeters(decimal).removeSuffix(" 미터") else "")+" 킬로미터"
        }
        val unit=if(rounded>=1000)android.icu.util.MeasureUnit.KILOMETER else android.icu.util.MeasureUnit.METER
        val value:Number=if(rounded>=1000)rounded/1000.0 else rounded
        return android.icu.text.MeasureFormat.getInstance(locale,android.icu.text.MeasureFormat.FormatWidth.WIDE)
            .format(android.icu.util.Measure(value,unit))
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
