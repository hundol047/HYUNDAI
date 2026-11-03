package io.cabinos.one.platform

import io.cabinos.core.*
import kotlinx.coroutines.CancellationException
import org.json.JSONObject
import java.net.URLEncoder

/** Each source can fail independently. A file reference is not a successfully loaded photo. */
internal class PlacePhotoResolver<T:Any>(
    private val read:suspend (String)->String,
    private val image:suspend (String)->T?,
) {
    data class Result<T>(val file:String,val photo:T)
    private suspend fun <R> optional(block:suspend ()->R):R?=try {block()}
        catch(error:Exception){if(error is CancellationException)throw error;null}

    suspend fun resolve(place:NearbyActivity):Result<T>? {
        if(place.simulated)return null
        val tried=mutableSetOf<String>()
        suspend fun load(file:String?):Result<T>? {
            if(file==null||!tried.add(file))return null
            return optional {image(file)}?.let {Result(file,it)}
        }
        load(place.commonsPhoto)?.let {return it}
        val endpoint=PlacePhotoReference.osmUrl(place.id) ?: return null
        val tags=optional {PlacePhotoReference.tags(read(endpoint),place.id)} ?: JSONObject()
        load(PlacePhotoReference.commons(tags))?.let {return it}
        val point=NavPoint(place.latitude ?: return null,place.longitude ?: return null)
        if(!point.valid())return null
        PlacePhotoReference.wikidata(tags)?.let {id->
            val file=optional {PlacePhotoReference.wikidataFile(read("https://www.wikidata.org/w/api.php?action=wbgetentities&ids=$id&props=claims&format=json"),id,point)}
            load(file)?.let {return it}
        }
        PlacePhotoReference.wikipediaUrl(tags)?.let {url->
            load(optional {PlacePhotoReference.wikipediaFile(read(url),point)})?.let {return it}
        }
        val query=URLEncoder.encode(place.name,"UTF-8")
        val language=if(place.name.any {it in '가'..'힣'})"ko" else "en"
        val ids=optional {PlacePhotoReference.candidates(read("https://www.wikidata.org/w/api.php?action=wbsearchentities&format=json&language=$language&limit=3&search=$query"))}.orEmpty()
        if(ids.isEmpty())return null
        val entities=optional {read("https://www.wikidata.org/w/api.php?action=wbgetentities&format=json&props=claims%7Clabels%7Caliases&ids="+ids.joinToString("%7C"))} ?: return null
        for(id in ids)load(optional {PlacePhotoReference.namedFile(entities,id,place.name,point)})?.let {return it}
        return null
    }
}
