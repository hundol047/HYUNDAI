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
    private var searchAccessToken=""
    private var lastSearch=0L
    private var lastRoute=0L
    private val cache=linkedMapOf<String,PlaceSearchPage>()
    fun configure(search:String,route:String,accessToken:String="") {
        fun valid(s:String):Boolean=runCatching {val u=URL(s);u.protocol=="https"&&u.host.isNotBlank()&&u.userInfo==null&&u.query==null&&u.ref==null}.getOrDefault(false)
        require(valid(search)&&valid(route)) {"HTTPS 서비스 기본 주소를 입력해 주세요."}
        require(accessToken.isEmpty()||(accessToken.length in 12..256&&accessToken.all {it in '!'..'~'}&&URL(search).host.lowercase().trimEnd('.')!="nominatim.openstreetmap.org")){"접근키는 자체 검색 서버에만 사용하며 12~256자의 공백 없는 값이어야 합니다."}
        searchEndpoint=search.trimEnd('/');routeEndpoint=route.trimEnd('/');searchAccessToken=accessToken;cache.clear()
    }
    fun clearCache(){cache.clear();searchAccessToken=""}
    fun cancel(){connection?.disconnect()}
    private suspend fun get(url:String,accessToken:String=""):String=withContext(Dispatchers.IO) {
        val c=URL(url).openConnection() as HttpURLConnection;connection=c
        try {
            c.connectTimeout=12_000;c.readTimeout=20_000;c.instanceFollowRedirects=false
            c.setRequestProperty("User-Agent","CabinOS_ONE/0.8.0 (+https://github.com/hundol047/HYUNDAI)")
            if(accessToken.isNotEmpty())c.setRequestProperty("Authorization","Bearer $accessToken")
            c.setRequestProperty("Accept","application/json");c.setRequestProperty("Accept-Language","ko,en")
            check(c.responseCode==200) {if(c.responseCode==429)"서비스 요청 한도를 넘었습니다. 잠시 후 재시도하세요." else if(url.contains("/route/v1/driving/")&&c.responseCode in listOf(400,404,422))"경로 요청을 처리하지 못했습니다 (${c.responseCode}). 좌표·도로 연결·서버 주소를 확인하세요." else "내비게이션 서버 연결 실패 (${c.responseCode})"}
            val output=java.io.ByteArrayOutputStream();val buffer=ByteArray(8192)
            c.inputStream.use { stream ->while(true){currentCoroutineContext().ensureActive();val n=stream.read(buffer);if(n<0)break;check(output.size()+n<=12_000_000){"경로가 너무 큽니다. 가까운 경유 구간으로 나누세요."};output.write(buffer,0,n)} }
            output.toString("UTF-8")
        } finally {c.disconnect();if(connection===c)connection=null}
    }
    open suspend fun search(query:String,origin:NavPoint?=null,nearby:Boolean=false,excluded:List<String> = emptyList()):PlaceSearchPage {
        val requests=PlaceSearch.requests(query,origin,nearby,excluded)
        val key=requests.toString();cache[key]?.let {return it}
        val places=mutableListOf<NavPlace>()
        for(params in requests) {
            delay((1500-(System.currentTimeMillis()-lastSearch)).coerceAtLeast(0));currentCoroutineContext().ensureActive();lastSearch=System.currentTimeMillis()
            val encoded=params.entries.joinToString("&"){URLEncoder.encode(it.key,"UTF-8")+"="+URLEncoder.encode(it.value,"UTF-8")}
            val raw=get("$searchEndpoint/search?$encoded",searchAccessToken)
            places+=withContext(Dispatchers.Default){parsePlaces(raw)}
        }
        val result=PlaceSearch.page(places,origin,nearby,excluded)
        if(cache.size>=30)cache.remove(cache.keys.first());cache[key]=result;return result
    }
    open suspend fun route(origin:NavPoint,destination:NavPlace):DrivingRoute {
        require(origin.valid()&&destination.point.valid())
        require(NavigationGeometry.distance(origin,destination.point)>=20) {"목적지가 출발 위치와 너무 가깝습니다."}
        delay((1500-(System.currentTimeMillis()-lastRoute)).coerceAtLeast(0));currentCoroutineContext().ensureActive()
        lastRoute=System.currentTimeMillis()
        val p=destination.point
        val raw=get("$routeEndpoint/route/v1/driving/${origin.longitude},${origin.latitude};${p.longitude},${p.latitude}?overview=full&geometries=geojson&steps=true&alternatives=false")
        val route=withContext(Dispatchers.Default){parseRoute(raw,destination)}
        NavigationGeometry.validateOrigin(route,origin)
        return route
    }
    companion object {
        fun parsePlaces(raw:String):List<NavPlace> {
            val array=JSONArray(raw)
            return (0 until minOf(6,array.length())).mapNotNull { i ->val o=array.optJSONObject(i) ?: return@mapNotNull null
                fun text(key:String)=if(o.isNull(key))"" else o.optString(key)
                val tags=o.optJSONObject("extratags") ?: JSONObject()
                fun tag(key:String)=if(tags.isNull(key))"" else tags.optString(key)
                PlaceSearch.place(text("name").ifBlank {text("display_name")},text("lat").toDoubleOrNull() ?: Double.NaN,text("lon").toDoubleOrNull() ?: Double.NaN,
                    text("place_id"),text("display_name"),PlaceSearch.category(text("type")),tag("phone").ifBlank {tag("contact:phone")},tag("website").ifBlank {tag("contact:website")},tag("opening_hours"),text("source").ifBlank {"OSM / Nominatim"})
            }.distinctBy {it.id.ifBlank {it.name+it.point}}
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

data class NavigationUi(val guidance:Guidance=Guidance(),val places:List<NavPlace> = emptyList(),val busy:Boolean=false,val selectedPlace:NavPlace?=null,val hasMore:Boolean=false,val originAccessMeters:Double=0.0,val originReady:Boolean=false,val notice:String="목적지를 검색하거나 위도,경도를 입력하세요.",val gpsNotice:String="GPS 미연결 · 내 위치 사용을 눌러 주세요.")

/** Foreground-only location: no background permission/service and no stored GPS history. */
class LiveNavigation(private val context:Context,private val engine:UnifiedVehicleContextEngine,private val speech:AndroidSpeechProvider,val service:NavigationService=NavigationService(),private val clock:()->Long=System::currentTimeMillis) {
    private val tracker=NavigationTracker(clock)
    private val mutable=MutableStateFlow(NavigationUi());val state=mutable.asStateFlow()
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    private val manager=context.getSystemService(LocationManager::class.java)
    private var job:Job?=null;private var generation=0;private var foreground=true;private var listening=false
    private var safetyHeld=false;private var stationarySince:Long?=null
    private var lastQuery="";private var nearbySearch=false;private var seenPlaceIds:List<String> = emptyList()
    private var lastFixTimestamp=Long.MIN_VALUE;private var resumeLocation=false
    private var fix:NavFix?=null;private var spoken="";private var rerouteFailures=0
    private val listener=object:LocationListener {
        override fun onLocationChanged(location:Location) {
            if(!foreground||!listening)return
            val f=NavFix(NavPoint(location.latitude,location.longitude),if(location.hasAccuracy())location.accuracy.toDouble() else Double.POSITIVE_INFINITY,location.time,if(location.hasSpeed())location.speed.toDouble() else null,if(location.hasBearing())location.bearing.toDouble() else null)
            if(f.timestampMs<=lastFixTimestamp)return
            if(clock()-f.timestampMs in 0..10_000)lastFixTimestamp=f.timestampMs
            if(!tracker.usable(f)){fix=null;tracker.update(f);speech.stopSpeaking();publish();mutable.value=mutable.value.copy(gpsNotice="위치가 부정확하거나 오래됐습니다. 정확한 GPS를 기다려 주세요.");return}
            fix=f
            mutable.value=mutable.value.copy(gpsNotice="GPS 위치 확인 · 정확도 약 ${f.accuracy.toInt()}m")
            if(f.speedMps?.let {it>=2}==true){safetyHeld=true;stationarySince=null;engine.setNavigationLock(true)}
            if(safetyHeld&&!tracker.state.active) {
                if(f.speedMps?.let {it in 0.0..0.5}==true) {
                    if(stationarySince==null)stationarySince=clock()
                    if(clock()-(stationarySince ?: clock())>=5000){safetyHeld=false;engine.setNavigationLock(false)}
                } else stationarySince=null
            }
            if(tracker.state.active){tracker.update(f);publish();guide();reroute()}
            else publish()
        }
        override fun onProviderDisabled(provider:String){fix=null;tracker.suspendTracking();speech.stopSpeaking();publish();mutable.value=mutable.value.copy(gpsNotice="위치 서비스를 확인해 주세요. GPS 안내 일시중지")}
    }
    private fun publish(){
        val r=tracker.state.route;val f=fix;val gap=if(r!=null&&f!=null)NavigationGeometry.distance(f.point,r.points.first()) else 0.0
        mutable.value=mutable.value.copy(guidance=tracker.state,originAccessMeters=gap,originReady=r!=null&&f!=null&&tracker.usable(f)&&gap<=kotlin.math.max(45.0,f.accuracy*2))
    }
    private fun cancelRequest(){generation++;job?.cancel();service.cancel();mutable.value=mutable.value.copy(busy=false)}
    private fun request(block:suspend (()->Boolean)->Unit) {
        cancelRequest();val id=generation;mutable.value=mutable.value.copy(busy=true)
        job=scope.launch {
            try {block {id==generation}} catch(e:Exception){if(e is CancellationException)throw e;if(id==generation)mutable.value=mutable.value.copy(notice=when(e){is java.net.SocketTimeoutException->"서버 응답 시간이 지났습니다. 연결 상태를 확인해 주세요.";is java.io.IOException->"내비게이션 서버에 연결하지 못했습니다. 네트워크·서비스 주소를 확인해 주세요.";is org.json.JSONException->"경로 서버의 응답 형식을 확인할 수 없습니다.";else->e.message ?: "연결할 수 없습니다. 네트워크를 확인해 주세요."})}
            finally {if(id==generation)mutable.value=mutable.value.copy(busy=false)}
        }
    }
    fun canPlan()=foreground&&engine.state.value.parkedAllowed&&!tracker.state.active
    fun locate() {
        if(!canPlan()&&!tracker.state.active&&!safetyHeld)return
        if(context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED){mutable.value=mutable.value.copy(gpsNotice="정확한 위치 권한이 필요합니다. 대략적 위치로는 주행 안내를 시작하지 않습니다.");return}
        if(listening)return
        try {
            val providers=manager.getProviders(true).filter {it==LocationManager.GPS_PROVIDER||it==LocationManager.NETWORK_PROVIDER}
            if(providers.isEmpty()){mutable.value=mutable.value.copy(gpsNotice="기기 위치 서비스를 켜 주세요.");return}
            providers.forEach {manager.requestLocationUpdates(it,1000L,1f,listener,Looper.getMainLooper())};listening=true
            mutable.value=mutable.value.copy(gpsNotice="GPS 수신 중 · 야외에서 정확한 위치를 기다려 주세요.")
        } catch(_:SecurityException){mutable.value=mutable.value.copy(gpsNotice="위치 권한을 확인해 주세요.")}
    }
    fun search(query:String,nearby:Boolean=false,more:Boolean=false) {
        if(!canPlan()||!engine.state.value.networkAvailable)return
        if(more&&(!mutable.value.hasMore||query!=lastQuery||nearby!=nearbySearch))return
        val center=fix?.takeIf {tracker.usable(it)}?.point
        if(nearby&&center==null){mutable.value=mutable.value.copy(notice="주변 가게 검색은 내 위치를 먼저 받아 주세요.");return}
        val old=if(more)mutable.value.places else emptyList()
        if(!more){cancelRequest();seenPlaceIds=emptyList();tracker.stop();publish();mutable.value=mutable.value.copy(places=emptyList(),selectedPlace=null,hasMore=false)}
        val coordinates=query.split(',').map {it.trim().toDoubleOrNull()}
        if(!more&&coordinates.size==2&&coordinates.all {it!=null}) {
            val p=NavPoint(coordinates[0]!!,coordinates[1]!!)
            mutable.value=mutable.value.copy(places=if(p.valid())listOf(NavPlace("좌표 목적지 ${p.latitude}, ${p.longitude}",p,source="좌표 입력")) else emptyList(),hasMore=false,notice="좌표는 위도,경도 순서입니다.");return
        }
        lastQuery=query;nearbySearch=nearby
        request {current->val page=service.search(query,center,nearby,seenPlaceIds);if(current()&&canPlan()){
            seenPlaceIds=page.seenIds;val result=page.places
            val combined=(old+result).distinctBy {it.id.ifBlank {it.name+it.point}}.take(PlaceSearch.maxResults)
            mutable.value=mutable.value.copy(places=combined,hasMore=page.hasMore&&combined.size<PlaceSearch.maxResults,notice=if(result.isEmpty()&&page.hasMore)"이번 결과에 20km 내 가게가 없습니다. 다른 결과 더 보기를 눌러 확인하세요." else if(result.isEmpty())"등록된 검색 결과가 없습니다. 가게명·지점·도시나 현지어를 바꿔 보세요. 누락된 가게가 있을 수 있습니다." else "가게명·주소·출처를 확인하고 선택하세요. 검색 결과는 전체 가게 목록이 아닙니다.")
        }}
    }
    fun plan(destination:NavPlace) {
        if(!canPlan())return
        cancelRequest();tracker.stop();publish();mutable.value=mutable.value.copy(selectedPlace=destination)
        if(!engine.state.value.networkAvailable){mutable.value=mutable.value.copy(notice="선택한 가게 위치는 표시합니다. 새 자동차 경로는 온라인 연결이 필요합니다.");return}
        val f=fix
        if(f==null||!tracker.usable(f)){mutable.value=mutable.value.copy(notice="선택한 가게를 지도에 표시했습니다. 자동차 경로는 내 위치를 먼저 받아 주세요.");return}
        request {current->val r=service.route(f.point,destination);if(current()&&canPlan()){tracker.preview(r);publish();mutable.value=mutable.value.copy(notice="경로를 확인한 뒤 안내 시작을 누르세요. 실제 교통·차선 정보는 제공하지 않습니다.")}}
    }
    fun start(accessConfirmed:Boolean=false) {
        if(!canPlan()||mutable.value.busy)return
        if(context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED){fix=null;publish();mutable.value=mutable.value.copy(gpsNotice="정확한 위치 권한을 확인해 주세요.");return}
        if((tracker.state.route?.destinationAccessMeters ?: 0.0)>100&&!accessConfirmed){mutable.value=mutable.value.copy(notice="차량 접근 지점과 목적지의 차이를 먼저 확인해 주세요.");return}
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
        request {current->
            try {val r=service.route(origin,destination);if(current()&&foreground&&tracker.state.active&&tracker.replaceRoute(r)){spoken="";rerouteFailures=0;publish();guide();mutable.value=mutable.value.copy(notice="경로를 다시 계산했습니다.")}}
            catch(e:Exception){if(e !is CancellationException)rerouteFailures++;throw e}
        }
    }
    fun tick(){val wasTracking=tracker.state.tracking;tracker.tick();if(wasTracking&&!tracker.state.tracking)speech.stopSpeaking();publish();resumeLocationIfReady();if(tracker.state.active)reroute()}
    fun configure(search:String,route:String,accessToken:String=""){if(canPlan()){cancelRequest();service.configure(search,route,accessToken);tracker.stop();mutable.value=NavigationUi(notice="검색 서버 설정을 적용했습니다. 가게를 다시 검색하세요.")}}
    fun cancelPending(){if(canPlan()&&mutable.value.busy){cancelRequest();mutable.value=mutable.value.copy(notice="요청을 취소했습니다. 검색어나 목적지를 바꿀 수 있습니다.")}}
    fun retry(){rerouteFailures=0;reroute()}
    fun stop(){cancelRequest();speech.stopSpeaking();tracker.stop();if(!safetyHeld){engine.setNavigationLock(false);fix=null;stopLocation()};publish();mutable.value=mutable.value.copy(places=emptyList(),selectedPlace=null,hasMore=false,notice="안내를 종료했습니다. 정차 상태를 다시 확인합니다.")}
    private fun stopLocation(){runCatching {manager.removeUpdates(listener)};listening=false}
    fun setForeground(active:Boolean){foreground=active;if(!active){resumeLocation=resumeLocation||listening;cancelRequest();stopLocation();fix=null;tracker.suspendTracking();speech.stopSpeaking();publish();mutable.value=mutable.value.copy(gpsNotice="화면 밖에서는 위치 수신 중지")}else{if(tracker.state.active||safetyHeld)resumeLocation=true;resumeLocationIfReady()}}
    private fun resumeLocationIfReady(){if(foreground&&resumeLocation&&(canPlan()||tracker.state.active||safetyHeld)){resumeLocation=false;locate()}}
    fun clearSelection(){if(canPlan()){cancelRequest();tracker.stop();publish();mutable.value=mutable.value.copy(selectedPlace=null,notice="선택한 장소를 지웠습니다.")}}
    fun openPlaceWebsite(place:NavPlace) {
        if(!canPlan()||place.website.isBlank())return
        val uri=android.net.Uri.parse(place.website)
        if(uri.scheme!="https"||uri.host.isNullOrBlank()||uri.userInfo!=null)return
        runCatching {context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW,uri).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))}.onFailure {mutable.value=mutable.value.copy(notice="상세정보 페이지를 열 수 없습니다.")}
    }
    fun externalSearch(query:String) {
        if(!canPlan()||query.isBlank())return
        val uri=android.net.Uri.parse("https://www.google.com/maps/search/?api=1&query="+android.net.Uri.encode(query.take(180)))
        runCatching {context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW,uri).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))}.onFailure {mutable.value=mutable.value.copy(notice="외부 지도를 열 수 없습니다. 브라우저 설치를 확인하세요.")}
    }
    fun clear(){stop();service.clearCache();lastQuery="";nearbySearch=false;seenPlaceIds=emptyList();mutable.value=NavigationUi(notice="검색·좌표·접근키를 삭제했습니다.")}
    fun close(){cancelRequest();stopLocation();scope.cancel()}
}
