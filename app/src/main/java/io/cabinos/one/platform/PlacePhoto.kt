package io.cabinos.one.platform

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.text.Html
import android.util.LruCache
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import io.cabinos.core.*
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Place-specific, attributed Commons photos; never a stock photo of another business. */
object PlacePhoto {
    data class Photo(val bitmap:Bitmap,val credit:String,val source:String)
    private val cache=object:LruCache<String,Photo>(4*1024*1024) {
        override fun sizeOf(key:String,value:Photo)=value.bitmap.byteCount
    }
    private data class Reference(val at:Long,val file:String?)
    private val references=LruCache<String,Reference>(128)
    private val permits=Semaphore(2)
    suspend fun forPlace(place:NearbyActivity):Photo? {
        if(place.simulated)return null
        place.commonsPhoto?.let {load(it)?.let {photo->return photo}}
        val endpoint=PlacePhotoReference.osmUrl(place.id) ?: return null
        val key="${place.id}|${place.name}|${place.latitude}|${place.longitude}"
        return permits.withPermit {
            val previous=references.get(key)
            if(previous!=null&&System.currentTimeMillis()-previous.at in 0..600000)return@withPermit previous.file?.let {load(it)}
            withContext(Dispatchers.IO) {
                try {
                    delay(500)
                    val tags=try {PlacePhotoReference.tags(String(download(endpoint,700000)),place.id) ?: JSONObject()}
                    catch(error:Exception){if(error is CancellationException)throw error;JSONObject()}
                    var file=PlacePhotoReference.commons(tags)
                    if(file==null&&place.latitude!=null&&place.longitude!=null) {
                        val id=PlacePhotoReference.wikidata(tags)
                        if(id!=null)file=PlacePhotoReference.wikidataFile(String(download("https://www.wikidata.org/w/api.php?action=wbgetentities&ids=$id&props=claims&format=json",700000)),id,NavPoint(place.latitude!!,place.longitude!!))
                    }
                    if(file==null&&place.latitude!=null&&place.longitude!=null) {
                        val point=NavPoint(place.latitude!!,place.longitude!!)
                        PlacePhotoReference.wikipediaUrl(tags)?.let {url->
                            file=PlacePhotoReference.wikipediaFile(String(download(url,200000)),point)
                        }
                        if(file==null) {
                            val query=URLEncoder.encode(place.name,"UTF-8")
                            val language=if(place.name.any {it in '가'..'힣'})"ko" else "en"
                            val ids=PlacePhotoReference.candidates(String(download("https://www.wikidata.org/w/api.php?action=wbsearchentities&format=json&language=$language&limit=3&search=$query",200000)))
                            if(ids.isNotEmpty()) {
                                val entities=String(download("https://www.wikidata.org/w/api.php?action=wbgetentities&format=json&props=claims%7Clabels%7Caliases&ids="+ids.joinToString("%7C"),700000))
                                file=ids.firstNotNullOfOrNull {id->PlacePhotoReference.namedFile(entities,id,place.name,point)}
                            }
                        }
                    }
                    references.put(key,Reference(System.currentTimeMillis(),file))
                    file?.let {load(it)}
                }catch(error:Exception){if(error is CancellationException)throw error;null}
            }
        }
    }
    fun clear(){cache.evictAll();references.evictAll()}
    fun file(value:String):String?=value.takeIf {
        it.startsWith("File:") && it.length in 7..240 && it.none {c->c<' '||c=='/'||c=='\\'} &&
            it.substringAfterLast('.').lowercase() in listOf("jpg","jpeg","png","webp")
    }
    suspend fun load(value:String):Photo?=withContext(Dispatchers.IO) {
        val name=file(value) ?: return@withContext null
        cache.get(name)?.let {return@withContext it}
        try {
            val encoded=URLEncoder.encode(name,"UTF-8")
            val json=JSONObject(String(download("https://commons.wikimedia.org/w/api.php?action=query&format=json&prop=imageinfo&iiprop=url%7Cextmetadata&iiurlwidth=320&titles=$encoded",200_000)))
            val pages=json.getJSONObject("query").getJSONObject("pages")
            val info=pages.getJSONObject(pages.keys().next()).getJSONArray("imageinfo").getJSONObject(0)
            val thumb=info.getString("thumburl")
            if(!PlacePhotoReference.thumbnailUrl(thumb))return@withContext null
            val bytes=download(thumb,1_500_000)
            val bounds=BitmapFactory.Options().apply {inJustDecodeBounds=true}
            BitmapFactory.decodeByteArray(bytes,0,bytes.size,bounds)
            if(bounds.outWidth !in 1..4096||bounds.outHeight !in 1..4096)return@withContext null
            val options=BitmapFactory.Options().apply {inSampleSize=(maxOf(bounds.outWidth,bounds.outHeight)/320).coerceAtLeast(1)}
            val bitmap=BitmapFactory.decodeByteArray(bytes,0,bytes.size,options) ?: return@withContext null
            val metadata=info.getJSONObject("extmetadata")
            fun field(key:String)=Html.fromHtml(metadata.optJSONObject(key)?.optString("value").orEmpty(),Html.FROM_HTML_MODE_LEGACY).toString().trim().take(120)
            val artist=field("Artist");val license=field("LicenseShortName")
            if(artist.isBlank()||license.isBlank())return@withContext null
            Photo(bitmap,"$artist · $license","https://commons.wikimedia.org/wiki/${android.net.Uri.encode(name)}").also {cache.put(name,it)}
        } catch(error:Exception){if(error is CancellationException)throw error;null}
    }
    private suspend fun download(url:String,limit:Int):ByteArray {
        val connection=URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout=5000;connection.readTimeout=7000;connection.instanceFollowRedirects=false
            connection.setRequestProperty("User-Agent","CabinOS_ONE/${io.cabinos.one.BuildConfig.VERSION_NAME} (FutureLabs; Android)")
            check(connection.responseCode==200)
            val output=java.io.ByteArrayOutputStream()
            connection.inputStream.use {input->val buffer=ByteArray(8192);while(true){currentCoroutineContext().ensureActive();val count=input.read(buffer);if(count<0)break;check(output.size()+count<=limit);output.write(buffer,0,count)}}
            return output.toByteArray()
        }finally{connection.disconnect()}
    }
}
