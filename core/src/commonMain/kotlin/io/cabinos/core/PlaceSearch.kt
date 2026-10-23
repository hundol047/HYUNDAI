package io.cabinos.core

import kotlin.math.*

data class PlaceSearchPage(val places:List<NavPlace>,val seenIds:List<String>,val hasMore:Boolean)
/** Explicit searches only. Nearby bounds never become a country restriction. */
object PlaceSearch {
    const val pageSize=6
    const val maxResults=18
    const val radiusMeters=20_000.0
    /** A bounded retry preserving every character; never remove a city/branch.
     * Only a multi-word Korean query with a city joined to a following locality
     * is eligible. The original query always runs first. */
    fun spacingSuggestion(query:String):String? {
        val words=clean(query,180).split(Regex("\\s+"))
        if(words.size<2)return null
        val cities=listOf("서울","부산","대구","인천","광주","대전","울산","세종","수원","성남","고양","용인","청주","천안","전주","포항","창원","김해","양산","춘천","원주","강릉","충주","제천","목포","여수","순천","경주","구미","안동","제주","서귀포")
        val last=words.last();val city=cities.firstOrNull {last.startsWith(it)&&last.length>=it.length+2&&last.all {c->c in '가'..'힣'}} ?: return null
        return (words.dropLast(1)+city+last.substring(city.length)).joinToString(" ")
    }
    private fun clean(s:String,limit:Int)=s.filter {it>=' '&&it!='\u007f'&&it !in '\u202a'..'\u202e'&&it !in '\u2066'..'\u2069'}.trim().take(limit)
    fun place(name:String,lat:Double,lon:Double,id:String="",address:String="",category:String="",phone:String="",website:String="",hours:String="",source:String="OSM / Nominatim"):NavPlace? {
        val point=NavPoint(lat,lon);val label=clean(name,240)
        if(!point.valid()||label.isBlank())return null
        val url=clean(website,500).takeIf {it.startsWith("https://")&&!it.contains('@')&&it.none {c->c.isWhitespace()}} ?: ""
        return NavPlace(label,point,clean(id,160),clean(address,500),clean(category,100),clean(phone,100),url,clean(hours,300),clean(source,100))
    }
    fun category(value:String)=when(value){"cafe"->"카페";"restaurant"->"식당";"fast_food"->"간편식";"bakery"->"베이커리";"convenience"->"편의점";"supermarket"->"슈퍼마켓";"mall"->"쇼핑몰";"pharmacy"->"약국";"fuel"->"주유소";"charging_station"->"충전소";else->clean(value,100)}
    fun results(places:List<NavPlace>,origin:NavPoint?,nearby:Boolean):List<NavPlace> {
        val unique=places.distinctBy {it.id.ifBlank {it.name.lowercase()+"|"+it.point}}
        return if(nearby){require(origin?.valid()==true);unique.filter {NavigationGeometry.distance(origin!!,it.point)<=radiusMeters}.sortedBy {NavigationGeometry.distance(origin!!,it.point)}.take(pageSize)} else unique.take(pageSize)
    }
    fun page(places:List<NavPlace>,origin:NavPoint?,nearby:Boolean,excluded:List<String>):PlaceSearchPage {
        val seen=(excluded+places.map {it.id}.filter {it.isNotBlank()}).distinct().take(maxResults)
        return PlaceSearchPage(results(places,origin,nearby),seen,places.size>=pageSize&&seen.size>excluded.size&&seen.size<maxResults)
    }
    fun requests(query:String,origin:NavPoint?=null,nearby:Boolean=false,excluded:List<String> = emptyList(),language:String="ko"):List<Map<String,String>> {
        require(NavigationLanguages.validTag(language))
        val q=clean(query,180);require(q.length>=2){"가게 이름·지점·도시 또는 주소를 2자 이상 입력하세요."}
        val ids=excluded.take(maxResults).filter {it.length in 1..160&&it.all {c->c.isLetterOrDigit()||c in "._:-"}}.distinct()
        val base=linkedMapOf("format" to "jsonv2","limit" to "$pageSize","accept-language" to "$language,en","namedetails" to "1","addressdetails" to "1","extratags" to "1","q" to q)
        if(ids.isNotEmpty()){base["exclude_ids"]=ids.joinToString(",");val numeric=ids.filter {it.all(Char::isDigit)};if(numeric.isNotEmpty())base["exclude_place_ids"]=numeric.joinToString(",")}
        if(!nearby)return listOf(base)
        require(origin?.valid()==true){"주변 가게 검색은 정확한 내 위치를 먼저 받아 주세요."}
        val p=origin!!;val latDelta=radiusMeters/111195.0;val lonDelta=latDelta/cos(p.latitude*PI/180).coerceAtLeast(.00001)
        val north=(p.latitude+latDelta).coerceAtMost(90.0);val south=(p.latitude-latDelta).coerceAtLeast(-90.0)
        val west=p.longitude-lonDelta;val east=p.longitude+lonDelta
        val ranges=when {lonDelta>=180->listOf(-180.0 to 180.0);west< -180->listOf(west+360 to 180.0,-180.0 to east);east>180->listOf(west to 180.0,-180.0 to east-360);else->listOf(west to east)}
        return ranges.map {(w,e)->base.toMutableMap().apply {put("viewbox","$w,$north,$e,$south");put("bounded","1");put("lat","${p.latitude}");put("lon","${p.longitude}");put("radius","${radiusMeters.toInt()}")}}
    }
}
