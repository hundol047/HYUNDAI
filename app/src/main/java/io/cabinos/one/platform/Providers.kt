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
            "실차 연결" -> VehicleSample(timestampMs=System.currentTimeMillis(),connected=false,source=DataSource.VEHICLE)
            else -> old
        }.copy(timestampMs=System.currentTimeMillis())
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
    private val listener=object:LocationListener {
        override fun onLocationChanged(location:Location) { lastLocation=location;mutable.value=true }
        override fun onProviderDisabled(provider:String) { mutable.value=false }
    }
    override fun start() {
        if (context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED && context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)!=PackageManager.PERMISSION_GRANTED) { mutable.value=false;return }
        try {
            manager.getProviders(true).forEach { provider -> manager.requestLocationUpdates(provider,2000L,5f,listener,Looper.getMainLooper()) }
        } catch (_:SecurityException) { mutable.value=false }
    }
    override fun stop() { manager.removeUpdates(listener);mutable.value=false }
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
class AndroidSpeechProvider(private val context:Context):SpeechProvider {
    private var ready=false
    private var pending:String?=null
    var status="한국어 음성 엔진 준비 중"; private set
    private var tts:TextToSpeech?=null
    private var recognizer:SpeechRecognizer?=null
    init {
        tts=TextToSpeech(context) { result ->
            if(result==TextToSpeech.SUCCESS) {
                val supported=tts?.setLanguage(Locale.KOREAN) ?: TextToSpeech.LANG_NOT_SUPPORTED
                ready=supported!=TextToSpeech.LANG_NOT_SUPPORTED && supported!=TextToSpeech.LANG_MISSING_DATA
                status=if(ready) "한국어 음성 안내 사용 가능" else "한국어 TTS 음성팩 설치 필요"
                pending?.let { if(ready) speak(it) };pending=null
            } else status="TTS 사용 불가 · 화면 안내 사용"
        }
    }
    override fun speak(text:String) { if(ready) tts?.speak(text,TextToSpeech.QUEUE_FLUSH,null,"cabinos-${System.nanoTime()}") else pending=text }
    override fun listen(onResult:(String)->Unit) {
        if(context.checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED) { onResult("마이크 권한이 필요합니다. 명령 버튼도 사용할 수 있습니다.");return }
        if(!SpeechRecognizer.isRecognitionAvailable(context)) { onResult("음성 인식 서비스가 없습니다. 명령 버튼을 사용해 주세요.");return }
        recognizer?.destroy();recognizer=SpeechRecognizer.createSpeechRecognizer(context)
        recognizer?.setRecognitionListener(object:RecognitionListener {
            override fun onReadyForSpeech(params:Bundle?) { status="듣고 있습니다" }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB:Float) {}
            override fun onBufferReceived(buffer:ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onError(error:Int) { onResult("음성 인식이 완료되지 않았습니다. 명령 버튼으로 다시 시도해 주세요.") }
            override fun onResults(results:Bundle?) { results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let(onResult) }
            override fun onPartialResults(partialResults:Bundle?) {}
            override fun onEvent(eventType:Int,params:Bundle?) {}
        })
        recognizer?.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).putExtra(RecognizerIntent.EXTRA_LANGUAGE,"ko-KR")
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM).putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE,true))
    }
    fun cancelListening() { recognizer?.cancel();recognizer?.destroy();recognizer=null;pending=null;tts?.stop() }
    override fun close() { cancelListening();tts?.shutdown() }
}
