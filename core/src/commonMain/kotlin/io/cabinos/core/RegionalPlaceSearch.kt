package io.cabinos.core

data class RegionalSearchSplit(val name:String,val region:String)
data class RegionalSearchResult(val places:List<NavPlace>,val region:String="",val query:String="",val radius:Int=20000) {
    val notice:String get()=RegionalPlaceSearch.notice(region,query,places.isNotEmpty(),radius)
}

/** User-requested recovery only. Never silently drop a branch or replace a destination. */
object RegionalPlaceSearch {
    fun aliasKey(key:String)=key=="int_name"||Regex("(name|official_name|short_name|alt_name)(:[A-Za-z]{2,3}([-_][A-Za-z0-9]{2,8})*)?").matches(key)
    fun radius(kind:String)=when(kind){"station","gate","gatehouse","mall","attraction"->1200;"peak","saddle"->5000;else->20000}
    fun notice(region:String,query:String,hasResults:Boolean,radius:Int=20000):String=if(region.isBlank())"지역을 하나로 확인하지 못했습니다. 도시·지역명을 더 자세히 입력해 주세요."
        else "$region 중심 반경 ${if(radius%1000==0)"${radius/1000}" else "${radius/1000}.${radius%1000/100}"}km · '${query.replace(Regex("^\\[[a-z_]+\\]\\s*"),"")}' 주변 후보 검색입니다. 전체 입력과 정확히 일치하는 지점은 아닐 수 있습니다. 주소·지점을 확인하세요."+
            if(!hasResults)" 등록된 후보를 찾지 못했습니다. 지도에 없는 가게가 있을 수 있습니다." else ""
    fun splits(query:String):List<RegionalSearchSplit> {
        val words=query.trim().split(Regex("\\s+")).filter {it.isNotBlank()}
        if(query.length>180||words.size<2||query.any {it<' '})return emptyList()
        return listOf(2,1).filter {it<words.size}.map {n->
            RegionalSearchSplit(words.dropLast(n).joinToString(" "),words.takeLast(n).joinToString(" "))
        }.filter {it.name.length>=2&&it.region.length>=2}
    }
    fun normalized(value:String):String {
        val accents="àáâãäåāăąçćčďèéêëēėęěğìíîïīıłñńňòóôõöøōřśšşťùúûüūůýÿžźż"
        val plain=  "aaaaaaaaacccdeeeeeeeegiiiiiilnnnooooooorssstuuuuuuyyzzz"
        return value.lowercase().map {c->accents.indexOf(c).let {if(it>=0)plain[it] else c}}.filter {it.isLetterOrDigit()}.joinToString("")
    }
    fun regionMatches(query:String,names:List<String>,kind:String):Boolean =
        kind in setOf("administrative","city","town","village","suburb","neighbourhood","quarter","hamlet","municipality","borough","locality","island","peak","saddle","mall","station","gate","gatehouse","attraction")&&
            names.any {normalized(it)==normalized(query)}
    fun chooseRegion(places:List<NavPlace>,origin:NavPoint?):NavPlace? {
        val areas=setOf("city","town","village","administrative","suburb","neighbourhood")
        val distinct=mutableListOf<NavPlace>()
        // A city's station can carry the same name. Prefer the city within
        // the same urban cluster; never merge equally named distant cities.
        places.sortedBy {if(it.category in areas)0 else 1}.forEach {p->
            if(distinct.none {NavigationGeometry.distance(it.point,p.point)<if(it.category in areas&&p.category !in areas)5000 else 1000})distinct.add(p)
        }
        if(distinct.size<=1)return distinct.firstOrNull()
        if(origin?.valid()!=true)return null
        val nearest=distinct.sortedBy {NavigationGeometry.distance(origin,it.point)}
        val first=NavigationGeometry.distance(origin,nearest[0].point)
        val second=NavigationGeometry.distance(origin,nearest[1].point)
        return nearest[0].takeIf {first<50_000&&second-first>5000}
    }
    fun names(name:String):List<String> {
        val words=name.split(Regex("\\s+"))
        val generic=setOf("the","cafe","café","coffee","shop","hotel","padaria","카페","식당")
        val simplified=words.filter {it.lowercase() !in generic}.map {word->
            val w=word.replace(Regex("['’][sS]$"),"")
            if(w.lowercase()!="starbucks"&&w.matches(Regex("[A-Za-z]{5,}[sS]")))w.dropLast(1) else w
        }.joinToString(" ")
        val joined=if(words.size>1&&words.take(2).all {it.matches(Regex("[A-Za-z]+"))})
            (listOf(words[0]+words[1])+words.drop(2)).joinToString(" ") else name
        // Registered English brand alias; do not translate arbitrary shop names.
        val alias=if(words.firstOrNull()=="스타벅스")("Starbucks "+words.drop(1).joinToString(" ")).trim() else name
        val kind=when {
            words.firstOrNull()?.lowercase() in setOf("padaria","bakery","boulangerie","빵집")->"bakery"
            words.firstOrNull()?.lowercase() in setOf("hotel","호텔")->"hotel"
            words.any {it.lowercase() in setOf("cafe","café","카페")}->"cafe"
            else->""
        }
        val qualified=if(kind.isNotEmpty()&&simplified.isNotBlank())"[$kind] $simplified" else name
        return listOf(name,qualified,alias,simplified,joined).distinct().filter {it.length in 2..180}.take(4)
    }
    fun matchesName(query:String,candidate:String,regionAddress:String=""):Boolean {
        val generic=setOf("the","cafe","café","coffee","shop","hotel","padaria","bakery","boulangerie","카페","식당","호텔","빵집")
        fun word(raw:String):String {
            val n=normalized(raw.replace("스타벅스","Starbucks"))
            return if(n.matches(Regex("[a-z]{5,}s")))n.dropLast(1) else n
        }
        val tokens=query.split(Regex("\\s+")).toMutableList()
        if(tokens.size>1&&normalized(tokens.last()).length>=2&&normalized(regionAddress).contains(normalized(tokens.last())))tokens.removeAt(tokens.lastIndex)
        val required=tokens.filter {it.lowercase() !in generic}.map(::word).filter {it.length>=2}
        val label=candidate.split(Regex("[\\s'’.,()/–-]+")).joinToString(""){word(it)}
        return required.all {label.contains(it)}
    }
    fun acceptsCategory(query:String,category:String):Boolean {
        val words=query.lowercase().split(Regex("\\s+"))
        return when {
            words.firstOrNull() in setOf("hotel","호텔")->category in setOf("hotel","guest_house","hostel")
            words.firstOrNull() in setOf("padaria","bakery","boulangerie","빵집")->category in setOf("베이커리","카페","식당","bakery","cafe","restaurant")
            words.any {it in setOf("cafe","café","카페")}->category in setOf("카페","cafe")
            else->true
        }
    }
}
