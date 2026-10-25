package io.cabinos.one.platform

import io.cabinos.core.*
import org.json.JSONObject
import java.net.URI
import java.net.URLDecoder

/** Resolve only references attached to this OSM object, never a same-name/brand search. */
object PlacePhotoReference {
    fun osmUrl(id:String):String? {
        val match=Regex("osm-(node|way|relation)-([1-9][0-9]{0,18})").matchEntire(id) ?: return null
        return "https://api.openstreetmap.org/api/0.6/${match.groupValues[1]}/${match.groupValues[2]}.json"
    }
    fun tags(json:String,id:String):JSONObject? {
        if(osmUrl(id)==null)return null
        val parts=id.split('-');val elements=JSONObject(json).getJSONArray("elements")
        for(i in 0 until elements.length()) {
            val element=elements.getJSONObject(i)
            if(element.optString("type")==parts[1]&&element.optString("id")==parts[2])return element.optJSONObject("tags")
        }
        return null
    }
    fun commons(tags:JSONObject):String? {
        PlacePhoto.file(tags.optString("wikimedia_commons"))?.let {return it}
        val raw=tags.optString("image")
        PlacePhoto.file(raw)?.let {return it}
        return runCatching {
            val uri=URI(raw)
            if(uri.scheme!="https"||uri.userInfo!=null)return@runCatching null
            val path=uri.rawPath ?: return@runCatching null
            val file=when {
                uri.host=="commons.wikimedia.org"&&path.startsWith("/wiki/File:")->path.removePrefix("/wiki/")
                uri.host=="upload.wikimedia.org"&&path.startsWith("/wikipedia/commons/")&&!path.contains("/thumb/")->"File:"+path.substringAfterLast('/')
                else->return@runCatching null
            }
            PlacePhoto.file(URLDecoder.decode(file.replace("+","%2B"),"UTF-8"))
        }.getOrNull()
    }
    fun wikidata(tags:JSONObject):String?=tags.optString("wikidata").takeIf {Regex("Q[1-9][0-9]{0,15}").matches(it)}
    fun wikidataFile(json:String,id:String,point:NavPoint):String? {
        val claims=JSONObject(json).getJSONObject("entities").optJSONObject(id)?.optJSONObject("claims") ?: return null
        val coordinates=claims.optJSONArray("P625") ?: return null
        val located=(0 until coordinates.length()).any {i->
            val claim=coordinates.optJSONObject(i) ?: return@any false
            if(claim.optString("rank")=="deprecated")return@any false
            val value=claim.optJSONObject("mainsnak")?.optJSONObject("datavalue")?.optJSONObject("value") ?: return@any false
            val location=NavPoint(value.optDouble("latitude",Double.NaN),value.optDouble("longitude",Double.NaN))
            point.valid()&&location.valid()&&NavigationGeometry.distance(point,location)<=300
        }
        if(!located)return null
        val images=claims.optJSONArray("P18") ?: return null
        for(i in 0 until images.length()) {
            val claim=images.optJSONObject(i) ?: continue
            if(claim.optString("rank")=="deprecated")continue
            val name=claim.optJSONObject("mainsnak")?.optJSONObject("datavalue")?.optString("value").orEmpty()
            PlacePhoto.file("File:$name")?.let {return it}
        }
        return null
    }
}
