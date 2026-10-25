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
    var language="ko-KR"
    private var searchAccessToken=""
    private var lastSearch=0L
    private var lastRoute=0L
    private val cache=linkedMapOf<String,Pair<Long,PlaceSearchPage>>()
    fun configure(search:String,route:String,accessToken:String="") {
        fun valid(s:String):Boolean=runCatching {val u=URL(s);u.protocol=="https"&&u.host.isNotBlank()&&u.userInfo==null&&u.query==null&&u.ref==null}.getOrDefault(false)
        require(valid(search)&&valid(route)) {"HTTPS 서비스 기본 주소를 입력해 주세요."}
        require(accessToken.isEmpty()||(accessToken.length in 12..256&&accessToken.all {it in '!'..'~'}&&URL(search).host.lowercase().trimEnd('.')!="nominatim.openstreetmap.org")){"접근키는 자체 검색 서버에만 사용하며 12~256자의 공백 없는 값이어야 합니다."}
        searchEndpoint=search.trimEnd('/');routeEndpoint=route.trimEnd('/');searchAccessToken=accessToken;cache.clear()
    }
    fun clearCache(){cache.clear()}
    fun clearPersonalData(){clearCache();searchAccessToken=""}
    fun cancel(){connection?.disconnect()}
    private suspend fun get(url:String,accessToken:String=""):String=withContext(Dispatchers.IO) {
        val c=URL(url).openConnection() as HttpURLConnection;connection=c
        try {
            c.connectTimeout=12_000;c.readTimeout=20_000;c.instanceFollowRedirects=false
            c.setRequestProperty("User-Agent","CabinOS_ONE/${io.cabinos.one.BuildConfig.VERSION_NAME} (FutureLabs; Android)")
            if(accessToken.isNotEmpty())c.setRequestProperty("Authorization","Bearer $accessToken")
            c.setRequestProperty("Accept","application/json");c.setRequestProperty("Accept-Language",language+",en;q=0.7")
            check(c.responseCode==200) {if(c.responseCode==429)"서비스 요청 한도를 넘었습니다. 잠시 후 재시도하세요." else if(url.contains("/route/v1/driving/")&&c.responseCode in listOf(400,404,422))"경로 요청을 처리하지 못했습니다 (${c.responseCode}). 좌표·도로 연결·서버 주소를 확인하세요." else "내비게이션 서버 연결 실패 (${c.responseCode})"}
            val output=java.io.ByteArrayOutputStream();val buffer=ByteArray(8192)
            c.inputStream.use { stream ->while(true){currentCoroutineContext().ensureActive();val n=stream.read(buffer);if(n<0)break;check(output.size()+n<=12_000_000){"경로가 너무 큽니다. 가까운 경유 구간으로 나누세요."};output.write(buffer,0,n)} }
            output.toString("UTF-8")
        } finally {c.disconnect();if(connection===c)connection=null}
    }
    open suspend fun search(query:String,origin:NavPoint?=null,nearby:Boolean=false,excluded:List<String> = emptyList()):PlaceSearchPage {
        val requests=PlaceSearch.requests(query,origin,nearby,excluded,language)
        return searchPage(requests,origin,nearby,excluded)
    }
    private suspend fun searchPage(requests:List<Map<String,String>>,origin:NavPoint?,nearby:Boolean,excluded:List<String>):PlaceSearchPage {
        val key=language+"|"+requests.toString();cache[key]?.let {if(System.currentTimeMillis()-it.first in 0..59_999)return it.second else cache.remove(key)}
        val places=mutableListOf<NavPlace>()
        for(params in requests) {
            delay((1500-(System.currentTimeMillis()-lastSearch)).coerceAtLeast(0));currentCoroutineContext().ensureActive();lastSearch=System.currentTimeMillis()
            val encoded=params.entries.joinToString("&"){URLEncoder.encode(it.key,"UTF-8")+"="+URLEncoder.encode(it.value,"UTF-8")}
            val raw=get("$searchEndpoint/search?$encoded",searchAccessToken)
            places+=withContext(Dispatchers.Default){parsePlaces(raw)}
        }
        val result=PlaceSearch.page(places,origin,nearby,excluded)
        if(cache.size>=30)cache.remove(cache.keys.first());cache[key]=System.currentTimeMillis() to result;return result
    }
    open suspend fun regionalSearch(query:String,origin:NavPoint?):RegionalSearchResult {
        for(split in RegionalPlaceSearch.splits(query)) {
            delay((1500-(System.currentTimeMillis()-lastSearch)).coerceAtLeast(0));currentCoroutineContext().ensureActive();lastSearch=System.currentTimeMillis()
            val params=PlaceSearch.requests(split.region,language=language).single()
            val encoded=params.entries.joinToString("&"){URLEncoder.encode(it.key,"UTF-8")+"="+URLEncoder.encode(it.value,"UTF-8")}
            val raw=get("$searchEndpoint/search?$encoded",searchAccessToken)
            val candidates=parseRegions(raw,split.region)
            if(candidates.isEmpty())continue
            val region=RegionalPlaceSearch.chooseRegion(candidates,origin) ?: return RegionalSearchResult(emptyList())
            val radius=RegionalPlaceSearch.radius(region.category)
            val collected=mutableListOf<NavPlace>()
            for(name in RegionalPlaceSearch.names(split.name)) {
                val page=searchPage(PlaceSearch.requests(name,region.point,true,language=language,radius=radius.toDouble()),region.point,true,emptyList())
                val accepted=page.places.filter {RegionalPlaceSearch.acceptsCategory(split.name,it.category)&&RegionalPlaceSearch.matchesName(split.name,it.name,region.address)&&NavigationGeometry.distance(region.point,it.point)<=radius}
                collected+=accepted
            }
            return RegionalSearchResult(PlaceSearch.results(collected,region.point,true),region.name,split.name,radius)
        }
        return RegionalSearchResult(emptyList())
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
        fun parseRegions(raw:String,query:String):List<NavPlace> {
            val rows=JSONArray(raw)
            return (0 until minOf(6,rows.length())).mapNotNull {i->
                val row=rows.optJSONObject(i) ?: return@mapNotNull null
                val details=row.optJSONObject("namedetails")
                val names=listOf(row.optString("name"))+details?.keys()?.asSequence()?.filter {RegionalPlaceSearch.aliasKey(it)}?.flatMap {details.optString(it).split(';').asSequence()}?.toList().orEmpty()
                if(!RegionalPlaceSearch.regionMatches(query,names,row.optString("type")))return@mapNotNull null
                PlaceSearch.place(row.optString("name"),row.optDouble("lat",Double.NaN),row.optDouble("lon",Double.NaN),row.optString("place_id"),row.optString("display_name"),row.optString("type"))
            }
        }
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
                    val road=NavigationGeometry.roadLabel(step.optString("name"),step.optString("destinations"),step.optString("ref"))
                    maneuvers.add(NavManeuver(NavPoint(p.getDouble(1),p.getDouble(0)),m.getString("type"),m.optString("modifier"),road,if(m.has("exit"))m.getInt("exit") else null))
                }
            }
            return NavigationGeometry.route(destination,points,route.getDouble("distance"),route.getDouble("duration"),maneuvers)
        }
    }
}

