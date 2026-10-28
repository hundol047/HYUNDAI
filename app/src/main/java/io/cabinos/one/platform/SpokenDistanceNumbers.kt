package io.cabinos.one.platform

/** Explicit readings for engines which misread short numeric navigation phrases. */
internal object SpokenDistanceNumbers {
    private val frenchSmall=listOf("zéro","un","deux","trois","quatre","cinq","six","sept","huit","neuf",
        "dix","onze","douze","treize","quatorze","quinze","seize")
    fun french(n:Int):String {
        require(n>=0)
        if(n<17)return frenchSmall[n]
        if(n<20)return "dix-"+frenchSmall[n-10]
        if(n<70) {
            val tens=listOf("","","vingt","trente","quarante","cinquante","soixante")[n/10]
            return tens+when(n%10){0->"";1->" et un";else->"-"+french(n%10)}
        }
        if(n<80)return "soixante"+(if(n==71)" et " else "-")+french(n-60)
        if(n<100)return if(n==80)"quatre-vingts" else "quatre-vingt-"+french(n-80)
        if(n<1000)return (if(n/100==1)"cent" else french(n/100)+" cent"+(if(n%100==0)"s" else ""))+
            if(n%100==0)"" else " "+french(n%100)
        val thousands=french(n/1000).let {if(it.endsWith("cents")||it.endsWith("vingts"))it.dropLast(1) else it}
        return (if(n/1000==1)"mille" else thousands+" mille")+
            if(n%1000==0)"" else " "+french(n%1000)
    }
    private val japaneseDigits=listOf("ゼロ","いち","に","さん","よん","ご","ろく","なな","はち","きゅう")
    fun japanese(n:Int):String {
        require(n>=0)
        if(n<10)return japaneseDigits[n]
        if(n>=10000)return japanese(n/10000)+"まん"+if(n%10000==0)"" else japanese(n%10000)
        if(n>=1000)return when(n/1000){1->"せん";3->"さんぜん";8->"はっせん";else->japanese(n/1000)+"せん"}+
            if(n%1000==0)"" else japanese(n%1000)
        if(n>=100)return when(n/100){1->"ひゃく";3->"さんびゃく";6->"ろっぴゃく";8->"はっぴゃく";else->japanese(n/100)+"ひゃく"}+
            if(n%100==0)"" else japanese(n%100)
        return (if(n/10==1)"" else japanese(n/10))+"じゅう"+if(n%10==0)"" else japanese(n%10)
    }
}
