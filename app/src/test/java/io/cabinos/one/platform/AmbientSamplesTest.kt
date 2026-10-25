package io.cabinos.one.platform

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*

class AmbientSamplesTest {
    @Test fun everyPresetHasAudibleSignalWithoutClippingAndQuietLoopEdges() {
        AmbientSamples.presets.forEach {name->
            val pcm=AmbientSamples.create(name)
            val rms=sqrt(pcm.sumOf {it.toDouble()*it}/pcm.size)/Short.MAX_VALUE
            assertTrue("$name rms=$rms",rms in .12.. .16)
            assertTrue(pcm.maxOf {abs(it.toInt())}<Short.MAX_VALUE*.66)
            assertEquals(0,pcm.first().toInt());assertEquals(0,pcm.last().toInt())
        }
    }
    @Test fun presetsAreDistinctAndToneUsesPhoneAudibleMidrange() {
        val tone=AmbientSamples.create("잔잔한 톤")
        assertFalse(tone.contentEquals(AmbientSamples.create("자연의 소리")))
        assertFalse(AmbientSamples.create("백색 소음").contentEquals(AmbientSamples.create("자연의 소리")))
        fun energy(hz:Int)=hypot(tone.indices.sumOf {tone[it]*cos(2*PI*hz*it/AmbientSamples.RATE)},tone.indices.sumOf {tone[it]*sin(2*PI*hz*it/AmbientSamples.RATE)})
        assertTrue(energy(440)>energy(110)*100)
    }
}
