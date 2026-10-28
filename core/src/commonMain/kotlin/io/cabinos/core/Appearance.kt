package io.cabinos.core

import kotlin.math.*

data class AppearanceSettings(val preset:String="graphite",val accent:String="",val brightness:String="system",val nightMap:Boolean=true)
data class CabinPalette(val id:String,val label:String,val background:Int,val panel:Int,val line:Int,val accent:Int,val text:Int,val muted:Int)
object AppearanceCatalog {
    val presets=listOf(
        CabinPalette("white","화이트",0xF3F5F7,0xFFFFFF,0xA4ADB7,0x175E66,0x15202B,0x465565),
        CabinPalette("black","블랙",0x000000,0x101010,0x454545,0xA3D5CB,0xF5F5F5,0xB8BEC5),
        CabinPalette("ocean","오션 블루",0x071725,0x102A3B,0x355973,0x8BCDE8,0xEDF8FC,0xADC6D2),
        CabinPalette("rose","로즈",0x21151C,0x32232C,0x674959,0xE0AEC4,0xF7ECF1,0xCDB7C2),
        CabinPalette("lavender","라벤더",0xECEAF4,0xF9F7FF,0xAAA0C4,0x624A8A,0x292335,0x61556D),
        CabinPalette("graphite","그래파이트",0x080F15,0x101C25,0x344650,0x82C5B7,0xEEF3F5,0xA9BCC5),
        CabinPalette("sand","웜 샌드",0x171411,0x24201B,0x544A3E,0xDBC29B,0xF1EBE3,0xBFB5A8),
        CabinPalette("wood","모카 우드",0x151211,0x231D1B,0x51423C,0xCFAD9B,0xF0E9E5,0xBFB1AA),
        CabinPalette("sage","세이지",0x101612,0x1B251F,0x3B5144,0xB3C6AD,0xEAF1E9,0xADBDAC),
        CabinPalette("midnight","미드나이트",0x0D111A,0x181F2D,0x37445B,0xA9BBDA,0xECF0F7,0xACB8CA),
    )
    fun normalize(s:AppearanceSettings)=s.copy(preset=presets.firstOrNull {it.id==s.preset}?.id ?: "graphite",accent=hex(s.accent)?.let {format(it)} ?: "",brightness=s.brightness.takeIf {it in listOf("system","comfort","night")} ?: "system")
    fun hex(value:String):Int?=value.trim().removePrefix("#").takeIf {it.length==6&&it.all {c->c in '0'..'9'||c.lowercaseChar() in 'a'..'f'}}?.toIntOrNull(16)
    fun format(color:Int)="#"+(color and 0xFFFFFF).toString(16).uppercase().padStart(6,'0')
    fun blend(a:Int,b:Int,t:Double):Int {
        fun channel(shift:Int)=(((a shr shift and 255)*(1-t)+(b shr shift and 255)*t).roundToInt().coerceIn(0,255)) shl shift
        return channel(16) or channel(8) or channel(0)
    }
    private fun luminance(c:Int):Double {
        fun linear(v:Int):Double {val x=v/255.0;return if(x<=.04045)x/12.92 else ((x+.055)/1.055).pow(2.4)}
        return .2126*linear(c shr 16 and 255)+.7152*linear(c shr 8 and 255)+.0722*linear(c and 255)
    }
    fun contrast(a:Int,b:Int):Double {val x=luminance(a);val y=luminance(b);return (max(x,y)+.05)/(min(x,y)+.05)}
    private fun legible(value:Int,bg:Int,button:Boolean=false):Int {
        var color=value
        val target=if(contrast(0x000000,bg)>contrast(0xFFFFFF,bg))0x000000 else 0xFFFFFF
        repeat(40) {
            if(contrast(color,bg)>=4.5&&(!button||contrast(color,blend(bg,color,.14))>=4.5))return color
            color=blend(color,target,.07)
        }
        return target
    }
    fun palette(settings:AppearanceSettings):CabinPalette {
        val s=normalize(settings);val p=presets.first {it.id==s.preset}
        val amount=when(s.brightness){"night"->.12;"comfort"->.05;else->0.0}
        fun shade(c:Int)=blend(c,0,amount)
        val panel=shade(p.panel)
        return p.copy(background=shade(p.background),panel=panel,line=shade(p.line),accent=legible(shade(hex(s.accent) ?: p.accent),panel,true),text=legible(shade(p.text),panel),muted=legible(shade(p.muted),panel))
    }
    fun windowBrightness(mode:String):Float=when(mode){"comfort"->.60f;"night"->.25f;else->-1f}
}
