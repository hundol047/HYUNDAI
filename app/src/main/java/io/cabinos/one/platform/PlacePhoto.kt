package io.cabinos.one.platform

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.text.Html
import android.util.LruCache
import kotlinx.coroutines.*
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Only the place's registered Commons file; never a stock image of another business. */
object PlacePhoto {
    data class Photo(val bitmap:Bitmap,val credit:String,val source:String)
    private val cache=object:LruCache<String,Photo>(4*1024*1024) {
        override fun sizeOf(key:String,value:Photo)=value.bitmap.byteCount
    }
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
            val thumb=info.getString("thumburl");val url=URL(thumb)
            if(url.protocol!="https"||url.host!="upload.wikimedia.org"||url.userInfo!=null)return@withContext null
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
