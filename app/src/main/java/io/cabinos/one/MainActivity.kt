package io.cabinos.one

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import dagger.hilt.android.AndroidEntryPoint
import io.cabinos.one.ui.CabinApp

@AndroidEntryPoint
class MainActivity:ComponentActivity() {
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window,false)
        // Keep OEM/system navigation visible. Forcing immersive mode opens a first-run
        // coach overlay and is inappropriate for Pleos split windows. CabinApp consumes safe insets.
        setContent { CabinApp() }
    }
}
