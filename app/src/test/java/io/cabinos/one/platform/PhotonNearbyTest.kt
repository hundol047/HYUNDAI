package io.cabinos.one.platform

import io.cabinos.core.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[29])
class PhotonNearbyTest {
    @Test fun documentedReverseQueriesAreBoundedAndCoverFourCategories() {
        val urls=PhotonNearby.urls(MapPosition())
        assertEquals(4,urls.size)
        assertTrue(urls.all {it.startsWith("https://photon.komoot.io/reverse?")&&it.contains("radius=2&limit=15")})
        assertTrue(urls[0].contains("amenity%3Acafe"));assertTrue(urls[2].contains("leisure%3Agarden"))
    }
    @Test fun registeredNamesCoordinatesAndTypesAreRequiredWithoutInventingEmptyResults() {
        fun feature(id:Int,name:String,key:String,value:String,lon:Double=127.111,lat:Double=37.395)="""{"type":"Feature","geometry":{"type":"Point","coordinates":[$lon,$lat]},"properties":{"osm_id":$id,"osm_type":"N","name":"$name","osm_key":"$key","osm_value":"$value"}}"""
        val items=listOf(feature(1,"카페","amenity","cafe"),feature(1,"중복","amenity","cafe"),feature(2,"정원","leisure","garden"),feature(3,"","amenity","cafe"),feature(4,"먼 가게","shop","mall",0.0,0.0),feature(5,"은행","amenity","bank"))
        val results=PhotonNearby.parse("""{"features":[${items.joinToString(",")}]}""",MapPosition())
        assertEquals(listOf("카페","정원"),results.map {it.name});assertTrue(results.none {it.simulated})
        assertTrue(results.all {it.description.contains("Photon")})
    }
}
