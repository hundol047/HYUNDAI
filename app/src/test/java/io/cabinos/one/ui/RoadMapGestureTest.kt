package io.cabinos.one.ui

import android.view.MotionEvent
import android.view.View
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[29])
class RoadMapGestureTest {
    private fun map()=RoadMapView(ApplicationProvider.getApplicationContext()).apply {
        setUseDataConnection(false);setMultiTouchControls(true)
        val size=View.MeasureSpec.makeMeasureSpec(400,View.MeasureSpec.EXACTLY)
        measure(size,size);layout(0,0,400,400)
    }
    private fun touch(map:RoadMapView,action:Int,x:Float,y:Float) {
        val event=MotionEvent.obtain(0,20,action,x,y,0)
        try {map.dispatchTouchEvent(event)}finally{event.recycle()}
    }
    @Test fun smallTapJitterDoesNotDisableNavigationFollowing() {
        val map=map();var manual=0;map.onUserGesture={manual++}
        try {
            touch(map,MotionEvent.ACTION_DOWN,100f,100f)
            touch(map,MotionEvent.ACTION_MOVE,101f,101f)
            touch(map,MotionEvent.ACTION_UP,101f,101f)
            assertEquals(0,manual)
        }finally{map.onDetach()}
    }
    @Test fun twoFingerZoomDisablesFollowingEvenBeforeTheFirstFingerMoves() {
        val map=map();var manual=0;map.onUserGesture={manual++}
        try {
            touch(map,MotionEvent.ACTION_DOWN,100f,100f)
            val properties=Array(2){i->MotionEvent.PointerProperties().apply {id=i;toolType=MotionEvent.TOOL_TYPE_FINGER}}
            val coordinates=Array(2){i->MotionEvent.PointerCoords().apply {x=100f+i*80;y=100f;pressure=1f;size=1f}}
            val event=MotionEvent.obtain(0,20,MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),
                2,properties,coordinates,0,0,1f,1f,0,0,android.view.InputDevice.SOURCE_TOUCHSCREEN,0)
            try {map.dispatchTouchEvent(event)}finally{event.recycle()}
            assertEquals(1,manual)
            touch(map,MotionEvent.ACTION_CANCEL,100f,100f)
            map.gesturesEnabled=false
            touch(map,MotionEvent.ACTION_DOWN,100f,100f);touch(map,MotionEvent.ACTION_MOVE,200f,100f)
            assertEquals(1,manual)
        }finally{map.onDetach()}
    }
    @Test fun dragNotifiesOnceAndNextTapDoesNotKeepDragging() {
        val map=map();var manual=0;map.onUserGesture={manual++}
        try {
            touch(map,MotionEvent.ACTION_DOWN,100f,100f)
            touch(map,MotionEvent.ACTION_MOVE,160f,100f)
            touch(map,MotionEvent.ACTION_MOVE,180f,100f)
            touch(map,MotionEvent.ACTION_CANCEL,180f,100f)
            touch(map,MotionEvent.ACTION_DOWN,100f,100f)
            touch(map,MotionEvent.ACTION_MOVE,101f,101f)
            touch(map,MotionEvent.ACTION_UP,101f,101f)
            assertEquals(1,manual)
        }finally{map.onDetach()}
    }
}
