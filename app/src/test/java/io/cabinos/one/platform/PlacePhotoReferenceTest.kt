package io.cabinos.one.platform

import io.cabinos.core.NavPoint
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[29])
class PlacePhotoReferenceTest {
    @Test fun onlyTheRequestedOsmObjectCanSupplyPhotoTags() {
        val json="""{"elements":[{"type":"node","id":1,"tags":{"image":"File:Wrong.jpg"}},{"type":"way","id":2,"tags":{"image":"File:Right.jpg"}}]}"""
        assertEquals("File:Right.jpg",PlacePhotoReference.commons(PlacePhotoReference.tags(json,"osm-way-2")!!))
        assertNull(PlacePhotoReference.tags(json,"osm-way-3"))
        assertNull(PlacePhotoReference.osmUrl("osm-node-../../secret"))
        assertNull(PlacePhotoReference.wikidata(JSONObject("""{"brand:wikidata":"Q1"}""")))
    }
    @Test fun commonsImageLinksAreResolvedWithoutAcceptingArbitraryHosts() {
        assertEquals("File:Place photo.jpg",PlacePhotoReference.commons(JSONObject("""{"image":"https://commons.wikimedia.org/wiki/File:Place%20photo.jpg"}""")))
        assertNull(PlacePhotoReference.commons(JSONObject("""{"image":"https://example.com/wiki/File:Wrong.jpg"}""")))
    }
    @Test fun wikidataImageNeedsCoordinatesAtTheActualPlace() {
        val json="""{"entities":{"Q1":{"claims":{"P625":[{"mainsnak":{"datavalue":{"value":{"latitude":37.4,"longitude":127.1}}}}],"P18":[{"mainsnak":{"datavalue":{"value":"Park.jpg"}}}]}}}}"""
        assertEquals("File:Park.jpg",PlacePhotoReference.wikidataFile(json,"Q1",NavPoint(37.4,127.1)))
        assertNull(PlacePhotoReference.wikidataFile(json,"Q1",NavPoint(48.8,2.3)))
    }
}
