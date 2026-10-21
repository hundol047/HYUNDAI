package io.cabinos.one.platform

import android.content.Context

/** Persist the chosen data source, never a trusted physical safety state. */
class RuntimePreferences(context: Context) {
    private val preferences = context.getSharedPreferences("vehicle-source", Context.MODE_PRIVATE)
    var scenario: String
        get() = preferences.getString("scenario", "충전") ?: "충전"
        set(value) { preferences.edit().putString("scenario", value).apply() }
}
