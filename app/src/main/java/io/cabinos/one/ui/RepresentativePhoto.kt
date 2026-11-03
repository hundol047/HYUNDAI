package io.cabinos.one.ui

import androidx.annotation.DrawableRes
import io.cabinos.one.R

internal data class RepresentativePhoto(@DrawableRes val drawable:Int,val credit:String,val source:String)
internal const val REPRESENTATIVE_PHOTO_LABEL="업종 대표 이미지 · 실제 장소 사진 아님"
internal fun representativePhoto(category:String):RepresentativePhoto=when(category) {
    "카페" -> RepresentativePhoto(R.drawable.representative_cafe, "Andy Li · CC0", "https://commons.wikimedia.org/wiki/File:French_Coffee_Shop,_Dieppe_2026-05-11.jpg")
    "식당" -> RepresentativePhoto(R.drawable.representative_restaurant, "Basile Morin · CC BY-SA 4.0", "https://commons.wikimedia.org/wiki/File:Restaurant_room_of_Amantaka_luxury_Resort_%26_Hotel_in_Luang_Prabang_Laos.jpg")
    "산책" -> RepresentativePhoto(R.drawable.representative_walking, "Famartin · CC BY-SA 4.0", "https://commons.wikimedia.org/wiki/File:2021-11-02_13_26_27_Trees_changing_color_in_autumn_along_a_wooded_walking_path_in_the_Franklin_Glen_section_of_Chantilly,_Fairfax_County,_Virginia.jpg")
    "쇼핑" -> RepresentativePhoto(R.drawable.representative_shopping, "Xnatedawgx · CC BY-SA 4.0", "https://commons.wikimedia.org/wiki/File:The_Citadel_(mall)_interior,_Dillard%27s,_Colorado_Springs_2016-01-07.jpg")
    else -> representativePhoto("쇼핑")
}
