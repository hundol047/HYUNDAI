package io.cabinos.one.platform

import io.cabinos.core.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[29])
class NearbyPlacesTest {
    @Test fun registeredCommonsPhotoRemainsAttachedToItsOwnPlace() {
        val raw="""{"elements":[{"type":"node","id":1,"lat":37.395,"lon":127.111,"tags":{"amenity":"cafe","name":"등록 장소","wikimedia_commons":"File:Cafe.jpg"}}]}"""
        val place=NearbyPlaces.parse(raw,MapPosition()).single()
        assertEquals("File:Cafe.jpg",place.commonsPhoto);assertFalse(place.simulated)
        listOf("Category:Cafes", "https://example.com/photo.jpg", "File:../secret.jpg", "File:Movie.mp4", "File:Bad\n.jpg").forEach {assertNull(PlacePhoto.file(it))}
    }

    @Test fun providerRuntimeErrorsNeverEraseExistingPlacesAsAnEmptySuccess() {
        for(elements in listOf("[]","[{\"type\":\"node\",\"id\":1}]")) {
            try {NearbyPlaces.parse("""{"remark":"runtime error: Query timed out","elements":$elements}""",MapPosition());fail("Partial provider response must fail")}
            catch(_:IllegalStateException){}
        }
    }
    @Test fun localizedOnlyNamesAreNotDroppedByTheAndroidParser() {
        val raw="""{"elements":[
          {"type":"node","id":20,"lat":37.395,"lon":127.111,"tags":{"amenity":"cafe","name:fr":"Café enregistré"}},
          {"type":"node","id":21,"lat":37.395,"lon":127.111,"tags":{"amenity":"cafe","name:ja":"喫茶店"}},
          {"type":"node","id":22,"lat":37.395,"lon":127.111,"tags":{"amenity":"cafe","name:pronunciation":"metadata"}}
        ]}"""
        assertEquals(listOf("Café enregistré","喫茶店"),NearbyPlaces.parse(raw,MapPosition()).map {it.name})
    }

    @Test fun registeredEnglishNamesSurviveMissingLocalNamesWithoutInventingUnnamedBusinesses() {
        val raw="""{"elements":[null,
          {"type":"node","id":9,"lat":37.395,"lon":127.111,"tags":{"amenity":"cafe","name:en":"Registered English name"}},
          {"type":"node","id":10,"lat":37.395,"lon":127.111,"tags":{"amenity":"cafe"}}
        ]}"""
        val result=NearbyPlaces.parse(raw,MapPosition())
        assertEquals(1,result.size);assertEquals("Registered English name",result.single().name)
        assertFalse(result.single().simulated)
    }
    @Test fun overpassNodesAndWayCentersUseActualCoordinatesAndRejectBadData() {
        // Deliberately synthetic protocol fixture; not evidence of a live provider request.
        val json="""{"elements":[
          {"type":"node","id":1,"lat":37.395,"lon":127.111,"tags":{"amenity":"cafe","name":"Cafe","name:ko":"한국어 카페"}},
          {"type":"way","id":2,"center":{"lat":37.394,"lon":127.112},"tags":{"leisure":"park","name":"공원"}},
          {"type":"node","id":3,"lat":0,"lon":0,"tags":{"amenity":"cafe","name":"먼 곳"}},
          {"type":"node","id":4,"tags":{"amenity":"restaurant","name":"좌표 없음"}},
          {"type":"node","id":5,"lat":37.395,"lon":127.111,"tags":{"amenity":"bank","name":"제외"}},
          {"type":"node","id":1,"lat":37.395,"lon":127.111,"tags":{"amenity":"cafe","name":"중복"}}
        ]}"""
        val items=NearbyPlaces.parse(json,MapPosition())
        assertEquals(2,items.size);assertEquals("한국어 카페",items[0].name)
        assertEquals("산책",items[1].category);assertFalse(items.any {it.simulated})
        assertTrue(items.all {it.latitude!=null && it.oneWaySeconds>=60})
    }
    @Test fun cafeHeavyResponseCannotCrowdOutOtherCategories() {
        val elements=(1..60).map {"""{"type":"node","id":$it,"lat":37.395,"lon":127.111,"tags":{"name":"Cafe $it","amenity":"cafe"}}"""}+
            listOf("amenity" to "restaurant","leisure" to "park","shop" to "mall").mapIndexed {i,(key,value)->"""{"type":"node","id":${100+i},"lat":37.395,"lon":127.111,"tags":{"name":"Place $i","$key":"$value"}}"""}
        val result=NearbyPlaces.parse("""{"elements":[${elements.joinToString(",") }]}""",MapPosition())
        assertEquals(setOf("카페","식당","산책","쇼핑"),result.map {it.category}.toSet())
        assertEquals(15,result.count {it.category=="카페"})
    }

}
