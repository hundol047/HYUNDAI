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
    @Test fun recordingsAreNotJustVolumeAdjustedCopiesOfOneSound() {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val sounds=listOf("빗소리","계곡 소리","파도 소리","자연의 소리").map {RecordedAmbience.load(context,it)}
        for(a in sounds.indices) for(b in a+1 until sounds.size) {
            val count=minOf(sounds[a].size,sounds[b].size)
            var dot=0.0;var firstEnergy=0.0;var secondEnergy=0.0
            for(i in 0 until count) {
                val x=sounds[a][i].toDouble();val y=sounds[b][i].toDouble()
                dot+=x*y;firstEnergy+=x*x;secondEnergy+=y*y
            }
            val similarity=kotlin.math.abs(dot)/kotlin.math.sqrt(firstEnergy*secondEnergy)
            assertTrue("녹음 $a/$b 상관계수 $similarity",similarity<.8)
        }
    }
    @Test fun recordingsHaveNoHalfSecondSilenceAtEveryLoopBoundary() {
        val context=ApplicationProvider.getApplicationContext<Context>()
        listOf("빗소리","계곡 소리","파도 소리","자연의 소리").forEach {name->
            val pcm=RecordedAmbience.load(context,name)
            assertTrue("$name 시작 부분이 너무 오래 무음입니다",pcm.take(AmbientSamples.RATE/10).any {kotlin.math.abs(it.toInt())>5})
            assertTrue("$name 반복 경계가 튑니다",kotlin.math.abs(pcm.first().toInt()-pcm.last().toInt())<10)
            assertTrue("$name 클리핑",pcm.maxOf {kotlin.math.abs(it.toInt())}<32767)
        }
    }
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
