package io.cabinos.core

/** Offline instruction packs; speech engines independently decide which voices they support. */
object NavigationLanguages {
    val codes:List<String> get()=navLanguageTables.keys.toList()
    fun validTag(tag:String)=tag.length in 2..48&&Regex("^[A-Za-z]{2,3}(-[A-Za-z0-9]{2,8})*$").matches(tag)
    fun resolve(tag:String):String {
        val normalized=tag.replace('_','-')
        codes.firstOrNull {it.equals(normalized,true)}?.let {return it}
        val base=normalized.substringBefore('-').lowercase()
        if(base=="zh")return if(normalized.contains("Hant",true)||normalized.endsWith("TW",true)||normalized.endsWith("HK",true))"en" else "zh-Hans"
        if(base=="pt")return if(normalized.endsWith("BR",true))"pt-BR" else "pt-PT"
        if(base=="nb"||base=="nn")return "no"
        return codes.firstOrNull {it==base} ?: "en"
    }
    fun instruction(m:NavManeuver,tag:String):String {
        val code=resolve(tag)
        if(code=="ko")return NavigationGeometry.instruction(m)
        val table=navLanguageTables.getValue(code)()
        val type=m.type.takeIf {"$it|straight|0" in table} ?: "turn"
        val modifier=m.modifier.ifBlank {"straight"}.takeIf {"$type|$it|0" in table} ?: "straight"
        val exit=if(type in listOf("roundabout","rotary"))m.exit?.takeIf {it in 1..10} ?: 0 else 0
        val text=table.getValue("$type|$modifier|$exit")
        return text+(if(m.exit!=null&&m.exit>10&&type in listOf("roundabout","rotary"))" · #${m.exit}" else "")+if(m.road.isNotBlank())" · ${m.road.take(100)}" else ""
    }
    fun spoken(tag:String,distance:String,instruction:String):String = navDistancePhrases.getValue(resolve(tag)).replace("{distance}",distance).replace("{instruction_one}",instruction)
    fun upcomingArrival(tag:String,road:String=""):String = navDestinationLabels.getValue(resolve(tag))+
        if(road.isNotBlank())" · ${road.take(100)}" else ""
    fun arrival(tag:String,accessMeters:Double):String {
        if(resolve(tag)=="ko")return if(accessMeters>30)"자동차 도착 지점입니다. 목적지까지 약 ${accessMeters.toInt()}미터 남았습니다." else "목적지에 도착했습니다."
        return instruction(NavManeuver(NavPoint(0.0,0.0),"arrive"),tag)+if(accessMeters>30)" · ${accessMeters.toInt()} m" else ""
    }
}
fun DrivingRoute.localized(tag:String):DrivingRoute=copy(language=NavigationLanguages.resolve(tag),steps=steps.map {s->s.maneuver?.let {s.copy(instruction=NavigationLanguages.instruction(it,tag))} ?: s})

enum class NavigationVoiceAction { SEARCH, SELECT, START, STOP, REPEAT, CANCEL }
data class NavigationVoiceCommand(val action:NavigationVoiceAction,val query:String="",val index:Int=-1)
/** Exact short commands never consume a shop name that merely contains a command word. */
object NavigationVoice {
    fun parse(raw:String):NavigationVoiceCommand {
        val t=raw.trim().take(180).lowercase().trimEnd('.','!','?','。','！','？')
        val phrases=mapOf(
            NavigationVoiceAction.START to listOf("안내 시작","길 안내 시작","출발","start navigation","start guidance","start","iniciar navegación","démarrer la navigation","navigation starten","案内開始","导航开始","بدء الملاحة","начать навигацию"),
            NavigationVoiceAction.STOP to listOf("안내 종료","길 안내 종료","stop navigation","end navigation","stop","detener navegación","arrêter la navigation","navigation beenden","案内終了","停止导航","إيقاف الملاحة","остановить навигацию"),
            NavigationVoiceAction.REPEAT to listOf("안내 반복","다시 말해줘","다시 말해 줘","repeat","repeat directions","repetir","répéter","wiederholen","もう一度","重复","كرر","повтори"),
            NavigationVoiceAction.CANCEL to listOf("취소","cancel","cancelar","annuler","abbrechen","キャンセル","取消","إلغاء","отмена")
        )
        phrases.entries.firstOrNull {t in it.value}?.let {return NavigationVoiceCommand(it.key)}
        val numbers=listOf(listOf("1","one","first","일번","일 번","1번","첫 번째","uno","un","eins","一","一番"),listOf("2","two","second","이번","이 번","2번","두 번째","dos","deux","zwei","二","二番"),listOf("3","three","third","삼번","삼 번","3번","세 번째","tres","trois","drei","三","三番"),listOf("4","four","fourth","사번","사 번","4번","네 번째","cuatro","quatre","vier","四","四番"),listOf("5","five","fifth","오번","오 번","5번","다섯 번째","cinco","cinq","fünf","五","五番"),listOf("6","six","sixth","육번","육 번","6번","여섯 번째","seis","sechs","六","六番"))
        val number=numbers.indexOfFirst {t in it||t=="select ${it.first()}"||t=="${it.first()} 번"}
        if(number>=0)return NavigationVoiceCommand(NavigationVoiceAction.SELECT,index=number)
        val query=raw.trim().take(180).replace(Regex("^(navigate to |directions to |find |검색 )",RegexOption.IGNORE_CASE),"").replace(Regex(" (길 찾아줘|길 찾아 줘|찾아줘|찾아 줘)[.!?]*$"),"").trim()
        return NavigationVoiceCommand(NavigationVoiceAction.SEARCH,query=if(query.length>=2)query else "")
    }
}
