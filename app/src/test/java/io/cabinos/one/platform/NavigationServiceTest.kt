package io.cabinos.one.platform

import io.cabinos.core.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[29])
class NavigationServiceTest {
    @Test fun recordedSeoulToMiraeRoutePreservesNamwonjuExitDestinationSign() {
        val raw=javaClass.getResourceAsStream("/navigation/seoul-yonsei-live.json")!!.bufferedReader().use {it.readText()}
        val r=NavigationService.parseRoute(raw,NavPlace("연세대학교 미래캠퍼스",NavPoint(37.2800965,127.901503)))
        assertTrue(r.distanceMeters>100_000)
        assertTrue(r.steps.any {it.type=="off ramp"&&it.instruction.contains("남원주")&&it.instruction.contains("충주")})
    }

    @Test fun recordedInternationalRoadRouteParsesWithoutACountryBoundaryRestriction() {
        val r=NavigationService.parseRoute(captured("route-international.json"),NavPlace("Brussels",NavPoint(50.8466,4.3528)))
        NavigationGeometry.validateOrigin(r,NavPoint(48.8566,2.3522))
        assertTrue(r.distanceMeters>300_000);assertTrue(r.points.size>4000);assertTrue(r.steps.size>30)
    }
    private fun captured(name:String)=javaClass.getResourceAsStream("/shop-live/$name")!!.bufferedReader().use {it.readText()}
    @Test fun realCapturedShopsHaveNamesAddressesAndUsableRecordedCarRoutes() {
        val cases=org.json.JSONArray(captured("manifest.json"))
        for(i in 0 until cases.length()) {
            val c=cases.getJSONObject(i);val places=NavigationService.parsePlaces(captured(c.getString("searchCapture")))
            val selected=places.first {it.id==c.getString("selectedId")}
            assertTrue(selected.name.isNotBlank());assertTrue(selected.address.isNotBlank());assertEquals("OSM / Nominatim",selected.source)
            val route=NavigationService.parseRoute(captured(c.getString("capture")),selected)
            val origin=c.getJSONArray("origin");NavigationGeometry.validateOrigin(route,NavPoint(origin.getDouble(0),origin.getDouble(1)))
            assertTrue(route.points.size>1);assertTrue(route.steps.size>1)
        }
        assertTrue(NavigationService.parsePlaces(captured("search-2.json")).isEmpty())
    }
    @Test fun providerSuccessWithAnOriginSnappedAcrossTheOceanIsRejected() {
        val route=NavigationService.parseRoute(captured("route-ocean.json"),NavPlace("London",NavPoint(51.5074,-.1278)))
        try{NavigationGeometry.validateOrigin(route,NavPoint(40.7484,-73.9857));fail("cross-ocean snap accepted")}catch(e:IllegalArgumentException){assertTrue(e.message!!.contains("출발 위치"))}
    }
    @Test fun shopDetailsKeepBranchesAtOneCoordinateAndRejectUnsafeLinks() {
        val places=NavigationService.parsePlaces("""[{"place_id":1,"name":"카페 A","display_name":"A 지점 주소","lat":"37.5","lon":"127","type":"cafe","extratags":{"phone":"000-0000","website":"javascript:alert(1)","opening_hours":"Mo-Fr 09:00-18:00"}},{"place_id":2,"name":"카페 B","display_name":"B 지점 주소","lat":"37.5","lon":"127"}]""")
        assertEquals(2,places.size);assertEquals("카페",places[0].category);assertEquals("000-0000",places[0].phone);assertEquals("",places[0].website);assertTrue(places[0].openingHours.contains("09:00"))
    }
    @Test fun internationalSearchParsesCoordinatesAndRejectsMalformedEntries() {
        val result=NavigationService.parsePlaces("""[{"display_name":"東京, 日本","lat":"35.68","lon":"139.76"},{"display_name":"Paris, France","lat":"48.85","lon":"2.35"},{"display_name":"bad","lat":"NaN","lon":"1"},{"display_name":"missing"}]""")
        assertEquals(2,result.size);assertEquals(139.76,result[0].point.longitude,0.0)
    }
    @Test fun osrmRoadGeometryAndManeuversAreParsedInsteadOfDrawingAStraightLine() {
        val json="""{"code":"Ok","routes":[{"distance":3000,"duration":600,"geometry":{"coordinates":[[2.35,48.85],[2.35,48.86],[2.36,48.87]]},"legs":[{"steps":[{"name":"Rue de Rivoli","maneuver":{"type":"depart","location":[2.35,48.85]}},{"name":"Rue du Louvre","maneuver":{"type":"turn","modifier":"left","location":[2.35,48.86]}},{"name":"","maneuver":{"type":"arrive","location":[2.36,48.87]}}]}]}]}"""
        val route=NavigationService.parseRoute(json,NavPlace("Paris",NavPoint(48.87,2.36)))
        assertEquals(3,route.points.size);assertTrue(route.steps[1].instruction.contains("좌회전 · Rue du Louvre"));assertEquals(600.0,route.durationSeconds,0.0)
    }
    @Test fun capturedLiveRoutesFromFourCountriesParseWithOrderedInstructions() {
        val cases=listOf("seoul-station-south-korea" to NavPoint(37.5665,126.9779),"paris-france" to NavPoint(48.8584,2.2945),"new-york-usa" to NavPoint(40.7580,-73.9851),"tokyo-station-japan" to NavPoint(35.6900,139.7700))
        for((id,end) in cases) {
            val raw=javaClass.getResourceAsStream("/navigation-live/$id-driving-route.json")!!.bufferedReader().use {it.readText()}
            val route=NavigationService.parseRoute(raw,NavPlace(id,end))
            assertTrue(route.points.size>10);assertTrue(route.steps.size>3)
            assertTrue(route.steps.zipWithNext().all {(a,b)->a.atMeters<=b.atMeters})
            assertTrue(route.distanceMeters>1000);assertTrue(route.destinationAccessMeters<1000)
        }
    }
    @Test fun noRouteIsAnErrorNotInventedDrivingInstructions() {
        try{NavigationService.parseRoute("""{"code":"NoRoute","routes":[]}""",NavPlace("ocean",NavPoint(0.0,0.0)));fail("NoRoute accepted")}catch(_:IllegalStateException){}
    }
    @Test fun productionEndpointsRequireHttpsWithoutEmbeddedCredentials() {
        val service=NavigationService()
        service.configure("https://search.example.com","https://routes.example.com/osrm")
        assertEquals("https://routes.example.com/osrm",service.routeEndpoint)
        for(url in listOf("http://example.com","https://user:password@example.com","https://example.com/?key=secret")) {
            try{service.configure(url,"https://routes.example.com");fail("unsafe endpoint accepted")}catch(_:IllegalArgumentException){}
        }
    }
}
