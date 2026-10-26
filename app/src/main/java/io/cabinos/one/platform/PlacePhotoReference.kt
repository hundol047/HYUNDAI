package io.cabinos.one.platform

import io.cabinos.core.*
import org.json.JSONObject
import java.net.URI
import java.net.URLDecoder

/** Resolve registered references or a named physical place with matching coordinates. */
object PlacePhotoReference {
    fun thumbnailUrl(value:String):Boolean=runCatching {
        val uri=URI(value)
        uri.scheme=="https"&&uri.host in setOf("upload.wikimedia.org","thumb.wikimedia.org")&&
            uri.userInfo==null&&(uri.port==-1||uri.port==443)&&uri.path.startsWith("/wikipedia/commons/")
    }.getOrDefault(false)

    fun wikipediaUrl(tags:JSONObject):String? {
        val raw=tags.optString("wikipedia");val language=raw.substringBefore(':')
        val title=raw.substringAfter(':',"")
        if(!Regex("[a-z]{2,3}(-[a-z]{2,12})?").matches(language)||title.isBlank()||title.length>240)return null
        return "https://$language.wikipedia.org/w/api.php?action=query&format=json&redirects=1&prop=pageimages%7Ccoordinates&piprop=name&titles="+java.net.URLEncoder.encode(title,"UTF-8")
    }
    fun wikipediaFile(json:String,point:NavPoint):String? {
        val pages=JSONObject(json).optJSONObject("query")?.optJSONObject("pages") ?: return null
        for(key in pages.keys()) {
            val page=pages.getJSONObject(key)
            val coordinates=page.optJSONArray("coordinates") ?: continue
            val nearby=(0 until coordinates.length()).any {i->
                val c=coordinates.getJSONObject(i)
                val location=NavPoint(c.optDouble("lat",Double.NaN),c.optDouble("lon",Double.NaN))
                c.optString("globe")=="earth"&&point.valid()&&location.valid()&&NavigationGeometry.distance(point,location)<=300
            }
            if(nearby)PlacePhoto.file("File:"+page.optString("pageimage"))?.let {return it}
        }
        return null
    }
    fun candidates(json:String):List<String> {
        val results=JSONObject(json).optJSONArray("search") ?: return emptyList()
        return (0 until minOf(3,results.length())).mapNotNull {i->
            results.optJSONObject(i)?.optString("id")?.takeIf {Regex("Q[1-9][0-9]{0,15}").matches(it)}
        }.distinct()
    }
    fun namedFile(json:String,id:String,name:String,point:NavPoint):String? {
        val entity=JSONObject(json).optJSONObject("entities")?.optJSONObject(id) ?: return null
        val claims=entity.optJSONObject("claims") ?: return null
        // A business brand, company HQ, film or work of art is not a photo of this branch.
        val physical=setOf("Q30022","Q11707","Q22698","Q11315","Q187456","Q483110","Q46169","Q43501","Q22746","Q33506")
        val types=claims.optJSONArray("P31") ?: return null
        if(!(0 until types.length()).any {i->types.optJSONObject(i)?.optJSONObject("mainsnak")?.optJSONObject("datavalue")?.optJSONObject("value")?.optString("id") in physical})return null
        fun normalized(text:String)=java.text.Normalizer.normalize(text,java.text.Normalizer.Form.NFKD)
            .lowercase(java.util.Locale.ROOT).filter {it.isLetterOrDigit()}
        val target=normalized(name)
        if(target.length<3)return null
        val labels=entity.optJSONObject("labels") ?: return null
        val matches=labels.keys().asSequence().any {normalized(labels.getJSONObject(it).optString("value"))==target} ||
            entity.optJSONObject("aliases")?.let {aliases->aliases.keys().asSequence().any {language->
                val values=aliases.getJSONArray(language)
                (0 until values.length()).any {normalized(values.getJSONObject(it).optString("value"))==target}
            }}==true
        return if(matches)wikidataFile(json,id,point) else null
    }
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
