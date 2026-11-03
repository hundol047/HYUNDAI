package io.cabinos.one.platform

import android.content.Context
import io.cabinos.core.*

/** Persist the chosen data source, never a trusted physical safety state. */
class RuntimePreferences(context: Context) {
    private val preferences = context.getSharedPreferences("vehicle-source", Context.MODE_PRIVATE)
    var vehiclePermissionRequested: Boolean
        get() = preferences.getBoolean("vehicle.permission-requested",false)
        set(value) { preferences.edit().putBoolean("vehicle.permission-requested",value).apply() }
    var appearance:AppearanceSettings
        get()=AppearanceCatalog.normalize(AppearanceSettings(preferences.getString("appearance.preset","graphite") ?: "graphite",preferences.getString("appearance.accent","") ?: "",preferences.getString("appearance.brightness","system") ?: "system",preferences.getBoolean("appearance.nightMap",true)))
        set(value) {val s=AppearanceCatalog.normalize(value);preferences.edit().putString("appearance.preset",s.preset).putString("appearance.accent",s.accent).putString("appearance.brightness",s.brightness).putBoolean("appearance.nightMap",s.nightMap).apply()}
    var navigationLanguage:String
        get()=preferences.getString("navigation.language","ko-KR")?.takeIf {NavigationLanguages.validTag(it)} ?: "ko-KR"
        set(value){if(NavigationLanguages.validTag(value))preferences.edit().putString("navigation.language",value).apply()}
    var scenario: String
        get() = preferences.getString("scenario", "충전") ?: "충전"
        set(value) { preferences.edit().putString("scenario", value).apply() }
}
