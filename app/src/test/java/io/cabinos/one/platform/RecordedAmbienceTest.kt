package io.cabinos.one.platform

import android.content.Context
import android.location.Location
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[29])
class RecordedAmbienceTest {
    @Test fun fourRecordingsAreDistinctAndPianoIsRemoved() {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val sounds=listOf("빗소리","계곡 소리","파도 소리","자연의 소리").map {RecordedAmbience.load(context,it)}
        sounds.forEach {assertTrue(it.size>=AmbientSamples.RATE*10);assertTrue(it.any {v->v!=0.toShort()})}
        assertEquals(4,sounds.map {it.contentHashCode()}.distinct().size)
        assertFalse("잔잔한 피아노" in AmbientSamples.presets)
    }
    @Test fun nearbyLocationRejectsStaleCoarseAndMissingAccuracy() {
        val provider=NearbyPlaces(ApplicationProvider.getApplicationContext())
        val f=Location("gps").apply {latitude=37.5;longitude=127.0;time=10000;accuracy=10f}
        assertTrue(provider.usableLocation(f,11000))
        f.accuracy=1000f;assertFalse(provider.usableLocation(f,11000))
        f.accuracy=10f;assertFalse(provider.usableLocation(f,21000))
        f.removeAccuracy();assertFalse(provider.usableLocation(f,11000))
    }
}
