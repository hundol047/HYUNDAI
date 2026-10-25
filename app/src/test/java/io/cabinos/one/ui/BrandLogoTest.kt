package io.cabinos.one.ui

import io.cabinos.core.DemoActivityRepository
import org.junit.Assert.*
import org.junit.Test

class BrandLogoTest {
    private val base=DemoActivityRepository().activities().first().copy(category="쇼핑",simulated=false)
    @Test fun knownConvenienceBrandsHaveLogosButUnrelatedNamesDoNot() {
        listOf("CU 판교점","씨유 연세점","GS25 서울점","세븐일레븐 미래점","7-Eleven","이마트24 원주점","emart24").forEach {assertNotNull(it,brandLogo(base.copy(name=it)))}
        listOf("CUBIC 쇼핑몰","CUCKOO","GS 주유소","동네 편의점").forEach {assertNull(it,brandLogo(base.copy(name=it)))}
        assertNull(brandLogo(base.copy(name="CU",simulated=true)))
        assertNull(brandLogo(base.copy(name="CU",category="카페")))
    }
}
