package io.cabinos.one.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.cabinos.core.NearbyActivity
import io.cabinos.one.platform.PlacePhoto

@Composable internal fun PlaceThumbnail(place:NearbyActivity,online:Boolean) {
    val context=LocalContext.current
    var photo by remember(place.id,place.commonsPhoto,online) {mutableStateOf<PlacePhoto.Photo?>(null)}
    var loading by remember(place.id,online) {mutableStateOf(online&&!place.simulated)}
    LaunchedEffect(place.id,place.commonsPhoto,online) {
        if(online&&!place.simulated) {
            kotlinx.coroutines.delay(300)
            try {photo=PlacePhoto.forPlace(place)}finally{loading=false}
        } else {photo=null;loading=false}
    }
    Column(Modifier.width(88.dp),horizontalAlignment=Alignment.CenterHorizontally) {
        Box(Modifier.size(88.dp).clip(RoundedCornerShape(12.dp)).background(Panel),contentAlignment=Alignment.Center) {
            val image=photo
            if(image!=null)Image(image.bitmap.asImageBitmap(),"${place.name} 등록 사진",Modifier.fillMaxSize(),contentScale=ContentScale.Crop)
            else Icon(when(place.category){"카페"->Icons.Outlined.LocalCafe;"식당"->Icons.Outlined.Restaurant;"산책"->Icons.Outlined.Park;else->Icons.Outlined.ShoppingBag},null,Modifier.size(36.dp),tint=Mint)
        }
        val image=photo
        if(image==null)Text(if(place.simulated)"예시 · 사진 없음" else if(loading)"사진 확인 중…" else "등록 사진 없음",color=Muted,fontSize=10.sp)
        else Text(image.credit,color=Muted,fontSize=9.sp,modifier=Modifier.clickable(enabled=online){
            runCatching {context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW,android.net.Uri.parse(image.source)))}
        })
    }
}
