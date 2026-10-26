package io.cabinos.core

/** Spoken directions use maneuver data, never infer lanes or landmarks from a road label. */
object NavigationSpeech {
    fun guidance(m:NavManeuver,tag:String,distance:String,near:Boolean=false):String {
        val code=NavigationLanguages.resolve(tag)
        val clean=m.copy(road=m.road.replace(Regex("[\\r\\n\\t]+")," ").trim())
        if(code!="ko") {
            if(m.type=="arrive")return when(code) {
                "en"->"In $distance, you will reach the destination area."
                "fr"->"Dans $distance, vous arriverez à proximité de votre destination."
                "de"->"In $distance erreichen Sie den Zielbereich."
                "ja"->"${distance}先、目的地付近です。"
                "es","es-ES"->"En $distance, llegará a las inmediaciones de su destino."
                else->NavigationLanguages.spoken(tag,distance,NavigationLanguages.upcomingArrival(tag))
            }
            var instruction=NavigationLanguages.instruction(clean,tag)
            if(code=="ja"&&m.type in listOf("turn","continue","new name","end of road")) {
                val action=when(m.modifier) {
                    "left"->"左に曲がります"
                    "right"->"右に曲がります"
                    "slight left"->"左の道に進みます"
                    "slight right"->"右の道に進みます"
                    "sharp left"->"左に大きく曲がります"
                    "sharp right"->"右に大きく曲がります"
                    "uturn"->"ユーターンします"
                    else->"道なりに進みます"
                }
                val road=clean.road.replace(" · ","、").take(100)
                return "${distance}先、${action}。"+if(road.isNotBlank()&&!near)"${road}方面です。" else ""
            }
            // French display labels use infinitives; spoken directions address the driver.
            if(code=="fr") {
                val verbs=mapOf("Tourner" to "Tournez","Continuer" to "Continuez","Prendre" to "Prenez",
                    "Sortir" to "Sortez","Entrer" to "Entrez","Rester" to "Restez","Faire" to "Faites",
                    "Rejoindre" to "Rejoignez","Traverser" to "Traversez")
                val first=instruction.substringBefore(' ')
                verbs[first]?.let {instruction=it+instruction.removePrefix(first)}
            }
            val sentence=NavigationLanguages.spoken(tag,distance,instruction).trim()
            return if(sentence.lastOrNull() in listOf('.','。','!','！','؟'))sentence
                else sentence+if(code in listOf("ja","zh-Hans"))"。" else "."
        }
        val side=when {
            m.modifier.contains("left")->"왼쪽"
            m.modifier.contains("right")->"오른쪽"
            else->"앞쪽"
        }
        val turn=when(m.modifier) {
            "left"->"좌회전입니다."
            "right"->"우회전입니다."
            "uturn"->"유턴입니다."
            "slight left","slight right"->"$side 길로 진행하세요."
            "sharp left","sharp right"->"${side}으로 크게 돌아 진행하세요."
            else->"직진하세요."
        }
        val action=when(m.type) {
            "depart"->"안내 경로를 따라 출발하세요."
            "arrive"->"목적지 부근입니다."
            "merge"->"$side 도로로 합류하세요."
            "fork"->"갈림길에서 $side 길로 진행하세요."
            "on ramp"->"$side 진입로로 진입하세요."
            "off ramp"->"$side 출구로 나가세요."
            "roundabout","rotary"->m.exit?.takeIf {it>0}?.let {"회전교차로에서 ${ordinal(it)} 출구로 나가세요."}
                ?: "회전교차로로 진입하세요."
            "exit roundabout","exit rotary"->"회전교차로에서 나가세요."
            "end of road"->"도로 끝에서 $turn"
            else->turn
        }
        // Close to a turn, omit long road labels so the action is heard first.
        val prefix=if(m.type=="depart")"" else if(near)"잠시 후, " else "약 $distance 앞에서, "
        val road=clean.road.replace(" · ",", ").take(100)
        return prefix+action+if(!near&&road.isNotBlank()&&m.type!="arrive")" $road 방면입니다." else ""
    }

    private fun ordinal(exit:Int)=when(exit) {
        1->"첫 번째";2->"두 번째";3->"세 번째";4->"네 번째";5->"다섯 번째"
        6->"여섯 번째";7->"일곱 번째";8->"여덟 번째";9->"아홉 번째";10->"열 번째"
        else->"${exit}번째"
    }
}
