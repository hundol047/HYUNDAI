package io.cabinos.one.platform

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import io.cabinos.core.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/** Test telemetry only. No hardware command is sent by this adapter. */
class SimulationVehicleProvider(private val preferences: RuntimePreferences? = null) : VehicleDataProvider, ChargingProvider {
    private val mutable=MutableStateFlow(VehicleSample(gear=Gear.PARK,parkingReliable=true,charging=true,chargeRemainingSeconds=2400,timestampMs=System.currentTimeMillis()))
    override val samples=mutable.asStateFlow()
    private val remaining=MutableStateFlow<Long?>(2400)
    override val remainingSeconds=remaining.asStateFlow()
    var realMode=false; private set
    var selected="충전"; private set
    init { select(preferences?.scenario ?: "충전") }
    fun select(scenario:String) {
        if (scenario !in listOf("주행","신호 대기","주차","충전","충전 15분","출발 준비","SDK 끊김","실차 연결")) { select("충전"); return }
        preferences?.scenario = scenario
        selected=scenario; realMode=scenario=="실차 연결"
        val old=mutable.value
        mutable.value=when(scenario) {
            "주행" -> old.copy(speedKph=80.0,gear=Gear.DRIVE,charging=false,connected=true,source=DataSource.SIMULATION)
            "신호 대기" -> old.copy(speedKph=0.0,gear=Gear.DRIVE,charging=false,connected=true,source=DataSource.SIMULATION)
            "주차" -> old.copy(speedKph=0.0,gear=Gear.PARK,charging=false,connected=true,parkingReliable=true,source=DataSource.SIMULATION)
            "충전" -> old.copy(speedKph=0.0,gear=Gear.PARK,charging=true,chargeRemainingSeconds=2400,connected=true,parkingReliable=true,source=DataSource.SIMULATION)
            "충전 15분" -> old.copy(speedKph=0.0,gear=Gear.PARK,charging=true,chargeRemainingSeconds=900,connected=true,parkingReliable=true,source=DataSource.SIMULATION)
            "출발 준비" -> old.copy(speedKph=0.0,gear=Gear.PARK,charging=true,chargeRemainingSeconds=300,connected=true,parkingReliable=true,source=DataSource.SIMULATION)
            "SDK 끊김" -> old.copy(connected=false,source=DataSource.SIMULATION)
            "실차 연결" -> VehicleSample(timestampMs=System.currentTimeMillis(),connected=false,batteryPercent=-1,source=DataSource.VEHICLE)
            else -> old
        }.copy(timestampMs=System.currentTimeMillis(),batteryPercent=if(realMode)-1 else 68)
        remaining.value=mutable.value.chargeRemainingSeconds
    }
    fun heartbeat() { mutable.value=mutable.value.copy(timestampMs=System.currentTimeMillis()) }
}

