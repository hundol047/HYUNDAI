package io.cabinos.one.platform

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[29])
class NetworkMonitorTest {
    @Test fun validatesInternetAndRecoversAfterLossWithoutLeakingCallbacks()=runTest {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val manager=context.getSystemService(ConnectivityManager::class.java)
        val shadow=shadowOf(manager)
        val network=manager.activeNetwork!!
        val capabilities=NetworkCapabilities().apply {shadowOf(this).addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)}
        shadow.setNetworkCapabilities(network,capabilities)
        val results=mutableListOf<Boolean>()
        val job=launch {NetworkMonitor(context).available.collect {results+=it}}
        runCurrent()
        assertEquals(listOf(false),results) // A captive portal is not usable internet.
        val callback=shadow.networkCallbacks.single()
        shadowOf(capabilities).addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        shadow.setNetworkCapabilities(network,capabilities)
        callback.onCapabilitiesChanged(network,capabilities);runCurrent()
        assertEquals(listOf(false,true),results)
        callback.onCapabilitiesChanged(network,capabilities);runCurrent()
        assertEquals(2,results.size)
        callback.onLost(network);runCurrent()
        assertFalse(results.last())
        callback.onAvailable(network);runCurrent()
        assertTrue(results.last())
        callback.onLost(org.robolectric.shadows.ShadowNetwork.newInstance(999));runCurrent()
        assertTrue(results.last()) // Losing a non-default network must not mark this one offline.
        job.cancelAndJoin()
        assertTrue(shadow.networkCallbacks.isEmpty())
    }
}
