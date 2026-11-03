package io.cabinos.one.platform

import io.cabinos.core.*

/** Memory only. Category changes reuse all categories from the same area. */
class NearbyQueryCache {
    private var attempted:MapPosition?=null
    private var attemptAt=0L
    private var failed=false
    private var saved:MapPosition?=null
    private var savedAt=0L
    private var places:List<NearbyActivity> = emptyList()
    private fun same(a:MapPosition?,b:MapPosition)=a!=null&&NearbyGeometry.distanceMeters(a,b)<=50
    fun remainingSeconds(origin:MapPosition,now:Long):Long {
        if(!same(attempted,origin) || now<attemptAt)return 0
        return ((if(failed)5000 else 30000)-(now-attemptAt)).coerceAtLeast(0).let {(it+999)/1000}
    }
    fun begin(origin:MapPosition,now:Long){attempted=origin;attemptAt=now;failed=false}
    fun fail(){failed=true}
    fun save(origin:MapPosition,values:List<NearbyActivity>,now:Long){saved=origin;savedAt=now;places=values}
    fun read(origin:MapPosition,now:Long):List<NearbyActivity>? {
        if(!same(saved,origin)||now-savedAt !in 0..120000)return null
        return places.map {place->place.copy(oneWaySeconds=NearbyGeometry.walkingSeconds(origin,MapPosition(place.latitude!!,place.longitude!!)))}
    }
    fun clear(){attempted=null;saved=null;places=emptyList()}
}
