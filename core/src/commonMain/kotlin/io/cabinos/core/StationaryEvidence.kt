package io.cabinos.core

import kotlin.math.max

/** Five seconds of fresh, continuous stationary evidence; gaps do not count. */
class StationaryEvidence {
    private var since:Long?=null
    private var receivedAt:Long?=null
    private var last:NavFix?=null
    fun reset(){since=null;receivedAt=null;last=null}
    fun accept(fix:NavFix,now:Long):Boolean {
        if(!fix.point.valid()||!fix.accuracy.isFinite()||fix.accuracy !in 0.0..50.0||now-fix.timestampMs !in 0..10_000||fix.speedMps?.let {it.isFinite()&&it in 0.0..0.5}!=true){reset();return false}
        val previous=last
        if(previous!=null&&fix.timestampMs<=previous.timestampMs)return false
        if(previous!=null&&(now-(receivedAt ?: now) !in 0..2500||fix.timestampMs-previous.timestampMs !in 1..2500||NavigationGeometry.distance(previous.point,fix.point)>max(10.0,previous.accuracy+fix.accuracy)))since=null
        if(since==null)since=now
        last=fix;receivedAt=now
        return now-(since ?: now)>=5000
    }
}
