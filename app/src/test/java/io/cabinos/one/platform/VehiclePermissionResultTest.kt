package io.cabinos.one.platform

import org.junit.Assert.*
import org.junit.Test

class VehiclePermissionResultTest {
    @Test fun unchangedGrantAndDenialDoNotReconnect() {
        assertFalse(vehiclePermissionChanged(mapOf("speed" to false,"energy" to true),mapOf("speed" to false,"energy" to true)))
        assertFalse(vehiclePermissionChanged(mapOf("speed" to false),mapOf("speed" to false)))
    }
    @Test fun changedGrantOrRevocationReconnectsToRevalidate() {
        assertTrue(vehiclePermissionChanged(mapOf("speed" to false),mapOf("speed" to true)))
        assertTrue(vehiclePermissionChanged(mapOf("speed" to true),mapOf("speed" to false)))
        assertFalse(vehiclePermissionChanged(emptyMap(),emptyMap()))
    }
}
