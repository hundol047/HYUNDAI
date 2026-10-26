package io.cabinos.one.platform

import io.cabinos.core.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[29])
class PlacePhotoResolverTest {
    private val place=DemoActivityRepository().activities().first().copy(id="osm-node-1",name="Park",latitude=37.4,longitude=127.1,simulated=false)
    private val osm="""{"elements":[{"type":"node","id":1,"tags":{"image":"File:Broken.jpg","wikidata":"Q1","wikipedia":"en:Park"}}]}"""
    private val wiki="""{"query":{"pages":{"1":{"pageimage":"Park.jpg","coordinates":[{"lat":37.4,"lon":127.1,"globe":"earth"}]}}}}"""
    @Test fun brokenRegisteredPhotoAndFailedWikidataStillTryLinkedWikipedia()=runBlocking {
        val loaded=mutableListOf<String>()
        val resolver=PlacePhotoResolver(read={url->when {
            url.contains("openstreetmap")->osm
            url.contains("en.wikipedia")->wiki
            else->throw java.io.IOException("temporary failure")
        }},image={file->loaded.add(file);if(file=="File:Park.jpg")"park pixels" else null})
        assertEquals("park pixels",resolver.resolve(place)?.photo)
        assertEquals(listOf("File:Broken.jpg","File:Park.jpg"),loaded)
    }
    @Test fun repeatedBrokenReferenceIsOnlyDownloadedOncePerLookup()=runBlocking {
        var broken=0
        val resolver=PlacePhotoResolver(read={url->when {
            url.contains("openstreetmap")->osm
            url.contains("en.wikipedia")->wiki
            else->throw java.io.IOException()
        }},image={file->if(file=="File:Broken.jpg"){broken++;null}else "pixels"})
        assertNotNull(resolver.resolve(place.copy(commonsPhoto="File:Broken.jpg")))
        assertEquals(1,broken)
    }
    @Test fun cancellationStopsLookupWithoutTryingAnotherProvider()=runBlocking {
        var reads=0
        val resolver=PlacePhotoResolver<String>(read={reads++;throw CancellationException()},image={null})
        try {resolver.resolve(place);fail("Cancellation must propagate")}catch(_:CancellationException){}
        assertEquals(1,reads)
    }
    @Test fun demoPlacesNeverLookUpRealPhotos()=runBlocking {
        val resolver=PlacePhotoResolver<String>(read={error("network must not run")},image={error("image must not run")})
        assertNull(resolver.resolve(place.copy(simulated=true)))
    }
    @Test fun fullCommonsUrlInWikimediaTagIsRecognized() {
        assertEquals("File:Park photo.jpg",PlacePhotoReference.commons(org.json.JSONObject("""{"wikimedia_commons":"https://commons.wikimedia.org/wiki/File:Park%20photo.jpg?uselang=ko"}""")))
        assertNull(PlacePhotoReference.commons(org.json.JSONObject("""{"wikimedia_commons":"https://other.example/wiki/File:Wrong.jpg"}""")))
    }
}
