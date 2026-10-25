package io.cabinos.one.ui

import io.cabinos.core.NearbyActivity
import io.cabinos.one.R

internal fun brandLogo(place:NearbyActivity):RepresentativePhoto? {
    if(place.simulated||place.category!="쇼핑")return null
    val name=place.name.trim()
    return when {
        Regex("^(CU|씨유)(?:$|\\s|[가-힣])",RegexOption.IGNORE_CASE).containsMatchIn(name) -> RepresentativePhoto(R.drawable.brand_cu, "브랜드 로고 · 매장 사진 아님", "https://commons.wikimedia.org/wiki/File:CU_BI_(2017).svg")
        Regex("^GS25(?:$|\\s|[가-힣])",RegexOption.IGNORE_CASE).containsMatchIn(name) -> RepresentativePhoto(R.drawable.brand_gs25, "브랜드 로고 · 매장 사진 아님", "https://commons.wikimedia.org/wiki/File:GS25_bi_(2019).svg")
        Regex("^(세븐일레븐|7[ -]?eleven)(?:$|\\s|[가-힣])",RegexOption.IGNORE_CASE).containsMatchIn(name) -> RepresentativePhoto(R.drawable.brand_seven, "브랜드 로고 · 매장 사진 아님", "https://commons.wikimedia.org/wiki/File:7-Eleven_logo_2021.svg")
        Regex("^(이마트24|emart\\s?24)(?:$|\\s|[가-힣])",RegexOption.IGNORE_CASE).containsMatchIn(name) -> RepresentativePhoto(R.drawable.brand_emart, "브랜드 로고 · 매장 사진 아님", "https://commons.wikimedia.org/wiki/File:Emart_24_logo.svg")
        else -> null
    }
}
