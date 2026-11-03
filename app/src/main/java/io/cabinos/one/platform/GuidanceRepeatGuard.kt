package io.cabinos.one.platform

import io.cabinos.core.DrivingRoute
import io.cabinos.core.Guidance

/** Suppress GPS/band oscillation, while allowing explicit repeat and a new route. */
internal class GuidanceRepeatGuard {
    private val recent=linkedMapOf<String,Long>()
    private var route:DrivingRoute?=null
    private var step=-1
    private var spokenBand=-1
    private fun band(g:Guidance)=when {g.arrived->3;g.turnMeters<60->2;g.turnMeters<300->1;else->0}
    fun allow(g:Guidance,now:Long):Boolean {
        if(route!==g.route || step!=g.maneuverIndex) {clear();route=g.route;step=g.maneuverIndex}
        if(band(g)<=spokenBand)return false
        // A rejected TTS request must not reserve this instruction for 30 seconds.
        recent.entries.removeAll {now-it.value>=30_000}
        return "${g.maneuverIndex}|${g.nextInstruction}|${band(g)}" !in recent
    }
    fun accepted(g:Guidance) {spokenBand=band(g)}
    fun allow(key:String,now:Long):Boolean {
        recent.entries.removeAll {now-it.value>=30_000}
        if(key in recent)return false
        recent[key]=now
        if(recent.size>32)recent.remove(recent.keys.first())
        return true
    }
    fun clear(){recent.clear();route=null;step=-1;spokenBand=-1}
}
