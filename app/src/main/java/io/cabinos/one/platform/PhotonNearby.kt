package io.cabinos.one.platform

import io.cabinos.core.*
import org.json.JSONObject
import java.net.URLEncoder

/** Documented Photon reverse/tag search; bounded results, no invented businesses. */
object PhotonNearby {
    fun urls(origin:MapPosition):List<String> {
        require(origin.valid())
        val groups=listOf(listOf("amenity:cafe"),listOf("amenity:restaurant","amenity:fast_food"),
            listOf("leisure:park","leisure:garden","leisure:nature_reserve"),listOf("shop:mall","shop:supermarket","shop:convenience"))
        return groups.map {tags->"https://photon.komoot.io/reverse?lat=${origin.latitude}&lon=${origin.longitude}&radius=2&limit=15"+
            tags.joinToString("") {"&osm_tag="+URLEncoder.encode(it,"UTF-8")} }
    }
    fun parse(json:String,origin:MapPosition):List<NearbyActivity> {
        val features=JSONObject(json).getJSONArray("features")
        return (0 until minOf(features.length(),60)).mapNotNull {i->
            val feature=features.optJSONObject(i) ?: return@mapNotNull null
            val properties=feature.optJSONObject("properties") ?: return@mapNotNull null
            val geometry=feature.optJSONObject("geometry") ?: return@mapNotNull null
            if(geometry.optString("type")!="Point")return@mapNotNull null
            val coordinates=geometry.optJSONArray("coordinates") ?: return@mapNotNull null
            val category=when(properties.optString("osm_key")+":"+properties.optString("osm_value")) {
                "amenity:cafe"->"카페"
                "amenity:restaurant","amenity:fast_food"->"식당"
                "leisure:park","leisure:garden","leisure:nature_reserve"->"산책"
                "shop:mall","shop:supermarket","shop:convenience"->"쇼핑"
                else->return@mapNotNull null
            }
            val id=properties.optLong("osm_id",0);if(id<=0)return@mapNotNull null
            val type=when(properties.optString("osm_type")){"N"->"node";"W"->"way";"R"->"relation";else->return@mapNotNull null}
            NearbyGeometry.osmActivity("osm-$type-$id",NearbyGeometry.placeName("",properties.optString("name"),""),category,
                coordinates.optDouble(1,Double.NaN),coordinates.optDouble(0,Double.NaN),origin)?.copy(
                    description="OpenStreetMap / Photon 등록 장소 · 도보 시간은 직선거리 기반 추정 · 영업 여부 확인 필요",
                    commonsPhoto=PlacePhoto.file(properties.optJSONObject("extra")?.optString("wikimedia_commons").orEmpty()))
        }.distinctBy {it.id}.sortedBy {it.oneWaySeconds}
    }
}
