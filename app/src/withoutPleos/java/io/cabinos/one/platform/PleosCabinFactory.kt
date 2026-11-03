package io.cabinos.one.platform

import android.content.Context

internal object PleosCabinFactory {
    fun create(context:Context)=PleosCabinReader(null)
}
