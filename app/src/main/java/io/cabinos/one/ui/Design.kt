package io.cabinos.one.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val Night=Color(0xFF080F15)
val Panel=Color(0xFF101C25)
val Line=Color(0xFF263842)
val Mint=Color(0xFF62E2C3)
val Blue=Color(0xFF58A9FF)
val Purple=Color(0xFFA59AFF)
val White=Color(0xFFF0F6FA)
val Muted=Color(0xFF8DABB9)
val Amber=Color(0xFFFFCB7A)
val Red=Color(0xFFFF8983)

@Composable fun CabinTheme(content:@Composable ()->Unit) {
    MaterialTheme(colorScheme=darkColorScheme(primary=Mint,onPrimary=Night,secondary=Blue,background=Night,surface=Panel,
        onBackground=White,onSurface=White,outline=Line),content=content)
}
@Composable fun SurfacePanel(modifier:Modifier=Modifier,accent:Color=Line,content:@Composable ColumnScope.()->Unit) {
    Column(modifier.background(Brush.linearGradient(listOf(Panel,Color(0xFF0C171F))),RoundedCornerShape(20.dp))
        .border(1.dp,accent.copy(alpha=.65f),RoundedCornerShape(20.dp)).padding(20.dp),content=content)
}
@Composable fun Pill(text:String,color:Color=Mint,modifier:Modifier=Modifier) {
    Row(modifier.background(color.copy(alpha=.10f),RoundedCornerShape(50)).border(1.dp,color.copy(alpha=.22f),RoundedCornerShape(50)).padding(horizontal=12.dp,vertical=6.dp),verticalAlignment=Alignment.CenterVertically) {
        Box(Modifier.size(5.dp).background(color,RoundedCornerShape(5.dp)));Spacer(Modifier.width(7.dp))
        Text(text,color=color,fontSize=11.sp,fontWeight=FontWeight.Medium,maxLines=1)
    }
}
@Composable fun SectionLabel(title:String,subtitle:String?=null,color:Color=Mint) {
    Row(verticalAlignment=Alignment.CenterVertically) {
        Box(Modifier.size(7.dp).background(color,RoundedCornerShape(2.dp)));Spacer(Modifier.width(8.dp))
        Text(title,fontSize=17.sp,fontWeight=FontWeight.SemiBold,color=White)
        subtitle?.let { Spacer(Modifier.width(12.dp));Text(it,fontSize=11.sp,color=Muted) }
    }
}
@Composable fun ActionButton(text:String,onClick:()->Unit,modifier:Modifier=Modifier,color:Color=Mint,enabled:Boolean=true,icon:ImageVector?=null) {
    Button(onClick=onClick,enabled=enabled,modifier=modifier.heightIn(min=48.dp),shape=RoundedCornerShape(12.dp),
        colors=ButtonDefaults.buttonColors(containerColor=color.copy(alpha=.14f),contentColor=color,disabledContainerColor=Line.copy(alpha=.3f)),
        border=BorderStroke(1.dp,color.copy(alpha=if(enabled).3f else .1f)),contentPadding=PaddingValues(horizontal=14.dp,vertical=10.dp)) {
        icon?.let { Icon(it,null,Modifier.size(18.dp));Spacer(Modifier.width(7.dp)) };Text(text,fontSize=13.sp,fontWeight=FontWeight.SemiBold)
    }
}
fun timeText(seconds:Long):String = "%02d:%02d".format(seconds.coerceAtLeast(0)/60,seconds.coerceAtLeast(0)%60)
