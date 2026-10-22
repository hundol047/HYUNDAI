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

/** Only called by explicit parked UI actions. Coordinates are not persisted. */
class NearbyPlaces(private val context:Context) {
    suspend fun locate():MapPosition? {
        if(context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)!=PackageManager.PERMISSION_GRANTED &&
            context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED)return null
        val manager=context.getSystemService(LocationManager::class.java)
        return try {
            val fine=context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED
            val providers=manager.getProviders(true).filter { it!=LocationManager.PASSIVE_PROVIDER && (fine || it!=LocationManager.GPS_PROVIDER) }
            val cached=providers.mapNotNull { manager.getLastKnownLocation(it) }.filter { System.currentTimeMillis()-it.time in 0..60_000 && it.accuracy<=1000 }.maxByOrNull { it.time }
            if(cached!=null)return position(cached)
            if(providers.isEmpty())return null
            withTimeoutOrNull(12_000) {
                suspendCancellableCoroutine { continuation ->
                    val listener=object:LocationListener {
                        override fun onLocationChanged(location:Location) {
                            if(continuation.isActive && System.currentTimeMillis()-location.time in 0..60_000 && location.accuracy<=1000) {
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
    private fun position(location:Location)=MapPosition(location.latitude,location.longitude,"기기 위치 · 정확도 약 ${location.accuracy.toInt()}m")
    suspend fun search(origin:MapPosition):List<NearbyActivity> = withContext(Dispatchers.IO) {
        require(origin.valid())
        val query="[out:json][timeout:12];(nwr[amenity~\"^(cafe|restaurant|fast_food)$\"](around:1200,${origin.latitude},${origin.longitude});nwr[leisure=park](around:1200,${origin.latitude},${origin.longitude});nwr[shop~\"^(convenience|supermarket|mall)$\"](around:1200,${origin.latitude},${origin.longitude}););out center tags 60;"
        val connection=URL("https://overpass-api.de/api/interpreter?data="+URLEncoder.encode(query,"UTF-8")).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout=12_000;connection.readTimeout=18_000
            connection.setRequestProperty("User-Agent","CabinOS_ONE/0.3.0 (+https://github.com/hundol047/HYUNDAI)")
            connection.setRequestProperty("Accept","application/json")
            check(connection.responseCode==200) {"장소 검색 서버가 응답하지 않습니다 (${connection.responseCode})."}
            val bytes=connection.inputStream.use { it.readBytesLimited(2_000_000) }
            parse(String(bytes,Charsets.UTF_8),origin)
        } finally { connection.disconnect() }
    }
    companion object {
        fun parse(json:String,origin:MapPosition):List<NearbyActivity> {
            val elements=JSONObject(json).getJSONArray("elements")
            return (0 until elements.length()).mapNotNull { index ->
                val item=elements.optJSONObject(index) ?: return@mapNotNull null
                val tags=item.optJSONObject("tags") ?: return@mapNotNull null
                val name=tags.optString("name:ko").ifBlank { tags.optString("name") }
                val category=when {
                    tags.optString("amenity")=="cafe"->"카페"
                    tags.optString("amenity") in listOf("restaurant","fast_food")->"식당"
                    tags.optString("leisure")=="park"->"산책"
                    tags.optString("shop") in listOf("convenience","supermarket","mall")->"쇼핑"
                    else->return@mapNotNull null
                }
                val center=item.optJSONObject("center") ?: item
                NearbyGeometry.osmActivity("osm-${item.optString("type")}-${item.optLong("id")}",name,category,
                    center.optDouble("lat",Double.NaN),center.optDouble("lon",Double.NaN),origin)
            }.distinctBy { it.id }.take(60)
        }
    }
}
private fun java.io.InputStream.readBytesLimited(limit:Int):ByteArray {
    val output=java.io.ByteArrayOutputStream();val buffer=ByteArray(8192)
    while(true){val n=read(buffer);if(n<0)break;check(output.size()+n<=limit);output.write(buffer,0,n)}
    return output.toByteArray()
}
