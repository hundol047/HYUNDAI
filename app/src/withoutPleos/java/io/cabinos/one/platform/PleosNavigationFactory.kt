package io.cabinos.one.platform

import android.content.Context

internal object PleosNavigationFactory {
    fun create(context: Context) = PleosNavigation(null)
}
