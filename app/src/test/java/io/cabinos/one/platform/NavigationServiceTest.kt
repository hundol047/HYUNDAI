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
    @Test fun internationalSearchParsesCoordinatesAndRejectsMalformedEntries() {
        val result=NavigationService.parsePlaces("""[{"display_name":"東京, 日本","lat":"35.68","lon":"139.76"},{"display_name":"Paris, France","lat":"48.85","lon":"2.35"},{"display_name":"bad","lat":"NaN","lon":"1"},{"display_name":"missing"}]""")
        assertEquals(2,result.size);assertEquals(139.76,result[0].point.longitude,0.0)
    }
    @Test fun osrmRoadGeometryAndManeuversAreParsedInsteadOfDrawingAStraightLine() {
        val json="""{"code":"Ok","routes":[{"distance":3000,"duration":600,"geometry":{"coordinates":[[2.35,48.85],[2.35,48.86],[2.36,48.87]]},"legs":[{"steps":[{"name":"Rue de Rivoli","maneuver":{"type":"depart","location":[2.35,48.85]}},{"name":"Rue du Louvre","maneuver":{"type":"turn","modifier":"left","location":[2.35,48.86]}},{"name":"","maneuver":{"type":"arrive","location":[2.36,48.87]}}]}]}]}"""
        val route=NavigationService.parseRoute(json,NavPlace("Paris",NavPoint(48.87,2.36)))
        assertEquals(3,route.points.size);assertTrue(route.steps[1].instruction.contains("좌회전 · Rue du Louvre"));assertEquals(600.0,route.durationSeconds,0.0)
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
