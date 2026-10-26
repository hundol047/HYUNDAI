package io.cabinos.one.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.cabinos.core.NearbyActivity
import io.cabinos.one.platform.PlacePhoto

@Composable internal fun PlaceThumbnail(place:NearbyActivity,online:Boolean) {
    var photo by remember(place,online) {mutableStateOf<PlacePhoto.Photo?>(null)}
    var loading by remember(place,online) {mutableStateOf(online&&!place.simulated)}
    LaunchedEffect(place,online) {
        if(online&&!place.simulated) {
            kotlinx.coroutines.delay(300)
            try {photo=PlacePhoto.forPlace(place)}finally{loading=false}
        } else {photo=null;loading=false}
    }
    PlacePhotoFrame(place,photo,online,loading)
}

@Composable internal fun PlacePhotoFrame(place:NearbyActivity,photo:PlacePhoto.Photo?,online:Boolean,loading:Boolean=false) {
    val context=LocalContext.current
    val logo=brandLogo(place)
    val fallbackLabel=if(logo!=null)"브랜드 로고 · 매장 사진 아님" else if(place.simulated)"예시 장소 · 실제 사진 없음" else "실제 장소 사진 미확인"
    Column(Modifier.width(88.dp),horizontalAlignment=Alignment.CenterHorizontally) {
        Box(Modifier.size(88.dp).clip(RoundedCornerShape(12.dp)).background(Panel),contentAlignment=Alignment.Center) {
            val image=photo
            if(image!=null)Image(image.bitmap.asImageBitmap(),"${place.name} 등록 사진",Modifier.fillMaxSize(),contentScale=ContentScale.Crop)
            else if(logo!=null)Image(painterResource(logo.drawable),"${place.name} $fallbackLabel",Modifier.fillMaxSize(),contentScale=ContentScale.Fit)
            else Column(horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(6.dp)) {
                Text(place.name.take(2),color=Mint,fontSize=22.sp)
                Text(place.category,color=Muted,fontSize=11.sp)
            }
        }
        val image=photo
        if(image==null) {
            Text(fallbackLabel,color=Muted,fontSize=10.sp)
            if(loading)Text("장소 사진 확인 중…",color=Muted,fontSize=10.sp)
        }
        val credit=image?.credit?:logo?.credit
        val source=image?.source?:logo?.source
        if(credit!=null&&source!=null)Text(credit,color=Muted,fontSize=9.sp,modifier=Modifier.clickable(enabled=online){
            runCatching {context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW,android.net.Uri.parse(source)))}
        })
    }
}
