package io.cabinos.one.platform

import android.content.Context
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.data.NavigationScreenRequest
import ai.pleos.playground.navi.data.DestinationInfo
import ai.pleos.playground.navi.data.DestinationArrivedInfo
import ai.pleos.playground.navi.constants.NavigationStatus

internal object PleosNavigationFactory {
    fun create(context: Context) = PleosNavigation({ object: PleosNavigationClient {
        private val helper=NaviHelper(context.applicationContext)
        private var listener: NaviHelperEventListener? = null
        @Volatile private var released=false
        private var destinationCallback: (DestinationEstimate?) -> Unit = {}
        override fun destinationListener(listener: (DestinationEstimate?) -> Unit) { destinationCallback=listener }
        override fun requestDestination() = helper.getDestinationInfo()
        override fun initialize(connected: () -> Unit, failed: (Throwable) -> Unit, error: (String) -> Unit) {
            val events=object: NaviHelperEventListener {
                override fun onError(errorCode: NaviErrorCode) { if(!released) error(errorCode.toString()) }
                override fun onDestinationInfo(destinationInfo: DestinationInfo) {
                    if(!released) destinationCallback(DestinationEstimate(destinationInfo.name,destinationInfo.remainTime,destinationInfo.remainDistance))
                }
                override fun onDestinationChanged() { if(!released) destinationCallback(null) }
                override fun onRouteCancelled() { if(!released) destinationCallback(null) }
                override fun onRouteEnded() { if(!released) destinationCallback(null) }
                override fun onDestinationArrived(destinationArrivedInfo: DestinationArrivedInfo) { if(!released) destinationCallback(null) }
                override fun onNavigationStatus(navigationStatus: NavigationStatus) {
                    if(!released && navigationStatus != NavigationStatus.ROUTE_DRIVE) destinationCallback(null)
                }
            }
            listener=events
            helper.initialize(onConnected={if(!released){helper.addListener(events);connected()}},onFailed={if(!released)failed(it)})
        }
        override fun open(screen: VehicleNavigationScreen, keyword: String) {
            helper.requestNavigationScreen(when(screen) {
                VehicleNavigationScreen.MAP -> NavigationScreenRequest.Foreground
                VehicleNavigationScreen.SEARCH -> NavigationScreenRequest.SearchPlace(keyword)
                VehicleNavigationScreen.CHARGERS -> NavigationScreenRequest.NearbyChargingStations
                VehicleNavigationScreen.RECENT -> NavigationScreenRequest.RecentDestinations
            })
        }
        override fun release() { released=true;listener?.let {helper.removeListener(it)};listener=null;helper.release() }
    } })
}
