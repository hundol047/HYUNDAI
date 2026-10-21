plugins {
    id("com.android.application") version "8.7.3" apply false
    kotlin("android") version "2.0.21" apply false
    kotlin("multiplatform") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
    id("com.google.devtools.ksp") version "2.0.21-1.0.28" apply false
    id("com.google.dagger.hilt.android") version "2.52" apply false
}

plugins.withType<org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsRootPlugin> {
    extensions.configure<org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsRootExtension> {
        download = false
    }
}
