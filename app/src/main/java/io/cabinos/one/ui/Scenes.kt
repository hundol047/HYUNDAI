package io.cabinos.one.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.*

/** Original procedural artwork. No external map tiles or geographic data are implied. */
@Composable fun RouteScene(modifier:Modifier=Modifier,progress:Float=0f) {
    val Night=Night;val White=White;val Blue=Blue;val Mint=Mint;val Muted=Muted
    Canvas(modifier.background(Color(0xFF091622))) {
        val w=size.width;val h=size.height
        drawRect(Brush.radialGradient(listOf(Color(0xFF173448),Color(0xFF08131D)),center=Offset(w*.52f,h*.5f),radius=w*.9f))
        for(i in -7..9) {
            val x=i*w/9f
            drawLine(Color(0xFF203749),Offset(x,-h*.1f),Offset(x+w*.32f,h*1.1f),1.2f)
            drawLine(Color(0xFF203749),Offset(0f,i*h/7f),Offset(w,i*h/7f-h*.20f),1.2f)
        }
        for(i in 0..32) {
            val x=((i*137)%997)/997f*w;val y=((i*269)%991)/991f*h
            drawRoundRect(Color(0xFF162C3A),Offset(x,y),Size(w*.07f,h*.048f),cornerRadius=androidx.compose.ui.geometry.CornerRadius(3f),style=Stroke(1f))
        }
        val points=listOf(Offset(w*.32f,h*1.1f),Offset(w*.45f,h*.73f),Offset(w*.40f,h*.48f),Offset(w*.60f,h*.28f),Offset(w*.57f,-h*.1f))
        val path=Path().apply { moveTo(points[0].x,points[0].y);for(i in 1 until points.size) lineTo(points[i].x,points[i].y) }
        drawPath(path,Color(0xFF1F4054),style=Stroke(54f,cap=StrokeCap.Round,join=StrokeJoin.Round))
        drawPath(path,Color(0xFF112431),style=Stroke(45f,cap=StrokeCap.Round,join=StrokeJoin.Round))
        drawPath(path,Blue.copy(alpha=.15f),style=Stroke(26f,cap=StrokeCap.Round,join=StrokeJoin.Round))
        drawPath(path,Blue,style=Stroke(6f,cap=StrokeCap.Round,join=StrokeJoin.Round))
        val center=Offset(w*.43f,h*.64f)
        drawCircle(Blue.copy(alpha=.08f),58f,center);drawCircle(Blue.copy(alpha=.13f),40f,center)
        val arrow=Path().apply {moveTo(center.x,center.y-23);lineTo(center.x-18,center.y+17);lineTo(center.x,center.y+8);lineTo(center.x+18,center.y+17);close()}
        drawPath(arrow,White);drawPath(arrow,Blue,style=Stroke(3f))
        val alert=Offset(w*.48f,h*.40f)
        drawCircle(Red.copy(alpha=.12f),27f,alert);drawCircle(Red,12f,alert)
        drawLine(Night,alert.copy(y=alert.y-6),alert.copy(y=alert.y+2),3f);drawCircle(Night,1.5f,alert.copy(y=alert.y+6))
    }
}
@Composable fun MountainScene(modifier:Modifier=Modifier) {
    val Night=Night;val White=White;val Blue=Blue;val Mint=Mint;val Muted=Muted
    Canvas(modifier) {
        val w=size.width;val h=size.height
        drawRect(Brush.verticalGradient(listOf(Color(0xFF172446),Color(0xFF425177),Color(0xFFA18489),Color(0xFF243C4B))))
        drawCircle(Color(0xFFE8CCB6),h*.055f,Offset(w*.72f,h*.26f))
        for(i in 0..29) drawCircle(White.copy(alpha=.45f),if(i%3==0)1.4f else .7f,Offset(((i*137)%997)/997f*w,((i*83)%271)/700f*h))
        fun mountain(color:Color,base:Float,peaks:List<Pair<Float,Float>>) {
            val p=Path().apply {moveTo(0f,h);lineTo(0f,h*base);peaks.forEach{(x,y)->lineTo(w*x,h*y)};lineTo(w,h*base);lineTo(w,h);close()};drawPath(p,color)
        }
        mountain(Color(0xFF39415E),.65f,listOf(.12f to .46f,.24f to .58f,.38f to .35f,.51f to .57f,.66f to .43f,.82f to .62f))
        mountain(Color(0xFF1E3048),.8f,listOf(.18f to .6f,.31f to .72f,.45f to .58f,.62f to .78f,.81f to .54f))
        mountain(Color(0xFF0F2333),.94f,listOf(.20f to .82f,.47f to .9f,.66f to .77f,.90f to .84f))
        for(i in 0..6) {
            val x=w*(.79f+i*.035f);val bottom=h*.94f;val top=h*(.35f+(i%3)*.11f)
            drawLine(Color(0xFF0B1B2A),Offset(x,top),Offset(x,bottom),3f)
            for(j in 1..5) {
                val y=top+(bottom-top)*j/6f;val spread=(y-top)*.22f
                val p=Path().apply{moveTo(x,y-spread);lineTo(x-spread,y+spread);lineTo(x+spread,y+spread);close()};drawPath(p,Color(0xFF0B1B2A))
            }
        }
        drawRect(Brush.horizontalGradient(listOf(Night.copy(alpha=.65f),Color.Transparent,Night.copy(alpha=.1f))))
    }
}
@Composable fun CarIllustration(modifier:Modifier=Modifier) {
    val Night=Night;val White=White;val Blue=Blue;val Mint=Mint;val Muted=Muted
    Canvas(modifier) {
        val w=size.width;val h=size.height
        drawOval(Brush.radialGradient(listOf(Mint.copy(alpha=.18f),Color.Transparent)),Offset(w*.03f,h*.49f),Size(w*.94f,h*.5f))
        val body=Path().apply {moveTo(w*.18f,h*.72f);lineTo(w*.21f,h*.4f);quadraticTo(w*.26f,h*.25f,w*.34f,h*.23f);lineTo(w*.68f,h*.23f);quadraticTo(w*.77f,h*.25f,w*.81f,h*.4f);lineTo(w*.85f,h*.72f);quadraticTo(w*.5f,h*.87f,w*.18f,h*.72f);close()}
        drawPath(body,Brush.verticalGradient(listOf(Color(0xFF84A8B5),Color(0xFF1D3541),Color(0xFF0B1C25))))
        drawPath(body,Muted.copy(alpha=.6f),style=Stroke(1.5f))
        val glass=Path().apply{moveTo(w*.29f,h*.39f);lineTo(w*.36f,h*.28f);lineTo(w*.66f,h*.28f);lineTo(w*.73f,h*.39f);close()};drawPath(glass,Color(0xFF102732))
        drawLine(Mint,Offset(w*.24f,h*.54f),Offset(w*.77f,h*.54f),4f)
        drawLine(White,Offset(w*.24f,h*.55f),Offset(w*.24f,h*.64f),3f);drawLine(White,Offset(w*.77f,h*.55f),Offset(w*.77f,h*.64f),3f)
        drawRoundRect(Color(0xFF0B1820),Offset(w*.34f,h*.65f),Size(w*.32f,h*.08f),androidx.compose.ui.geometry.CornerRadius(4f))
    }
}
