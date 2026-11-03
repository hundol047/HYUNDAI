package io.cabinos.one.platform

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.cabinos.core.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[29])
class TrafficLifecycleTest {
    @Suppress("UNCHECKED_CAST")
    @Test fun offlineLocationDisabledOrStopDropsPreviouslyLoadedTraffic() {
        for(locationOff in listOf(false,true,null)) {
            val context=ApplicationProvider.getApplicationContext<Context>()
            var now=System.currentTimeMillis();val engine=UnifiedVehicleContextEngine({now})
            engine.acceptVehicle(VehicleSample(gear=Gear.PARK,parkingReliable=true,timestampMs=now))
            now+=1600;engine.acceptVehicle(VehicleSample(gear=Gear.PARK,parkingReliable=true,timestampMs=now))
            val speech=AndroidSpeechProvider(context);val nav=LiveNavigation(context,engine,speech,clock={now})
            try {
                val f=NavFix(NavPoint(37.5,127.0),5.0,now,0.0)
                LiveNavigation::class.java.getDeclaredField("fix").apply {isAccessible=true}.set(nav,f)
                val state=LiveNavigation::class.java.getDeclaredField("mutable").apply {isAccessible=true}.get(nav) as MutableStateFlow<NavigationUi>
                state.value=state.value.copy(traffic=TrafficFlow(listOf(f.point,NavPoint(37.501,127.0)),20.0,60.0,1.0,false,now))
                assertNotNull(nav.state.value.traffic)
                if(locationOff==null)nav.stop()
                else {engine.setConnectivity(network=locationOff,location=!locationOff);nav.tick()}
                assertNull("Old road colour must not survive disabled data/location",nav.state.value.traffic)
            }finally{nav.close();speech.close()}
        }
    }
}
