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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Public OSM-based providers, explicit queries only. No country restriction or API key. */
open class NavigationService {
    var searchEndpoint="https://nominatim.openstreetmap.org"
        private set
    var routeEndpoint="https://router.project-osrm.org"
        private set
    @Volatile private var connection:HttpURLConnection?=null
    private var lastSearch=0L
    private var lastRoute=0L
    private val cache=linkedMapOf<String,List<NavPlace>>()
    fun configure(search:String,route:String) {
        fun valid(s:String):Boolean=runCatching {val u=URL(s);u.protocol=="https"&&u.host.isNotBlank()&&u.userInfo==null&&u.query==null&&u.ref==null}.getOrDefault(false)
        require(valid(search)&&valid(route)) {"HTTPS 서비스 기본 주소를 입력해 주세요."}
        searchEndpoint=search.trimEnd('/');routeEndpoint=route.trimEnd('/');cache.clear()
    }
    fun clearCache(){cache.clear()}
    fun cancel(){connection?.disconnect()}
    private suspend fun get(url:String):String=withContext(Dispatchers.IO) {
        val c=URL(url).openConnection() as HttpURLConnection;connection=c
        try {
            c.connectTimeout=12_000;c.readTimeout=20_000;c.instanceFollowRedirects=false
            c.setRequestProperty("User-Agent","CabinOS_ONE/0.4.0 (+https://github.com/hundol047/HYUNDAI)")
            c.setRequestProperty("Accept","application/json");c.setRequestProperty("Accept-Language","ko,en")
            check(c.responseCode==200) {if(c.responseCode==429)"서비스 요청 한도를 넘었습니다. 잠시 후 재시도하세요." else "내비게이션 서버 연결 실패 (${c.responseCode})"}
            val output=java.io.ByteArrayOutputStream();val buffer=ByteArray(8192)
            c.inputStream.use { stream ->while(true){currentCoroutineContext().ensureActive();val n=stream.read(buffer);if(n<0)break;check(output.size()+n<=12_000_000){"경로가 너무 큽니다. 가까운 경유 구간으로 나누세요."};output.write(buffer,0,n)} }
            output.toString("UTF-8")
        } finally {c.disconnect();if(connection===c)connection=null}
    }
    open suspend fun search(query:String):List<NavPlace> {
        val q=query.trim().take(180);require(q.length>=2)
        cache[q]?.let {return it}
        check(System.currentTimeMillis()-lastSearch>=1500) {"검색은 잠시 간격을 두고 실행해 주세요."}
        lastSearch=System.currentTimeMillis()
        val result=parsePlaces(get("$searchEndpoint/search?format=jsonv2&limit=6&accept-language=ko,en&q="+URLEncoder.encode(q,"UTF-8")))
        if(cache.size>=30)cache.remove(cache.keys.first());cache[q]=result;return result
    }
    open suspend fun route(origin:NavPoint,destination:NavPlace):DrivingRoute {
        require(origin.valid()&&destination.point.valid())
        require(NavigationGeometry.distance(origin,destination.point)>=20) {"목적지가 출발 위치와 너무 가깝습니다."}
        check(System.currentTimeMillis()-lastRoute>=1500) {"잠시 후 경로 계산을 다시 요청해 주세요."}
        lastRoute=System.currentTimeMillis()
        val p=destination.point
        val route=parseRoute(get("$routeEndpoint/route/v1/driving/${origin.longitude},${origin.latitude};${p.longitude},${p.latitude}?overview=full&geometries=geojson&steps=true&alternatives=false"),destination)
        check(NavigationGeometry.distance(origin,route.points.first())<=1000) {"출발 위치 근처에 연결 가능한 자동차 도로가 없습니다."}
        return route
    }
    companion object {
        fun parsePlaces(raw:String):List<NavPlace> {
            val array=JSONArray(raw)
            return (0 until minOf(6,array.length())).mapNotNull { i ->val o=array.optJSONObject(i) ?: return@mapNotNull null
                val p=NavPoint(o.optString("lat").toDoubleOrNull() ?: Double.NaN,o.optString("lon").toDoubleOrNull() ?: Double.NaN)
                val name=o.optString("display_name").take(240)
                if(p.valid()&&name.isNotBlank())NavPlace(name,p) else null
            }.distinctBy {it.point}
        }
        fun parseRoute(raw:String,destination:NavPlace):DrivingRoute {
            val json=JSONObject(raw);check(json.optString("code")=="Ok") {"자동차로 연결되는 경로를 찾지 못했습니다. 바다·국경·도로 정보를 확인하세요."}
            val route=json.getJSONArray("routes").getJSONObject(0)
            val coordinates=route.getJSONObject("geometry").getJSONArray("coordinates");require(coordinates.length()<=150_000)
            val points=(0 until coordinates.length()).map {i->val p=coordinates.getJSONArray(i);NavPoint(p.getDouble(1),p.getDouble(0))}
            val legs=route.getJSONArray("legs");val maneuvers=mutableListOf<NavManeuver>()
            for(i in 0 until legs.length()) {
                val steps=legs.getJSONObject(i).getJSONArray("steps")
                for(j in 0 until steps.length()) {require(maneuvers.size<10_000);val step=steps.getJSONObject(j);val m=step.getJSONObject("maneuver");val p=m.getJSONArray("location")
                    maneuvers.add(NavManeuver(NavPoint(p.getDouble(1),p.getDouble(0)),m.getString("type"),m.optString("modifier"),step.optString("name"),if(m.has("exit"))m.getInt("exit") else null))
                }
            }
            return NavigationGeometry.route(destination,points,route.getDouble("distance"),route.getDouble("duration"),maneuvers)
        }
    }
}

