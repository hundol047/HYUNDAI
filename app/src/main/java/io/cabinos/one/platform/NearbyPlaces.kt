package io.cabinos.one.platform

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import io.cabinos.core.*
import kotlinx.coroutines.*
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.coroutines.resume

data class NearbySearchResult(val places:List<NearbyActivity>,val notice:String,val complete:Boolean=true)

/** Only called by explicit parked UI actions. Coordinates are not persisted. */
class NearbyPlaces(private val context:Context) {
    @Volatile private var activeConnection:HttpURLConnection?=null
    fun cancel(){activeConnection?.disconnect()}
    suspend fun locate():MapPosition? {
        if(context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)!=PackageManager.PERMISSION_GRANTED &&
            context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED)return null
        val manager=context.getSystemService(LocationManager::class.java)
        return try {
            val fine=context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED
            val providers=manager.getProviders(true).filter { it!=LocationManager.PASSIVE_PROVIDER && (fine || it!=LocationManager.GPS_PROVIDER) }
            val cached=providers.mapNotNull { manager.getLastKnownLocation(it) }.filter { usableLocation(it) }.minByOrNull { it.accuracy }
            if(cached!=null)return position(cached)
            if(providers.isEmpty())return null
            withTimeoutOrNull(25_000) {
                suspendCancellableCoroutine { continuation ->
                    val listener=object:LocationListener {
                        override fun onLocationChanged(location:Location) {
                            if(continuation.isActive && usableLocation(location)) {
                                runCatching {manager.removeUpdates(this)};continuation.resume(position(location))
                            }
                        }
                    }
                    continuation.invokeOnCancellation { runCatching {manager.removeUpdates(listener)} }
                    try { providers.forEach { manager.requestSingleUpdate(it,listener,Looper.getMainLooper()) } }
                    catch(_:SecurityException) { manager.removeUpdates(listener);if(continuation.isActive)continuation.resume(null) }
                }
            }
        } catch(_:SecurityException) { null }
    }
    internal fun usableLocation(location:Location,now:Long=System.currentTimeMillis())=
        location.hasAccuracy()&&location.accuracy.isFinite()&&location.accuracy in 0f..50f&&
        now-location.time in 0..10_000&&location.latitude.isFinite()&&location.latitude in -90.0..90.0&&
        location.longitude.isFinite()&&location.longitude in -180.0..180.0
    private fun position(location:Location)=MapPosition(location.latitude,location.longitude,"기기 위치 · 정확도 약 ${location.accuracy.toInt()}m")
    suspend fun search(origin:MapPosition):NearbySearchResult {
        val urls=PhotonNearby.urls(origin)
        return NearbySearchCoordinator.search(primary={index->
            if(index>0)delay(1100)
            requestPhoton(urls[index],origin)
        },fallback={categories->
            var result:List<NearbyActivity>?=null
            var last:java.io.IOException?=null
            for(endpoint in listOf("https://overpass-api.de/api/interpreter","https://overpass.kumi.systems/api/interpreter")) {
                currentCoroutineContext().ensureActive()
                try {result=request(origin,endpoint,categories);break}catch(error:java.io.IOException){last=error}
            }
            result ?: throw (last ?: java.io.IOException("주변 검색 서버에 연결할 수 없습니다."))
        })
    }
    private suspend fun requestPhoton(url:String,origin:MapPosition):List<NearbyActivity> = withContext(Dispatchers.IO) {
        val connection=URL(url).openConnection() as HttpURLConnection;activeConnection=connection
        val deadline=launch(Dispatchers.Default) {try{delay(12000)}finally{connection.disconnect()}}
        try {
            connection.connectTimeout=5000;connection.readTimeout=8000
            connection.setRequestProperty("User-Agent","CabinOS_ONE/${io.cabinos.one.BuildConfig.VERSION_NAME} (FutureLabs; Android)")
            connection.setRequestProperty("Accept","application/json")
            if(connection.responseCode==429)throw java.io.IOException("장소 검색 서비스의 요청 한도입니다. 잠시 후 다시 시도하세요.")
            if(connection.responseCode!=200)throw java.io.IOException("Photon 장소 검색 오류 (${connection.responseCode})")
            val bytes=connection.inputStream.use {it.readBytesLimited(2_000_000)}
            currentCoroutineContext().ensureActive()
            try {PhotonNearby.parse(String(bytes,Charsets.UTF_8),origin)}catch(error:org.json.JSONException){throw java.io.IOException("장소 검색 응답 오류",error)}
        }finally{deadline.cancel();connection.disconnect();if(activeConnection===connection)activeConnection=null}
    }
    private suspend fun request(origin:MapPosition,endpoint:String,categories:Set<String>):List<NearbyActivity> = withContext(Dispatchers.IO) {
        require(origin.valid())
        val query=NearbyGeometry.query(origin,categories)
        val connection=URL(endpoint).openConnection() as HttpURLConnection
        activeConnection=connection
        val deadline=launch(Dispatchers.Default) {try{delay(20_000)}finally{connection.disconnect()}}
        try {
            // The provider may queue a 12-second query before executing it.
            connection.connectTimeout=6_000;connection.readTimeout=18_000
            connection.requestMethod="POST";connection.doOutput=true
            connection.setRequestProperty("Content-Type","application/x-www-form-urlencoded;charset=UTF-8")
            connection.setRequestProperty("User-Agent","CabinOS_ONE/${io.cabinos.one.BuildConfig.VERSION_NAME} (FutureLabs; Android)")
            connection.setRequestProperty("Accept","application/json")
            currentCoroutineContext().ensureActive()
            val form=("data="+URLEncoder.encode(query,"UTF-8")).toByteArray(Charsets.UTF_8)
            connection.setFixedLengthStreamingMode(form.size);connection.outputStream.use {it.write(form)}
            if(connection.responseCode>=500)throw java.io.IOException("주변 검색 서버 일시 오류")
            if(connection.responseCode!=200)throw java.io.IOException("장소 검색 서버가 응답하지 않습니다 (${connection.responseCode}).")
            val bytes=connection.inputStream.use { it.readBytesLimited(2_000_000) }
            try {parse(String(bytes,Charsets.UTF_8),origin)}
            catch(error:org.json.JSONException){throw java.io.IOException("주변 검색 응답 오류",error)}
            catch(error:IllegalStateException){throw java.io.IOException("주변 검색 미완료",error)}
        } finally { deadline.cancel();connection.disconnect();if(activeConnection===connection)activeConnection=null }
    }
    companion object {
        fun parse(json:String,origin:MapPosition):List<NearbyActivity> {
            val response=JSONObject(json)
            check(response.optString("remark").isBlank()) {"주변 검색 서버가 검색을 완료하지 못했습니다. 기존 표시를 유지합니다."}
            val elements=response.getJSONArray("elements")
            return (0 until elements.length()).mapNotNull { index ->
                val item=elements.optJSONObject(index) ?: return@mapNotNull null
                val tags=item.optJSONObject("tags") ?: return@mapNotNull null
                val name=NearbyGeometry.placeName(tags.keys().asSequence().associateWith {tags.opt(it) as? String ?: ""})
                val category=when {
                    tags.optString("amenity")=="cafe"->"카페"
                    tags.optString("amenity") in listOf("restaurant","fast_food")->"식당"
                    tags.optString("leisure") in listOf("park","garden","nature_reserve")->"산책"
                    tags.optString("shop") in listOf("convenience","supermarket","mall")->"쇼핑"
                    else->return@mapNotNull null
                }
                val center=item.optJSONObject("center") ?: item
                NearbyGeometry.osmActivity("osm-${item.optString("type")}-${item.optLong("id")}",name,category,
                    center.optDouble("lat",Double.NaN),center.optDouble("lon",Double.NaN),origin)?.copy(commonsPhoto=PlacePhotoReference.commons(tags))
            }.distinctBy { it.id }.groupBy {it.category}.values.flatMap {places->
                places.sortedBy {NearbyGeometry.distanceMeters(origin,MapPosition(it.latitude!!,it.longitude!!))}.take(15)
            }.sortedBy {it.oneWaySeconds}
        }
    }
}
private fun java.io.InputStream.readBytesLimited(limit:Int):ByteArray {
    val output=java.io.ByteArrayOutputStream();val buffer=ByteArray(8192)
    while(true){val n=read(buffer);if(n<0)break;check(output.size()+n<=limit);output.write(buffer,0,n)}
    return output.toByteArray()
}