enum class GpsConnection { OFF, WAITING, READY, POOR, STALE, DENIED, DISABLED }

data class NavigationUi(val guidance:Guidance=Guidance(),val places:List<NavPlace> = emptyList(),val busy:Boolean=false,val selectedPlace:NavPlace?=null,val hasMore:Boolean=false,val originAccessMeters:Double=0.0,val originReady:Boolean=false,val notice:String="목적지를 검색하거나 위도,경도를 입력하세요.",val gpsNotice:String="GPS 미연결 · 내 위치 사용을 눌러 주세요.",val gpsConnection:GpsConnection=GpsConnection.OFF,val gpsAccuracy:Double?=null,val mapFix:NavFix?=null,val gpsAgeSeconds:Long?=null,val language:String="ko-KR",val voiceListening:Boolean=false,val voiceNotice:String="목적지 → 결과 번호 → 안내 시작을 말하세요.")

/** Foreground-only location: no background permission/service and no stored GPS history. */
class LiveNavigation(private val context:Context,private val engine:UnifiedVehicleContextEngine,private val speech:AndroidSpeechProvider,val service:NavigationService=NavigationService(),private val clock:()->Long=System::currentTimeMillis) {
    val voiceAvailable=speech.voiceAvailable
    private val tracker=NavigationTracker(clock)
    private val preferences=RuntimePreferences(context)
    private var language=preferences.navigationLanguage
    private val mutable=MutableStateFlow(NavigationUi(language=language));val state=mutable.asStateFlow()
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    private val manager=context.getSystemService(LocationManager::class.java)
    private var job:Job?=null;private var generation=0;private var foreground=true;private var listening=false
    private var safetyHeld=false;private val stationary=StationaryEvidence()
    private var voiceGeneration=0
    init {service.language=language}
    private var lastQuery="";private var resolvedSearchQuery="";private var nearbySearch=false;private var seenPlaceIds:List<String> = emptyList()
    private var lastFixTimestamp=Long.MIN_VALUE;private var resumeLocation=false
    private var locationGeneration=0;private var requestedAt:Long?=null;private var receivedAt:Long?=null
    private var listener:LocationListener?=null
    private var pendingDestination:NavPlace?=null
    private var fix:NavFix?=null;private var fixProvider:String?=null;private var spoken="";private var rerouteFailures=0
    private fun listenerFor(epoch:Int)=object:LocationListener {
        override fun onLocationChanged(location:Location) {
            if(epoch!=locationGeneration||!foreground||!listening)return
            if(context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED){stopLocation();invalidateGps(GpsConnection.DENIED,"위치 권한이 해제됐습니다. 앱 권한 설정을 확인해 주세요.");return}
            val f=NavFix(NavPoint(location.latitude,location.longitude),if(location.hasAccuracy())location.accuracy.toDouble() else Double.POSITIVE_INFINITY,location.time,if(location.hasSpeed())location.speed.toDouble() else null,if(location.hasBearing())location.bearing.toDouble() else null)
            // A coarse Wi-Fi/cell update must not interrupt a fresh precise GPS
            // stream. A poor GPS update itself still invalidates readiness.
            if(location.provider==LocationManager.NETWORK_PROVIDER&&fixProvider==LocationManager.GPS_PROVIDER&&fix?.let {tracker.usable(it)&&clock()-it.timestampMs<=3000&&f.accuracy>=it.accuracy}==true)return
            if(f.timestampMs<=lastFixTimestamp)return
            if(clock()-f.timestampMs in 0..10_000)lastFixTimestamp=f.timestampMs
            receivedAt=clock()
            if(!tracker.usable(f)){invalidateGps(GpsConnection.POOR,"위치가 부정확하거나 오래됐습니다. 정확한 GPS를 기다려 주세요.");return}
            fix=f;fixProvider=location.provider
            mutable.value=mutable.value.copy(gpsConnection=GpsConnection.READY,gpsNotice="${if(location.provider==LocationManager.GPS_PROVIDER)"GPS" else "네트워크 위치"} 확인 · 정확도 약 ${f.accuracy.toInt()}m")
            if(f.speedMps?.let {it>=2}==true){safetyHeld=true;stationary.reset();engine.setNavigationLock(true)}
            if(safetyHeld&&!tracker.state.active&&stationary.accept(f,clock())){safetyHeld=false;stationary.reset();engine.setNavigationLock(false)}
            if(tracker.state.active){tracker.update(f);publish();guide();reroute()}else {
                publish()
                if(canPlan())pendingDestination?.let {pendingDestination=null;plan(it)}
            }
        }
        override fun onProviderDisabled(provider:String){if(epoch==locationGeneration&&foreground&&listening)invalidateGps(GpsConnection.DISABLED,"위치 제공자가 중지됐습니다. 위치 설정 또는 GPS 다시 연결을 확인해 주세요.")}
    }
    private fun invalidateGps(status:GpsConnection,message:String){val ownVoice=tracker.state.tracking||spoken.isNotEmpty();fix=null;fixProvider=null;stationary.reset();tracker.suspendTracking();if(ownVoice)speech.stopSpeaking();spoken="";mutable.value=mutable.value.copy(gpsConnection=status,gpsNotice=message);publish()}
    private fun publish(){
        val r=tracker.state.route;val f=fix;val gap=if(r!=null&&f!=null)NavigationGeometry.distance(f.point,r.points.first()) else 0.0
        mutable.value=mutable.value.copy(guidance=tracker.state,originAccessMeters=gap,originReady=r!=null&&f!=null&&tracker.usable(f)&&gap<=kotlin.math.max(45.0,f.accuracy*2),gpsAccuracy=f?.accuracy,mapFix=f,gpsAgeSeconds=f?.let {((clock()-it.timestampMs)/1000).coerceAtLeast(0)})
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
        if(!foreground||(!canPlan()&&!tracker.state.active&&!safetyHeld))return
        stopLocation();requestedAt=null;receivedAt=null
        invalidateGps(GpsConnection.WAITING,"GPS 수신 중 · 야외에서 정확한 위치를 기다려 주세요.")
        if(context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED){invalidateGps(GpsConnection.DENIED,"정확한 위치 권한이 필요합니다. 앱 권한 설정에서 정확한 위치를 허용해 주세요.");return}
        try {
            val providers=manager.getProviders(true).filter {it==LocationManager.GPS_PROVIDER||it==LocationManager.NETWORK_PROVIDER}
            if(!manager.isLocationEnabled||providers.isEmpty()){invalidateGps(GpsConnection.DISABLED,"기기 위치 서비스를 켜 주세요.");return}
            val current=listenerFor(++locationGeneration);listener=current;listening=true;requestedAt=clock()
            // Receive fresh fixes even when the parked vehicle does not move.
            providers.forEach {manager.requestLocationUpdates(it,1000L,0f,current,Looper.getMainLooper())}
        } catch(_:SecurityException){stopLocation();invalidateGps(GpsConnection.DENIED,"위치 권한을 확인해 주세요.")}
          catch(_:IllegalArgumentException){stopLocation();invalidateGps(GpsConnection.DISABLED,"위치 제공자를 사용할 수 없습니다. 위치 설정을 확인해 주세요.")}
    }
    fun search(query:String,nearby:Boolean=false,more:Boolean=false) {
        if(!canPlan()||!engine.state.value.networkAvailable)return
        if(more&&(!mutable.value.hasMore||query!=lastQuery||nearby!=nearbySearch))return
        val center=fix?.takeIf {tracker.usable(it)}?.point
        if(nearby&&center==null){mutable.value=mutable.value.copy(notice="주변 가게 검색은 내 위치를 먼저 받아 주세요.");return}
        val old=if(more)mutable.value.places else emptyList()
        if(!more){pendingDestination=null;resolvedSearchQuery=query;cancelRequest();seenPlaceIds=emptyList();tracker.stop();publish();mutable.value=mutable.value.copy(places=emptyList(),selectedPlace=null,hasMore=false)}
        val coordinates=query.split(',').map {it.trim().toDoubleOrNull()}
        if(!more&&coordinates.size==2&&coordinates.all {it!=null}) {
            val p=NavPoint(coordinates[0]!!,coordinates[1]!!)
            mutable.value=mutable.value.copy(places=if(p.valid())listOf(NavPlace("좌표 목적지 ${p.latitude}, ${p.longitude}",p,source="좌표 입력")) else emptyList(),hasMore=false,notice="좌표는 위도,경도 순서입니다.");return
        }
        lastQuery=query;nearbySearch=nearby
        request {current->
            var page=service.search(if(more)resolvedSearchQuery else query,center,nearby,seenPlaceIds)
            if(!current()||!canPlan())return@request
            val suggestion=if(!more&&page.places.isEmpty()&&page.seenIds.isEmpty())PlaceSearch.spacingSuggestion(query) else null
            if(suggestion!=null){page=service.search(suggestion,center,nearby,seenPlaceIds);if(!current()||!canPlan())return@request;resolvedSearchQuery=suggestion}
            if(current()&&canPlan()){
            val spacingNotice=if(resolvedSearchQuery!=query)"띄어쓰기를 나눠 검색했습니다: $resolvedSearchQuery · " else ""
            seenPlaceIds=page.seenIds;val result=page.places
            val combined=(old+result).distinctBy {it.id.ifBlank {it.name+it.point}}.take(PlaceSearch.maxResults)
            mutable.value=mutable.value.copy(places=combined,hasMore=page.hasMore&&combined.size<PlaceSearch.maxResults,notice=spacingNotice+(if(result.isEmpty()&&page.hasMore)"이번 결과에 20km 내 가게가 없습니다. 다른 결과 더 보기를 눌러 확인하세요." else if(result.isEmpty())"일치하는 검색 결과를 찾지 못했습니다. 가게명·지점·도시나 현지어를 바꿔 보세요. 누락된 가게가 있을 수 있습니다." else "가게명·주소·출처를 확인하고 선택하세요. 검색 결과는 전체 가게 목록이 아닙니다."))
            if(engine.state.value.preferences.voiceEnabled&&combined.isNotEmpty())speech.speakNavigation(combined.take(6).mapIndexed {i,p->"${i+1}. ${p.name}"}.joinToString(". "),language)
        }}
    }
    fun searchRegional(query:String) {
        if(!canPlan()||mutable.value.busy||!engine.state.value.networkAvailable||RegionalPlaceSearch.splits(query).isEmpty())return
        val center=fix?.takeIf {tracker.usable(it)}?.point
        cancelRequest();tracker.stop();publish();lastQuery=query;seenPlaceIds=emptyList()
        mutable.value=mutable.value.copy(places=emptyList(),selectedPlace=null,hasMore=false,notice="지역과 상호를 나눠 주변 후보를 확인합니다…")
        request {current->
            val result=service.regionalSearch(query,center)
            if(current()&&canPlan()) {
                mutable.value=mutable.value.copy(places=result.places,hasMore=false,notice=result.notice)
                if(engine.state.value.preferences.voiceEnabled&&result.places.isNotEmpty())speech.speakNavigation("주변 후보입니다. 주소와 지점을 확인하세요. "+result.places.mapIndexed {i,p->"${i+1}. ${p.name}"}.joinToString(". "),language)
            }
        }
    }
    fun plan(destination:NavPlace) {
        if(!canPlan())return
        pendingDestination=null
        cancelRequest();tracker.stop();publish();mutable.value=mutable.value.copy(selectedPlace=destination)
        if(!engine.state.value.networkAvailable){mutable.value=mutable.value.copy(notice="선택한 지점 위치는 표시합니다. 새 자동차 경로는 온라인 연결이 필요합니다.");return}
        val f=fix
        if(f==null||!tracker.usable(f)){pendingDestination=destination;mutable.value=mutable.value.copy(notice="선택한 지점을 표시했습니다. 내 위치를 허용하면 앱에서 자동차 경로를 계산합니다.");return}
        request {current->val r=service.route(f.point,destination);if(current()&&canPlan()){tracker.preview(r.localized(language));publish();if(engine.state.value.preferences.voiceEnabled)speech.speakNavigation(destination.name,language);mutable.value=mutable.value.copy(notice="경로를 확인한 뒤 안내 시작을 누르세요. 실제 교통·차선 정보는 제공하지 않습니다.")}}
    }
    fun start(accessConfirmed:Boolean=false) {
        if(!canPlan()||mutable.value.busy)return
        if(context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED){stopLocation();invalidateGps(GpsConnection.DENIED,"정확한 위치 권한을 확인해 주세요.");return}
        if((tracker.state.route?.destinationAccessMeters ?: 0.0)>100&&!accessConfirmed){mutable.value=mutable.value.copy(notice="차량 접근 지점과 목적지의 차이를 먼저 확인해 주세요.");return}
        val f=fix
        if(f==null||!tracker.start(f)){mutable.value=mutable.value.copy(notice="출발 위치의 정확한 GPS가 필요합니다. 이동했다면 경로를 다시 계산하세요.");return}
        cancelRequest();safetyHeld=true;stationary.reset();engine.setNavigationLock(true);publish();spoken="";rerouteFailures=0;guide()
    }
    private fun guide() {
        val g=tracker.state
        if(!g.tracking||g.offRoute||g.directionUncertain||!engine.state.value.preferences.voiceEnabled){speech.stopSpeaking();spoken="";return}
        val band=when{g.arrived->"arrived";g.turnMeters<60->"now";g.turnMeters<300->"soon";else->"ahead"}
        val key="${g.nextInstruction}|$band"
        if(key!=spoken&&!mutable.value.voiceListening){
            val code=NavigationLanguages.resolve(language)
            val spokenLocale=java.util.Locale.forLanguageTag(if(code=="ko")"ko-KR" else code)
            val distance=android.icu.text.MeasureFormat.getInstance(spokenLocale,android.icu.text.MeasureFormat.FormatWidth.WIDE).format(android.icu.util.Measure(g.turnMeters.toInt(),android.icu.util.MeasureUnit.METER))
            if(speech.speakNavigation(if(g.arrived)g.nextInstruction else NavigationLanguages.spoken(code,distance,g.nextInstruction),spokenLocale.toLanguageTag()))spoken=key
            mutable.value=mutable.value.copy(voiceNotice=speech.status)
        }
    }
    private fun reroute() {
        if(!foreground||!engine.state.value.networkAvailable||mutable.value.busy||rerouteFailures>=3||!tracker.takeReroute())return
        val g=tracker.state;val destination=g.route?.destination ?: return;val origin=g.fix?.point ?: return
        request {current->
            try {val r=service.route(origin,destination);if(current()&&foreground&&tracker.state.active&&tracker.replaceRoute(r.localized(language))){spoken="";rerouteFailures=0;publish();guide();mutable.value=mutable.value.copy(notice="경로를 다시 계산했습니다.")}}
            catch(e:Exception){if(e !is CancellationException)rerouteFailures++;throw e}
        }
    }
    fun tick(){
        val wasTracking=tracker.state.tracking;tracker.tick()
        if(wasTracking&&!tracker.state.tracking){speech.stopSpeaking();spoken=""}
        if(listening){
            if(context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED){stopLocation();invalidateGps(GpsConnection.DENIED,"위치 권한이 해제됐습니다. 앱 권한 설정을 확인해 주세요.")}
            else if(mutable.value.gpsConnection!=GpsConnection.STALE&&(fix?.let {!tracker.usable(it)}==true||fix==null&&receivedAt?.let {clock()-it>10_000}==true||fix==null&&receivedAt==null&&requestedAt?.let {clock()-it>=15_000}==true))invalidateGps(GpsConnection.STALE,"GPS 응답이 오래됐습니다. 다시 연결하거나 위치 설정을 확인해 주세요.")
        }
        publish();resumeLocationIfReady();if(tracker.state.active)reroute()
    }
    fun configure(search:String,route:String,accessToken:String=""){if(canPlan()){cancelRequest();service.configure(search,route,accessToken);tracker.stop();mutable.value=mutable.value.copy(guidance=tracker.state,places=emptyList(),selectedPlace=null,hasMore=false,originReady=false,notice="검색 서버 설정을 적용했습니다. 가게를 다시 검색하세요.")}}
    fun cancelPending(){pendingDestination=null;if(canPlan()&&mutable.value.busy){cancelRequest();mutable.value=mutable.value.copy(notice="요청을 취소했습니다. 검색어나 목적지를 바꿀 수 있습니다.")}}
    fun retry(){rerouteFailures=0;reroute()}
    fun stop(){pendingDestination=null;cancelVoice();cancelRequest();speech.stopSpeaking();tracker.stop();stationary.reset();if(!safetyHeld){resumeLocation=false;engine.setNavigationLock(false);stopLocation();invalidateGps(GpsConnection.OFF,"GPS 미연결 · 내 위치 사용을 눌러 주세요.")};publish();mutable.value=mutable.value.copy(places=emptyList(),selectedPlace=null,hasMore=false,notice="안내를 종료했습니다. 정차 상태를 다시 확인합니다.")}
    private fun stopLocation(){locationGeneration++;listener?.let {runCatching {manager.removeUpdates(it)}};listener=null;listening=false}
    fun setForeground(active:Boolean){foreground=active;if(!active)cancelVoice();if(!active){resumeLocation=resumeLocation||listening;cancelRequest();stopLocation();invalidateGps(GpsConnection.OFF,"화면 밖에서는 위치 수신 중지")}else{if(tracker.state.active||safetyHeld)resumeLocation=true;resumeLocationIfReady()}}
    private fun resumeLocationIfReady(){if(foreground&&resumeLocation&&(canPlan()||tracker.state.active||safetyHeld)){resumeLocation=false;locate()}}
    fun openLocationSettings(appPermissions:Boolean=false){
        if(!canPlan())return
        val intent=if(appPermissions)android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,android.net.Uri.parse("package:${context.packageName}")) else android.content.Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS)
        resumeLocation=true
        runCatching {context.startActivity(intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))}.onFailure {resumeLocation=false;mutable.value=mutable.value.copy(gpsNotice="설정 화면을 열 수 없습니다. 기기 설정에서 위치·앱 권한을 확인해 주세요.")}
    }
    fun setLanguage(tag:String){
        if(!canPlan()||!NavigationLanguages.validTag(tag))return
        cancelVoice();cancelRequest();language=tag;preferences.navigationLanguage=tag;service.language=tag;service.clearCache()
        tracker.state.route?.let {tracker.preview(it.localized(tag))};spoken="";publish()
        mutable.value=mutable.value.copy(language=tag,places=emptyList(),hasMore=false,voiceNotice=if(NavigationLanguages.resolve(tag)=="en"&&!tag.startsWith("en",true))"선택한 언어의 안내 번역 없음 · 영어 안내로 대체. 목적지 인식은 선택 언어 사용." else "언어 적용 · 기기 음성팩이 필요합니다.")
    }
    fun availableVoiceLanguages()=speech.navigationLanguages()
    fun voiceError(message:String){mutable.value=mutable.value.copy(voiceListening=false,voiceNotice=message)}
    fun cancelVoice(){voiceGeneration++;speech.cancelRecognition();mutable.value=mutable.value.copy(voiceListening=false)}
    fun listenDestination(){
        if(!foreground)return
        cancelVoice();val epoch=voiceGeneration
        mutable.value=mutable.value.copy(voiceListening=true,voiceNotice="듣고 있습니다 · 목적지 또는 짧은 명령을 말하세요.")
        speech.listenNavigation(language,{raw->if(epoch==voiceGeneration&&foreground){mutable.value=mutable.value.copy(voiceListening=false);handleVoice(raw)}},{error->if(epoch==voiceGeneration)voiceError(error)})
    }
    fun handleVoice(raw:String){
        if(!foreground)return
        val c=NavigationVoice.parse(raw)
        mutable.value=mutable.value.copy(voiceNotice="인식: ${raw.take(180)}")
        when(c.action){
            NavigationVoiceAction.REPEAT->{spoken="";guide()}
            NavigationVoiceAction.STOP->stop()
            NavigationVoiceAction.CANCEL->{cancelPending();cancelVoice()}
            else->{
                if(!canPlan()){voiceError("새 목적지·결과 선택·안내 시작은 안전한 주차 확인 후 가능합니다. 주행 중에는 안내 반복·종료를 말하세요.");return}
                when(c.action){
                    NavigationVoiceAction.SEARCH->if(c.query.isNotBlank())search(c.query) else voiceError("목적지를 조금 더 자세히 말씀해 주세요.")
                    NavigationVoiceAction.REGIONAL->searchRegional(lastQuery)
                    NavigationVoiceAction.SELECT->mutable.value.places.getOrNull(c.index)?.let {plan(it)} ?: voiceError("검색 결과에 있는 번호를 말씀해 주세요.")
                    NavigationVoiceAction.START->if(tracker.state.route==null||mutable.value.busy)voiceError("먼저 결과 번호를 말하고 경로 계산을 기다려 주세요.") else start()
                    else->Unit
                }
            }
        }
    }
    fun clearSelection(){if(canPlan()){pendingDestination=null;cancelRequest();tracker.stop();publish();mutable.value=mutable.value.copy(selectedPlace=null,notice="선택한 장소를 지웠습니다.")}}
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
    fun clear(){cancelVoice();language="ko-KR";preferences.navigationLanguage=language;service.language=language;stop();service.clearPersonalData();lastQuery="";resolvedSearchQuery="";nearbySearch=false;seenPlaceIds=emptyList();mutable.value=NavigationUi(notice="검색·좌표·접근키를 삭제했습니다.")}
    fun close(){cancelVoice();cancelRequest();stopLocation();scope.cancel()}
}
