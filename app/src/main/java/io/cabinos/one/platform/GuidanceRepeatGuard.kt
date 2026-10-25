package io.cabinos.one.platform

/** Suppress GPS/band oscillation, while allowing explicit repeat and a new route. */
internal class GuidanceRepeatGuard {
    private val recent=linkedMapOf<String,Long>()
    fun allow(key:String,now:Long):Boolean {
        recent.entries.removeAll {now-it.value>=30_000}
        if(key in recent)return false
        recent[key]=now
        if(recent.size>32)recent.remove(recent.keys.first())
        return true
    }
    fun clear(){recent.clear()}
}