/** Actual framework location; it does not establish gear/P or unlock parked UI. */
class AndroidLocationProvider(private val context:Context) : LocationProvider {
    private val manager=context.getSystemService(LocationManager::class.java)
    private val mutable=MutableStateFlow(false)
    override val available=mutable.asStateFlow()
    var lastLocation:Location?=null; private set
    private val handler=android.os.Handler(Looper.getMainLooper())
    private var expiry:Runnable?=null
    private var listener:LocationListener?=null
    private var generation=0L
    private fun permitted()=context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED ||
        context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)==PackageManager.PERMISSION_GRANTED
    private fun fresh(location:Location)=location.latitude.isFinite() && location.latitude in -90.0..90.0 &&
        location.longitude.isFinite() && location.longitude in -180.0..180.0 && System.currentTimeMillis()-location.time in 0..10_000
    private fun clearFix() {
        expiry?.let {handler.removeCallbacks(it)};expiry=null
        lastLocation=null;mutable.value=false
    }
    override fun start() {
        stop()
        if (!permitted() || !manager.isLocationEnabled) return
        val fine=context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED
        fun stillPermitted()=permitted() && (!fine || context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED)
        val token=generation
        val current=object:LocationListener {
            override fun onProviderEnabled(provider:String)=Unit
            override fun onStatusChanged(provider:String?,status:Int,extras:Bundle?)=Unit
            override fun onLocationChanged(location:Location) {
                if(token!=generation)return
                if(!stillPermitted() || !manager.isLocationEnabled) { stop();return }
                if(!fresh(location)) {
                    if(lastLocation?.let {fresh(it)}!=true)clearFix()
                    return
                }
                if(lastLocation?.let {location.time<it.time && fresh(it)}==true)return
                clearFix()
                val accepted=Location(location)
                lastLocation=accepted;mutable.value=true
                expiry=Runnable {if(token==generation && lastLocation===accepted)clearFix()}.also {
                    handler.postDelayed(it,(10_001-(System.currentTimeMillis()-accepted.time)).coerceAtLeast(1))
                }
            }
            override fun onProviderDisabled(provider:String) {
                if(token!=generation)return
                if(!stillPermitted()) {stop();return}
                if(manager.isLocationEnabled && lastLocation?.let {it.provider!=provider && fresh(it)}==true)return
                clearFix()
            }
        }
        listener=current
        try {
            manager.getProviders(true).filter {it!=LocationManager.PASSIVE_PROVIDER && (fine || it!=LocationManager.GPS_PROVIDER)}.forEach { provider ->
                // A stationary car still needs fresh evidence; do not require five metres of movement.
                manager.requestLocationUpdates(provider,2000L,0f,current,Looper.getMainLooper())
            }
        } catch (_:SecurityException) { stop() }
    }
    override fun stop() {
        ++generation
        val previous=listener;listener=null;clearFix()
        try { previous?.let {manager.removeUpdates(it)} } catch (_:SecurityException) { /* Revoked permission cannot retain a fix. */ }
    }
}
class AndroidNavigationProvider(private val context:Context, private val location:AndroidLocationProvider):NavigationProvider {
    override val route=MutableStateFlow(Route(id="unavailable")).asStateFlow()
    override fun resumeNavigation():Boolean {
        val l=location.lastLocation ?: return false
        if (System.currentTimeMillis()-l.time !in 0..60_000) return false
        if (context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED && context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)!=PackageManager.PERMISSION_GRANTED) return false
        val intent=Intent(Intent.ACTION_VIEW,Uri.parse("geo:${l.latitude},${l.longitude}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try { context.startActivity(intent);true } catch (_:Exception) { false }
    }
}
internal fun voiceServicesAvailable(context:Context):Boolean = runCatching {
    context.packageManager.queryIntentServices(Intent("android.intent.action.TTS_SERVICE"),0).isNotEmpty() &&
        SpeechRecognizer.isRecognitionAvailable(context)
}.getOrDefault(false)

class AndroidSpeechProvider(private val context:Context):SpeechProvider {
    private val outputPreferences=context.getSharedPreferences("voice-output",Context.MODE_PRIVATE)
    private val mutableGentle=MutableStateFlow(outputPreferences.getBoolean("gentle",true))
    val gentleOutput=mutableGentle.asStateFlow()
    private val mutableVoiceAvailable=MutableStateFlow(false)
    val voiceAvailable=mutableVoiceAvailable.asStateFlow()
    fun refreshVoiceAvailability() { mutableVoiceAvailable.value=runCatching {SpeechRecognizer.isRecognitionAvailable(context)}.getOrDefault(false) }
    private var initialized=false
    private val mutableOutputReady=MutableStateFlow(false)
    val outputReady=mutableOutputReady.asStateFlow()
    private val mutableOutputInitializationFailed=MutableStateFlow(false)
    val outputInitializationFailed=mutableOutputInitializationFailed.asStateFlow()
    private var pending:String?=null
    private val mutableStatus=MutableStateFlow("음성 엔진 준비 중")
    val statusFlow=mutableStatus.asStateFlow()
    private val mutableOutputFailure=MutableStateFlow<String?>(null)
    val outputFailure=mutableOutputFailure.asStateFlow()
    var status:String
        get()=mutableStatus.value
        private set(value){mutableStatus.value=value}
    private var tts:TextToSpeech?=null
    private var configuredLanguage:String?=null
    private var voiceDescription=""
    private var lastSpokenText:String?=null
    private var lastSpokenLanguage:String?=null
    private var lastSpokenKey:String?=null
    private var recognizer:SpeechRecognizer?=null
    private var recognitionEpoch=0
    private val handler=android.os.Handler(android.os.Looper.getMainLooper())
    private var timeout:Runnable?=null
    private var outputEngine:String?=null
    private var outputGeneration=0
    private var activeUtterance:String?=null
    private var synthesisTimeout:Runnable?=null
    init { connectOutput() }
    private fun defaultOutputEngine()=android.provider.Settings.Secure.getString(context.contentResolver,"tts_default_synth")
    private fun connectOutput() {
        val generation=++outputGeneration
        stopSpeaking()
        initialized=false;mutableOutputReady.value=false;mutableOutputInitializationFailed.value=false;configuredLanguage=null
        tts?.shutdown()
        outputEngine=defaultOutputEngine()
        status="음성 엔진 준비 중"
        tts=TextToSpeech(context,{result ->
            if(generation==outputGeneration) {
                initialized=result==TextToSpeech.SUCCESS
                if(initialized)observeOutput(generation)
                mutableOutputReady.value=initialized
                mutableOutputInitializationFailed.value=!initialized
                refreshVoiceAvailability()
                status=if(initialized)"음성 엔진 준비됨 · 언어별 오프라인 음성 사용" else "TTS 사용 불가 · 화면 안내 사용"
                pending?.let {if(initialized)speak(it)};pending=null
            }
        },outputEngine)
    }
    private fun observeOutput(generation:Int) {
        tts?.setOnUtteranceProgressListener(object:android.speech.tts.UtteranceProgressListener() {
            private fun update(id:String?,action:()->Unit) {
                handler.post {if(generation==outputGeneration&&id!=null&&id==activeUtterance)action()}
            }
            override fun onStart(id:String?)=update(id) {clearSynthesisTimeout()}
            override fun onDone(id:String?)=update(id) {activeUtterance=null;clearSynthesisTimeout()}
            override fun onError(id:String?)=onError(id,TextToSpeech.ERROR)
            override fun onError(id:String?,errorCode:Int)=update(id) {
                activeUtterance=null;clearSynthesisTimeout();configuredLanguage=null
                status="음성 재생 실패 · 기기 음성팩을 확인하고 다시 듣기를 눌러 주세요."
                mutableOutputFailure.value=status
            }
            override fun onStop(id:String?,interrupted:Boolean)=update(id) {activeUtterance=null;clearSynthesisTimeout()}
        })
    }
    private fun clearSynthesisTimeout() {synthesisTimeout?.let {handler.removeCallbacks(it)};synthesisTimeout=null}
    fun refreshOutputEngine() {if(outputEngine!=defaultOutputEngine()||mutableOutputInitializationFailed.value)connectOutput()}
    fun setGentleOutput(value:Boolean) {
        stopSpeaking();configuredLanguage=null
        outputPreferences.edit().putBoolean("gentle",value).apply();mutableGentle.value=value
    }
    private fun voiceKey(tag:String)="voice:${outputEngine.orEmpty()}:${NavigationSpeechVoice.locale(tag).toLanguageTag()}"
    fun outputVoices(tag:String)=NavigationSpeechVoice.available(runCatching {tts?.voices.orEmpty()}.getOrDefault(emptySet()),NavigationSpeechVoice.locale(tag))
    fun selectedVoiceName(tag:String):String?=outputPreferences.getString(voiceKey(tag),null)
    fun chooseOutputVoice(tag:String,name:String?):Boolean {
        if(name!=null&&outputVoices(tag).none {it.name==name})return false
        stopSpeaking();configuredLanguage=null
        outputPreferences.edit().apply {if(name==null)remove(voiceKey(tag)) else putString(voiceKey(tag),name)}.apply()
        return true
    }
    fun resetOutputPreferences() {
        stopSpeaking();configuredLanguage=null;outputPreferences.edit().clear().apply();mutableGentle.value=true
    }
    fun openOutputSettings() {
        val intent=android.content.Intent("com.android.settings.TTS_SETTINGS").addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        try {context.startActivity(intent)} catch(_:android.content.ActivityNotFoundException) {status="기기 설정에서 글자 읽어주기와 오프라인 음성팩을 확인해 주세요."}
    }
    fun previewOutput(tag:String) {
        val code=io.cabinos.core.NavigationLanguages.resolve(tag)
        val maneuver=NavManeuver(NavPoint(0.0,0.0),"turn","right")
        speakNavigation(NavigationSpeech.guidance(maneuver,code,NavigationSpeechVoice.distance(300.0,tag)),NavigationSpeechVoice.locale(tag).toLanguageTag())
    }
    override fun speak(text:String) {
        // Informational cabin messages must not cut off a turn already being spoken.
        if(initialized) { if(!navigationSpeaking)speakNavigation(text,"ko-KR") } else pending=text
    }
    fun navigationLanguages():List<String> = runCatching {tts?.availableLanguages?.map {it.toLanguageTag()}?.sorted() ?: emptyList()}.getOrDefault(emptyList())
    val navigationSpeaking:Boolean get()=activeUtterance!=null||tts?.isSpeaking==true
    fun speakNavigation(text:String,tag:String,requestKey:String?=null):Boolean {
        if(!initialized)return false
        val locale=Locale.forLanguageTag(tag)
        if(configuredLanguage!=locale.toLanguageTag()) {
            val supported=tts?.setLanguage(locale) ?: TextToSpeech.LANG_NOT_SUPPORTED
            if(supported<TextToSpeech.LANG_AVAILABLE){status="${locale.getDisplayName(locale)} 음성팩 미지원/미설치 · 화면 안내 사용";return false}
            val voices=runCatching {tts?.voices.orEmpty()}.getOrDefault(emptySet())
            val selected=NavigationSpeechVoice.available(voices,locale).firstOrNull {it.name==selectedVoiceName(tag)}
                ?: NavigationSpeechVoice.select(voices,locale,tts?.voice)
            if(selected!=null&&tts?.setVoice(selected)!=TextToSpeech.SUCCESS) {
                status="음성을 선택하지 못했습니다 · 기기 음성팩을 확인해 주세요."
                return false
            }
            if(tts?.voice?.isNetworkConnectionRequired==true){status="이 언어의 오프라인 음성팩을 설치해 주세요.";return false}
            configuredLanguage=locale.toLanguageTag()
            // Preserve the engine's natural pitch; a small rate change must not become a synthetic pitch shift.
            tts?.setSpeechRate(if(mutableGentle.value)0.94f else 1.0f)
            voiceDescription=if((tts?.voice?.quality ?: 0)>=android.speech.tts.Voice.QUALITY_HIGH)"고품질 기기 음성" else "기기 기본 음성 · 품질은 음성팩에 따라 다릅니다"
        }
        if(navigationSpeaking && text==lastSpokenText && configuredLanguage==lastSpokenLanguage&&requestKey==lastSpokenKey)return true
        mutableOutputFailure.value=null
        lastSpokenText=text;lastSpokenLanguage=configuredLanguage;lastSpokenKey=requestKey
        clearSynthesisTimeout()
        val id="cabinos-${System.nanoTime()}"
        activeUtterance=id
        val result=tts?.speak(text,TextToSpeech.QUEUE_FLUSH,null,id)
        if(result==TextToSpeech.SUCCESS) {
            synthesisTimeout=Runnable {
                if(activeUtterance==id) {
                    stopSpeaking()
                    status="음성 합성이 지연되어 중지했습니다 · 화면을 확인하고 기기 음성 설정을 점검해 주세요."
                    mutableOutputFailure.value=status
                }
            }.also {handler.postDelayed(it,8_000)}
        } else activeUtterance=null
        status=if(result==TextToSpeech.SUCCESS)"${locale.getDisplayName(locale)} · $voiceDescription" else "음성 재생 불가 · 화면 안내 사용"
        if(result!=TextToSpeech.SUCCESS) {
            configuredLanguage=null
            mutableOutputFailure.value=status
        }
        return result==TextToSpeech.SUCCESS
    }
    override fun listen(onResult:(String)->Unit) { listenNavigation("ko-KR",onResult,{onResult(it)}) }
    fun listenNavigation(tag:String,onResult:(String)->Unit,onError:(String)->Unit,onReady:()->Unit={}) {
        cancelRecognition();val epoch=recognitionEpoch
        if(context.checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){onError("마이크 권한이 필요합니다. 주차 후 권한을 허용해 주세요.");return}
        if(!SpeechRecognizer.isRecognitionAvailable(context)){onError("음성 인식 서비스가 없습니다. 기기의 음성 인식 서비스를 설치/설정해 주세요.");return}
        fun finish(text:String?,error:String?) {
            if(epoch!=recognitionEpoch)return
            cancelRecognition()
            if(text.isNullOrBlank())onError(error ?: "말씀을 인식하지 못했습니다. 다시 시도해 주세요.") else onResult(text.trim().take(180))
        }
        try {
            val current=SpeechRecognizer.createSpeechRecognizer(context);recognizer=current
            current.setRecognitionListener(object:RecognitionListener {
                override fun onReadyForSpeech(params:Bundle?){if(epoch==recognitionEpoch){status="듣고 있습니다";onReady()}}
                override fun onBeginningOfSpeech(){}
                override fun onRmsChanged(rmsdB:Float){}
                override fun onBufferReceived(buffer:ByteArray?){}
                override fun onEndOfSpeech(){}
                override fun onError(error:Int){finish(null,when(error){
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS->"마이크 권한이 해제됐습니다. 주차 후 다시 허용해 주세요."
                    SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED,SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE->"선택한 언어의 음성 인식이 지원되지 않거나 언어팩이 없습니다. 언어/기기 설정을 확인하세요."
                    SpeechRecognizer.ERROR_NETWORK,SpeechRecognizer.ERROR_NETWORK_TIMEOUT->"음성 인식 서버에 연결하지 못했습니다. 네트워크를 확인해 주세요."
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT,SpeechRecognizer.ERROR_NO_MATCH->"말씀을 인식하지 못했습니다. 다시 시도해 주세요."
                    else->"음성 인식이 완료되지 않았습니다. 다시 시도해 주세요."
                })}
                override fun onResults(results:Bundle?){
                    val confidence=results?.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES)?.firstOrNull()
                    if(confidence!=null&&confidence>=0&&confidence<0.4f)finish(null,"말씀을 확실히 인식하지 못했습니다. 다시 말씀해 주세요.")
                    else finish(results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull(),null)
                }
                override fun onPartialResults(partialResults:Bundle?){}
                override fun onEvent(eventType:Int,params:Bundle?){}
            })
            timeout=Runnable {finish(null,"음성 입력 시간이 지났습니다. 다시 눌러 말씀해 주세요.")}.also {handler.postDelayed(it,30_000)}
            stopSpeaking()
            current.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE,tag)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS,1))
        }catch(_:RuntimeException){finish(null,"음성 인식을 시작하지 못했습니다. 마이크·기기 음성 서비스를 확인하세요.")}
    }
    fun stopSpeaking(){clearSynthesisTimeout();activeUtterance=null;pending=null;lastSpokenText=null;lastSpokenLanguage=null;lastSpokenKey=null;tts?.stop()}
    fun cancelRecognition(){recognitionEpoch++;timeout?.let {handler.removeCallbacks(it)};timeout=null;val old=recognizer;recognizer=null;runCatching {old?.cancel()};runCatching {old?.destroy()}}
    fun cancelListening(){cancelRecognition();stopSpeaking()}
    override fun close(){outputGeneration++;cancelListening();tts?.shutdown()}
}
