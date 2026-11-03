package io.cabinos.one.platform

import android.content.Context
import io.cabinos.one.R
import java.nio.ByteBuffer
import java.nio.ByteOrder

internal object RecordedAmbience {
    fun load(context:Context,name:String):ShortArray {
        val resource=when(name){
            "빗소리"->R.raw.nature_rain
            "계곡 소리"->R.raw.nature_stream
            "파도 소리"->R.raw.nature_ocean
            "자연의 소리"->R.raw.nature_birds
            else->return AmbientSamples.create(name)
        }
        val bytes=context.resources.openRawResource(resource).use {it.readBytes()}
        require(bytes.isNotEmpty()&&bytes.size%2==0)
        return ShortArray(bytes.size/2).also {ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(it)}
    }
}
