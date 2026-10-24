package io.cabinos.one.platform

import android.content.Context

/** Base build explicitly reports SDK absence. It supplies no fake SDK or brake signals. */
internal object PleosSdkFactory {
    fun create(@Suppress("UNUSED_PARAMETER") context: Context) = PleosVehicleReader(null)
}
