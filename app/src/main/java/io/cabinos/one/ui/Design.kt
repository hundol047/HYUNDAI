package io.cabinos.one.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import io.cabinos.core.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val LocalAppearance=staticCompositionLocalOf {AppearanceSettings()}
val LocalCabinPalette=staticCompositionLocalOf {AppearanceCatalog.palette(AppearanceSettings())}
private fun rgb(value:Int)=Color(value or (0xFF shl 24))
val Night:Color @Composable get()=rgb(LocalCabinPalette.current.background)
val Panel:Color @Composable get()=rgb(LocalCabinPalette.current.panel)
val Line:Color @Composable get()=rgb(LocalCabinPalette.current.line)
val Mint:Color @Composable get()=rgb(LocalCabinPalette.current.accent)
val Blue:Color @Composable get()=rgb(LocalCabinPalette.current.accent)
val Purple:Color @Composable get()=if(lightPalette())Color(0xFF65458A) else Color(0xFFB8AFD4)
val White:Color @Composable get()=rgb(LocalCabinPalette.current.text)
val Muted:Color @Composable get()=rgb(LocalCabinPalette.current.muted)
@Composable private fun lightPalette()=AppearanceCatalog.contrast(LocalCabinPalette.current.panel,0x000000)>7
val Amber:Color @Composable get()=if(lightPalette())Color(0xFF805000) else Color(0xFFFFCB7A)
val Red:Color @Composable get()=if(lightPalette())Color(0xFFB3261E) else Color(0xFFFF8983)

@Composable fun CabinTheme(appearance:AppearanceSettings=AppearanceSettings(),content:@Composable ()->Unit) {
    val palette=remember(appearance){AppearanceCatalog.palette(appearance)}
    CompositionLocalProvider(LocalAppearance provides appearance,LocalCabinPalette provides palette) {
        MaterialTheme(colorScheme=darkColorScheme(primary=Mint,onPrimary=Night,secondary=Blue,background=Night,surface=Panel,
            onBackground=White,onSurface=White,outline=Line,onSurfaceVariant=Muted,
            primaryContainer=rgb(AppearanceCatalog.blend(palette.panel,palette.accent,.14)),onPrimaryContainer=Mint,
            secondaryContainer=rgb(AppearanceCatalog.blend(palette.panel,palette.accent,.14)),onSecondaryContainer=White,
            tertiary=Mint,onTertiary=Night,tertiaryContainer=Panel,onTertiaryContainer=White,
            surfaceVariant=Panel,surfaceTint=Mint,error=Red,onError=Night,
            errorContainer=rgb(AppearanceCatalog.blend(palette.panel,0xFF8983,.14)),onErrorContainer=Red),content=content)
    }
}
@Composable fun SurfacePanel(modifier:Modifier=Modifier,accent:Color=Line,content:@Composable ColumnScope.()->Unit) {
    Column(modifier.background(Brush.linearGradient(listOf(Panel,Night)),RoundedCornerShape(20.dp))
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
