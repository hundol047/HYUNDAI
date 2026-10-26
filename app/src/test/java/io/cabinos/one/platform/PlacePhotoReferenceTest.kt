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
    @Test fun currentOfficialThumbnailHostIsAcceptedWithoutOpeningArbitraryHosts() {
        assertTrue(PlacePhotoReference.thumbnailUrl("https://thumb.wikimedia.org/wikipedia/commons/thumb/a/ab/Park.jpg/330px-Park.jpg"))
        assertTrue(PlacePhotoReference.thumbnailUrl("https://upload.wikimedia.org/wikipedia/commons/thumb/a/ab/Park.jpg/320px-Park.jpg"))
        for(url in listOf("https://thumb.wikimedia.org.evil.test/wikipedia/commons/x.jpg","http://thumb.wikimedia.org/wikipedia/commons/x.jpg","https://user@thumb.wikimedia.org/wikipedia/commons/x.jpg","https://thumb.wikimedia.org:444/wikipedia/commons/x.jpg","https://thumb.wikimedia.org/other/x.jpg"))assertFalse(url,PlacePhotoReference.thumbnailUrl(url))
    }
    @Test fun wikipediaPhotoRequiresTheLinkedArticleCoordinates() {
        val json="""{"query":{"pages":{"1":{"pageimage":"Park.jpg","coordinates":[{"lat":37.4,"lon":127.1,"globe":"earth"}]}}}}"""
        assertEquals("File:Park.jpg",PlacePhotoReference.wikipediaFile(json,NavPoint(37.4,127.1)))
        assertNull(PlacePhotoReference.wikipediaFile(json,NavPoint(48.8,2.3)))
        assertNull(PlacePhotoReference.wikipediaFile(json.replace("earth","mars"),NavPoint(37.4,127.1)))
        assertTrue(PlacePhotoReference.wikipediaUrl(JSONObject("""{"wikipedia":"ko:서울숲"}"""))!!.startsWith("https://ko.wikipedia.org/"))
        assertNull(PlacePhotoReference.wikipediaUrl(JSONObject("""{"wikipedia":"evil.test:Park"}""")))
    }
    @Test fun nameSearchRejectsFilmBrandWrongBranchAndSimilarNames() {
        val json="""{"entities":{"Q1":{"labels":{"en":{"value":"Café de Flore"}},"claims":{"P31":[{"mainsnak":{"datavalue":{"value":{"id":"Q30022"}}}}],"P625":[{"mainsnak":{"datavalue":{"value":{"latitude":48.854,"longitude":2.332}}}}],"P18":[{"mainsnak":{"datavalue":{"value":"Cafe.jpg"}}}]}}}}"""
        val point=NavPoint(48.854,2.332)
        assertEquals("File:Cafe.jpg",PlacePhotoReference.namedFile(json,"Q1","Cafe de Flore",point))
        assertNull(PlacePhotoReference.namedFile(json.replace("Q30022","Q11424"),"Q1","Cafe de Flore",point))
        assertNull(PlacePhotoReference.namedFile(json.replace("Q30022","Q431289"),"Q1","Cafe de Flore",point))
        assertNull(PlacePhotoReference.namedFile(json,"Q1","Cafe de Flore",NavPoint(37.4,127.1)))
        assertNull(PlacePhotoReference.namedFile(json,"Q1","Cafe de Flore Annex",point))
    }
    @Test fun photoCandidateSearchIsBoundedAndRejectsInvalidIds() {
        assertEquals(listOf("Q1","Q2"),PlacePhotoReference.candidates("""{"search":[{"id":"Q1"},{"id":"../x"},{"id":"Q2"},{"id":"Q3"}]}"""))
    }
}
