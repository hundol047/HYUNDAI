package io.cabinos.core

import kotlin.math.*

/** This point is a visible reference, never a fabricated GPS fix. */
data class MapPosition(val latitude:Double=37.3948,val longitude:Double=127.1112,val label:String="판교 체험 기준점 · 내 위치 아님") {
    fun valid() = latitude.isFinite() && longitude.isFinite() && latitude in -85.0..85.0 && longitude in -180.0..180.0
}
object NearbyGeometry {
    /** Filter usable names before the provider's 60-object limit, so unnamed
     * objects cannot crowd named businesses out of the response. */
    fun query(origin:MapPosition):String {
        require(origin.valid())
        val named="[~\"^(name|name:ko|name:en)$\"~\".\"]"
        val around="(around:1200,${origin.latitude},${origin.longitude})"
        return "[out:json][timeout:12];(nwr[amenity~\"^(cafe|restaurant|fast_food)$\"]$named$around;nwr[leisure=park]$named$around;nwr[shop~\"^(convenience|supermarket|mall)$\"]$named$around;);out center tags 60;"
    }
    fun placeName(korean:String,local:String,english:String):String =
        listOf(korean,local,english).map {value->value.filter {it>=' '&&it!='\u007f'&&it !in '\u202a'..'\u202e'&&it !in '\u2066'..'\u2069'}.trim().take(100)}.firstOrNull {it.isNotBlank()} ?: ""
    fun distanceMeters(a:MapPosition,b:MapPosition):Double {
        require(a.valid() && b.valid())
        val dLat=(b.latitude-a.latitude)*PI/180;val dLon=(b.longitude-a.longitude)*PI/180
        val h=sin(dLat/2).pow(2)+cos(a.latitude*PI/180)*cos(b.latitude*PI/180)*sin(dLon/2).pow(2)
        return 6_371_000*2*asin(sqrt(h.coerceIn(0.0,1.0)))
    }
    /** Conservative straight-distance estimate, not a computed walking route. */
    fun walkingSeconds(origin:MapPosition,destination:MapPosition):Long =
        (ceil(distanceMeters(origin,destination)*1.4/1.1/60).toLong().coerceAtLeast(1))*60
    fun osmActivity(id:String,name:String,category:String,latitude:Double,longitude:Double,origin:MapPosition):NearbyActivity? {
        val point=MapPosition(latitude,longitude)
        if(category !in listOf("카페","식당","산책","쇼핑") || id.isBlank() || !origin.valid() || !point.valid() || name.isBlank() || distanceMeters(origin,point)>2000)return null
        val minimum=when(category){"식당"->1200L;"쇼핑"->900L;else->600L}
        return NearbyActivity(id.take(100),name.trim().take(100),category,walkingSeconds(origin,point),minimum,
            "OpenStreetMap 등록 장소 · 도보 시간은 직선거리 기반 추정 · 영업 여부 확인 필요",false,latitude,longitude)
    }
}
