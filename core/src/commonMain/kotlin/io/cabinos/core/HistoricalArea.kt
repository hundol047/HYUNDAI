package io.cabinos.core

import kotlin.math.abs

/** GeoJSON components with holes. Statistical areas are not real-time road incidents. */
data class HistoricalArea(val polygons: List<List<List<NavPoint>>>) {
    fun valid():Boolean = polygons.size in 1..32 && polygons.all { p ->
        p.size in 1..32 && p.all { r -> r.size>=4 && r.first()==r.last() && r.all {it.valid()} }
    } && polygons.sumOf {p -> p.sumOf {it.size}}<=2048

    private fun inRing(point:NavPoint,ring:List<NavPoint>):Boolean {
        var inside=false
        for(i in 0 until ring.lastIndex) {
            val a=ring[i];val b=ring[i+1]
            if((a.latitude>point.latitude)!=(b.latitude>point.latitude) &&
                point.longitude<(b.longitude-a.longitude)*(point.latitude-a.latitude)/(b.latitude-a.latitude)+a.longitude)inside=!inside
        }
        return inside
    }
    fun contains(point:NavPoint):Boolean = polygons.any { p -> inRing(point,p.first()) && p.drop(1).none {inRing(point,it)} }

    fun ahead(route:DrivingRoute,progress:Double,horizon:Double=700.0):Double? {
        if(!valid() || !progress.isFinite() || progress<0)return null
        val startIndex=route.cumulative.binarySearch(progress).let {if(it>=0)it else -it-2}.coerceAtLeast(0)
        for(i in startIndex until route.points.lastIndex) {
            if(route.cumulative[i]>progress+horizon)break
            val length=route.cumulative[i+1]-route.cumulative[i]
            if(length<=0 || route.cumulative[i+1]<progress)continue
            val a=route.points[i];val b=route.points[i+1]
            val lo=((progress-route.cumulative[i])/length).coerceIn(0.0,1.0)
            val hi=((progress+horizon-route.cumulative[i])/length).coerceIn(0.0,1.0)
            fun at(t:Double)=NavPoint(a.latitude+(b.latitude-a.latitude)*t,a.longitude+(b.longitude-a.longitude)*t)
            if(contains(at(lo)))return (route.cumulative[i]+lo*length-progress).coerceAtLeast(0.0)
            val cuts=mutableListOf(lo,hi)
            val dx=b.longitude-a.longitude;val dy=b.latitude-a.latitude
            for(poly in polygons)for(ring in poly)for(j in 0 until ring.lastIndex) {
                val c=ring[j];val d=ring[j+1]
                val ex=d.longitude-c.longitude;val ey=d.latitude-c.latitude
                val determinant=dx*ey-dy*ex
                if(abs(determinant)<1e-15)continue
                val cx=c.longitude-a.longitude;val cy=c.latitude-a.latitude
                val t=(cx*ey-cy*ex)/determinant;val u=(cx*dy-cy*dx)/determinant
                if(t in lo..hi && u in 0.0..1.0)cuts.add(t)
            }
            val sorted=cuts.distinct().sorted()
            for(j in 0 until sorted.lastIndex)if(contains(at((sorted[j]+sorted[j+1])/2)))
                return (route.cumulative[i]+sorted[j]*length-progress).coerceAtLeast(0.0)
        }
        return null
    }
}
