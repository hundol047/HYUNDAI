package io.cabinos.one.platform

/** Compare against the pre-request snapshot, not permissions already updated by Android. */
internal fun vehiclePermissionChanged(before: Map<String,Boolean>, result: Map<String,Boolean>) =
    result.any { (permission, granted) -> before[permission]?.let { it!=granted } == true }