data class NavigationUi(val guidance:Guidance=Guidance(),val places:List<NavPlace> = emptyList(),val busy:Boolean=false,val notice:String="목적지를 검색하거나 위도,경도를 입력하세요.")

/** Foreground-only location: no background permission/service and no stored GPS history. */
class LiveNavigation(private val context:Context,private val engine:UnifiedVehicleContextEngine,private val speech:AndroidSpeechProvider,val service:NavigationService=NavigationService()) {
    private val tracker=NavigationTracker()
    private val mutable=MutableStateFlow(NavigationUi());val state=mutable.asStateFlow()
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    private val manager=context.getSystemService(LocationManager::class.java)
    private var job:Job?=null;private var generation=0;private var foreground=true;private var listening=false
    private var safetyHeld=false;private var stationarySince:Long?=null
    private var fix:NavFix?=null;private var spoken="";private var rerouteFailures=0
    private val listener=object:LocationListener {
        override fun onLocationChanged(location:Location) {
            val f=NavFix(NavPoint(location.latitude,location.longitude),if(location.hasAccuracy())location.accuracy.toDouble() else Double.POSITIVE_INFINITY,location.time,if(location.hasSpeed())location.speed.toDouble() else null)
            if(!tracker.usable(f)){if(tracker.state.active){tracker.update(f);speech.stopSpeaking();publish()};return}
            fix=f
            if(f.speedMps?.let {it>=2}==true){safetyHeld=true;stationarySince=null;engine.setNavigationLock(true)}
            if(safetyHeld&&!tracker.state.active) {
                if(f.speedMps?.let {it in 0.0..0.5}==true) {
                    if(stationarySince==null)stationarySince=System.currentTimeMillis()
                    if(System.currentTimeMillis()-(stationarySince ?: System.currentTimeMillis())>=5000){safetyHeld=false;engine.setNavigationLock(false)}
                } else stationarySince=null
            }
            if(tracker.state.active){tracker.update(f);publish();guide();reroute()}
            else mutable.value=mutable.value.copy(notice="GPS 위치 확인 · 정확도 약 ${f.accuracy.toInt()}m")
        }
        override fun onProviderDisabled(provider:String){if(tracker.state.active){tracker.suspendTracking();speech.stopSpeaking();publish()}}
    }
    private fun publish(){mutable.value=mutable.value.copy(guidance=tracker.state)}
    private fun cancelRequest(){generation++;job?.cancel();service.cancel();mutable.value=mutable.value.copy(busy=false)}
    private fun request(block:suspend ()->Unit) {
        cancelRequest();val id=generation;mutable.value=mutable.value.copy(busy=true)
        job=scope.launch {
            try {block()} catch(e:Exception){if(e is CancellationException)throw e;if(id==generation)mutable.value=mutable.value.copy(notice=e.message ?: "연결할 수 없습니다. 네트워크를 확인해 주세요.")}
            finally {if(id==generation)mutable.value=mutable.value.copy(busy=false)}
        }
    }
    fun canPlan()=foreground&&engine.state.value.parkedAllowed&&!tracker.state.active
    fun locate() {
        if(!canPlan()&&!tracker.state.active&&!safetyHeld)return
        if(context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED){mutable.value=mutable.value.copy(notice="정확한 위치 권한이 필요합니다. 대략적 위치로는 주행 안내를 시작하지 않습니다.");return}
        if(listening)return
        try {
            val providers=manager.getProviders(true).filter {it==LocationManager.GPS_PROVIDER||it==LocationManager.NETWORK_PROVIDER}
            if(providers.isEmpty()){mutable.value=mutable.value.copy(notice="기기 위치 서비스를 켜 주세요.");return}
            providers.forEach {manager.requestLocationUpdates(it,1000L,1f,listener,Looper.getMainLooper())};listening=true
            mutable.value=mutable.value.copy(notice="GPS 수신 중 · 야외에서 정확한 위치를 기다려 주세요.")
        } catch(_:SecurityException){mutable.value=mutable.value.copy(notice="위치 권한을 확인해 주세요.")}
    }
    fun search(query:String) {
        if(!canPlan()||!engine.state.value.networkAvailable)return
        val coordinates=query.split(',').map {it.trim().toDoubleOrNull()}
        if(coordinates.size==2&&coordinates.all {it!=null}) {
            val p=NavPoint(coordinates[0]!!,coordinates[1]!!)
            mutable.value=mutable.value.copy(places=if(p.valid())listOf(NavPlace("좌표 목적지 ${p.latitude}, ${p.longitude}",p)) else emptyList(),notice="좌표는 위도,경도 순서입니다.");return
        }
        request {val result=service.search(query);if(canPlan())mutable.value=mutable.value.copy(places=result,notice=if(result.isEmpty())"검색 결과가 없습니다. 국가·도시·주소를 함께 입력해 보세요." else "목적지를 선택해 자동차 경로를 계산하세요.")}
    }
    fun plan(destination:NavPlace) {
        if(!canPlan()||!engine.state.value.networkAvailable)return
        val f=fix
        if(f==null||!tracker.usable(f)){mutable.value=mutable.value.copy(notice="먼저 내 위치를 사용해 정확한 GPS 위치를 받아 주세요.");return}
        request {val r=service.route(f.point,destination);if(canPlan()){tracker.preview(r);publish();mutable.value=mutable.value.copy(notice="경로를 확인한 뒤 안내 시작을 누르세요. 실제 교통·차선 정보는 제공하지 않습니다.")}}
    }
    fun start() {
        if(!canPlan())return
        if(context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED){fix=null;mutable.value=mutable.value.copy(notice="정확한 위치 권한을 확인해 주세요.");return}
        val f=fix
        if(f==null||!tracker.start(f)){mutable.value=mutable.value.copy(notice="출발 위치의 정확한 GPS가 필요합니다. 이동했다면 경로를 다시 계산하세요.");return}
        cancelRequest();safetyHeld=true;stationarySince=null;engine.setNavigationLock(true);publish();spoken="";rerouteFailures=0;guide()
    }
    private fun guide() {
        val g=tracker.state
        if(!g.tracking||g.offRoute||!engine.state.value.preferences.voiceEnabled){speech.stopSpeaking();return}
        val band=when{g.arrived->"arrived";g.turnMeters<60->"now";g.turnMeters<300->"soon";else->"ahead"}
        val key="${g.nextInstruction}|$band"
        if(key!=spoken){spoken=key;speech.speak(if(g.arrived)g.nextInstruction else "${g.turnMeters.toInt()}미터 앞, ${g.nextInstruction}")}
    }
    private fun reroute() {
        if(!foreground||!engine.state.value.networkAvailable||mutable.value.busy||rerouteFailures>=3||!tracker.takeReroute())return
        val g=tracker.state;val destination=g.route?.destination ?: return;val origin=g.fix?.point ?: return
        request {
            try {val r=service.route(origin,destination);if(foreground&&tracker.state.active&&tracker.replaceRoute(r)){spoken="";rerouteFailures=0;publish();guide();mutable.value=mutable.value.copy(notice="경로를 다시 계산했습니다.")}}
            catch(e:Exception){if(e !is CancellationException)rerouteFailures++;throw e}
        }
    }
    fun tick(){val wasTracking=tracker.state.tracking;tracker.tick();if(wasTracking&&!tracker.state.tracking)speech.stopSpeaking();publish();if(tracker.state.active)reroute()}
    fun configure(search:String,route:String){if(canPlan()){cancelRequest();service.configure(search,route)}}
    fun retry(){rerouteFailures=0;reroute()}
    fun stop(){cancelRequest();speech.stopSpeaking();tracker.stop();if(!safetyHeld){engine.setNavigationLock(false);fix=null;stopLocation()};publish();mutable.value=mutable.value.copy(places=emptyList(),notice="안내를 종료했습니다. 정차 상태를 다시 확인합니다.")}
    private fun stopLocation(){runCatching {manager.removeUpdates(listener)};listening=false}
    fun setForeground(active:Boolean){foreground=active;if(!active){cancelRequest();stopLocation();fix=null;tracker.suspendTracking();publish()}else if(tracker.state.active||safetyHeld)locate()}
    fun clear(){stop();service.clearCache()}
    fun close(){cancelRequest();stopLocation();scope.cancel()}
}
